package com.tunely.app.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tunely.app.data.SettingsManager
import com.tunely.app.data.Track
import com.tunely.app.player.PlayerUiState
import kotlin.math.roundToInt

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}

/** Extracts a dominant color from the artwork to tint the whole player. */
@Composable
fun rememberArtworkColor(url: String?): Color {
    val ctx = LocalContext.current
    var color by remember(url) { mutableStateOf(Color(0xFF3A3A3C)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(200).build()
        val res = ImageLoader(ctx).execute(req)
        if (res is SuccessResult) {
            val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: res.drawable.toBitmap()
            val p = Palette.from(bmp).generate()
            val rgb = p.getDarkVibrantColor(p.getDominantColor(0xFF3A3A3C.toInt()))
            color = Color(rgb)
        }
    }
    return animateColorAsState(color, tween(900), label = "artColor").value
}

/** Glass mini-player shown at the bottom above the navigation bar. */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    glassEnabled: Boolean = true
) {
    val t = state.track ?: return
    val tint = rememberArtworkColor(t.artworkUrl)
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (glassEnabled)
                        Modifier.background(
                            brush = Brush.linearGradient(
                                listOf(
                                    tint.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        ).border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    else
                        Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                )
                .clickable(onClick = onTap)
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Artwork with subtle glow
                Box {
                    if (glassEnabled) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .blur(18.dp)
                                .background(tint.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                    AsyncImage(
                        t.artworkUrl, null,
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(
                        t.artist, color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                // Play / pause — glowing
                IconButton(onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, tint = Color.White
                    )
                }
                IconButton(onNext) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        // Thin progress bar under the glass panel
        Box(
            Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(tint.copy(alpha = 0.9f), AccentRed)
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    vm: MainViewModel,
    onClose: () -> Unit
) {
    val t = state.track ?: return
    val tint = rememberArtworkColor(t.artworkUrl)
    val lyrics by vm.lyrics.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    val inLibrary by vm.isInLibrary(t.id).collectAsState(false)
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    val sleepTimer by vm.sleepTimer.collectAsState()
    val glowEnabled by vm.settings.lyricsGlow.collectAsState()
    val glassEnabled by vm.settings.glassEffect.collectAsState()
    val animBg by vm.settings.animatedBackground.collectAsState()
    val fontSize by vm.settings.lyricsFontSize.collectAsState()
    val accent by vm.settings.accentColor.collectAsState()
    val speed by vm.settings.playbackSpeed.collectAsState()
    val accentColor = accentFor(accent)

    // Slowly drifting gradient, like Apple Music's animated backdrop.
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "d"
    )
    val artScale by animateFloatAsState(
        if (state.isPlaying) 1f else 0.82f,
        spring(dampingRatio = 0.6f), label = "art"
    )
    val artGlow = pulsingGlowAlpha(min = 0.4f, max = 0.85f)

    Box(
        Modifier.fillMaxSize().background(
            if (animBg) Brush.linearGradient(
                listOf(tint, Color.Black, tint.copy(alpha = 0.6f)),
                start = Offset(0f, 2000f * drift),
                end = Offset(1500f * (1 - drift), 0f)
            ) else Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.7f), Color.Black)
            )
        )
    ) {
        // A blurred halo behind the artwork for extra depth
        if (glassEnabled && !showLyrics) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .blur(120.dp)
                    .alpha(artGlow)
                    .background(tint, CircleShape)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.85f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (showLyrics) "LYRICS" else "NOW PLAYING",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        t.source.replace("_", " ").uppercase(),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
                IconButton({ showQueue = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.85f))
                }
            }

            if (showLyrics) {
                // Compact artwork + metadata while lyrics are shown
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        t.artworkUrl, null,
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(t.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f)) {
                    LyricsView(
                        lyrics = lyrics,
                        positionMs = state.positionMs,
                        onSeek = vm.player::seekTo,
                        fontSize = fontSize,
                        glowEnabled = glowEnabled,
                        accentColor = accentColor
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))

                // ─── Artwork with glow ───────────────────────────────
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow halo
                    if (glassEnabled && state.isPlaying) {
                        Box(
                            Modifier
                                .fillMaxSize(0.95f)
                                .blur(60.dp)
                                .alpha(artGlow * 0.6f)
                                .background(tint, RoundedCornerShape(24.dp))
                        )
                    }
                    AsyncImage(
                        t.artworkUrl, null,
                        Modifier
                            .fillMaxSize()
                            .scale(artScale)
                            .clip(RoundedCornerShape(18.dp))
                            .shadow(30.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black),
                        contentScale = ContentScale.Crop
                    )
                    // Explicit badge
                    if (t.explicit) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("E", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ─── Track info ──────────────────────────────────────
                Text(
                    t.title, color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    t.artist, color = Color.White.copy(alpha = 0.7f),
                    fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                listOfNotNull(t.album, t.genre, t.releaseYear)
                    .joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, maxLines = 1)
                    }
            }

            Spacer(Modifier.height(14.dp))

            // ─── Progress bar (glass-styled) ────────────────────────
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            Slider(
                value = progress,
                onValueChange = { vm.player.seekTo((it * state.durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(state.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("-" + fmt(state.durationMs - state.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            // ─── Main playback controls ─────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(vm.player::previous) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                // Glowing play/pause button
                Box(contentAlignment = Alignment.Center) {
                    if (state.isPlaying && glassEnabled) {
                        Box(
                            Modifier
                                .size(84.dp)
                                .blur(28.dp)
                                .alpha(0.6f)
                                .background(tint.copy(alpha = 0.9f), CircleShape)
                        )
                    }
                    IconButton(vm.player::togglePlay, Modifier.size(76.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                }
                IconButton(vm.player::next) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }

            // ─── Secondary controls (glass pill bar) ────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
                    .then(
                        if (glassEnabled) Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        else Modifier
                    )
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lyrics toggle
                    ControlIcon(
                        Icons.Default.Lyrics,
                        active = showLyrics,
                        accentColor = accentColor,
                        onClick = { showLyrics = !showLyrics }
                    )
                    // Shuffle
                    ControlIcon(
                        Icons.Default.Shuffle,
                        active = state.shuffle,
                        accentColor = accentColor,
                        onClick = vm.player::toggleShuffle
                    )
                    // Repeat
                    ControlIcon(
                        when (state.repeatMode) {
                            2 -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        active = state.repeatMode != 0,
                        accentColor = accentColor,
                        onClick = vm.player::cycleRepeat
                    )
                    // Library
                    ControlIcon(
                        if (inLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        active = inLibrary,
                        accentColor = accentColor,
                        onClick = { vm.toggleLibrary(t, inLibrary) }
                    )
                    // Sleep timer
                    ControlIcon(
                        Icons.Default.Bedtime,
                        active = sleepTimer.active,
                        accentColor = accentColor,
                        onClick = { showSleepTimer = true },
                        badge = if (sleepTimer.active) {
                            val remaining = ((sleepTimer.endTimeMs - System.currentTimeMillis()) / 60_000)
                                .coerceAtLeast(0).toInt()
                            "${remaining}m"
                        } else null
                    )
                    // Speed
                    ControlIcon(
                        Icons.Default.Speed,
                        active = speed != 1.0f,
                        accentColor = accentColor,
                        onClick = { showSpeedSheet = true },
                        badge = if (speed != 1.0f) "${speed}x" else null
                    )
                }
            }
        }
    }

    // ─── Queue sheet ─────────────────────────────────────────────
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Color(0xFF141416)) {
            QueueSheet(state, vm) { showQueue = false }
        }
    }
    if (showSleepTimer) {
        ModalBottomSheet(onDismissRequest = { showSleepTimer = false }, containerColor = Color(0xFF141416)) {
            SleepTimerSheet(vm) { showSleepTimer = false }
        }
    }
    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, containerColor = Color(0xFF141416)) {
            SpeedSheet(vm) { showSpeedSheet = false }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    badge: String? = null
) {
    val tint by animateColorAsState(
        if (active) accentColor else Color.White.copy(alpha = 0.55f),
        tween(280), label = "ctrl"
    )
    Box {
        IconButton(onClick) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QueueSheet(state: PlayerUiState, vm: MainViewModel, onDismiss: () -> Unit) {
    Column(Modifier.padding(16.dp).fillMaxWidth()) {
        Text("Up Next", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Text("${state.queue.size} tracks", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        state.queue.forEachIndexed { i, q ->
            val current = i == state.queueIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (current) Modifier.background(AccentRed.copy(alpha = 0.15f))
                        else Modifier
                    )
                    .clickable { vm.player.skipTo(i); onDismiss() }
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (current) {
                    Icon(
                        Icons.Default.GraphicEq, null,
                        tint = AccentRed, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AsyncImage(
                    q.artworkUrl, null,
                    Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        q.title,
                        color = if (current) AccentRed else Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(q.artist, fontSize = 12.sp, maxLines = 1, color = Color.White.copy(alpha = 0.6f))
                }
                if (q.durationMs > 0) {
                    Text(
                        fmt(q.durationMs),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SleepTimerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val sleepTimer by vm.sleepTimer.collectAsState()
    val presets = listOf(5, 10, 15, 30, 45, 60, 90)
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text("Sleep Timer", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Text("Stop playback after", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (sleepTimer.active) {
            val remaining = ((sleepTimer.endTimeMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
            Box(
                Modifier
                    .fillMaxWidth()
                    .glassPanel(tint = AccentRed.copy(alpha = 0.15f), borderColor = AccentRed.copy(alpha = 0.35f))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, null, tint = AccentRed)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Active", color = AccentRed, fontWeight = FontWeight.Bold)
                        Text("Stops in ~$remaining min", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                    TextButton({ vm.cancelSleepTimer(); onDismiss() }) {
                        Text("Cancel", color = AccentRed)
                    }
                }
            }
        } else {
            presets.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { min ->
                        Box(
                            Modifier
                                .weight(1f)
                                .glassPanel()
                                .clickable { vm.startSleepTimer(min); onDismiss() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${min}m", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // Pad remaining
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpeedSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val speed by vm.settings.playbackSpeed.collectAsState()
    Column(Modifier.padding(20.dp).fillMaxWidth()) {
        Text("Playback Speed", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Spacer(Modifier.height(14.dp))
        SettingsManager.PLAYBACK_SPEEDS.forEach { s ->
            val selected = s == speed
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) AccentRed.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { vm.settings.setPlaybackSpeed(s); vm.player.setPlaybackSpeed(s); onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${s}x",
                    color = if (selected) AccentRed else Color.White,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (selected) Icon(Icons.Default.Check, null, tint = AccentRed)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
