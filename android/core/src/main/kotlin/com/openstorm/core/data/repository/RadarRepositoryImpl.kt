package com.openstorm.core.data.repository

import com.openstorm.core.data.local.dao.RadarStationDao
import com.openstorm.core.data.local.entity.RadarStationEntity
import com.openstorm.core.data.remote.OpenStormApi
import com.openstorm.core.domain.model.ArchiveQuery
import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarFrameSummary
import com.openstorm.core.domain.model.RadarPlaybackManifest
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.repository.RadarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class RadarRepositoryImpl @Inject constructor(
    private val api: OpenStormApi,
    private val stationDao: RadarStationDao,
    @Named("apiBaseUrl") private val apiBaseUrl: String,
) : RadarRepository {

    override fun observeNearestStations(lat: Double, lon: Double, radiusKm: Double): Flow<List<RadarStation>> {
        return stationDao.observeAll().map { entities ->
            entities
                .map { it.toDomain(distanceKm = haversineKm(lat, lon, it.lat, it.lon)) }
                .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
                .sortedBy { it.distanceKm }
        }
    }

    override suspend fun getStation(stationId: String): RadarStation? {
        return stationDao.getById(stationId)?.toDomain()
    }

    override suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame> {
        return try {
            val response = api.getRadarFrames(stationId, product, count)
            response.frames.map { dto ->
                RadarFrame(
                    stationId = response.station,
                    product = response.product,
                    timestamp = Instant.parse(dto.timestamp),
                    tileUrl = rewriteTileUrl(dto.tileUrl),
                    expiresAt = Instant.parse(dto.expiresAt),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch radar frames for $stationId/$product")
            emptyList()
        }
    }

    override suspend fun getLatestFrame(stationId: String, product: String): RadarFrame? {
        return getFrames(stationId, product, 1).firstOrNull()
    }

    override suspend fun refreshStations(lat: Double, lon: Double, radiusKm: Double) {
        try {
            val response = api.getStations(lat, lon, radiusKm)
            val entities = response.stations.map { dto ->
                RadarStationEntity(
                    id = dto.id,
                    name = dto.name,
                    lat = dto.lat,
                    lon = dto.lon,
                    elevation = dto.elevation,
                    stationType = dto.stationType,
                    status = dto.status,
                )
            }
            stationDao.insertAll(entities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh radar stations")
        }
    }

    override suspend fun getAvailableProducts(stationId: String): List<String> {
        return try {
            // Look up the station from the local cache first
            val station = stationDao.getById(stationId)
            if (station != null) {
                // Use the station's own location to query nearby stations
                val response = api.getStations(station.lat, station.lon, 50.0)
                response.stations.find { it.id == stationId }?.products ?: DEFAULT_PRODUCTS
            } else {
                DEFAULT_PRODUCTS
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get products for $stationId")
            DEFAULT_PRODUCTS
        }
    }

    // ── Archive ──

    override suspend fun getArchiveFrames(query: ArchiveQuery): List<RadarFrameSummary> {
        return try {
            val response = api.getArchiveFrames(
                stationId = query.stationId,
                product = query.product,
                start = query.start.toString(),
                end = query.end.toString(),
                limit = query.limit,
            )
            response.frames.map { dto ->
                RadarFrameSummary(
                    timestamp = Instant.parse(dto.timestamp),
                    tileUrl = rewriteTileUrl(dto.tileUrl),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch archive frames")
            emptyList()
        }
    }

    override suspend fun getNearestFrame(
        stationId: String,
        product: String,
        target: Instant,
    ): RadarFrameSummary? {
        return try {
            val response = api.getNearestFrame(stationId, product, target.toString())
            response.frame?.let {
                RadarFrameSummary(
                    timestamp = Instant.parse(it.timestamp),
                    tileUrl = rewriteTileUrl(it.tileUrl),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch nearest frame")
            null
        }
    }

    override suspend fun getPlaybackManifest(query: ArchiveQuery): RadarPlaybackManifest? {
        return try {
            val dto = api.getPlaybackManifest(
                stationId = query.stationId,
                product = query.product,
                start = query.start.toString(),
                end = query.end.toString(),
            )
            RadarPlaybackManifest(
                station = dto.station,
                product = dto.product,
                frames = dto.frames.map { f ->
                    RadarFrameSummary(
                        timestamp = Instant.parse(f.timestamp),
                        tileUrl = rewriteTileUrl(f.tileUrl),
                    )
                },
                startTime = Instant.parse(dto.startTime),
                endTime = Instant.parse(dto.endTime),
                frameCount = dto.frameCount,
                retentionHours = dto.retentionHours,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch playback manifest")
            null
        }
    }

    /**
     * Rewrite tile URLs from the backend so they are reachable from this device.
     * In dev mode the backend returns URLs with "localhost" which is unreachable
     * from the Android emulator/device. Replace with the configured API base URL.
     */
    private fun rewriteTileUrl(tileUrl: String): String {
        // Match common localhost patterns the backend might return
        val localhostPattern = Regex("^https?://localhost(:\\d+)?")
        val match = localhostPattern.find(tileUrl) ?: return tileUrl
        return tileUrl.replaceRange(match.range, apiBaseUrl)
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private val DEFAULT_PRODUCTS = listOf("N0Q", "N0U")

        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_KM * c
        }
    }
}
