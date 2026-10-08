package com.tunely.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunely.app.data.PlaybackKind
import com.tunely.app.data.Sources
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.AuroraBackdrop
import com.tunely.app.ui.design.ArtworkImage
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.FilterChip
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.KindChip
import com.tunely.app.ui.design.SheetRow
import com.tunely.app.ui.design.SheetScaffold
import com.tunely.app.ui.design.SheetTitle
import com.tunely.app.ui.design.SourceBadge
import com.tunely.app.ui.design.SpinningDisc
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TunelySlider
import com.tunely.app.ui.design.WaveGlyph
import com.tunely.app.ui.design.glow
import com.tunely.app.ui.design.rememberArtworkColors
import com.tunely.app.ui.design.tapable
import com.tunely.app.player.PlayerUiState

@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    onCollapse: () -> Unit
) {
    val colors = T.colors
    val state by vm.playerState.collectAsState()
    val position by vm.position.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val lyricsLoading by vm.lyricsLoading.collectAsState()
    val settings = vm.settings
    val style by settings.playerStyle.collectAsState()
    val blurArt by settings.blurArtwork.collectAsState()
    val glowLyrics by settings.lyricsGlow.collectAsState()
    val lyricsFont by settings.lyricsFontSize.collectAsState()
    val centreLyrics by settings.lyricsCenter.collectAsState()
    val lyricsGlow by settings.lyricsGlow.collectAsState()
    val sleepState by vm.sleepTimer.collectAsState()
    val track = state.track

    val fallbackColors = remember(colors.accent) {
        listOf(colors.accent.primary, colors.accent.secondary)
    }
    val artworkColors = rememberArtworkColors(track?.artworkUrl, fallbackColors)

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var remainingMode by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(
            seeds = artworkColors,
            animated = true,
            modifier = Modifier.fillMaxSize(),
            intensity = 1.1f
        )

        if (blurArt && track?.artworkUrl != null) {
            Box(Modifier.fillMaxSize()) {
                ArtworkImage(
                    url = track.artworkUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp),
                    shape = RoundedCornerShape(0.dp)
                )
                // Scrim so type stays readable over the blurred cover.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.66f))
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            PlayerTopBar(
                track = track,
                sleepActive = sleepState.active,
                onCollapse = onCollapse,
                onQueue = { showQueue = true },
                onSleep = { showSleep = true },
                onMore = { showActions = true }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (style == com.tunely.app.data.PlayerStyle.DISC) {
                            SpinningDisc(
                                url = track?.artworkUrl,
                                playing = state.isPlaying,
                                size = 288.dp
                            )
                        } else {
                            ArtworkImage(
                                url = track?.artworkUrl,
                                modifier = Modifier
                                    .size(288.dp)
                                    .glow(colors.accent.primary, 1.2f, if (state.isPlaying) 0.5f else 0.2f),
                                shape = RoundedCornerShape(28.dp)
                            )
                        }
                    }
                    else -> LyricsPane(
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        positionMs = position,
                        fontSize = lyricsFont,
                        glowEnabled = lyricsGlow,
                        centre = centreLyrics,
                        onSeek = { vm.player.seekTo(it) },
                        onRetry = { vm.retryLyrics() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == index) 18.dp else 7.dp, 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) colors.accent.primary
                                else colors.outlineStrong
                            )
                    )
                }
            }

            TrackHeadline(track)

            Column(Modifier.padding(horizontal = Dimens.xl)) {
                if (state.isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E0A4))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE STREAM", style = T.type.micro, color = Color(0xFF00E0A4))
                    }
                } else {
                    val duration = state.durationMs.coerceAtLeast(1L)
                    val fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    TunelySlider(
                        value = fraction,
                        onValueChange = { vm.player.seekToFraction(it) }
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatTime(if (remainingMode) position - duration else position),
                            style = T.type.caption,
                            color = colors.textTertiary,
                            modifier = Modifier
                                .weight(1f)
                                .tapable { remainingMode = !remainingMode }
                        )
                        Text(
                            formatTime(duration - position),
                            style = T.type.caption,
                            color = colors.textTertiary
                        )
                    }
                }
            }

            PlayerControls(state, vm)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl, vertical = Dimens.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val favorite = track?.let { vm.isFavorite(it) } ?: false
                GhostIconButton(
                    icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = { track?.let { vm.toggleFavorite(it) } },
                    active = favorite,
                    contentDescription = "Favourite",
                    tint = if (favorite) colors.accent.secondary else null
                )
                GhostIconButton(
                    icon = Icons.Rounded.Speed,
                    onClick = { showSpeed = true },
                    contentDescription = "Playback speed"
                )
                GhostIconButton(
                    icon = Icons.Rounded.QueueMusic,
                    onClick = { showQueue = true },
                    contentDescription = "Queue"
                )
                GhostIconButton(
                    icon = Icons.Rounded.MoreVert,
                    onClick = { showActions = true },
                    contentDescription = "More"
                )
            }
        }
    }

    // ── Sheets ──────────────────────────────────────────────────────
    SheetScaffold(visible = showSleep, onDismiss = { showSleep = false }) {
        SheetTitle("Sleep timer", onClose = { showSleep = false })
        SheetRow(
            icon = Icons.Rounded.Nightlight,
            label = "End of current track",
            onClick = {
                vm.startSleepTimer(1, finishCurrentSong = true)
                showSleep = false
            },
            showCheck = true,
            checked = sleepState.active && sleepState.finishLastSong
        )
        listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
            SheetRow(
                icon = Icons.Rounded.Nightlight,
                label = "$minutes minutes",
                onClick = {
                    vm.startSleepTimer(minutes)
                    showSleep = false
                },
                showCheck = true,
                checked = sleepState.active && !sleepState.finishLastSong
            )
        }
        if (sleepState.active) {
            SheetRow(
                icon = Icons.Rounded.Delete,
                label = "Cancel timer",
                onClick = {
                    vm.cancelSleepTimer()
                    showSleep = false
                },
                tint = Color(0xFFFF4D6D)
            )
        }
        Spacer(Modifier.height(Dimens.lg))
    }

    SheetScaffold(visible = showSpeed, onDismiss = { showSpeed = false }) {
        SheetTitle("Playback speed", onClose = { showSpeed = false })
        val currentSpeed by settings.playbackSpeed.collectAsState()
        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
            SheetRow(
                icon = Icons.Rounded.Speed,
                label = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                onClick = {
                    settings.setPlaybackSpeed(speed)
                    showSpeed = false
                },
                showCheck = true,
                checked = kotlin.math.abs(currentSpeed - speed) < 0.01f
            )
        }
        Spacer(Modifier.height(Dimens.lg))
    }

    SheetScaffold(visible = showQueue, onDismiss = { showQueue = false }) {
        SheetTitle(
            "Up next",
            subtitle = "${state.queue.size} tracks",
            onClose = { showQueue = false }
        )
        LazyColumn {
            itemsIndexed(state.queue) { index, item ->
                val isCurrent = index == state.queueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.md, vertical = 2.dp)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(
                            if (isCurrent) colors.accent.primary.copy(alpha = 0.10f)
                            else Color.Transparent
                        )
                        .tapable { vm.player.skipTo(index) }
                        .padding(horizontal = Dimens.md, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ArtworkImage(
                            url = item.artworkUrl,
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        if (isCurrent) {
                            WaveGlyph(
                                Modifier.size(18.dp, 16.dp),
                                playing = state.isPlaying,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(Dimens.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = T.type.subtitle,
                            color = if (isCurrent) colors.accent.primary else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.artist,
                            style = T.type.caption,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(Dimens.sm))
                    SourceBadge(item.source)
                    GhostIconButton(
                        icon = Icons.Rounded.Delete,
                        onClick = { vm.player.removeFromQueue(index) },
                        size = 30.dp,
                        iconSize = 15.dp
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Dimens.lg),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        label = "Clear queue",
                        selected = false,
                        onClick = {
                            vm.player.clearQueue()
                            showQueue = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.sm))
    }

    TrackActionsSheet(track = if (showActions) track else null, onDismiss = { showActions = false }, vm = vm)
}

@Composable
private fun PlayerTopBar(
    track: Track?,
    sleepActive: Boolean,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
    onMore: () -> Unit
) {
    val colors = T.colors
    val sourceName = track?.let { Sources.byId(it.source)?.name } ?: "Tunely"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            onClick = onCollapse,
            contentDescription = "Collapse",
            size = 40.dp
        )
        Spacer(Modifier.width(Dimens.sm))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM", style = T.type.micro, color = colors.textTertiary)
            Text(
                sourceName,
                style = T.type.caption,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(Dimens.sm))
        GhostIconButton(
            icon = Icons.Rounded.Nightlight,
            onClick = onSleep,
            active = sleepActive,
            contentDescription = "Sleep timer",
            size = 40.dp
        )
        Spacer(Modifier.width(6.dp))
        GhostIconButton(
            icon = Icons.Rounded.QueueMusic,
            onClick = onQueue,
            contentDescription = "Queue",
            size = 40.dp
        )
    }
}

@Composable
private fun TrackHeadline(track: Track?) {
    val colors = T.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.xl, vertical = Dimens.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                track?.title ?: "Nothing playing",
                style = T.type.headline,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (track != null && track.kind != PlaybackKind.FULL) {
                Spacer(Modifier.width(Dimens.sm))
                KindChip(track.kind)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            listOfNotNull(track?.artist, track?.album).joinToString(" · "),
            style = T.type.body,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerControls(state: PlayerUiState, vm: MainViewModel) {
    val colors = T.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.xl, vertical = Dimens.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostIconButton(
            icon = Icons.Rounded.Shuffle,
            onClick = { vm.player.toggleShuffle() },
            active = state.shuffle,
            contentDescription = "Shuffle"
        )
        GhostIconButton(
            icon = Icons.Rounded.SkipPrevious,
            onClick = { vm.player.previous() },
            size = 52.dp,
            iconSize = 30.dp,
            contentDescription = "Previous"
        )

        Box(
            Modifier
                .size(76.dp)
                .glow(
                    colors.accent.primary,
                    1.25f,
                    if (state.isPlaying) 0.55f else 0.2f
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(colors.accentGradient))
                .tapable { vm.player.togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state.isPlaying,
                transitionSpec = {
                    (scaleIn(tween(220)) + fadeIn(tween(160))) togetherWith
                        (scaleOut(tween(160)) + fadeOut(tween(120)))
                },
                label = "playPause"
            ) { playing ->
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        GhostIconButton(
            icon = Icons.Rounded.SkipNext,
            onClick = { vm.player.next() },
            size = 52.dp,
            iconSize = 30.dp,
            contentDescription = "Next"
        )
        GhostIconButton(
            icon = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            },
            onClick = { vm.player.cycleRepeat() },
            active = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
            contentDescription = "Repeat"
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    val sign = if (ms < 0) "-" else ""
    return "%s%d:%02d".format(sign, minutes, seconds)
}

/**
 * The floating mini player: artwork, a live waveform, a progress hairline and
 * the transport. Drag it up (or tap it) to expand the full player.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    positionMs: Long,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.track ?: return
    val colors = T.colors
    var drag by remember { mutableFloatStateOf(0f) }

    val canAdvance = state.queueIndex > 0
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(colors.surfaceGlass)
            .border(1.dp, colors.outline, RoundedCornerShape(Dimens.radiusMd))
            .tapable { onExpand() }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, amount ->
                    change.consume()
                    drag += amount
                    if (drag < -70f) {
                        drag = 0f
                        onExpand()
                    }
                }
            }
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.sm, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    url = track.artworkUrl,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = T.type.subtitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            track.artist,
                            style = T.type.caption,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        SourceBadge(track.source)
                    }
                }
                WaveGlyph(
                    Modifier.size(22.dp, 20.dp),
                    playing = state.isPlaying,
                    color = colors.accent.primary
                )
                Spacer(Modifier.width(Dimens.sm))
                GhostIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    onClick = onPrevious,
                    enabled = canAdvance,
                    size = 34.dp,
                    iconSize = 18.dp,
                    contentDescription = "Previous"
                )
                Spacer(Modifier.width(4.dp))
                GhostIconButton(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onTogglePlay,
                    size = 40.dp,
                    iconSize = 20.dp,
                    contentDescription = "Play/pause",
                    active = true
                )
                Spacer(Modifier.width(4.dp))
                GhostIconButton(
                    icon = Icons.Rounded.SkipNext,
                    onClick = onNext,
                    size = 34.dp,
                    iconSize = 18.dp,
                    contentDescription = "Next"
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Drag handle",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .tapable { onExpand() }
                )
            }
            // Progress hairline that doubles as a "buffering" indicator.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.outlineStrong)
            ) {
                val fraction = if (state.durationMs > 0) {
                    (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else if (state.isLive) {
                    1f
                } else 0f
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(colors.accentGradient))
                )
            }
        }
    }
}
