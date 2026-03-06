package com.openstorm.core.domain.model

import java.time.Instant

data class Alert(
    val id: String,
    val type: AlertType,
    val event: String,
    val headline: String,
    val description: String,
    val areaDesc: String,
    val severity: AlertSeverity,
    val certainty: AlertCertainty,
    val urgency: AlertUrgency,
    val effective: Instant,
    val expires: Instant,
    val senderName: String,
    val geometry: GeoPolygon? = null,
)

enum class AlertType { WARNING, WATCH, ADVISORY, STATEMENT }

enum class AlertSeverity { EXTREME, SEVERE, MODERATE, MINOR, UNKNOWN }

enum class AlertCertainty { OBSERVED, LIKELY, POSSIBLE, UNLIKELY, UNKNOWN }

enum class AlertUrgency { IMMEDIATE, EXPECTED, FUTURE, PAST, UNKNOWN }

data class GeoPolygon(
    val coordinates: List<List<LatLon>>,
)

data class LatLon(
    val lat: Double,
    val lon: Double,
)
