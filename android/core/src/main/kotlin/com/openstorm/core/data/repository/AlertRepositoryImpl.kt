package com.openstorm.core.data.repository

import com.openstorm.core.data.local.dao.AlertDao
import com.openstorm.core.data.local.entity.AlertEntity
import com.openstorm.core.data.remote.OpenStormApi
import com.openstorm.core.domain.model.Alert
import com.openstorm.core.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val api: OpenStormApi,
    private val alertDao: AlertDao,
) : AlertRepository {

    override fun observeActiveAlerts(lat: Double, lon: Double, radiusKm: Double): Flow<List<Alert>> {
        return alertDao.observeActive(System.currentTimeMillis()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAlert(alertId: String): Alert? {
        return alertDao.getById(alertId)?.toDomain()
    }

    override suspend fun refreshAlerts(lat: Double, lon: Double, radiusKm: Double) {
        try {
            val response = api.getAlerts(lat, lon, radiusKm)
            val entities = response.alerts.map { dto ->
                AlertEntity(
                    id = dto.id,
                    type = dto.type,
                    event = dto.event,
                    headline = dto.headline,
                    description = dto.description,
                    areaDesc = dto.areaDesc,
                    severity = dto.severity,
                    certainty = dto.certainty,
                    urgency = dto.urgency,
                    effectiveEpochMs = Instant.parse(dto.effective).toEpochMilli(),
                    expiresEpochMs = Instant.parse(dto.expires).toEpochMilli(),
                    senderName = dto.senderName,
                )
            }
            alertDao.deleteExpired(System.currentTimeMillis())
            alertDao.insertAll(entities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh alerts")
        }
    }
}
