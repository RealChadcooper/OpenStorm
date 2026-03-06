package com.openstorm.core.provider

import java.time.Instant

/**
 * Abstraction for satellite imagery. Default implementation uses GOES-East/West
 * public imagery from NOAA.
 */
interface SatelliteProvider {

    val providerId: String

    val providerName: String

    suspend fun isAvailable(): Boolean

    /** Get available satellite products (visible, infrared, water vapor, etc.). */
    suspend fun getProducts(): List<SatelliteProduct>

    /** Get image URL for a product at a given time. */
    suspend fun getImageUrl(productId: String, timestamp: Instant? = null): String?

    /** Get recent frames for animation. */
    suspend fun getFrames(productId: String, frameCount: Int = 10): List<SatelliteFrame>
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
