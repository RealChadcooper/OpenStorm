package com.openstorm.app.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.app.location.LocationProvider
import com.openstorm.app.ui.map.CameraState
import com.openstorm.core.domain.model.Alert
import com.openstorm.core.domain.model.RadarFrame
import com.openstorm.core.domain.model.RadarProduct
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcWatch
import com.openstorm.core.domain.repository.AlertRepository
import com.openstorm.core.domain.repository.PreferencesRepository
import com.openstorm.core.domain.repository.RadarRepository
import com.openstorm.core.domain.repository.SpcRepository
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
import javax.inject.Inject

/** Permission state communicated from the UI layer. */
enum class LocationPermissionState {
    /** Not yet determined — waiting for user response. */
    UNKNOWN,
    /** Permission granted — can use GPS. */
    GRANTED,
    /** Permission denied — use fallback location. */
    DENIED,
}

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
    val cameraState: CameraState = CameraState(
        latitude = LocationProvider.DEFAULT_LAT,
        longitude = LocationProvider.DEFAULT_LON,
        zoom = 4.0,
    ),
    val radarOpacity: Float = 0.75f,
    val alerts: List<Alert> = emptyList(),
    val showAlertOverlay: Boolean = true,
    val showStationMarkers: Boolean = true,
    val spcOutlook: SpcOutlook? = null,
    val spcWatches: List<SpcWatch> = emptyList(),
    val showSpcOverlay: Boolean = true,
    /** The tile URL template for the currently displayed frame. */
    val currentTileUrl: String? = null,
    /** Whether we've already initialized with a location. */
    val locationInitialized: Boolean = false,
    val locationPermission: LocationPermissionState = LocationPermissionState.UNKNOWN,
)

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
    private val alertRepository: AlertRepository,
    private val spcRepository: SpcRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null

    /**
     * Called by the UI when location permission status changes.
     * Triggers location acquisition and nearest-station loading.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        val newState = if (granted) LocationPermissionState.GRANTED else LocationPermissionState.DENIED
        _uiState.update { it.copy(locationPermission = newState) }

        if (_uiState.value.locationInitialized) return

        viewModelScope.launch {
            val lat: Double
            val lon: Double

            if (granted) {
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    Timber.d("Using GPS location: $lat, $lon")
                } else {
                    lat = LocationProvider.DEFAULT_LAT
                    lon = LocationProvider.DEFAULT_LON
                    Timber.d("GPS returned null, using default location")
                }
            } else {
                lat = LocationProvider.DEFAULT_LAT
                lon = LocationProvider.DEFAULT_LON
                Timber.d("Location denied, using default location")
            }

            _uiState.update { it.copy(locationInitialized = true) }
            loadNearestStation(lat, lon)
        }
    }

    /**
     * Re-center on device location (e.g., "My Location" button).
     * Requires permission to already be granted.
     */
    fun recenterOnDeviceLocation() {
        if (_uiState.value.locationPermission != LocationPermissionState.GRANTED) return

        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation() ?: return@launch
            loadNearestStation(location.latitude, location.longitude)
        }
    }

    fun loadNearestStation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                radarRepository.refreshStations(lat, lon)
                val stations = radarRepository.observeNearestStations(lat, lon).first()
                val nearest = stations.firstOrNull()
                _uiState.update {
                    it.copy(
                        nearbyStations = stations,
                        station = nearest,
                    )
                }
                if (nearest != null) {
                    // Fly camera to station
                    _uiState.update {
                        it.copy(
                            cameraState = CameraState(
                                latitude = nearest.lat,
                                longitude = nearest.lon,
                                zoom = 7.0,
                            ),
                        )
                    }
                    loadFrames(nearest.id)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "No radar stations found nearby") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stations") }
            }
        }

        // Also load alerts for this area
        viewModelScope.launch {
            try {
                alertRepository.refreshAlerts(lat, lon)
                alertRepository.observeActiveAlerts(lat, lon).collect { alerts ->
                    _uiState.update { it.copy(alerts = alerts) }
                }
            } catch (_: Exception) {
                // Alerts are non-critical; silently continue
            }
        }

        // Load SPC outlooks and watches
        viewModelScope.launch {
            try {
                val outlook = spcRepository.getOutlook(1)
                val watches = spcRepository.getActiveWatches()
                _uiState.update { it.copy(spcOutlook = outlook, spcWatches = watches) }
            } catch (_: Exception) {
                // SPC data is non-critical
            }
        }
    }

    fun selectStation(stationId: String) {
        viewModelScope.launch {
            val station = radarRepository.getStation(stationId)
            _uiState.update {
                it.copy(
                    station = station,
                    cameraState = if (station != null) {
                        CameraState(latitude = station.lat, longitude = station.lon, zoom = 7.0)
                    } else {
                        it.cameraState
                    },
                )
            }
            if (station != null) {
                loadFrames(stationId)
            }
        }
    }

    fun selectProduct(product: RadarProduct) {
        _uiState.update { it.copy(selectedProduct = product) }
        val stationId = _uiState.value.station?.id ?: return
        loadFrames(stationId)
    }

    fun toggleLoop() {
        if (_uiState.value.isLooping) stopLoop() else startLoop()
    }

    fun seekToFrame(index: Int) {
        val clamped = index.coerceIn(0, (_uiState.value.frames.size - 1).coerceAtLeast(0))
        _uiState.update {
            val frame = it.frames.getOrNull(clamped)
            it.copy(
                currentFrameIndex = clamped,
                currentTileUrl = frame?.tileUrl,
            )
        }
    }

    fun setRadarOpacity(opacity: Float) {
        _uiState.update { it.copy(radarOpacity = opacity.coerceIn(0f, 1f)) }
    }

    fun toggleAlertOverlay() {
        _uiState.update { it.copy(showAlertOverlay = !it.showAlertOverlay) }
    }

    fun toggleSpcOverlay() {
        _uiState.update { it.copy(showSpcOverlay = !it.showSpcOverlay) }
    }

    fun onCameraIdle(camera: CameraState) {
        _uiState.update { it.copy(cameraState = camera) }
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
                val lastIndex = (frames.size - 1).coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        frames = frames,
                        currentFrameIndex = lastIndex,
                        currentTileUrl = frames.getOrNull(lastIndex)?.tileUrl,
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
                val frame = state.frames[nextIndex]
                _uiState.update {
                    it.copy(
                        currentFrameIndex = nextIndex,
                        currentTileUrl = frame.tileUrl,
                    )
                }

                // Dwell longer on the last (most recent) frame
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
