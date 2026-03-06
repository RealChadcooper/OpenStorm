package com.openstorm.backend.routes

import com.openstorm.backend.configurePlugins
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class HealthRoutesTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        application {
            configurePlugins()
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "ok")
    }
}
