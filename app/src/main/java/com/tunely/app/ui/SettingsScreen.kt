package com.tunely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.data.SettingsManager

/**
 * Apple Music-style settings: grouped translucent rows, section headers,
 * colored icon tiles, and glassmorphism on the page background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings = vm.settings

    // Collect all settings flows
    val audioQuality by settings.audioQuality.collectAsState()
    val crossfade by settings.crossfadeDuration.collectAsState()
    val speed by settings.playbackSpeed.collectAsState()
    val normalize by settings.normalizeVolume.collectAsState()
    val eqEnabled by settings.eqEnabled.collectAsState()
    val eqPreset by settings.eqPreset.collectAsState()
    val theme by settings.themeMode.collectAsState()
    val accent by settings.accentColor.collectAsState()
    val fontSize by settings.lyricsFontSize.collectAsState()
    val glow by settings.lyricsGlow.collectAsState()
    val glass by settings.glassEffect.collectAsState()
    val animBg by settings.animatedBackground.collectAsState()
    val gapless by settings.gapless.collectAsState()
    val autoplay by settings.autoPlay.collectAsState()
    val srcYt by settings.sourceYoutube.collectAsState()
    val srcYtm by settings.sourceYtmusic.collectAsState()
    val srcSc by settings.sourceSoundcloud.collectAsState()
    val srcPiped by settings.sourcePiped.collectAsState()
    val cache by settings.cacheEnabled.collectAsState()

    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAccentSheet by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var showCrossfadeSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Title
            item {
                Text(
                    "Settings",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
                )
            }

            // ──── Audio Section ──────────────────────────────────────
            item { GlassSectionHeader("Audio") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(Icons.Default.Hd, "Audio Quality", AccentBlue) {
                        showQualitySheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "  ${audioQuality.replace("_", " ").replaceFirstChar { it.uppercase() }}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )

                    GlassSettingsRow(Icons.Default.Speed, "Playback Speed", AccentOrange) {
                        showSpeedSheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "  ${speed}x",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )

                    GlassSettingsRow(Icons.Default.CallMerge, "Crossfade", AccentPurple) {
                        showCrossfadeSheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "  ${if (crossfade == 0) "Off" else "${crossfade}s"}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )

                    GlassSettingsRow(
                        Icons.Default.GraphicEq, "Volume Normalization", AccentGreen,
                        trailing = { GlassSwitch(normalize, settings::setNormalizeVolume) }
                    )
                    Spacer(Modifier.height(2.dp))

                    GlassSettingsRow(
                        Icons.Default.Equalizer, "Equalizer", AccentTeal,
                        onClick = { showEqSheet = true },
                        trailing = {
                            GlassSwitch(eqEnabled, settings::setEqEnabled)
                        }
                    )
                    Spacer(Modifier.height(2.dp))
                    if (eqEnabled) {
                        Text(
                            "  Preset: ${eqPreset.replace("_", " ").replaceFirstChar { it.uppercase() }}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                        )
                    }

                    GlassSettingsRow(
                        Icons.Default.SkipNext, "Gapless Playback", AccentIndigo,
                        trailing = { GlassSwitch(gapless, settings::setGapless) }
                    )
                }
            }

            // ──── Appearance Section ─────────────────────────────────
            item { GlassSectionHeader("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(Icons.Default.DarkMode, "Theme", AccentPurple) {
                        showThemeSheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "  ${theme.replaceFirstChar { it.uppercase() }}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )

                    GlassSettingsRow(Icons.Default.Palette, "Accent Color", accentFor(accent)) {
                        showAccentSheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    // Color preview dots
                    Row(Modifier.padding(start = 56.dp, bottom = 6.dp)) {
                        SettingsManager.ACCENT_COLORS.forEach { (name, hex) ->
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(hex.toLong()))
                                    .then(
                                        if (name == accent) Modifier.background(Color.White.copy(alpha = 0.3f))
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }

                    GlassSettingsRow(
                        Icons.Default.BlurOn, "Glassmorphism Effects", AccentTeal,
                        trailing = { GlassSwitch(glass, settings::setGlassEffect) }
                    )
                    Spacer(Modifier.height(2.dp))

                    GlassSettingsRow(
                        Icons.Default.Wallpaper, "Animated Backgrounds", AccentOrange,
                        trailing = { GlassSwitch(animBg, settings::setAnimatedBackground) }
                    )
                }
            }

            // ──── Lyrics Section ─────────────────────────────────────
            item { GlassSectionHeader("Lyrics") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(Icons.Default.TextFields, "Font Size", AccentBlue) {
                        showFontSheet = true
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "  ${fontSize}sp",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )

                    GlassSettingsRow(
                        Icons.Default.AutoAwesome, "Glowing Active Line", AccentPink,
                        trailing = { GlassSwitch(glow, settings::setLyricsGlow) }
                    )
                }
            }

            // ──── Streaming Sources ──────────────────────────────────
            item { GlassSectionHeader("Streaming Sources", "Enable or disable music providers") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(
                        Icons.Default.PlayCircle, "YouTube", AccentRed,
                        trailing = { GlassSwitch(srcYt, settings::setSourceYoutube) }
                    )
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(
                        Icons.Default.Album, "YouTube Music", AccentRed,
                        trailing = { GlassSwitch(srcYtm, settings::setSourceYtmusic) }
                    )
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(
                        Icons.Default.Cloud, "SoundCloud", AccentOrange,
                        trailing = { GlassSwitch(srcSc, settings::setSourceSoundcloud) }
                    )
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(
                        Icons.Default.Shield, "Piped (Privacy)", AccentGreen,
                        trailing = { GlassSwitch(srcPiped, settings::setSourcePiped) }
                    )
                }
            }

            // ──── Playback Section ───────────────────────────────────
            item { GlassSectionHeader("Playback") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(
                        Icons.Default.Autorenew, "Autoplay Similar Songs", AccentPurple,
                        trailing = { GlassSwitch(autoplay, settings::setAutoPlay) }
                    )
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(
                        Icons.Default.Storage, "Stream Caching", AccentTeal,
                        trailing = { GlassSwitch(cache, settings::setCacheEnabled) }
                    )
                }
            }

            // ──── About ──────────────────────────────────────────────
            item { GlassSectionHeader("About") }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    GlassSettingsRow(Icons.Default.Info, "Version", AccentBlue,
                        trailing = { Text("0.2.0", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) })
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(Icons.Default.Code, "Built with", AccentGreen,
                        trailing = { Text("Jetpack Compose", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) })
                    Spacer(Modifier.height(2.dp))
                    GlassSettingsRow(Icons.Default.MusicNote, "Powered by", AccentPink,
                        trailing = { Text("NewPipe + Media3", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) })
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // ──── Bottom Sheets ──────────────────────────────────────────
    if (showQualitySheet) {
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }, containerColor = Color(0xFF1C1C1E)) {
            PickerSheet("Audio Quality", SettingsManager.AUDIO_QUALITIES, audioQuality) {
                settings.setAudioQuality(it); showQualitySheet = false
            }
        }
    }
    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            PickerSheet("Playback Speed", SettingsManager.PLAYBACK_SPEEDS.map { "${it}x" }, "${speed}x") {
                settings.setPlaybackSpeed(it.removeSuffix("x").toFloat()); showSpeedSheet = false
            }
        }
    }
    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            PickerSheet("Theme", listOf("system", "dark", "light"), theme) {
                settings.setThemeMode(it); showThemeSheet = false
            }
        }
    }
    if (showAccentSheet) {
        ModalBottomSheet(onDismissRequest = { showAccentSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            AccentPicker(accent) { settings.setAccentColor(it); showAccentSheet = false }
        }
    }
    if (showEqSheet) {
        ModalBottomSheet(onDismissRequest = { showEqSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            PickerSheet("EQ Preset", SettingsManager.EQ_PRESETS, eqPreset) {
                settings.setEqPreset(it); showEqSheet = false
            }
        }
    }
    if (showFontSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            FontSizeSlider(fontSize) { settings.setLyricsFontSize(it) }
        }
    }
    if (showCrossfadeSheet) {
        ModalBottomSheet(onDismissRequest = { showCrossfadeSheet = false }, containerColor = Color(0xFF1C1C1E)) {
            CrossfadeSlider(crossfade) { settings.setCrossfadeDuration(it) }
        }
    }
}

// ─── Reusable Picker ────────────────────────────────────────────────────

@Composable
fun PickerSheet(title: String, options: List<String>, current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(14.dp))
        options.forEach { opt ->
            val selected = opt == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) AccentRed.copy(alpha = 0.18f) else Color.Transparent)
                    .clickableNoRipple { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    opt.replace("_", " ").replaceFirstChar { it.uppercase() },
                    color = if (selected) AccentRed else Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (selected) Icon(Icons.Default.Check, null, tint = AccentRed)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun AccentPicker(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text("Accent Color", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        // Grid of color circles
        val cols = SettingsManager.ACCENT_COLORS.entries.toList().chunked(4)
        cols.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (name, hex) ->
                    val selected = name == current
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(hex.toLong()))
                            .noRippleClick { onSelect(name) }
                            .padding(if (selected) 3.dp else 0.dp)
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Transparent)
                                    .then(androidx.compose.foundation.border(2.dp, Color.White, RoundedCornerShape(50))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FontSizeSlider(current: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text("Lyrics Font Size", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${current}sp", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 18f..48f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = AccentRed,
                activeTrackColor = AccentRed,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Text("Preview", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("♪ La la la, feel the music ♪", color = Color.White, fontSize = current.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CrossfadeSlider(current: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text("Crossfade", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(if (current == 0) "Off" else "${current}s overlap", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..12f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = AccentPurple,
                activeTrackColor = AccentPurple,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Spacer(Modifier.height(24.dp))
    }
}

// helper — click without the ripple (for colored backgrounds)
@Composable
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = this.then(
    androidx.compose.foundation.clickable(
        onClick = onClick,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null
    )
)
