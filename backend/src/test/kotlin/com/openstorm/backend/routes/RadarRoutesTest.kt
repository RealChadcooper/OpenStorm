package com.openstorm.backend.routes

import com.openstorm.backend.configurePlugins
import com.openstorm.backend.provider.radar.NoaaRadarProvider
import com.openstorm.backend.service.RadarService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class RadarRoutesTest {

    @Test
    fun `stations endpoint requires lat and lon`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                radarRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/stations")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `stations endpoint returns nearby stations`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                radarRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/stations?lat=35.2&lon=-97.4&radius=100")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "KTLX")
    }

    @Test
    fun `frames endpoint returns frames`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                radarRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/stations/KTLX/products/N0Q?frames=5")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "KTLX")
        assertContains(response.bodyAsText(), "tileUrl")
    }
}
