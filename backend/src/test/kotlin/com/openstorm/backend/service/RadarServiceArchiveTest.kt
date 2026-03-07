package com.openstorm.backend.service

import com.openstorm.backend.provider.radar.NoaaRadarProvider
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RadarServiceArchiveTest {

    private val service = RadarService(NoaaRadarProvider())

    @Test
    fun `playback manifest returns correct metadata`() = runBlocking {
        val now = Instant.now()
        val start = now.minus(1, ChronoUnit.HOURS)

        val manifest = service.getPlaybackManifest("KTLX", "N0Q", start, now)

        assertEquals("KTLX", manifest.station)
        assertEquals("N0Q", manifest.product)
        assertEquals(manifest.frames.size, manifest.frameCount)
        assertEquals(24, manifest.retentionHours)
        assertTrue(manifest.frames.isNotEmpty())
    }

    @Test
    fun `playback manifest frames are chronologically ordered`() = runBlocking {
        val now = Instant.now()
        val start = now.minus(2, ChronoUnit.HOURS)

        val manifest = service.getPlaybackManifest("KTLX", "N0Q", start, now)

        for (i in 1 until manifest.frames.size) {
            assertTrue(
                manifest.frames[i].timestamp >= manifest.frames[i - 1].timestamp,
                "Frames should be in chronological order"
            )
        }
    }

    @Test
    fun `playback manifest startTime and endTime match actual frame range`() = runBlocking {
        val now = Instant.now()
        val start = now.minus(1, ChronoUnit.HOURS)

        val manifest = service.getPlaybackManifest("KTLX", "N0Q", start, now)

        if (manifest.frames.isNotEmpty()) {
            assertEquals(manifest.frames.first().timestamp, manifest.startTime)
            assertEquals(manifest.frames.last().timestamp, manifest.endTime)
        }
    }

    @Test
    fun `archive frames respect limit`() = runBlocking {
        val now = Instant.now()
        val start = now.minus(24, ChronoUnit.HOURS)

        val frames = service.getArchiveFrames("KTLX", "N0Q", start, now, limit = 5)

        assertTrue(frames.size <= 5, "Should respect limit parameter")
    }

    @Test
    fun `archive frames have valid tile URLs`() = runBlocking {
        val now = Instant.now()
        val start = now.minus(1, ChronoUnit.HOURS)

        val frames = service.getArchiveFrames("KTLX", "N0Q", start, now)

        frames.forEach { frame ->
            assertTrue(frame.tileUrl.contains("KTLX"), "Tile URL should contain station ID")
            assertTrue(frame.tileUrl.contains("N0Q"), "Tile URL should contain product")
            assertNotNull(frame.timestamp, "Frame should have a timestamp")
        }
    }

    @Test
    fun `retention hours has sensible default`() {
        assertTrue(service.retentionHours > 0, "Retention should be positive")
        assertTrue(service.retentionHours <= 168, "Retention should be at most 7 days")
    }
}
