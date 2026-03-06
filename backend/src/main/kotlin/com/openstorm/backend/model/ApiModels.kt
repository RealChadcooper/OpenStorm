package com.openstorm.backend.model

import java.time.Instant

data class RadarStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double = 0.0,
    val stationType: String = "WSR88D",
    val status: String = "ACTIVE",
    val distanceKm: Double? = null,
    val products: List<String> = listOf("N0Q", "N0U"),
)

data class RadarFrame(
    val timestamp: Instant,
    val tileUrl: String,
    val expiresAt: Instant,
)

data class Alert(
    val id: String,
    val type: String,
    val event: String,
    val headline: String,
    val description: String = "",
    val areaDesc: String = "",
    val severity: String = "UNKNOWN",
    val certainty: String = "UNKNOWN",
    val urgency: String = "UNKNOWN",
    val effective: Instant,
    val expires: Instant,
    val senderName: String = "",
)

// API response wrappers
data class ApiResponse<T>(val data: T)

data class StationListResponse(val stations: List<RadarStation>)

data class RadarFramesResponse(
    val station: String,
    val product: String,
    val frames: List<RadarFrame>,
)

data class AlertListResponse(val alerts: List<Alert>)

data class HealthResponse(
    val status: String = "ok",
    val version: String = "1.0.0",
    val uptime: Long,
)
