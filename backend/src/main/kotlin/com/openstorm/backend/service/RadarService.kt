package com.openstorm.backend.service

import com.openstorm.backend.model.RadarFrame
import com.openstorm.backend.model.RadarFrameSummary
import com.openstorm.backend.model.RadarFramesTable
import com.openstorm.backend.model.RadarPlaybackManifest
import com.openstorm.backend.model.RadarStation
import com.openstorm.backend.provider.radar.RadarProvider
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarService(private val provider: RadarProvider) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Default retention: 24 hours. Frames older than this are cleaned up by ingestion. */
    val retentionHours: Int = 24

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<RadarStation> {
        return provider.getStations()
            .map { it.copy(distanceKm = haversineKm(lat, lon, it.lat, it.lon)) }
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    suspend fun getStation(stationId: String): RadarStation? {
        return provider.getStation(stationId)
    }

    suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame> {
        return provider.getFrames(stationId, product, count)
    }

    // ── Archive / Playback queries ──

    /**
     * List frames for a station+product within a time window.
     * Queries the radar_frames table directly.
     * Falls back to the provider if DB has no results.
     */
    suspend fun getArchiveFrames(
        stationId: String,
        product: String,
        start: Instant,
        end: Instant,
        limit: Int = 200,
    ): List<RadarFrameSummary> {
        return try {
            newSuspendedTransaction {
                RadarFramesTable.selectAll().where {
                    (RadarFramesTable.stationId eq stationId) and
                        (RadarFramesTable.product eq product) and
                        (RadarFramesTable.capturedAt greaterEq start) and
                        (RadarFramesTable.capturedAt lessEq end)
                }
                    .orderBy(RadarFramesTable.capturedAt, SortOrder.ASC)
                    .limit(limit)
                    .map { row ->
                        RadarFrameSummary(
                            timestamp = row[RadarFramesTable.capturedAt],
                            tileUrl = row[RadarFramesTable.tileBaseUrl],
                        )
                    }
            }
        } catch (e: Exception) {
            logger.warn("Archive DB query failed, falling back to provider: ${e.message}")
            provider.getFrames(stationId, product, limit.coerceAtMost(50))
                .map { RadarFrameSummary(timestamp = it.timestamp, tileUrl = it.tileUrl) }
        }
    }

    /**
     * Find the single frame nearest to the requested timestamp.
     */
    suspend fun getNearestFrame(
        stationId: String,
        product: String,
        target: Instant,
    ): RadarFrameSummary? {
        return try {
            newSuspendedTransaction {
                val windowStart = target.minus(30, ChronoUnit.MINUTES)
                val windowEnd = target.plus(30, ChronoUnit.MINUTES)

                RadarFramesTable.selectAll().where {
                    (RadarFramesTable.stationId eq stationId) and
                        (RadarFramesTable.product eq product) and
                        (RadarFramesTable.capturedAt greaterEq windowStart) and
                        (RadarFramesTable.capturedAt lessEq windowEnd)
                }
                    .orderBy(RadarFramesTable.capturedAt, SortOrder.ASC)
                    .map { row ->
                        RadarFrameSummary(
                            timestamp = row[RadarFramesTable.capturedAt],
                            tileUrl = row[RadarFramesTable.tileBaseUrl],
                        )
                    }
                    .minByOrNull { abs(it.timestamp.epochSecond - target.epochSecond) }
            }
        } catch (e: Exception) {
            logger.warn("Nearest frame DB query failed: ${e.message}")
            null
        }
    }

    /**
     * Build a playback manifest for a time window.
     */
    suspend fun getPlaybackManifest(
        stationId: String,
        product: String,
        start: Instant,
        end: Instant,
    ): RadarPlaybackManifest {
        val frames = getArchiveFrames(stationId, product, start, end)
        val actualStart = frames.firstOrNull()?.timestamp ?: start
        val actualEnd = frames.lastOrNull()?.timestamp ?: end

        return RadarPlaybackManifest(
            station = stationId,
            product = product,
            frames = frames,
            startTime = actualStart,
            endTime = actualEnd,
            frameCount = frames.size,
            retentionHours = retentionHours,
        )
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0

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
