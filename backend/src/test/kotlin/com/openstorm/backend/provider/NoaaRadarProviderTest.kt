package com.openstorm.backend.provider

import com.openstorm.backend.provider.radar.NoaaRadarProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoaaRadarProviderTest {

    private val provider = NoaaRadarProvider()

    @Test
    fun `getStations returns non-empty list`() = runBlocking {
        val stations = provider.getStations()
        assertTrue(stations.isNotEmpty())
        assertTrue(stations.size > 50, "Should have at least 50 NEXRAD stations")
    }

    @Test
    fun `getStation returns known station`() = runBlocking {
        val station = provider.getStation("KTLX")
        assertNotNull(station)
        assertEquals("Oklahoma City, OK", station.name)
    }

    @Test
    fun `getFrames returns requested count`() = runBlocking {
        val frames = provider.getFrames("KTLX", "N0Q", 5)
        assertEquals(5, frames.size)
        assertTrue(frames.all { it.tileUrl.contains("KTLX") })
    }
}
