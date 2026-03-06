package com.openstorm.backend.provider.radar

import com.openstorm.backend.model.RadarFrame
import com.openstorm.backend.model.RadarStation

/**
 * Backend radar data provider interface.
 * Default: NOAA NEXRAD via TGFTP and AWS OpenData.
 */
interface RadarProvider {

    val providerId: String

    suspend fun getStations(): List<RadarStation>

    suspend fun getStation(stationId: String): RadarStation?

    suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame>
}
