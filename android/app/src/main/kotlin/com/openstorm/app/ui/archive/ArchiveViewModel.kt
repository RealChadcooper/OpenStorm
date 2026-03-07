package com.openstorm.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.core.domain.model.ArchiveQuery
import com.openstorm.core.domain.model.RadarFrameSummary
import com.openstorm.core.domain.model.RadarPlaybackManifest
import com.openstorm.core.domain.model.RadarProduct
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.repository.RadarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ArchiveUiState(
    val station: RadarStation? = null,
    val nearbyStations: List<RadarStation> = emptyList(),
    val selectedProduct: String = RadarProduct.BASE_REFLECTIVITY.code,
    val manifest: RadarPlaybackManifest? = null,
    val frames: List<RadarFrameSummary> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    /** The time window start (default: 2 hours ago) */
    val windowStart: Instant = Instant.now().minus(2, ChronoUnit.HOURS),
    /** The time window end (default: now) */
    val windowEnd: Instant = Instant.now(),
    /** Retention hours reported by backend */
    val retentionHours: Int = 24,
) {
    val currentFrame: RadarFrameSummary?
        get() = frames.getOrNull(currentFrameIndex)

    val currentTileUrl: String?
        get() = currentFrame?.tileUrl

    val frameTimestamp: String?
        get() = currentFrame?.timestamp?.toString()
            ?.substringAfter("T")
            ?.substringBefore(".")
            ?.let { "${it}Z" }

    /** Timeline progress 0.0..1.0 */
    val timelineProgress: Float
        get() = if (frames.size <= 1) 0f
        else currentFrameIndex.toFloat() / (frames.size - 1).toFloat()
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    companion object {
        private const val PLAYBACK_DELAY_MS = 200L
        private const val LAST_FRAME_DWELL_MS = 800L
    }

    fun initWithStation(stationId: String) {
        if (_uiState.value.station?.id == stationId) return
        viewModelScope.launch {
            val station = radarRepository.getStation(stationId) ?: return@launch
            _uiState.update { it.copy(station = station) }
            loadManifest()
        }
    }

    fun loadNearbyStations(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                radarRepository.refreshStations(lat, lon)
                val stations = radarRepository.observeNearestStations(lat, lon).first()
                _uiState.update { it.copy(nearbyStations = stations) }

                // Auto-select nearest if none selected
                if (_uiState.value.station == null && stations.isNotEmpty()) {
                    _uiState.update { it.copy(station = stations.first()) }
                    loadManifest()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load nearby stations for archive")
            }
        }
    }

    fun selectStation(station: RadarStation) {
        stopPlayback()
        _uiState.update { it.copy(station = station) }
        loadManifest()
    }

    fun selectProduct(product: String) {
        stopPlayback()
        _uiState.update { it.copy(selectedProduct = product) }
        loadManifest()
    }

    fun setTimeWindow(start: Instant, end: Instant) {
        stopPlayback()
        _uiState.update { it.copy(windowStart = start, windowEnd = end) }
        loadManifest()
    }

    fun setTimeWindowHours(hours: Int) {
        val now = Instant.now()
        setTimeWindow(now.minus(hours.toLong(), ChronoUnit.HOURS), now)
    }

    fun loadManifest() {
        val state = _uiState.value
        val stationId = state.station?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val query = ArchiveQuery(
                    stationId = stationId,
                    product = state.selectedProduct,
                    start = state.windowStart,
                    end = state.windowEnd,
                )
                val manifest = radarRepository.getPlaybackManifest(query)
                if (manifest != null) {
                    val lastIdx = (manifest.frames.size - 1).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            manifest = manifest,
                            frames = manifest.frames,
                            currentFrameIndex = lastIdx,
                            retentionHours = manifest.retentionHours,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "No archive data available")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load archive")
                }
            }
        }
    }

    fun seekToFrame(index: Int) {
        val clamped = index.coerceIn(0, (_uiState.value.frames.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentFrameIndex = clamped) }
    }

    fun seekToTimestamp(target: Instant) {
        val frames = _uiState.value.frames
        if (frames.isEmpty()) return

        val nearestIdx = frames.indices.minByOrNull {
            kotlin.math.abs(frames[it].timestamp.epochSecond - target.epochSecond)
        } ?: return

        seekToFrame(nearestIdx)
    }

    fun previousFrame() {
        val state = _uiState.value
        if (state.currentFrameIndex > 0) {
            seekToFrame(state.currentFrameIndex - 1)
        }
    }

    fun nextFrame() {
        val state = _uiState.value
        if (state.currentFrameIndex < state.frames.size - 1) {
            seekToFrame(state.currentFrameIndex + 1)
        }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) stopPlayback() else startPlayback()
    }

    private fun startPlayback() {
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                if (state.frames.isEmpty()) break

                val nextIndex = (state.currentFrameIndex + 1) % state.frames.size
                _uiState.update { it.copy(currentFrameIndex = nextIndex) }

                val delayMs = if (nextIndex == state.frames.size - 1) {
                    LAST_FRAME_DWELL_MS
                } else {
                    PLAYBACK_DELAY_MS
                }
                delay(delayMs)
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
