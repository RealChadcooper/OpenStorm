package com.openstorm.core.domain.repository

import com.openstorm.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {

    fun observePreferences(): Flow<UserPreferences>

    suspend fun getPreferences(): UserPreferences

    suspend fun updatePreferences(update: (UserPreferences) -> UserPreferences)

    suspend fun addFavoriteStation(stationId: String)

    suspend fun removeFavoriteStation(stationId: String)
}
