package com.tunely.app.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tunely.app.data.AccentIds

/**
 * A Tunely accent is a pair of colours rather than one, because the whole design
 * language is built on gradients — aurora backdrops, dock indicators, progress
 * fills, waveform bars.
 */
@Immutable
data class Accent(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color
) {
    val gradient: List<Color> get() = listOf(primary, secondary)
    fun sweep(alpha: Float = 1f): Brush = Brush.linearGradient(
        listOf(primary.copy(alpha = alpha), secondary.copy(alpha = alpha))
    )
}

object Accents {
    val Nova = Accent("nova", "Nova", Color(0xFF7C5CFF), Color(0xFFFF5EA8))
    val Pulse = Accent("pulse", "Pulse", Color(0xFF22D3EE), Color(0xFF3B82F6))
    val Solar = Accent("solar", "Solar", Color(0xFFFFB020), Color(0xFFFF5A3C))
    val Orchid = Accent("orchid", "Orchid", Color(0xFFC05CFF), Color(0xFF6D5BFF))
    val Ember = Accent("ember", "Ember", Color(0xFFFF4D6D), Color(0xFFFF9F1C))
    val Glacier = Accent("glacier", "Glacier", Color(0xFF9BE8FF), Color(0xFF4F8CFF))
    val Lime = Accent("lime", "Lime", Color(0xFF5BE37D), Color(0xFF13C2A3))
    val Mono = Accent("mono", "Mono", Color(0xFFE5E7EB), Color(0xFF9CA3AF))

    val all = listOf(Nova, Pulse, Solar, Orchid, Ember, Glacier, Lime, Mono)

    fun byId(id: String?): Accent = all.firstOrNull { it.id == id }
        ?: all.firstOrNull { it.id == AccentIds.DEFAULT }
        ?: Nova
}
