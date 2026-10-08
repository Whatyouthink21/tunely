package com.tunely.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Named accent identities; the actual colours live in ui/design/Accents.kt. */
object AccentIds {
    val ALL = listOf("nova", "pulse", "solar", "orchid", "ember", "glacier", "lime", "mono")
    const val DEFAULT = "nova"
}

object AudioQuality {
    val ALL = listOf("data_saver", "balanced", "high", "audiophile")
    const val DEFAULT = "high"

    /** Target bitrate in kbps; 0 means "as high as the provider offers". */
    fun targetKbps(id: String): Int = when (id) {
        "data_saver" -> 96
        "balanced" -> 128
        "high" -> 256
        else -> 0
    }

    fun label(id: String): String = when (id) {
        "data_saver" -> "Data saver"
        "balanced" -> "Balanced"
        "high" -> "High"
        else -> "Audiophile"
    }
}

/** Player artwork treatment. */
object PlayerStyle {
    const val DISC = "disc"
    const val COVER = "cover"
    val ALL = listOf(DISC, COVER)
}

/**
 * Central settings store backed by SharedPreferences with reactive StateFlows.
 * Every setter writes through to disk, so preferences survive a process death.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tunely_settings", Context.MODE_PRIVATE)

    // ─── Appearance ──────────────────────────────────────────────────
    val themeMode = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    fun setThemeMode(m: String) = putString("theme", themeMode, m)

    val accent = MutableStateFlow(prefs.getString("accent", AccentIds.DEFAULT) ?: AccentIds.DEFAULT)
    fun setAccent(a: String) = putString("accent", accent, a)

    val aurora = MutableStateFlow(prefs.getBoolean("aurora", true))
    fun setAurora(on: Boolean) = putBoolean("aurora", aurora, on)

    val motionEnabled = MutableStateFlow(prefs.getBoolean("motion", true))
    fun setMotionEnabled(on: Boolean) = putBoolean("motion", motionEnabled, on)

    val playerStyle = MutableStateFlow(prefs.getString("player_style", PlayerStyle.DISC) ?: PlayerStyle.DISC)
    fun setPlayerStyle(s: String) = putString("player_style", playerStyle, s)

    val blurArtwork = MutableStateFlow(prefs.getBoolean("blur_art", true))
    fun setBlurArtwork(on: Boolean) = putBoolean("blur_art", blurArtwork, on)

    // ─── Audio ───────────────────────────────────────────────────────
    val audioQuality = MutableStateFlow(prefs.getString("audio_quality", AudioQuality.DEFAULT) ?: AudioQuality.DEFAULT)
    fun setAudioQuality(q: String) = putString("audio_quality", audioQuality, q)

    val playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1f))
    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat("playback_speed", speed).apply(); playbackSpeed.value = speed
    }

    val crossfadeSeconds = MutableStateFlow(prefs.getInt("crossfade", 0))
    fun setCrossfadeSeconds(sec: Int) {
        prefs.edit().putInt("crossfade", sec).apply(); crossfadeSeconds.value = sec
    }

    val normalizeVolume = MutableStateFlow(prefs.getBoolean("normalize", false))
    fun setNormalizeVolume(on: Boolean) = putBoolean("normalize", normalizeVolume, on)

    val eqEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", false))
    fun setEqEnabled(on: Boolean) = putBoolean("eq_enabled", eqEnabled, on)

    val eqPreset = MutableStateFlow(prefs.getString("eq_preset", "flat") ?: "flat")
    fun setEqPreset(p: String) = putString("eq_preset", eqPreset, p)

    val gapless = MutableStateFlow(prefs.getBoolean("gapless", true))
    fun setGapless(on: Boolean) = putBoolean("gapless", gapless, on)

    val autoPlay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    fun setAutoPlay(on: Boolean) = putBoolean("autoplay", autoPlay, on)

    // ─── Lyrics ──────────────────────────────────────────────────────
    val lyricsFontSize = MutableStateFlow(prefs.getInt("lyrics_font", 30))
    fun setLyricsFontSize(s: Int) {
        prefs.edit().putInt("lyrics_font", s).apply(); lyricsFontSize.value = s
    }

    val lyricsGlow = MutableStateFlow(prefs.getBoolean("lyrics_glow", true))
    fun setLyricsGlow(on: Boolean) = putBoolean("lyrics_glow", lyricsGlow, on)

    val lyricsCenter = MutableStateFlow(prefs.getBoolean("lyrics_center", true))
    fun setLyricsCenter(on: Boolean) = putBoolean("lyrics_center", lyricsCenter, on)

    // ─── Streaming sources ───────────────────────────────────────────
    val sourceYoutube = MutableStateFlow(prefs.getBoolean("src_youtube", true))
    fun setSourceYoutube(on: Boolean) = putBoolean("src_youtube", sourceYoutube, on)

    val sourceSoundcloud = MutableStateFlow(prefs.getBoolean("src_soundcloud", true))
    fun setSourceSoundcloud(on: Boolean) = putBoolean("src_soundcloud", sourceSoundcloud, on)

    val sourceBandcamp = MutableStateFlow(prefs.getBoolean("src_bandcamp", true))
    fun setSourceBandcamp(on: Boolean) = putBoolean("src_bandcamp", sourceBandcamp, on)

    val sourceAudius = MutableStateFlow(prefs.getBoolean("src_audius", true))
    fun setSourceAudius(on: Boolean) = putBoolean("src_audius", sourceAudius, on)

    val sourceDeezer = MutableStateFlow(prefs.getBoolean("src_deezer", false))
    fun setSourceDeezer(on: Boolean) = putBoolean("src_deezer", sourceDeezer, on)

    val sourceItunes = MutableStateFlow(prefs.getBoolean("src_itunes", false))
    fun setSourceItunes(on: Boolean) = putBoolean("src_itunes", sourceItunes, on)

    val sourceRadio = MutableStateFlow(prefs.getBoolean("src_radio", false))
    fun setSourceRadio(on: Boolean) = putBoolean("src_radio", sourceRadio, on)

    val sourcePiped = MutableStateFlow(prefs.getBoolean("src_piped", false))
    fun setSourcePiped(on: Boolean) = putBoolean("src_piped", sourcePiped, on)

    /** Look up the toggle flow for a provider so Settings can be data-driven. */
    fun sourceFor(id: String): MutableStateFlow<Boolean>? = when (SourceIds.normalize(id)) {
        SourceIds.YOUTUBE -> sourceYoutube
        SourceIds.SOUNDCLOUD -> sourceSoundcloud
        SourceIds.BANDCAMP -> sourceBandcamp
        SourceIds.AUDIUS -> sourceAudius
        SourceIds.DEEZER -> sourceDeezer
        SourceIds.ITUNES -> sourceItunes
        SourceIds.RADIO -> sourceRadio
        SourceIds.PIPED -> sourcePiped
        else -> null
    }

    fun setSource(id: String, enabled: Boolean) {
        when (SourceIds.normalize(id)) {
            SourceIds.YOUTUBE -> setSourceYoutube(enabled)
            SourceIds.SOUNDCLOUD -> setSourceSoundcloud(enabled)
            SourceIds.BANDCAMP -> setSourceBandcamp(enabled)
            SourceIds.AUDIUS -> setSourceAudius(enabled)
            SourceIds.DEEZER -> setSourceDeezer(enabled)
            SourceIds.ITUNES -> setSourceItunes(enabled)
            SourceIds.RADIO -> setSourceRadio(enabled)
            SourceIds.PIPED -> setSourcePiped(enabled)
        }
    }

    // ─── Data ────────────────────────────────────────────────────────
    val cacheEnabled = MutableStateFlow(prefs.getBoolean("cache", true))
    fun setCacheEnabled(on: Boolean) = putBoolean("cache", cacheEnabled, on)

    // ─── Sleep timer ─────────────────────────────────────────────────
    val sleepTimerMinutes = MutableStateFlow(prefs.getInt("sleep_timer", 30))
    fun setSleepTimerMinutes(m: Int) {
        prefs.edit().putInt("sleep_timer", m).apply(); sleepTimerMinutes.value = m
    }

    init {
        migrateLegacyPrefs()
    }

    /** True once the POST_NOTIFICATIONS prompt has been shown; asked only once. */
    var notificationPrompted: Boolean
        get() = prefs.getBoolean("prompted_notifications", false)
        set(value) = prefs.edit().putBoolean("prompted_notifications", value).apply()

    /** One-shot migration of pre-1.0 settings so nobody loses their setup. */
    private fun migrateLegacyPrefs() {
        if (prefs.getBoolean("migrated_v2", false)) return
        val editor = prefs.edit()
        if (!prefs.contains("src_soundcloud") && prefs.contains("src_sc")) {
            editor.putBoolean("src_soundcloud", prefs.getBoolean("src_sc", true))
        }
        if (!prefs.contains("src_youtube")) {
            val legacy = prefs.getBoolean("src_yt", true) || prefs.getBoolean("src_ytm", true)
            editor.putBoolean("src_youtube", legacy)
        }
        editor.putBoolean("migrated_v2", true).apply()
        sourceSoundcloud.value = prefs.getBoolean("src_soundcloud", true)
        sourceYoutube.value = prefs.getBoolean("src_youtube", true)
    }

    private fun putBoolean(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply(); flow.value = value
    }

    private fun putString(key: String, flow: MutableStateFlow<String>, value: String) {
        prefs.edit().putString(key, value).apply(); flow.value = value
    }

    companion object {
        val EQ_PRESETS = listOf(
            "flat", "bass_boost", "treble_boost", "vocal", "electronic", "rock", "pop", "jazz", "classical"
        )
        val CROSSFADE_OPTIONS = listOf(0, 3, 6, 9, 12)
        val PLAYBACK_SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        fun label(id: String): String =
            id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
