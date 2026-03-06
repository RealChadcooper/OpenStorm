package com.openstorm.core.domain.repository

import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarStation
import kotlinx.coroutines.flow.Flow

interface RadarRepository {

    fun observeNearestStations(lat: Double, lon: Double, radiusKm: Double = 200.0): Flow<List<RadarStation>>

    suspend fun getStation(stationId: String): RadarStation?

    suspend fun getFrames(stationId: String, product: String, count: Int = 10): List<RadarFrame>

    suspend fun getLatestFrame(stationId: String, product: String): RadarFrame?

    suspend fun refreshStations(lat: Double, lon: Double, radiusKm: Double = 200.0)

    suspend fun getAvailableProducts(stationId: String): List<String>
}
