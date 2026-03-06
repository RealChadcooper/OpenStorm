package com.openstorm.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.openstorm.core.domain.model.RadarProduct
import com.openstorm.core.domain.model.ThemeMode
import com.openstorm.core.domain.model.UserPreferences
import com.openstorm.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    private object Keys {
        val FAVORITE_STATIONS = stringPreferencesKey("favorite_stations")
        val DEFAULT_PRODUCT = stringPreferencesKey("default_product")
        val LOOP_FRAME_COUNT = intPreferencesKey("loop_frame_count")
        val LOOP_SPEED_MS = longPreferencesKey("loop_speed_ms")
        val USE_METRIC = booleanPreferencesKey("use_metric")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    override fun observePreferences(): Flow<UserPreferences> {
        return dataStore.data.map { prefs -> prefs.toUserPreferences() }
    }

    override suspend fun getPreferences(): UserPreferences {
        return dataStore.data.first().toUserPreferences()
    }

    override suspend fun updatePreferences(update: (UserPreferences) -> UserPreferences) {
        val current = getPreferences()
        val updated = update(current)
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITE_STATIONS] = updated.favoriteStationIds.joinToString(",")
            prefs[Keys.DEFAULT_PRODUCT] = updated.defaultProduct
            prefs[Keys.LOOP_FRAME_COUNT] = updated.loopFrameCount
            prefs[Keys.LOOP_SPEED_MS] = updated.loopSpeedMs
            prefs[Keys.USE_METRIC] = updated.useMetricUnits
            prefs[Keys.THEME_MODE] = updated.themeMode.name
        }
    }

    override suspend fun addFavoriteStation(stationId: String) {
        updatePreferences { prefs ->
            prefs.copy(favoriteStationIds = (prefs.favoriteStationIds + stationId).distinct())
        }
    }

    override suspend fun removeFavoriteStation(stationId: String) {
        updatePreferences { prefs ->
            prefs.copy(favoriteStationIds = prefs.favoriteStationIds - stationId)
        }
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        favoriteStationIds = this[Keys.FAVORITE_STATIONS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList(),
        defaultProduct = this[Keys.DEFAULT_PRODUCT] ?: RadarProduct.BASE_REFLECTIVITY.code,
        loopFrameCount = this[Keys.LOOP_FRAME_COUNT] ?: 10,
        loopSpeedMs = this[Keys.LOOP_SPEED_MS] ?: 150L,
        useMetricUnits = this[Keys.USE_METRIC] ?: false,
        themeMode = this[Keys.THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
    )
}
