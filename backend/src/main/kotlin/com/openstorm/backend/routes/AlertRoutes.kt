package com.openstorm.backend.routes

import com.openstorm.backend.model.AlertListResponse
import com.openstorm.backend.service.AlertService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.alertRoutes(alertService: AlertService) {

    route("/api/v1/alerts") {

        get {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 150.0

            if (lat == null || lon == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat and lon required"))
                return@get
            }

            val alerts = alertService.getActiveAlerts(lat, lon, radius)
            call.respond(AlertListResponse(alerts = alerts))
        }

        get("/{alertId}") {
            val alertId = call.parameters["alertId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "alertId required"))

            val alert = alertService.getAlert(alertId)
            if (alert != null) {
                call.respond(alert)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Alert not found"))
            }
        }
    }
}
