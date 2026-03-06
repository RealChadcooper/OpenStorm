package com.openstorm.backend.routes

import com.openstorm.backend.model.HealthResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val startTime = System.currentTimeMillis()

fun Route.healthRoutes() {
    get("/api/v1/health") {
        val uptime = (System.currentTimeMillis() - startTime) / 1000
        call.respond(HealthResponse(uptime = uptime))
    }
}
