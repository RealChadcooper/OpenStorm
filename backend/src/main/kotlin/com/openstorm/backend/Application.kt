package com.openstorm.backend

import com.openstorm.backend.config.AppConfig
import com.openstorm.backend.config.configureDatabase
import com.openstorm.backend.ingestion.RadarIngestionWorker
import com.openstorm.backend.ingestion.AlertIngestionWorker
import com.openstorm.backend.provider.alert.NwsAlertProvider
import com.openstorm.backend.provider.radar.NoaaRadarProvider
import com.openstorm.backend.routes.alertRoutes
import com.openstorm.backend.routes.healthRoutes
import com.openstorm.backend.routes.radarRoutes
import com.openstorm.backend.service.AlertService
import com.openstorm.backend.service.RadarService
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("OpenStorm")

fun main() {
    val config = AppConfig.load()

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        configurePlugins()
        configureDatabase(config)
        configureRouting(config)
        configureIngestion(config)
    }.start(wait = true)
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 0.9 }
    }

    install(CallLogging)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
    }

    install(RateLimit) {
        global {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
    }
}

fun Application.configureRouting(config: AppConfig) {
    val radarProvider = NoaaRadarProvider()
    val alertProvider = NwsAlertProvider()
    val radarService = RadarService(radarProvider)
    val alertService = AlertService(alertProvider)

    routing {
        healthRoutes()
        radarRoutes(radarService)
        alertRoutes(alertService)
    }
}

fun Application.configureIngestion(config: AppConfig) {
    val radarWorker = RadarIngestionWorker()
    val alertWorker = AlertIngestionWorker()

    launch {
        radarWorker.start()
    }
    launch {
        alertWorker.start()
    }
}
