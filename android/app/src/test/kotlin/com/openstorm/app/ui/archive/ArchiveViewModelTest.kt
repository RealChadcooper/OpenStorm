package com.openstorm.app.ui.archive

import com.openstorm.test.FakeRadarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRadarRepository
    private lateinit var viewModel: ArchiveViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeRadarRepository()
        viewModel = ArchiveViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        val state = viewModel.uiState.value
        assertNull(state.station)
        assertFalse(state.isLoading)
        assertFalse(state.isPlaying)
        assertTrue(state.frames.isEmpty())
    }

    @Test
    fun `initWithStation loads station and manifest`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.station)
        assertEquals("KTLX", state.station!!.id)
        assertTrue(state.frames.isNotEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `setTimeWindowHours updates window and reloads`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        val framesBefore = viewModel.uiState.value.frames.size
        viewModel.setTimeWindowHours(6)
        advanceUntilIdle()

        val framesAfter = viewModel.uiState.value.frames.size
        // 6-hour window should have more frames than 2-hour default
        assertTrue(framesAfter >= framesBefore)
    }

    @Test
    fun `seekToFrame updates index`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(3)
        assertEquals(3, viewModel.uiState.value.currentFrameIndex)
    }

    @Test
    fun `seekToFrame clamps to valid range`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(-5)
        assertEquals(0, viewModel.uiState.value.currentFrameIndex)

        viewModel.seekToFrame(99999)
        assertEquals(
            viewModel.uiState.value.frames.size - 1,
            viewModel.uiState.value.currentFrameIndex,
        )
    }

    @Test
    fun `previousFrame decrements index`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(5)
        viewModel.previousFrame()
        assertEquals(4, viewModel.uiState.value.currentFrameIndex)
    }

    @Test
    fun `nextFrame increments index`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(0)
        viewModel.nextFrame()
        assertEquals(1, viewModel.uiState.value.currentFrameIndex)
    }

    @Test
    fun `previousFrame does not go below zero`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(0)
        viewModel.previousFrame()
        assertEquals(0, viewModel.uiState.value.currentFrameIndex)
    }

    @Test
    fun `selectProduct reloads manifest`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.selectProduct("N0U")
        advanceUntilIdle()

        assertEquals("N0U", viewModel.uiState.value.selectedProduct)
        assertTrue(viewModel.uiState.value.frames.isNotEmpty())
    }

    @Test
    fun `togglePlayback starts and stops`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayback()
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `seekToTimestamp finds nearest frame`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        val frames = viewModel.uiState.value.frames
        if (frames.size >= 3) {
            val targetTime = frames[2].timestamp
            viewModel.seekToFrame(0) // reset position
            viewModel.seekToTimestamp(targetTime)
            assertEquals(2, viewModel.uiState.value.currentFrameIndex)
        }
    }

    @Test
    fun `currentTileUrl updates with frame index`() = runTest {
        viewModel.initWithStation("KTLX")
        advanceUntilIdle()

        viewModel.seekToFrame(0)
        val url = viewModel.uiState.value.currentTileUrl
        assertNotNull(url)
        assertTrue(url.contains("KTLX"))
    }
}
