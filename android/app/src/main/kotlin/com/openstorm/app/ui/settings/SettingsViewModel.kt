package com.openstorm.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.core.domain.model.ThemeMode
import com.openstorm.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useMetric: Boolean = false,
    val defaultProduct: String = "N0Q",
    val loopFrameCount: Int = 10,
    val loopSpeedMs: Long = 150L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.update {
                    SettingsUiState(
                        themeMode = prefs.themeMode,
                        useMetric = prefs.useMetricUnits,
                        defaultProduct = prefs.defaultProduct,
                        loopFrameCount = prefs.loopFrameCount,
                        loopSpeedMs = prefs.loopSpeedMs,
                    )
                }
            }
        }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { prefs ->
                val next = when (prefs.themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                }
                prefs.copy(themeMode = next)
            }
        }
    }

    fun setMetric(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(useMetricUnits = enabled) }
        }
    }
}
