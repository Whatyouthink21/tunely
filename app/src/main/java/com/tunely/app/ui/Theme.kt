package com.tunely.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentRed = Color(0xFFFA2D48)

@Composable
fun TunelyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = AccentRed, background = Color.Black, surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White
    ) else lightColorScheme(
        primary = AccentRed, background = Color.White, surface = Color(0xFFF2F2F7),
        onBackground = Color.Black, onSurface = Color.Black
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
