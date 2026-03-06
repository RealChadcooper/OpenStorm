package com.openstorm.core.domain.repository

import com.openstorm.core.domain.model.Alert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {

    fun observeActiveAlerts(lat: Double, lon: Double, radiusKm: Double = 150.0): Flow<List<Alert>>

    suspend fun getAlert(alertId: String): Alert?

    suspend fun refreshAlerts(lat: Double, lon: Double, radiusKm: Double = 150.0)
}
