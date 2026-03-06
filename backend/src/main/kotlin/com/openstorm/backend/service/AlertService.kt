package com.openstorm.backend.service

import com.openstorm.backend.model.Alert
import com.openstorm.backend.provider.alert.AlertProvider

class AlertService(private val provider: AlertProvider) {

    suspend fun getActiveAlerts(lat: Double, lon: Double, radiusKm: Double): List<Alert> {
        return provider.getActiveAlerts(lat, lon, radiusKm)
    }

    suspend fun getAlert(alertId: String): Alert? {
        return provider.getAlert(alertId)
    }
}
