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
 * which serves NEXRAD radar data as slippy-map tiles.
 *
 * Two modes:
 * 1. Per-station (Ridge): single-site radar data at full resolution
 *    IEM URL: ridge::{STATION}-{PRODUCT}-0
 *    Example: ridge::KICT-N0Q-0
 *
 * 2. National composite (fallback): lower-res mosaic of all stations
 *    IEM URL: nexrad-n0q-900913
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

        // Per-station tiles: /api/v1/dev/tiles/{station}/{product}/{z}/{x}/{y}.png
        get("/{station}/{product}/{z}/{x}/{y}.png") {
            val station = call.parameters["station"]?.uppercase() ?: "KTLX"
            val product = call.parameters["product"]?.uppercase() ?: "N0Q"
            val z = call.parameters["z"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing z")
            val x = call.parameters["x"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing x")
            val y = call.parameters["y"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing y")

            // IEM Ridge single-site layer name: ridge::{STATION}-{PRODUCT}-0
            val layer = "ridge::${station}-${product}-0"
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
