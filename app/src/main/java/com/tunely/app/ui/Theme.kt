package com.tunely.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.tunely.app.data.SettingsManager

// ─── Accent Palette ─────────────────────────────────────────────────────
val AccentRed = Color(0xFFFA2D48)
val AccentBlue = Color(0xFF0A84FF)
val AccentPurple = Color(0xFFBF5AF2)
val AccentGreen = Color(0xFF30D158)
val AccentOrange = Color(0xFFFF9F0A)
val AccentPink = Color(0xFFFF375F)
val AccentTeal = Color(0xFF64D2FF)
val AccentIndigo = Color(0xFF5E5CE6)

fun accentFor(name: String): Color = when (name) {
    "blue" -> AccentBlue
    "purple" -> AccentPurple
    "green" -> AccentGreen
    "orange" -> AccentOrange
    "pink" -> AccentPink
    "teal" -> AccentTeal
    "indigo" -> AccentIndigo
    else -> AccentRed
}

@Composable
fun TunelyTheme(
    settings: SettingsManager? = null,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val mode = settings?.themeMode?.collectAsState()?.value ?: "system"
    val accent = settings?.accentColor?.collectAsState()?.value ?: "red"
    val useDark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> dark
    }
    val primary = accentFor(accent)
    val scheme = if (useDark) darkColorScheme(
        primary = primary,
        background = Color.Black,
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        onBackground = Color.White,
        onSurface = Color.White,
        onPrimary = Color.White
    ) else lightColorScheme(
        primary = primary,
        background = Color.White,
        surface = Color(0xFFF2F2F7),
        surfaceVariant = Color(0xFFE5E5EA),
        onBackground = Color.Black,
        onSurface = Color.Black,
        onPrimary = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
