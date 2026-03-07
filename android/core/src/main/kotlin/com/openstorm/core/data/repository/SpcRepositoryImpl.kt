package com.openstorm.core.data.repository

import com.openstorm.core.domain.model.GeoPolygon
import com.openstorm.core.domain.model.LatLon
import com.openstorm.core.domain.model.OutlookType
import com.openstorm.core.domain.model.SpcMesoscaleDiscussion
import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcRiskArea
import com.openstorm.core.domain.model.SpcRiskLevel
import com.openstorm.core.domain.model.SpcWatch
import com.openstorm.core.domain.model.WatchType
import com.openstorm.core.domain.repository.SpcRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches SPC products directly from spc.noaa.gov GeoJSON endpoints.
 *
 * Endpoints:
 *   - Day 1 Categorical: https://www.spc.noaa.gov/products/outlook/day1otlk_cat.lyr.geojson
 *   - Day 2 Categorical: https://www.spc.noaa.gov/products/outlook/day2otlk_cat.lyr.geojson
 *   - Day 3 Categorical: https://www.spc.noaa.gov/products/outlook/day3otlk_cat.lyr.geojson
 *   - Active MDs:        https://www.spc.noaa.gov/products/md/md.geojson (not official)
 *   - Active Watches:    https://www.spc.noaa.gov/products/watch/activeWW.geojson (not official)
 *
 * The SPC also provides KML and shapefile formats, but GeoJSON is simplest
 * for client-side parsing without native libraries.
 */
@Singleton
class SpcRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpcRepository {

    companion object {
        private const val SPC_BASE = "https://www.spc.noaa.gov/products/outlook"
        private const val DAY1_CAT = "$SPC_BASE/day1otlk_cat.lyr.geojson"
        private const val DAY2_CAT = "$SPC_BASE/day2otlk_cat.lyr.geojson"
        private const val DAY3_CAT = "$SPC_BASE/day3otlk_cat.lyr.geojson"

        private const val WATCHES_URL = "https://www.spc.noaa.gov/products/watch/activeWW.geojson"

        // Cache TTL: 10 minutes (SPC updates outlooks ~5 times/day)
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        private val USER_AGENT = "(OpenStorm, contact@openstorm.app)"
    }

    // In-memory cache
    private val cacheMutex = Mutex()
    private var cachedOutlooks = mutableMapOf<Int, CachedValue<SpcOutlook>>()
    private var cachedMDs: CachedValue<List<SpcMesoscaleDiscussion>>? = null
    private var cachedWatches: CachedValue<List<SpcWatch>>? = null

    override suspend fun getOutlook(day: Int): SpcOutlook? {
        val cached = cacheMutex.withLock { cachedOutlooks[day] }
        if (cached != null && !cached.isExpired()) return cached.value

        return withContext(Dispatchers.IO) {
            try {
                val url = when (day) {
                    1 -> DAY1_CAT
                    2 -> DAY2_CAT
                    3 -> DAY3_CAT
                    else -> return@withContext null
                }

                val json = fetchJson(url) ?: return@withContext null
                val outlook = parseOutlookGeoJson(json, day)
                cacheMutex.withLock {
                    cachedOutlooks[day] = CachedValue(outlook)
                }
                outlook
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch SPC Day $day outlook")
                null
            }
        }
    }

    override suspend fun getActiveMesoscaleDiscussions(): List<SpcMesoscaleDiscussion> {
        val cached = cacheMutex.withLock { cachedMDs }
        if (cached != null && !cached.isExpired()) return cached.value

        return withContext(Dispatchers.IO) {
            try {
                // SPC doesn't provide an official MD GeoJSON endpoint.
                // We parse from the watch/warning feed which sometimes includes MDs,
                // or we scrape the MD product page.
                // For now, return empty — MDs will be populated when SPC adds a GeoJSON feed
                // or we add HTML scraping.
                val mds = emptyList<SpcMesoscaleDiscussion>()
                cacheMutex.withLock { cachedMDs = CachedValue(mds) }
                mds
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch SPC mesoscale discussions")
                emptyList()
            }
        }
    }

    override suspend fun getActiveWatches(): List<SpcWatch> {
        val cached = cacheMutex.withLock { cachedWatches }
        if (cached != null && !cached.isExpired()) return cached.value

        return withContext(Dispatchers.IO) {
            try {
                val json = fetchJson(WATCHES_URL) ?: return@withContext emptyList()
                val watches = parseWatchesGeoJson(json)
                cacheMutex.withLock { cachedWatches = CachedValue(watches) }
                watches
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch SPC watches")
                emptyList()
            }
        }
    }

    override suspend fun refresh() {
        cacheMutex.withLock {
            cachedOutlooks.clear()
            cachedMDs = null
            cachedWatches = null
        }
        // Pre-fetch Day 1
        getOutlook(1)
        getActiveWatches()
    }

    // ── GeoJSON parsing ──

    private fun parseOutlookGeoJson(json: String, day: Int): SpcOutlook {
        val root = JSONObject(json)
        val features = root.getJSONArray("features")

        val riskAreas = mutableListOf<SpcRiskArea>()
        var issuedAt = Instant.now()
        var expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")

            // SPC uses different property names across products
            val label = properties.optString("LABEL", "")
                .ifEmpty { properties.optString("LABEL2", "") }
                .ifEmpty { properties.optString("dn", "") }
                .uppercase()

            val riskLevel = parseRiskLevel(label) ?: continue

            // Parse valid time if available
            val validStr = properties.optString("VALID", "")
            val expireStr = properties.optString("EXPIRE", "")
            if (validStr.isNotEmpty()) {
                issuedAt = parseSpcTimestamp(validStr) ?: issuedAt
            }
            if (expireStr.isNotEmpty()) {
                expiresAt = parseSpcTimestamp(expireStr) ?: expiresAt
            }

            val geometry = parseGeometry(feature.getJSONObject("geometry"))
            if (geometry != null) {
                riskAreas.add(
                    SpcRiskArea(
                        riskLevel = riskLevel,
                        label = riskLevel.label,
                        geometry = geometry,
                    )
                )
            }
        }

        // Sort by severity (lowest first so they render bottom-up on the map)
        riskAreas.sortBy { it.riskLevel.ordinal }

        return SpcOutlook(
            day = day,
            type = OutlookType.CATEGORICAL,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            riskAreas = riskAreas,
        )
    }

    private fun parseWatchesGeoJson(json: String): List<SpcWatch> {
        val root = JSONObject(json)
        val features = root.getJSONArray("features")
        val watches = mutableListOf<SpcWatch>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")

            val number = properties.optInt("WN", 0)
            if (number == 0) continue

            val typeStr = properties.optString("TYPE", "").uppercase()
            val watchType = when {
                typeStr.contains("TORNADO") -> WatchType.TORNADO
                else -> WatchType.SEVERE_THUNDERSTORM
            }

            val issuedStr = properties.optString("ISSUED", "")
            val expireStr = properties.optString("EXPIRED", "")
                .ifEmpty { properties.optString("EXPIRE", "") }

            val geometry = parseGeometry(feature.getJSONObject("geometry"))

            watches.add(
                SpcWatch(
                    id = "WW-$number",
                    number = number,
                    type = watchType,
                    issuedAt = parseSpcTimestamp(issuedStr) ?: Instant.now(),
                    expiresAt = parseSpcTimestamp(expireStr)
                        ?: Instant.now().plus(6, ChronoUnit.HOURS),
                    summary = properties.optString("WATCH", "Watch #$number"),
                    geometry = geometry,
                )
            )
        }

        return watches
    }

    private fun parseRiskLevel(label: String): SpcRiskLevel? {
        return when {
            label.contains("HIGH") -> SpcRiskLevel.HIGH
            label.contains("MDT") || label.contains("MODERATE") -> SpcRiskLevel.MDT
            label.contains("ENH") || label.contains("ENHANCED") -> SpcRiskLevel.ENH
            label.contains("SLGT") || label.contains("SLIGHT") -> SpcRiskLevel.SLGT
            label.contains("MRGL") || label.contains("MARGINAL") -> SpcRiskLevel.MRGL
            label.contains("TSTM") || label.contains("THUNDER") || label.contains("GENERAL") -> SpcRiskLevel.TSTM
            // Numeric DN values used in some GeoJSON products
            label == "2" -> SpcRiskLevel.TSTM
            label == "3" -> SpcRiskLevel.MRGL
            label == "4" -> SpcRiskLevel.SLGT
            label == "5" -> SpcRiskLevel.ENH
            label == "6" -> SpcRiskLevel.MDT
            label == "8" -> SpcRiskLevel.HIGH
            else -> null
        }
    }

    private fun parseGeometry(geometry: JSONObject): GeoPolygon? {
        val type = geometry.getString("type")
        val coordinates = geometry.getJSONArray("coordinates")

        return when (type) {
            "Polygon" -> parsePolygonCoords(coordinates)
            "MultiPolygon" -> {
                // Take the first polygon for simplicity
                // (most SPC risk areas are single polygons)
                if (coordinates.length() > 0) {
                    parsePolygonCoords(coordinates.getJSONArray(0))
                } else null
            }
            else -> null
        }
    }

    private fun parsePolygonCoords(rings: JSONArray): GeoPolygon? {
        val coordsList = mutableListOf<List<LatLon>>()
        for (r in 0 until rings.length()) {
            val ring = rings.getJSONArray(r)
            val points = mutableListOf<LatLon>()
            for (p in 0 until ring.length()) {
                val point = ring.getJSONArray(p)
                // GeoJSON is [lon, lat]
                points.add(LatLon(lat = point.getDouble(1), lon = point.getDouble(0)))
            }
            coordsList.add(points)
        }
        return if (coordsList.isNotEmpty()) GeoPolygon(coordsList) else null
    }

    private fun parseSpcTimestamp(str: String): Instant? {
        if (str.isBlank()) return null
        return try {
            // SPC uses yyyyMMddHHmm format
            val cleaned = str.replace("[^0-9]".toRegex(), "")
            if (cleaned.length >= 12) {
                val formatted = "${cleaned.substring(0, 4)}-${cleaned.substring(4, 6)}-${cleaned.substring(6, 8)}" +
                    "T${cleaned.substring(8, 10)}:${cleaned.substring(10, 12)}:00Z"
                Instant.parse(formatted)
            } else null
        } catch (e: Exception) {
            Timber.d("Could not parse SPC timestamp: $str")
            null
        }
    }

    private fun fetchJson(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("SPC fetch failed: ${response.code} for $url")
                return null
            }
            response.body?.string()
        }
    }

    private data class CachedValue<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }
}
