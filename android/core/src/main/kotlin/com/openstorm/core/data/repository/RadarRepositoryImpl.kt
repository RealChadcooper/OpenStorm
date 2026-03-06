package com.openstorm.core.data.repository

import com.openstorm.core.data.local.dao.RadarStationDao
import com.openstorm.core.data.local.entity.RadarStationEntity
import com.openstorm.core.data.remote.OpenStormApi
import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.repository.RadarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class RadarRepositoryImpl @Inject constructor(
    private val api: OpenStormApi,
    private val stationDao: RadarStationDao,
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
                    tileUrl = dto.tileUrl,
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
            val response = api.getStations(0.0, 0.0, 10000.0)
            response.stations.find { it.id == stationId }?.products ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get products for $stationId")
            emptyList()
        }
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
