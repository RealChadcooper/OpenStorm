package com.openstorm.app.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.repository.RadarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationsUiState(
    val stations: List<RadarStation> = emptyList(),
    val selectedStationId: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationsUiState())
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    fun loadStations(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            radarRepository.refreshStations(lat, lon, 500.0)
            radarRepository.observeNearestStations(lat, lon, 500.0).collect { stations ->
                _uiState.update { it.copy(stations = stations, isLoading = false) }
            }
        }
    }

    fun selectStation(stationId: String) {
        _uiState.update { it.copy(selectedStationId = stationId) }
    }
}
