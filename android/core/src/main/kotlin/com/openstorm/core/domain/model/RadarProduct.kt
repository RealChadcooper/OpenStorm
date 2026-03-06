package com.openstorm.core.domain.model

import java.time.Instant

data class RadarProduct(
    val code: String,
    val name: String,
    val description: String,
    val unit: String,
) {
    companion object {
        val BASE_REFLECTIVITY = RadarProduct("N0Q", "Base Reflectivity", "0.5° reflectivity", "dBZ")
        val BASE_VELOCITY = RadarProduct("N0U", "Base Velocity", "0.5° velocity", "kts")
        val CORRELATION_COEFFICIENT = RadarProduct("N0C", "Correlation Coefficient", "0.5° CC", "")
        val DIFFERENTIAL_REFLECTIVITY = RadarProduct("N0X", "Differential Reflectivity", "0.5° ZDR", "dB")
        val SPECIFIC_DIFF_PHASE = RadarProduct("N0K", "Specific Diff Phase", "0.5° KDP", "°/km")

        val ALL = listOf(BASE_REFLECTIVITY, BASE_VELOCITY, CORRELATION_COEFFICIENT, DIFFERENTIAL_REFLECTIVITY, SPECIFIC_DIFF_PHASE)
        val MVP = listOf(BASE_REFLECTIVITY, BASE_VELOCITY)
    }
}

data class RadarFrame(
    val stationId: String,
    val product: String,
    val timestamp: Instant,
    val tileUrl: String,
    val expiresAt: Instant,
)
