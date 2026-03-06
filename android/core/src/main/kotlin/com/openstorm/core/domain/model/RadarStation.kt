package com.openstorm.core.domain.model

data class RadarStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double,
    val stationType: StationType,
    val status: StationStatus,
    val distanceKm: Double? = null,
    val products: List<String> = emptyList(),
)

enum class StationType { WSR88D, TDWR }

enum class StationStatus { ACTIVE, DOWN, MAINTENANCE }
