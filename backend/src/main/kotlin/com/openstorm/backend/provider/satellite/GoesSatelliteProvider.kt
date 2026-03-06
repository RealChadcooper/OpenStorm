package com.openstorm.backend.provider.satellite

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * GOES-East (GOES-16) and GOES-West (GOES-18) satellite imagery.
 * Public domain via NOAA. Available on AWS S3 without auth.
 *
 * Products include:
 * - CONUS visible (Band 2)
 * - CONUS infrared (Band 13)
 * - CONUS water vapor (Band 8-10)
 * - Mesoscale sectors
 *
 * Feature-flagged: requires backend processing to crop/tile imagery.
 */
class GoesSatelliteProvider : SatelliteProvider {

    override val providerId = "noaa-goes"

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun getProducts(): List<SatelliteProduct> {
        return listOf(
            SatelliteProduct("goes16-conus-vis", "CONUS Visible", "GOES-16", "Band02"),
            SatelliteProduct("goes16-conus-ir", "CONUS Infrared", "GOES-16", "Band13"),
            SatelliteProduct("goes16-conus-wv", "CONUS Water Vapor", "GOES-16", "Band08"),
            SatelliteProduct("goes18-conus-vis", "CONUS Visible", "GOES-18", "Band02"),
            SatelliteProduct("goes18-conus-ir", "CONUS Infrared", "GOES-18", "Band13"),
        )
    }

    override suspend fun getImageUrl(productId: String, timestamp: Instant?): String? {
        // In production: resolve S3 URL for the nearest available image
        val ts = timestamp ?: Instant.now()
        return "https://noaa-goes16.s3.amazonaws.com/ABI-L2-CMIPC/${formatS3Path(ts)}"
    }

    override suspend fun getFrames(productId: String, count: Int): List<SatelliteFrame> {
        val now = Instant.now()
        return (0 until count).reversed().map { i ->
            val ts = now.minus((i * 15).toLong(), ChronoUnit.MINUTES)
            SatelliteFrame(
                productId = productId,
                timestamp = ts,
                imageUrl = "https://cdn.openstorm.app/satellite/$productId/${ts}.webp",
            )
        }
    }

    private fun formatS3Path(timestamp: Instant): String {
        val year = timestamp.toString().substring(0, 4)
        val dayOfYear = java.time.LocalDate.ofInstant(timestamp, java.time.ZoneOffset.UTC).dayOfYear
        val hour = timestamp.toString().substring(11, 13)
        return "$year/$dayOfYear/$hour/"
    }
}
