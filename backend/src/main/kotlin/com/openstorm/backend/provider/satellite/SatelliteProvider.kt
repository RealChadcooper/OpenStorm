package com.openstorm.backend.provider.satellite

import java.time.Instant

/**
 * Satellite imagery provider interface.
 * Default implementation: GOES-East/West via NOAA AWS S3 buckets.
 * Data source: s3://noaa-goes16/ and s3://noaa-goes18/
 */
interface SatelliteProvider {

    val providerId: String

    suspend fun getProducts(): List<SatelliteProduct>

    suspend fun getImageUrl(productId: String, timestamp: Instant? = null): String?

    suspend fun getFrames(productId: String, count: Int): List<SatelliteFrame>
}

data class SatelliteProduct(
    val id: String,
    val name: String,
    val satellite: String,
    val band: String,
)

data class SatelliteFrame(
    val productId: String,
    val timestamp: Instant,
    val imageUrl: String,
)
