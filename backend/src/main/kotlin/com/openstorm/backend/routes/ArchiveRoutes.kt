package com.openstorm.backend.routes

import com.openstorm.backend.model.ArchiveFramesResponse
import com.openstorm.backend.model.NearestFrameResponse
import com.openstorm.backend.service.RadarService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.temporal.ChronoUnit

fun Route.archiveRoutes(radarService: RadarService) {

    route("/api/v1/radar/archive") {

        /**
         * GET /api/v1/radar/archive/{stationId}/{product}/frames
         *
         * List available frames within a time window.
         * Query params:
         *   start - ISO-8601 timestamp (default: 2 hours ago)
         *   end   - ISO-8601 timestamp (default: now)
         *   limit - max frames to return (default: 200, max: 500)
         */
        get("/{stationId}/{product}/frames") {
            val stationId = call.parameters["stationId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "stationId required"))
            val product = call.parameters["product"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "product required"))

            val now = Instant.now()
            val start = call.request.queryParameters["start"]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: now.minus(2, ChronoUnit.HOURS)
            val end = call.request.queryParameters["end"]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: now
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 200)
                .coerceIn(1, 500)

            val frames = radarService.getArchiveFrames(stationId, product, start, end, limit)
            call.respond(
                ArchiveFramesResponse(
                    station = stationId,
                    product = product,
                    frames = frames,
                    totalAvailable = frames.size,
                )
            )
        }

        /**
         * GET /api/v1/radar/archive/{stationId}/{product}/nearest
         *
         * Find the frame closest to a given timestamp.
         * Query params:
         *   timestamp - ISO-8601 timestamp (required)
         */
        get("/{stationId}/{product}/nearest") {
            val stationId = call.parameters["stationId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "stationId required"))
            val product = call.parameters["product"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "product required"))

            val timestampStr = call.request.queryParameters["timestamp"]
            if (timestampStr == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "timestamp query parameter required"))
                return@get
            }

            val target = runCatching { Instant.parse(timestampStr) }.getOrNull()
            if (target == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ISO-8601 timestamp"))
                return@get
            }

            val frame = radarService.getNearestFrame(stationId, product, target)
            call.respond(NearestFrameResponse(station = stationId, product = product, frame = frame))
        }

        /**
         * GET /api/v1/radar/archive/{stationId}/{product}/manifest
         *
         * Get a playback manifest for a time window.
         * Query params:
         *   start - ISO-8601 timestamp (default: 2 hours ago)
         *   end   - ISO-8601 timestamp (default: now)
         */
        get("/{stationId}/{product}/manifest") {
            val stationId = call.parameters["stationId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "stationId required"))
            val product = call.parameters["product"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "product required"))

            val now = Instant.now()
            val start = call.request.queryParameters["start"]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: now.minus(2, ChronoUnit.HOURS)
            val end = call.request.queryParameters["end"]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: now

            val manifest = radarService.getPlaybackManifest(stationId, product, start, end)
            call.respond(manifest)
        }
    }
}
