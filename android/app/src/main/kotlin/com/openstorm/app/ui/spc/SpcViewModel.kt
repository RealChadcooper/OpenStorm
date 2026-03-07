package com.openstorm.app.ui.spc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openstorm.core.domain.model.SpcMesoscaleDiscussion
import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcWatch
import com.openstorm.core.domain.repository.SpcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpcUiState(
    val selectedDay: Int = 1,
    val day1Outlook: SpcOutlook? = null,
    val day2Outlook: SpcOutlook? = null,
    val day3Outlook: SpcOutlook? = null,
    val watches: List<SpcWatch> = emptyList(),
    val mesoscaleDiscussions: List<SpcMesoscaleDiscussion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val currentOutlook: SpcOutlook?
        get() = when (selectedDay) {
            1 -> day1Outlook
            2 -> day2Outlook
            3 -> day3Outlook
            else -> null
        }
}

@HiltViewModel
class SpcViewModel @Inject constructor(
    private val spcRepository: SpcRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpcUiState())
    val uiState: StateFlow<SpcUiState> = _uiState.asStateFlow()

    fun loadSpcProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val day1Deferred = async { spcRepository.getOutlook(1) }
                val day2Deferred = async { spcRepository.getOutlook(2) }
                val day3Deferred = async { spcRepository.getOutlook(3) }
                val watchesDeferred = async { spcRepository.getActiveWatches() }
                val mdsDeferred = async { spcRepository.getActiveMesoscaleDiscussions() }

                _uiState.update {
                    it.copy(
                        day1Outlook = day1Deferred.await(),
                        day2Outlook = day2Deferred.await(),
                        day3Outlook = day3Deferred.await(),
                        watches = watchesDeferred.await(),
                        mesoscaleDiscussions = mdsDeferred.await(),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load SPC products")
                }
            }
        }
    }

    fun selectDay(day: Int) {
        _uiState.update { it.copy(selectedDay = day.coerceIn(1, 3)) }
    }

    fun refresh() {
        viewModelScope.launch {
            spcRepository.refresh()
            loadSpcProducts()
        }
    }
}
