package com.openstorm.test

import com.openstorm.core.domain.model.Alert
import com.openstorm.core.domain.model.AlertCertainty
import com.openstorm.core.domain.model.AlertSeverity
import com.openstorm.core.domain.model.AlertType
import com.openstorm.core.domain.model.AlertUrgency
import com.openstorm.core.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

class FakeAlertRepository : AlertRepository {

    val alertsFlow = MutableStateFlow<List<Alert>>(emptyList())

    val testAlert = Alert(
        id = "test-alert-1",
        type = AlertType.WARNING,
        event = "Tornado Warning",
        headline = "Tornado Warning issued for Cleveland County, OK",
        description = "A severe thunderstorm capable of producing a tornado was located near Norman.",
        areaDesc = "Cleveland County, OK",
        severity = AlertSeverity.EXTREME,
        certainty = AlertCertainty.OBSERVED,
        urgency = AlertUrgency.IMMEDIATE,
        effective = Instant.now(),
        expires = Instant.now().plus(30, ChronoUnit.MINUTES),
        senderName = "NWS Norman OK",
    )

    override fun observeActiveAlerts(lat: Double, lon: Double, radiusKm: Double): Flow<List<Alert>> {
        return alertsFlow
    }

    override suspend fun getAlert(alertId: String): Alert? {
        return alertsFlow.value.find { it.id == alertId }
    }

    override suspend fun refreshAlerts(lat: Double, lon: Double, radiusKm: Double) {
        // No-op for tests
    }
}
