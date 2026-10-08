package com.tunely.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central settings store backed by SharedPreferences with reactive StateFlows.
 * Apple Music-style: every preference is a simple toggle or picker.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tunely_settings", Context.MODE_PRIVATE)

    // ─── Audio ───────────────────────────────────────────────────────
    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "high") ?: "high")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()
    fun setAudioQuality(q: String) { prefs.edit().putString("audio_quality", q).apply(); _audioQuality.value = q }

    private val _crossfadeDuration = MutableStateFlow(prefs.getInt("crossfade", 0))
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()
    fun setCrossfadeDuration(sec: Int) { prefs.edit().putInt("crossfade", sec).apply(); _crossfadeDuration.value = sec }

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    fun setPlaybackSpeed(speed: Float) { prefs.edit().putFloat("playback_speed", speed).apply(); _playbackSpeed.value = speed }

    private val _normalizeVolume = MutableStateFlow(prefs.getBoolean("normalize", false))
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()
    fun setNormalizeVolume(on: Boolean) { prefs.edit().putBoolean("normalize", on).apply(); _normalizeVolume.value = on }

    private val _eqEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", false))
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    fun setEqEnabled(on: Boolean) { prefs.edit().putBoolean("eq_enabled", on).apply(); _eqEnabled.value = on }

    private val _eqPreset = MutableStateFlow(prefs.getString("eq_preset", "flat") ?: "flat")
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()
    fun setEqPreset(p: String) { prefs.edit().putString("eq_preset", p).apply(); _eqPreset.value = p }

    // ─── Appearance ──────────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    fun setThemeMode(m: String) { prefs.edit().putString("theme", m).apply(); _themeMode.value = m }

    private val _accentColor = MutableStateFlow(prefs.getString("accent", "red") ?: "red")
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()
    fun setAccentColor(c: String) { prefs.edit().putString("accent", c).apply(); _accentColor.value = c }

    private val _lyricsFontSize = MutableStateFlow(prefs.getInt("lyrics_font", 30))
    val lyricsFontSize: StateFlow<Int> = _lyricsFontSize.asStateFlow()
    fun setLyricsFontSize(s: Int) { prefs.edit().putInt("lyrics_font", s).apply(); _lyricsFontSize.value = s }

    private val _lyricsGlow = MutableStateFlow(prefs.getBoolean("lyrics_glow", true))
    val lyricsGlow: StateFlow<Boolean> = _lyricsGlow.asStateFlow()
    fun setLyricsGlow(on: Boolean) { prefs.edit().putBoolean("lyrics_glow", on).apply(); _lyricsGlow.value = on }

    private val _glassEffect = MutableStateFlow(prefs.getBoolean("glass", true))
    val glassEffect: StateFlow<Boolean> = _glassEffect.asStateFlow()
    fun setGlassEffect(on: Boolean) { prefs.edit().putBoolean("glass", on).apply(); _glassEffect.value = on }

    private val _animatedBackground = MutableStateFlow(prefs.getBoolean("anim_bg", true))
    val animatedBackground: StateFlow<Boolean> = _animatedBackground.asStateFlow()
    fun setAnimatedBackground(on: Boolean) { prefs.edit().putBoolean("anim_bg", on).apply(); _animatedBackground.value = on }

    // ─── Playback ────────────────────────────────────────────────────
    private val _gapless = MutableStateFlow(prefs.getBoolean("gapless", true))
    val gapless: StateFlow<Boolean> = _gapless.asStateFlow()
    fun setGapless(on: Boolean) { prefs.edit().putBoolean("gapless", on).apply(); _gapless.value = on }

    private val _autoPlay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()
    fun setAutoPlay(on: Boolean) { prefs.edit().putBoolean("autoplay", on).apply(); _autoPlay.value = on }

    // ─── Streaming Sources ───────────────────────────────────────────
    private val _sourceYoutube = MutableStateFlow(prefs.getBoolean("src_yt", true))
    val sourceYoutube: StateFlow<Boolean> = _sourceYoutube.asStateFlow()
    fun setSourceYoutube(on: Boolean) { prefs.edit().putBoolean("src_yt", on).apply(); _sourceYoutube.value = on }

    private val _sourceYtmusic = MutableStateFlow(prefs.getBoolean("src_ytm", true))
    val sourceYtmusic: StateFlow<Boolean> = _sourceYtmusic.asStateFlow()
    fun setSourceYtmusic(on: Boolean) { prefs.edit().putBoolean("src_ytm", on).apply(); _sourceYtmusic.value = on }

    private val _sourceSoundcloud = MutableStateFlow(prefs.getBoolean("src_sc", true))
    val sourceSoundcloud: StateFlow<Boolean> = _sourceSoundcloud.asStateFlow()
    fun setSourceSoundcloud(on: Boolean) { prefs.edit().putBoolean("src_sc", on).apply(); _sourceSoundcloud.value = on }

    private val _sourcePiped = MutableStateFlow(prefs.getBoolean("src_piped", false))
    val sourcePiped: StateFlow<Boolean> = _sourcePiped.asStateFlow()
    fun setSourcePiped(on: Boolean) { prefs.edit().putBoolean("src_piped", on).apply(); _sourcePiped.value = on }

    // ─── Data ────────────────────────────────────────────────────────
    private val _cacheEnabled = MutableStateFlow(prefs.getBoolean("cache", true))
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()
    fun setCacheEnabled(on: Boolean) { prefs.edit().putBoolean("cache", on).apply(); _cacheEnabled.value = on }

    // ─── Sleep Timer ─────────────────────────────────────────────────
    private val _sleepTimerMinutes = MutableStateFlow(prefs.getInt("sleep_timer", 0))
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()
    fun setSleepTimerMinutes(m: Int) { prefs.edit().putInt("sleep_timer", m).apply(); _sleepTimerMinutes.value = m }

    companion object {
        val EQ_PRESETS = listOf("flat", "bass_boost", "treble_boost", "vocal", "electronic", "rock", "pop", "jazz", "classical")
        val AUDIO_QUALITIES = listOf("low", "normal", "high", "very_high")
        val ACCENT_COLORS = mapOf(
            "red" to 0xFFFA2D48,
            "blue" to 0xFF0A84FF,
            "purple" to 0xFFBF5AF2,
            "green" to 0xFF30D158,
            "orange" to 0xFFFF9F0A,
            "pink" to 0xFFFF375F,
            "teal" to 0xFF64D2FF,
            "indigo" to 0xFF5E5CE6
        )
        val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}
