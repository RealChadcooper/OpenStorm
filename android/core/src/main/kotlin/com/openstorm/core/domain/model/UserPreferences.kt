package com.openstorm.core.domain.model

data class UserPreferences(
    val favoriteStationIds: List<String> = emptyList(),
    val defaultProduct: String = RadarProduct.BASE_REFLECTIVITY.code,
    val loopFrameCount: Int = 10,
    val loopSpeedMs: Long = 150L,
    val useMetricUnits: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
