package com.openstorm.app.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarProduct
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.repository.PreferencesRepository
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
import javax.inject.Inject

data class RadarUiState(
    val station: RadarStation? = null,
    val nearbyStations: List<RadarStation> = emptyList(),
    val selectedProduct: RadarProduct = RadarProduct.BASE_REFLECTIVITY,
    val frames: List<RadarFrame> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isLooping: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: String? = null,
)

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null

    fun loadNearestStation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                radarRepository.refreshStations(lat, lon)
                val stations = radarRepository.observeNearestStations(lat, lon).first()
                val nearest = stations.firstOrNull()
                _uiState.update { it.copy(nearbyStations = stations, station = nearest) }
                if (nearest != null) {
                    loadFrames(nearest.id)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "No radar stations found nearby") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stations") }
            }
        }
    }

    fun selectStation(stationId: String) {
        viewModelScope.launch {
            val station = radarRepository.getStation(stationId)
            _uiState.update { it.copy(station = station) }
            loadFrames(stationId)
        }
    }

    fun selectProduct(product: RadarProduct) {
        _uiState.update { it.copy(selectedProduct = product) }
        val stationId = _uiState.value.station?.id ?: return
        loadFrames(stationId)
    }

    fun toggleLoop() {
        val current = _uiState.value
        if (current.isLooping) {
            stopLoop()
        } else {
            startLoop()
        }
    }

    fun seekToFrame(index: Int) {
        _uiState.update { it.copy(currentFrameIndex = index.coerceIn(0, it.frames.size - 1)) }
    }

    fun refresh() {
        val stationId = _uiState.value.station?.id ?: return
        loadFrames(stationId)
    }

    private fun loadFrames(stationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val prefs = preferencesRepository.getPreferences()
                val product = _uiState.value.selectedProduct.code
                val frames = radarRepository.getFrames(stationId, product, prefs.loopFrameCount)
                _uiState.update {
                    it.copy(
                        frames = frames,
                        currentFrameIndex = frames.size - 1,
                        isLoading = false,
                        lastUpdated = frames.lastOrNull()?.timestamp?.toString(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load radar") }
            }
        }
    }

    private fun startLoop() {
        _uiState.update { it.copy(isLooping = true) }
        loopJob = viewModelScope.launch {
            val prefs = preferencesRepository.getPreferences()
            while (true) {
                val state = _uiState.value
                if (state.frames.isEmpty()) break
                val nextIndex = (state.currentFrameIndex + 1) % state.frames.size
                _uiState.update { it.copy(currentFrameIndex = nextIndex) }

                // Pause longer on the last frame
                val delayMs = if (nextIndex == state.frames.size - 1) {
                    prefs.loopSpeedMs * 4
                } else {
                    prefs.loopSpeedMs
                }
                delay(delayMs)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        _uiState.update { it.copy(isLooping = false) }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
    }
}
