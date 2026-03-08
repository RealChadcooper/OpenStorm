package com.openstorm.backend.routes

import com.openstorm.backend.model.RadarFramesResponse
import com.openstorm.backend.model.StationListResponse
import com.openstorm.backend.service.RadarService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.radarRoutes(radarService: RadarService) {

    route("/api/v1/radar") {

        get("/stations") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 200.0

            if (lat == null || lon == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat and lon required"))
                return@get
            }

            val stations = radarService.getNearbyStations(lat, lon, radius)
            call.respond(StationListResponse(stations = stations))
        }

        get("/stations/{stationId}/products/{product}") {
            val stationId = call.parameters["stationId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "stationId required"))
            val product = call.parameters["product"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "product required"))
            val frameCount = call.request.queryParameters["frames"]?.toIntOrNull() ?: 10

            val frames = radarService.getFrames(stationId, product, frameCount)
            call.respond(RadarFramesResponse(station = stationId, product = product, frames = frames))
        }
    }
}
