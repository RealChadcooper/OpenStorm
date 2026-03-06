package com.openstorm.test

import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarProduct
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.model.StationStatus
import com.openstorm.core.domain.model.StationType
import com.openstorm.core.domain.repository.RadarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

class FakeRadarRepository : RadarRepository {

    val stationsFlow = MutableStateFlow<List<RadarStation>>(emptyList())

    val testStation = RadarStation(
        id = "KTLX",
        name = "Oklahoma City, OK",
        lat = 35.3331,
        lon = -97.2778,
        elevation = 370.0,
        stationType = StationType.WSR88D,
        status = StationStatus.ACTIVE,
        distanceKm = 15.2,
        products = listOf("N0Q", "N0U"),
    )

    init {
        stationsFlow.value = listOf(testStation)
    }

    override fun observeNearestStations(lat: Double, lon: Double, radiusKm: Double): Flow<List<RadarStation>> {
        return stationsFlow
    }

    override suspend fun getStation(stationId: String): RadarStation? {
        return stationsFlow.value.find { it.id == stationId }
    }

    override suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame> {
        val now = Instant.now()
        return (0 until count).reversed().map { i ->
            RadarFrame(
                stationId = stationId,
                product = product,
                timestamp = now.minus((i * 5).toLong(), ChronoUnit.MINUTES),
                tileUrl = "https://test.openstorm.app/tiles/$stationId/$product/frame_$i.webp",
                expiresAt = now.plus(10, ChronoUnit.MINUTES),
            )
        }
    }

    override suspend fun getLatestFrame(stationId: String, product: String): RadarFrame? {
        return getFrames(stationId, product, 1).firstOrNull()
    }

    override suspend fun refreshStations(lat: Double, lon: Double, radiusKm: Double) {
        // No-op for tests
    }

    override suspend fun getAvailableProducts(stationId: String): List<String> {
        return listOf("N0Q", "N0U")
    }
}
