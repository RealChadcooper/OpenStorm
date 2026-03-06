package com.openstorm.app

import androidx.lifecycle.ViewModel
import com.openstorm.core.domain.model.ThemeMode
import com.openstorm.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val themeMode: Flow<ThemeMode> = preferencesRepository.observePreferences()
        .map { it.themeMode }
}
