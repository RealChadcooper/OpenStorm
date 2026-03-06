package com.openstorm.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.openstorm.core.domain.model.Alert
import com.openstorm.core.domain.model.AlertCertainty
import com.openstorm.core.domain.model.AlertSeverity
import com.openstorm.core.domain.model.AlertType
import com.openstorm.core.domain.model.AlertUrgency
import java.time.Instant

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val type: String,
    val event: String,
    val headline: String,
    val description: String,
    val areaDesc: String,
    val severity: String,
    val certainty: String,
    val urgency: String,
    val effectiveEpochMs: Long,
    val expiresEpochMs: Long,
    val senderName: String,
) {
    fun toDomain() = Alert(
        id = id,
        type = runCatching { AlertType.valueOf(type) }.getOrDefault(AlertType.STATEMENT),
        event = event,
        headline = headline,
        description = description,
        areaDesc = areaDesc,
        severity = runCatching { AlertSeverity.valueOf(severity) }.getOrDefault(AlertSeverity.UNKNOWN),
        certainty = runCatching { AlertCertainty.valueOf(certainty) }.getOrDefault(AlertCertainty.UNKNOWN),
        urgency = runCatching { AlertUrgency.valueOf(urgency) }.getOrDefault(AlertUrgency.UNKNOWN),
        effective = Instant.ofEpochMilli(effectiveEpochMs),
        expires = Instant.ofEpochMilli(expiresEpochMs),
        senderName = senderName,
    )

    companion object {
        fun fromDomain(alert: Alert) = AlertEntity(
            id = alert.id,
            type = alert.type.name,
            event = alert.event,
            headline = alert.headline,
            description = alert.description,
            areaDesc = alert.areaDesc,
            severity = alert.severity.name,
            certainty = alert.certainty.name,
            urgency = alert.urgency.name,
            effectiveEpochMs = alert.effective.toEpochMilli(),
            expiresEpochMs = alert.expires.toEpochMilli(),
            senderName = alert.senderName,
        )
    }
}
