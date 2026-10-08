package com.tunely.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import com.tunely.app.data.AudioQuality
import com.tunely.app.data.PlayerStyle
import com.tunely.app.data.SettingsManager
import com.tunely.app.data.SourceInfo
import com.tunely.app.data.Sources
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.Accents
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.FilterChip
import com.tunely.app.ui.design.SectionHeader
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TunelyCard
import com.tunely.app.ui.design.TunelySlider
import com.tunely.app.ui.design.TunelySwitch
import com.tunely.app.ui.design.tapable
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val settings = vm.settings
    val colors = T.colors

    val themeMode by settings.themeMode.collectAsState()
    val accent by settings.accent.collectAsState()
    val aurora by settings.aurora.collectAsState()
    val motion by settings.motionEnabled.collectAsState()
    val blurArt by settings.blurArtwork.collectAsState()
    val playerStyle by settings.playerStyle.collectAsState()
    val quality by settings.audioQuality.collectAsState()
    val speed by settings.playbackSpeed.collectAsState()
    val crossfade by settings.crossfadeSeconds.collectAsState()
    val normalize by settings.normalizeVolume.collectAsState()
    val eqEnabled by settings.eqEnabled.collectAsState()
    val eqPreset by settings.eqPreset.collectAsState()
    val gapless by settings.gapless.collectAsState()
    val autoplay by settings.autoPlay.collectAsState()
    val lyricsFont by settings.lyricsFontSize.collectAsState()
    val lyricsGlow by settings.lyricsGlow.collectAsState()
    val lyricsCenter by settings.lyricsCenter.collectAsState()
    val cache by settings.cacheEnabled.collectAsState()
    val sleepState by vm.sleepTimer.collectAsState()

    val remaining by produceState(initialValue = 0L, sleepState) {
        while (true) {
            value = vm.sleepTimerRemainingMs()
            delay(1_000)
            if (!sleepState.active) break
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = Dimens.xl, top = Dimens.lg)) {
                Text("Settings", style = T.type.display, color = colors.textPrimary)
                Text(
                    "Make it sound and look the way you want",
                    style = T.type.caption,
                    color = colors.textTertiary
                )
            }
        }

        // ── Appearance ──────────────────────────────────────────────
        item { SectionHeader("Appearance") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.DarkMode,
                    label = "Theme",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("system" to "Auto", "dark" to "Dark", "light" to "Light")
                                .forEach { (value, label) ->
                                    FilterChip(
                                        label = label,
                                        selected = themeMode == value,
                                        onClick = { settings.setThemeMode(value) }
                                    )
                                }
                        }
                    }
                )

                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Palette, colors.accent.primary)
                        Spacer(Modifier.width(Dimens.md))
                        Text("Accent", style = T.type.subtitle, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(Dimens.md))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Accents.all.forEach { entry ->
                            AccentSwatch(
                                selected = entry.id == accent,
                                gradient = entry.gradient,
                                onClick = { settings.setAccent(entry.id) }
                            )
                        }
                    }
                }

                SettingsToggle(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Aurora backdrop",
                    subtitle = "Animated colour field taken from the artwork",
                    checked = aurora,
                    onChange = settings::setAurora
                )
                SettingsToggle(
                    icon = Icons.Rounded.Bolt,
                    label = "Motion & transitions",
                    subtitle = "Turn off to keep the UI still",
                    checked = motion,
                    onChange = settings::setMotionEnabled
                )
                SettingsToggle(
                    icon = Icons.Rounded.BlurOn,
                    label = "Blur artwork behind the player",
                    subtitle = "Softer, more expensive look",
                    checked = blurArt,
                    onChange = settings::setBlurArtwork
                )
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Now playing style",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                label = "Vinyl",
                                selected = playerStyle == PlayerStyle.DISC,
                                onClick = { settings.setPlayerStyle(PlayerStyle.DISC) }
                            )
                            FilterChip(
                                label = "Cover",
                                selected = playerStyle == PlayerStyle.COVER,
                                onClick = { settings.setPlayerStyle(PlayerStyle.COVER) }
                            )
                        }
                    }
                )
            }
        }

        // ── Audio ───────────────────────────────────────────────────
        item { SectionHeader("Audio") }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.GraphicEq, Color(0xFF30D158))
                        Spacer(Modifier.width(Dimens.md))
                        Column {
                            Text("Stream quality", style = T.type.subtitle, color = colors.textPrimary)
                            Text(
                                "Picks the closest stream bitrate per track",
                                style = T.type.caption,
                                color = colors.textTertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    FlowChips(
                        values = AudioQuality.ALL,
                        selected = quality,
                        labelFor = { AudioQuality.label(it) },
                        onClick = { settings.setAudioQuality(it) }
                    )
                }

                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Speed, Color(0xFF22D3EE))
                        Spacer(Modifier.width(Dimens.md))
                        Text("Playback speed", style = T.type.subtitle, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    FlowChips(
                        values = SettingsManager.PLAYBACK_SPEEDS,
                        selected = speed,
                        labelFor = { "${it}x" },
                        onClick = { settings.setPlaybackSpeed(it) }
                    )
                }

                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Tune, Color(0xFFFF9F0A))
                        Spacer(Modifier.width(Dimens.md))
                        Text(
                            "Fade & blend",
                            style = T.type.subtitle,
                            color = colors.textPrimary
                        )
                    }
                    Text(
                        if (crossfade == 0) "Off — tracks hand over instantly" else "Fades over ${crossfade}s",
                        style = T.type.caption,
                        color = colors.textTertiary
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    FlowChips(
                        values = SettingsManager.CROSSFADE_OPTIONS,
                        selected = crossfade,
                        labelFor = { if (it == 0) "Off" else "${it}s" },
                        onClick = { settings.setCrossfadeSeconds(it) }
                    )
                }

                SettingsToggle(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Normalise volume",
                    subtitle = "Lift quiet tracks with a loudness enhancer",
                    checked = normalize,
                    onChange = settings::setNormalizeVolume
                )
                SettingsToggle(
                    icon = Icons.Rounded.Equalizer,
                    label = "Equalizer",
                    subtitle = "Real 5-band EQ applied to the audio session",
                    checked = eqEnabled,
                    onChange = settings::setEqEnabled
                )
                AnimatedVisibility(
                    visible = eqEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                        Text(
                            "Preset",
                            style = T.type.caption,
                            color = colors.textTertiary
                        )
                        Spacer(Modifier.height(Dimens.sm))
                        FlowChips(
                            values = SettingsManager.EQ_PRESETS,
                            selected = eqPreset,
                            labelFor = { SettingsManager.label(it) },
                            onClick = { settings.setEqPreset(it) }
                        )
                    }
                }
                SettingsToggle(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Gapless playback",
                    subtitle = "Preload the next track and skip the silence",
                    checked = gapless,
                    onChange = settings::setGapless
                )
                SettingsToggle(
                    icon = Icons.Rounded.Bolt,
                    label = "Autoplay",
                    subtitle = "Resolve the next stream ahead of time",
                    checked = autoplay,
                    onChange = settings::setAutoPlay
                )
            }
        }

        // ── Sources ─────────────────────────────────────────────────
        item {
            SectionHeader(
                "Streaming sources",
                subtitle = "Search fans out to everything you switch on"
            )
        }
        item {
            SettingsCard {
                Sources.all.forEach { info ->
                    SourceToggleRow(info = info, settings = settings)
                }
            }
        }


        // ── Lyrics ──────────────────────────────────────────────────
        item { SectionHeader("Lyrics") }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.TextFields, Color(0xFFBF5AF2))
                        Spacer(Modifier.width(Dimens.md))
                        Text("Text size", style = T.type.subtitle, color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${lyricsFont}sp",
                            style = T.type.caption,
                            color = colors.textTertiary
                        )
                    }
                    Spacer(Modifier.height(Dimens.xs))
                    TunelySlider(
                        value = (lyricsFont - 18) / 34f,
                        onValueChange = { settings.setLyricsFontSize((18 + it * 34).toInt()) }
                    )
                }
                SettingsToggle(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Glow the active line",
                    subtitle = "Neon halo and word-by-word sweep",
                    checked = lyricsGlow,
                    onChange = settings::setLyricsGlow
                )
                SettingsToggle(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Centre the active line",
                    subtitle = "Auto-scroll keeps it in the middle",
                    checked = lyricsCenter,
                    onChange = settings::setLyricsCenter
                )
            }
        }

        // ── Sleep timer ─────────────────────────────────────────────
        item { SectionHeader("Sleep timer", subtitle = if (sleepState.active) "Active" else null) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Nightlight, Color(0xFF5E5CE6))
                        Spacer(Modifier.width(Dimens.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (sleepState.active) {
                                    if (sleepState.finishLastSong) "Stops after this track"
                                    else "Stopping in ${formatDuration(remaining)}"
                                } else "Off",
                                style = T.type.subtitle,
                                color = colors.textPrimary
                            )
                            Text(
                                "Pause playback automatically",
                                style = T.type.caption,
                                color = colors.textTertiary
                            )
                        }
                        if (sleepState.active) {
                            FilterChip(
                                label = "Cancel",
                                selected = false,
                                onClick = { vm.cancelSleepTimer() },
                                dotColor = Color(0xFFFF4D6D)
                            )
                        }
                    }
                    Spacer(Modifier.height(Dimens.md))
                    FlowChips(
                        values = listOf(5, 10, 15, 30, 45, 60, 90),
                        selected = 0,
                        labelFor = { "${it}m" },
                        onClick = { vm.startSleepTimer(it) }
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    FilterChip(
                        label = "End of current track",
                        selected = sleepState.active && sleepState.finishLastSong,
                        onClick = { vm.startSleepTimer(1, finishCurrentSong = true) },
                        dotColor = Color(0xFF5E5CE6)
                    )
                }
            }
        }

        // ── Data & about ────────────────────────────────────────────
        item { SectionHeader("Data") }
        item {
            SettingsCard {
                SettingsToggle(
                    icon = Icons.Rounded.Timer,
                    label = "Stream link caching",
                    subtitle = "Keeps resolved URLs for 15 minutes",
                    checked = cache,
                    onChange = settings::setCacheEnabled
                )
                SettingsRow(
                    icon = Icons.Rounded.Delete,
                    label = "Clear image cache",
                    subtitle = "Frees artwork that has been downloaded",
                    onClick = { vm.clearImageCache() }
                )
            }
        }

        item { SectionHeader("About") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    label = "Tunely",
                    subtitle = "Version 1.0 — a from-scratch player built with Compose & Media3",
                    value = null
                )
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)) {
                    Text(
                        "Streaming providers are unofficial and may break without warning. " +
                            "Typeface: Outfit, licensed under the SIL Open Font License. " +
                            "Charts, artwork and previews come from Deezer, Apple's iTunes API, " +
                            "Audius and radio-browser.info.",
                        style = T.type.caption,
                        color = colors.textTertiary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.xxl)) }
    }
}

@Composable
private fun SourceToggleRow(info: SourceInfo, settings: SettingsManager) {
    val colors = T.colors
    val flow = settings.sourceFor(info.id) ?: remember { MutableStateFlow(false) }
    val enabled by flow.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = 3.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(info.color).copy(alpha = 0.16f))
                .border(1.dp, Color(info.color).copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                info.short,
                style = T.type.micro,
                color = Color(info.color),
                fontWeight = FontWeight.W700
            )
        }
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(info.name, style = T.type.subtitle, color = colors.textPrimary)
            Text(
                info.notice ?: info.tagline,
                style = T.type.caption,
                color = colors.textTertiary
            )
        }
        TunelySwitch(
            checked = enabled,
            onCheckedChange = { settings.setSource(info.id, it) }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    TunelyCard(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        enabled = false
    ) {
        Column { content() }
    }
}

@Composable
private fun IconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = T.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tapable { onClick() } else Modifier)
            .padding(horizontal = Dimens.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, colors.accent.primary)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = T.type.subtitle, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = T.type.caption, color = colors.textTertiary)
            }
        }
        if (value != null) {
            Text(value, style = T.type.caption, color = colors.textTertiary)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    SettingsRow(
        icon = icon,
        label = label,
        subtitle = subtitle,
        trailing = { TunelySwitch(checked = checked, onCheckedChange = onChange) }
    )
}

@Composable
private fun AccentSwatch(
    selected: Boolean,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(if (selected) 38.dp else 32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .then(
                if (selected) {
                    Modifier.border(2.dp, T.colors.textPrimary.copy(alpha = 0.85f), CircleShape)
                } else Modifier
            )
            .tapable { onClick() }
    )
}

@Composable
private fun <T> FlowChips(
    values: List<T>,
    selected: T?,
    labelFor: (T) -> String,
    onClick: (T) -> Unit
) {
    // Simple wrapping rows: at most three chips per line so nothing overflows.
    val chunks = values.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chunks.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    FilterChip(
                        label = labelFor(value),
                        selected = value == selected,
                        onClick = { onClick(value) }
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
