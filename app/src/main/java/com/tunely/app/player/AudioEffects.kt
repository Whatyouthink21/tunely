package com.tunely.app.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.tunely.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real audio processing for the settings that used to be decoration:
 * a 5-band equalizer built on the device equalizer and a loudness enhancer for
 * volume normalisation. Everything is defensive — plenty of devices refuse an
 * audio effect and playback must never break because of it.
 */
class AudioEffects(
    private val player: ExoPlayer,
    scope: CoroutineScope,
    private val settings: SettingsManager
) {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedSession = C.AUDIO_SESSION_ID_UNSET

    init {
        scope.launch { settings.eqEnabled.collect { apply() } }
        scope.launch { settings.eqPreset.collect { apply() } }
        scope.launch { settings.normalizeVolume.collect { applyNormalization(it) } }
        scope.launch {
            while (isActive) {
                if (player.audioSessionId != attachedSession) {
                    attachedSession = player.audioSessionId
                    release()
                    apply()
                    applyNormalization(settings.normalizeVolume.value)
                }
                delay(2_000)
            }
        }
    }

    private fun apply() {
        val enabled = settings.eqEnabled.value
        val session = attachedSession
        if (!enabled || session == C.AUDIO_SESSION_ID_UNSET) {
            equalizer?.enabled = false
            return
        }
        val eq = equalizer ?: runCatching { Equalizer(0, session) }.getOrNull()?.also { equalizer = it }
            ?: return
        runCatching {
            val levels = presetLevels(settings.eqPreset.value)
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return@runCatching
            val range = eq.bandLevelRange
            for (band in 0 until bands) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000f
                val gainDb = gainFor(centerHz, levels)
                val millibels = (gainDb * 100).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), millibels.toShort())
            }
            eq.enabled = true
        }.onFailure { equalizer = null }
    }

    private fun applyNormalization(enabled: Boolean) {
        if (!enabled) {
            loudness?.enabled = false
            return
        }
        val session = attachedSession
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        val enhancer = loudness ?: runCatching { LoudnessEnhancer(session) }.getOrNull()
            ?.also { loudness = it } ?: return
        runCatching {
            enhancer.setTargetGain(350)   // ~3.5 dB of gentle make-up gain
            enhancer.enabled = true
        }.onFailure { loudness = null }
    }

    /** Relative gain in dB per octave band: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz. */
    private fun presetLevels(preset: String): FloatArray = when (preset) {
        "bass_boost" -> floatArrayOf(7f, 4f, 0f, -1f, -2f)
        "treble_boost" -> floatArrayOf(-2f, -1f, 1f, 5f, 7f)
        "vocal" -> floatArrayOf(-2f, 1f, 5f, 4f, 1f)
        "electronic" -> floatArrayOf(5f, 2f, -1f, 3f, 6f)
        "rock" -> floatArrayOf(5f, 1f, -2f, 3f, 5f)
        "pop" -> floatArrayOf(-1f, 2f, 4f, 3f, -1f)
        "jazz" -> floatArrayOf(3f, 1f, 0f, 2f, 4f)
        "classical" -> floatArrayOf(3f, 1f, 0f, 2f, 4f)
        "flat" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
    }

    /** Map the 5 designed bands onto whatever the device equalizer exposes. */
    private fun gainFor(centerHz: Float, levels: FloatArray): Float {
        val table = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        table.forEachIndexed { index, hz ->
            val distance = kotlin.math.abs(hz - centerHz)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return levels[bestIndex]
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
        attachedSession = C.AUDIO_SESSION_ID_UNSET
    }
}
