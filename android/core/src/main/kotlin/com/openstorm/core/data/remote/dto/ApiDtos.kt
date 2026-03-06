package com.openstorm.core.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: Long,
)

@JsonClass(generateAdapter = true)
data class StationListResponse(
    val stations: List<StationDto>,
)

@JsonClass(generateAdapter = true)
data class StationDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double = 0.0,
    val stationType: String = "WSR88D",
    val status: String = "ACTIVE",
    val distanceKm: Double? = null,
    val products: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RadarFramesResponse(
    val station: String,
    val product: String,
    val frames: List<FrameDto>,
)

@JsonClass(generateAdapter = true)
data class FrameDto(
    val timestamp: String,
    val tileUrl: String,
    val expiresAt: String,
)

@JsonClass(generateAdapter = true)
data class AlertListResponse(
    val alerts: List<AlertDto>,
)

@JsonClass(generateAdapter = true)
data class AlertDto(
    val id: String,
    val type: String,
    val event: String,
    val headline: String,
    val description: String = "",
    val areaDesc: String = "",
    val severity: String = "UNKNOWN",
    val certainty: String = "UNKNOWN",
    val urgency: String = "UNKNOWN",
    val effective: String,
    val expires: String,
    val senderName: String = "",
    val geometry: GeometryDto? = null,
)

@JsonClass(generateAdapter = true)
data class GeometryDto(
    val type: String,
    val coordinates: List<List<List<Double>>>? = null,
)
