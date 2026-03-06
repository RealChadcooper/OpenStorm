package com.openstorm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// OpenStorm brand colors — professional meteorology aesthetic
// Deep navy backgrounds, electric blue accents, warm amber alerts
private val StormNavy = Color(0xFF0D1B2A)
private val StormDeepBlue = Color(0xFF1B2838)
private val StormSurface = Color(0xFF1E293B)
private val StormElectricBlue = Color(0xFF38BDF8)
private val StormCyan = Color(0xFF22D3EE)
private val StormAmber = Color(0xFFFBBF24)
private val StormRed = Color(0xFFEF4444)
private val StormGreen = Color(0xFF22C55E)
private val StormWhite = Color(0xFFF1F5F9)
private val StormGray = Color(0xFF94A3B8)

// Light theme
private val LightSurface = Color(0xFFF8FAFC)
private val LightBackground = Color(0xFFFFFFFF)
private val LightPrimary = Color(0xFF0369A1)
private val LightOnSurface = Color(0xFF0F172A)

private val DarkColorScheme = darkColorScheme(
    primary = StormElectricBlue,
    onPrimary = StormNavy,
    primaryContainer = StormDeepBlue,
    secondary = StormCyan,
    onSecondary = StormNavy,
    tertiary = StormAmber,
    background = StormNavy,
    surface = StormSurface,
    onBackground = StormWhite,
    onSurface = StormWhite,
    error = StormRed,
    outline = StormGray,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    tertiary = Color(0xFFD97706),
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    error = StormRed,
    outline = Color(0xFF64748B),
)

@Composable
fun OpenStormTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenStormTypography,
        content = content,
    )
}
