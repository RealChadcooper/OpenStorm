package com.openstorm.core.provider

import java.time.Instant

/**
 * Abstraction for lightning data. This is feature-flagged OFF by default
 * because high-resolution lightning data typically requires commercial licensing
 * (Vaisala, ENTLN, etc.).
 *
 * GOES GLM (Geostationary Lightning Mapper) satellite data is publicly available
 * but lower resolution. A GLM implementation could be provided as a free tier.
 */
interface LightningProvider {

    val providerId: String

    val providerName: String

    /** Whether this provider is configured and licensed for use. */
    suspend fun isAvailable(): Boolean

    /** Get recent lightning strikes in a bounding box. */
    suspend fun getStrikes(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        since: Instant,
    ): List<LightningStrike>
}

data class LightningStrike(
    val lat: Double,
    val lon: Double,
    val timestamp: Instant,
    val type: StrikeType,
    val peakCurrentKa: Double? = null,
)

enum class StrikeType { CLOUD_TO_GROUND, INTRA_CLOUD, UNKNOWN }
