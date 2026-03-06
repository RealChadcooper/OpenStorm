package com.openstorm.core.provider

import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarStation

/**
 * Abstraction for radar data sources. Default implementation uses NOAA/NWS
 * via the OpenStorm backend. Commercial providers can implement this interface
 * for higher-resolution or additional products.
 */
interface RadarProvider {

    val providerId: String

    val providerName: String

    /** Whether this provider is currently available and configured. */
    suspend fun isAvailable(): Boolean

    /** List all known radar stations, optionally filtered by proximity. */
    suspend fun getStations(lat: Double? = null, lon: Double? = null, radiusKm: Double? = null): List<RadarStation>

    /** Get a single station by ID. */
    suspend fun getStation(stationId: String): RadarStation?

    /** Get radar frames for loop playback. */
    suspend fun getFrames(
        stationId: String,
        product: String,
        frameCount: Int = 10,
    ): List<RadarFrame>

    /** Get the latest single frame. */
    suspend fun getLatestFrame(stationId: String, product: String): RadarFrame?

    /** List available product codes for a station. */
    suspend fun getAvailableProducts(stationId: String): List<String>
}
