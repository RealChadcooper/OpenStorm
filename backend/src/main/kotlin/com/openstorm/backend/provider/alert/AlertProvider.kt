package com.openstorm.backend.provider.alert

import com.openstorm.backend.model.Alert

interface AlertProvider {

    val providerId: String

    suspend fun getActiveAlerts(lat: Double, lon: Double, radiusKm: Double): List<Alert>

    suspend fun getAlert(alertId: String): Alert?
}
