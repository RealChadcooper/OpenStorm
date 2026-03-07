package com.openstorm.backend.routes

import com.openstorm.backend.configurePlugins
import com.openstorm.backend.provider.radar.NoaaRadarProvider
import com.openstorm.backend.service.RadarService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class ArchiveRoutesTest {

    @Test
    fun `archive frames endpoint returns frames`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                archiveRoutes(radarService)
            }
        }

        val now = Instant.now()
        val start = now.minus(2, ChronoUnit.HOURS).toString()
        val end = now.toString()

        val response = client.get("/api/v1/radar/archive/KTLX/N0Q/frames?start=$start&end=$end")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "KTLX")
        assertContains(response.bodyAsText(), "frames")
    }

    @Test
    fun `archive frames endpoint uses defaults when no params`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                archiveRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/archive/KTLX/N0Q/frames")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "totalAvailable")
    }

    @Test
    fun `nearest frame endpoint requires timestamp`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                archiveRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/archive/KTLX/N0Q/nearest")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `nearest frame endpoint accepts valid timestamp`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                archiveRoutes(radarService)
            }
        }

        val ts = Instant.now().minus(30, ChronoUnit.MINUTES).toString()
        val response = client.get("/api/v1/radar/archive/KTLX/N0Q/nearest?timestamp=$ts")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "KTLX")
    }

    @Test
    fun `manifest endpoint returns manifest`() = testApplication {
        val radarService = RadarService(NoaaRadarProvider())
        application {
            configurePlugins()
            routing {
                archiveRoutes(radarService)
            }
        }

        val response = client.get("/api/v1/radar/archive/KTLX/N0Q/manifest")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "frameCount")
        assertContains(response.bodyAsText(), "retentionHours")
    }
}
