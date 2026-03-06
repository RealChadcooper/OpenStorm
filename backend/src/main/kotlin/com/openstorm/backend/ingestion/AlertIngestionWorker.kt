package com.openstorm.backend.ingestion

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Background worker that polls NWS for active alerts.
 *
 * Polls api.weather.gov/alerts/active every 60 seconds.
 * Stores alerts in PostgreSQL for efficient spatial queries.
 */
class AlertIngestionWorker {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun start() {
        logger.info("Alert ingestion worker started")
        while (true) {
            try {
                ingest()
            } catch (e: Exception) {
                logger.error("Alert ingestion error", e)
            }
            delay(60_000) // 1 minute
        }
    }

    private suspend fun ingest() {
        logger.debug("Alert ingestion tick — polling NWS")
        // Production implementation:
        // 1. Fetch api.weather.gov/alerts/active
        // 2. Parse GeoJSON response
        // 3. Upsert alerts into database
        // 4. Delete expired alerts
    }
}
