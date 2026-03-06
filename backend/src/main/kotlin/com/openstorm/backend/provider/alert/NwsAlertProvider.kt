package com.openstorm.backend.provider.alert

import com.openstorm.backend.model.Alert
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * NWS Alerts API provider.
 * Source: https://api.weather.gov/alerts/active
 * Data is public domain (US government work).
 */
class NwsAlertProvider : AlertProvider {

    override val providerId = "nws-alerts"

    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { jackson() }
    }

    override suspend fun getActiveAlerts(lat: Double, lon: Double, radiusKm: Double): List<Alert> {
        return try {
            // NWS API supports point-based queries
            val response = client.get("https://api.weather.gov/alerts/active") {
                parameter("point", "$lat,$lon")
                header("User-Agent", "(OpenStorm, contact@openstorm.app)")
                header("Accept", "application/geo+json")
            }

            val body = response.bodyAsText()
            val json = mapper.readTree(body)
            val features = json.path("features")

            features.mapNotNull { feature ->
                try {
                    parseAlert(feature.path("properties"))
                } catch (e: Exception) {
                    logger.warn("Failed to parse alert: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch NWS alerts", e)
            emptyList()
        }
    }

    override suspend fun getAlert(alertId: String): Alert? {
        return try {
            val response = client.get("https://api.weather.gov/alerts/$alertId") {
                header("User-Agent", "(OpenStorm, contact@openstorm.app)")
                header("Accept", "application/geo+json")
            }
            val body = response.bodyAsText()
            val json = mapper.readTree(body)
            parseAlert(json.path("properties"))
        } catch (e: Exception) {
            logger.error("Failed to fetch alert $alertId", e)
            null
        }
    }

    private fun parseAlert(props: JsonNode): Alert {
        return Alert(
            id = props.path("id").asText(""),
            type = categorizeAlert(props.path("event").asText("")),
            event = props.path("event").asText(""),
            headline = props.path("headline").asText(""),
            description = props.path("description").asText(""),
            areaDesc = props.path("areaDesc").asText(""),
            severity = props.path("severity").asText("Unknown").uppercase(),
            certainty = props.path("certainty").asText("Unknown").uppercase(),
            urgency = props.path("urgency").asText("Unknown").uppercase(),
            effective = parseInstant(props.path("effective").asText()),
            expires = parseInstant(props.path("expires").asText()),
            senderName = props.path("senderName").asText(""),
        )
    }

    private fun categorizeAlert(event: String): String {
        return when {
            event.contains("Warning", ignoreCase = true) -> "WARNING"
            event.contains("Watch", ignoreCase = true) -> "WATCH"
            event.contains("Advisory", ignoreCase = true) -> "ADVISORY"
            else -> "STATEMENT"
        }
    }

    private fun parseInstant(text: String): Instant {
        return try {
            Instant.parse(text)
        } catch (e: Exception) {
            Instant.now()
        }
    }
}
