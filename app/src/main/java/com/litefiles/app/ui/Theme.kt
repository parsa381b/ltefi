package com.litefiles.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.litefiles.app.AppSettings
import com.litefiles.app.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF3E91FF),
    onPrimary = Color.White,
    background = Color(0xFFF6F6F6),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFF6F6F6),
    surfaceContainer = Color.White,
    onSurface = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFF6B6B6B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FB4FF),
    onPrimary = Color(0xFF00305F),
    background = Color(0xFF101010),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF101010),
    surfaceContainer = Color(0xFF1E1E20),
    onSurface = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFFA0A0A0),
)

/**
 * True when the app should draw dark: the Theme setting picked in Settings, or the device setting
 * when it is on "Follow system". [AppSettings.themeMode] is snapshot state, so switching theme
 * in Settings recomposes everything that reads this.
 */
@Composable
fun isAppDarkTheme(): Boolean = when (AppSettings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Static Samsung-like palette. For Material You colors use dynamicLightColorScheme(context) on API 31+. */
@Composable
fun LiteFilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isAppDarkTheme()) Dark else Light, content = content)
}
