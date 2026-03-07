package com.openstorm.backend.routes

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DevTileProxy")

/**
 * DEV-MODE ONLY: Proxies radar tile requests to Iowa Environmental Mesonet (IEM)
 * which serves a live NEXRAD composite mosaic as slippy-map tiles.
 *
 * IEM tile URL pattern:
 *   https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/{layer}/{z}/{x}/{y}.png
 *
 * Supported layers:
 *   nexrad-n0q-900913  — Base Reflectivity (N0Q) composite
 *   nexrad-n0u-900913  — Base Velocity (N0U) composite
 *
 * This endpoint is NOT registered in production.
 */
fun Route.devTileProxyRoutes() {

    val proxyClient = HttpClient(CIO) {
        engine {
            requestTimeout = 15_000
        }
    }

    route("/api/v1/dev/tiles") {

        get("/{product}/{z}/{x}/{y}.png") {
            val product = call.parameters["product"]?.lowercase() ?: "n0q"
            val z = call.parameters["z"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing z")
            val x = call.parameters["x"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing x")
            val y = call.parameters["y"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing y")

            val layer = when (product) {
                "n0q" -> "nexrad-n0q-900913"
                "n0u" -> "nexrad-n0u-900913"
                "n0r" -> "nexrad-n0r-900913"
                else -> "nexrad-n0q-900913"
            }

            val upstreamUrl = "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layer/$z/$x/$y.png"

            try {
                val response = proxyClient.get(upstreamUrl)
                val bytes = response.readRawBytes()

                call.response.header(HttpHeaders.CacheControl, "public, max-age=120")
                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                call.respondBytes(bytes, ContentType.Image.PNG, response.status)
            } catch (e: Exception) {
                logger.warn("Tile proxy failed for $upstreamUrl: ${e.message}")
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "upstream tile fetch failed"))
            }
        }
    }
}
