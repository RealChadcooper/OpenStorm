package com.openstorm.core.domain.repository

import com.openstorm.core.domain.model.ArchiveQuery
import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarFrameSummary
import com.openstorm.core.domain.model.RadarPlaybackManifest
import com.openstorm.core.domain.model.RadarStation
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RadarRepository {

    fun observeNearestStations(lat: Double, lon: Double, radiusKm: Double = 200.0): Flow<List<RadarStation>>

    suspend fun getStation(stationId: String): RadarStation?

    suspend fun getFrames(stationId: String, product: String, count: Int = 10): List<RadarFrame>

    suspend fun getLatestFrame(stationId: String, product: String): RadarFrame?

    suspend fun refreshStations(lat: Double, lon: Double, radiusKm: Double = 200.0)

    suspend fun getAvailableProducts(stationId: String): List<String>

    // ── Archive ──

    suspend fun getArchiveFrames(query: ArchiveQuery): List<RadarFrameSummary>

    suspend fun getNearestFrame(stationId: String, product: String, target: Instant): RadarFrameSummary?

    suspend fun getPlaybackManifest(query: ArchiveQuery): RadarPlaybackManifest?
}
