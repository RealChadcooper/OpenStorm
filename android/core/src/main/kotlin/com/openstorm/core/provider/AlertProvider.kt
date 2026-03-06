package com.openstorm.core.provider

import com.openstorm.core.domain.model.Alert

/**
 * Abstraction for weather alert sources. Default uses NWS alerts API.
 */
interface AlertProvider {

    val providerId: String

    val providerName: String

    suspend fun isAvailable(): Boolean

    /** Get active alerts for a geographic point and radius. */
    suspend fun getActiveAlerts(lat: Double, lon: Double, radiusKm: Double = 150.0): List<Alert>

    /** Get a specific alert by ID. */
    suspend fun getAlert(alertId: String): Alert?
}
