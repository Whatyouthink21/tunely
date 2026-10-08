package com.tunely.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.R
import com.tunely.app.data.SettingsManager

// ─── Type ───────────────────────────────────────────────────────────────
/**
 * Outfit (SIL OFL) carries the whole app. It is a variable font: the weight
 * axis is requested explicitly so display type gets real heavyweight cuts.
 */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int): Font = Font(
    resId = R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
val TunelyFontFamily: FontFamily = FontFamily(
    outfit(400), outfit(500), outfit(600), outfit(700), outfit(800)
)

object TunelyType {
    val display = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W700,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp
    )
    val headline = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W700,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp
    )
    val title = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W600,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp
    )
    val subtitle = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W600,
        fontSize = 16.sp, lineHeight = 21.sp
    )
    val body = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W400,
        fontSize = 14.5.sp, lineHeight = 20.sp
    )
    val caption = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W400,
        fontSize = 12.5.sp, lineHeight = 17.sp
    )
    val micro = TextStyle(
        fontFamily = TunelyFontFamily, fontWeight = FontWeight.W600,
        fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 1.1.sp
    )
}

// ─── Colour ─────────────────────────────────────────────────────────────
@Immutable
data class TunelyColors(
    val accent: Accent,
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceGlass: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color
) {
    val accentGradient: List<Color> get() = accent.gradient
    /** Backdrop seeds: the accent plus a cool counterweight. */
    val backdropSeeds: List<Color>
        get() = if (isLight) listOf(accent.primary, accent.secondary, Color(0xFF8AB4FF))
        else listOf(accent.primary, accent.secondary, Color(0xFF2A1D6B))
}

val LocalTunelyColors = compositionLocalOf { darkTunelyColors(Accents.Nova) }

private fun darkTunelyColors(accent: Accent) = TunelyColors(
    accent = accent,
    isLight = false,
    background = Color(0xFF07060D),
    surface = Color(0xFF100E1A),
    surfaceHigh = Color(0xFF1A1727),
    surfaceGlass = Color(0xCC141122),
    outline = Color.White.copy(alpha = 0.08f),
    outlineStrong = Color.White.copy(alpha = 0.16f),
    textPrimary = Color(0xFFF6F4FF),
    textSecondary = Color(0xFFA9A3C2),
    textTertiary = Color(0xFF6F6A87),
    scrim = Color(0xCC05040A)
)

private fun lightTunelyColors(accent: Accent) = TunelyColors(
    accent = accent,
    isLight = true,
    background = Color(0xFFF7F5FD),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEAF8),
    surfaceGlass = Color(0xE6FFFFFF),
    outline = Color(0xFF14121F).copy(alpha = 0.07f),
    outlineStrong = Color(0xFF14121F).copy(alpha = 0.14f),
    textPrimary = Color(0xFF131120),
    textSecondary = Color(0xFF565070),
    textTertiary = Color(0xFF8B85A5),
    scrim = Color(0x990C0A16)
)

val LocalTunelyType = compositionLocalOf { TunelyType }

/** Spacing & shape rhythm, so nothing is hard-coded per screen. */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
    val xxl = 32.dp
    val radiusSm = 14.dp
    val radiusMd = 20.dp
    val radiusLg = 28.dp
    val dockHeight = 62.dp
}

@Composable
fun TunelyTheme(
    settings: SettingsManager? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val mode = settings?.themeMode?.collectAsState()?.value ?: "dark"
    val accentId = settings?.accent?.collectAsState()?.value
    val motion = settings?.motionEnabled?.collectAsState()?.value ?: true

    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val accent = Accents.byId(accentId)
    val colors = if (dark) darkTunelyColors(accent) else lightTunelyColors(accent)

    val scheme = if (dark) darkColorScheme(
        primary = accent.primary,
        secondary = accent.secondary,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceHigh,
        onPrimary = Color.White,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        outline = colors.outline
    ) else lightColorScheme(
        primary = accent.primary,
        secondary = accent.secondary,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceHigh,
        onPrimary = Color.White,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        outline = colors.outline
    )

    val typography = Typography(
        displaySmall = TunelyType.display,
        headlineSmall = TunelyType.headline,
        titleLarge = TunelyType.title,
        titleMedium = TunelyType.subtitle,
        bodyLarge = TunelyType.body,
        bodyMedium = TunelyType.body,
        labelSmall = TunelyType.micro
    )

    CompositionLocalProvider(
        LocalTunelyColors provides colors,
        LocalTunelyType provides TunelyType,
        LocalMotionEnabled provides motion
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Shorthand used all over the UI. */
object T {
    val colors: TunelyColors
        @Composable get() = LocalTunelyColors.current
    val type: TunelyType
        @Composable get() = LocalTunelyType.current
}
