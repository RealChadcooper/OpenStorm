package com.openstorm.core.provider

import java.time.Instant

/**
 * Abstraction for atmospheric sounding (upper air) data.
 * Default source: University of Wyoming or NOAA RAOB data (public).
 */
interface SoundingProvider {

    val providerId: String

    val providerName: String

    suspend fun isAvailable(): Boolean

    /** Get available sounding sites near a location. */
    suspend fun getSites(lat: Double, lon: Double, radiusKm: Double = 300.0): List<SoundingSite>

    /** Get the latest sounding for a site. */
    suspend fun getSounding(siteId: String, timestamp: Instant? = null): SoundingData?
}

data class SoundingSite(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val wmoId: String? = null,
)

data class SoundingData(
    val siteId: String,
    val timestamp: Instant,
    val levels: List<SoundingLevel>,
    val indices: SoundingIndices? = null,
)

data class SoundingLevel(
    val pressureMb: Double,
    val heightM: Double,
    val temperatureC: Double,
    val dewpointC: Double,
    val windDirectionDeg: Int,
    val windSpeedKts: Int,
)

data class SoundingIndices(
    val cape: Double?,
    val cin: Double?,
    val liftedIndex: Double?,
    val precipitableWaterMm: Double?,
    val lcl: Double?,
    val lfc: Double?,
    val equilibriumLevel: Double?,
    val bulkShear0to6km: Double?,
    val stormRelativeHelicity: Double?,
)
