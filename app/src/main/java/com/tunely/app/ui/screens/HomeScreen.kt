package com.tunely.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunely.app.data.Shelf
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.ArtworkImage
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.PillButton
import com.tunely.app.ui.design.ProgressRing
import com.tunely.app.ui.design.SectionHeader
import com.tunely.app.ui.design.SkeletonTrackRow
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackRow
import com.tunely.app.ui.design.TrackTile
import com.tunely.app.ui.design.TunelyCard
import com.tunely.app.ui.design.WaveGlyph
import com.tunely.app.ui.design.tapable

/**
 * Home — personalised like the big services: a hero to pick up where you left
 * off, daily mixes seeded from your history, "because you listened" shelves
 * that deliberately serve *different* songs, your artists, and moods to jump
 * straight into a vibe.
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenPlayer: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val colors = T.colors
    val shelves by vm.shelves.collectAsState()
    val loading by vm.shelvesLoading.collectAsState()
    val recent by vm.recent.collectAsState()
    val library by vm.library.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val position by vm.position.collectAsState()

    val recentTracks = remember(recent) { recent.map { it.toTrack() } }
    val favoriteTracks = remember(library) { library.map { it.toTrack() } }
    val hero = playerState.track ?: recentTracks.firstOrNull()
    val greeting = remember { greetingForHour() }

    // Artist shortcuts straight from listening history.
    val topArtists = remember(recent) {
        recent.map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("unknown artist", true) }
            .distinct()
            .take(8)
    }

    val mixes = remember(shelves) { shelves.filter { it.id.startsWith("mix-") } }
    val feed = remember(shelves) { shelves.filterNot { it.id.startsWith("mix-") } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(Modifier.padding(start = Dimens.xl, end = Dimens.xl, top = Dimens.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaveGlyph(Modifier.size(26.dp, 22.dp), playing = playerState.isPlaying)
                    Spacer(Modifier.width(Dimens.sm))
                    Text("TUNELY", style = T.type.micro, color = colors.textTertiary)
                }
                Spacer(Modifier.height(6.dp))
                Text(greeting, style = T.type.display, color = colors.textPrimary)
                Text(
                    if (topArtists.isEmpty()) "What are we playing today?"
                    else "Picked from what you love",
                    style = T.type.body,
                    color = colors.textSecondary
                )
            }
        }

        hero?.let { track ->
            item {
                HeroCard(
                    track = track,
                    playing = playerState.isPlaying && playerState.track?.uid == track.uid,
                    progress = if (playerState.durationMs > 0) {
                        (position.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                    } else 0f,
                    onToggle = {
                        if (playerState.track?.uid == track.uid) {
                            vm.player.togglePlay()
                        } else {
                            vm.play(listOf(track), 0)
                            onOpenPlayer()
                        }
                    },
                    onOpen = {
                        if (playerState.track?.uid == track.uid) onOpenPlayer()
                        else vm.play(listOf(track), 0)
                    },
                    modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.lg)
                )
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val mix = (favoriteTracks + recentTracks).distinctBy { it.uid }
                PillButton(
                    "Shuffle all",
                    onClick = { vm.playShuffled(mix) },
                    icon = Icons.Rounded.Shuffle,
                    enabled = mix.isNotEmpty()
                )
                PillButton(
                    "Favourites",
                    onClick = onOpenLibrary,
                    icon = Icons.Rounded.Favorite,
                    primary = false
                )
                Spacer(Modifier.weight(1f))
                GhostIconButton(
                    icon = Icons.Rounded.Refresh,
                    onClick = { vm.refreshShelves() },
                    contentDescription = "Refresh"
                )
            }
        }

        // Mood chips open the matching category page (charts + picks).
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.xl, vertical = Dimens.md),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                items(com.tunely.app.data.Categories.moods) { mood ->
                    MoodChip(
                        label = mood.label,
                        icon = when (mood.id) {
                            "mood_workout" -> Icons.Rounded.Bolt
                            "mood_focus" -> Icons.Rounded.AutoAwesome
                            "mood_party" -> Icons.Rounded.Celebration
                            else -> Icons.Rounded.Public
                        }
                    ) { onOpenCategory(mood.id) }
                }
            }
        }

        // Your artists — one tap to their page.
        if (topArtists.isNotEmpty()) {
            item { SectionHeader("Your artists", subtitle = "Tap in for top tracks & more") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.xl),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.lg)
                ) {
                    items(topArtists, key = { "home-artist-$it" }) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(84.dp)
                                .tapable { onOpenArtist(artist) }
                        ) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(colors.accentGradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    artist.take(1).uppercase(),
                                    style = T.type.title,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                artist,
                                style = T.type.caption,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Daily mixes — seeded from history, no two mixes share a song.
        if (mixes.isNotEmpty()) {
            item {
                SectionHeader("Made for you", subtitle = "Fresh mixes from your history")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.xl),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    itemsIndexed(mixes, key = { _, s -> "shelf-tile-${s.id}" }) { index, shelf ->
                        MixTile(
                            shelf = shelf,
                            index = index,
                            onClick = { vm.play(shelf.tracks, 0) }
                        )
                    }
                }
            }
        }

        if (loading && shelves.isEmpty()) {
            items(5) { SkeletonTrackRow() }
        }

        feed.forEach { shelf ->
            item(key = "header-${shelf.id}") {
                SectionHeader(
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    action = {
                        GhostIconButton(
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { vm.play(shelf.tracks, 0) },
                            contentDescription = "Play ${shelf.title}",
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                )
            }
            item(key = "row-${shelf.id}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.xl),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    itemsIndexed(shelf.tracks) { index, track ->
                        TrackTile(
                            track = track,
                            index = index,
                            isCurrent = playerState.track?.uid == track.uid,
                            onClick = { vm.play(shelf.tracks, index) },
                            onArtistClick = { onOpenArtist(track.artist) }
                        )
                    }
                }
            }
        }

        if (recentTracks.isNotEmpty()) {
            item { SectionHeader("Jump back in", subtitle = "From your listening history") }
            itemsIndexed(recentTracks.take(6)) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    isCurrent = playerState.track?.uid == track.uid,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(recentTracks, index) },
                    onArtistClick = { onOpenArtist(track.artist) },
                    modifier = Modifier.padding(horizontal = Dimens.md)
                )
            }
        }

        if (shelves.isEmpty() && !loading) {
            item {
                Column(Modifier.padding(horizontal = Dimens.xl)) {
                    Text(
                        "Discovery is quiet right now — check your connection or enable more sources in Settings.",
                        style = T.type.caption,
                        color = colors.textTertiary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.xxl)) }
    }
}

/** Daily-mix card: a gradient slab over the mix's cover art. */
@Composable
private fun MixTile(
    shelf: Shelf,
    index: Int,
    onClick: () -> Unit
) {
    val colors = T.colors
    val covers = shelf.tracks.take(3).mapNotNull { it.artworkUrl }
    val gradients = listOf(
        listOf(Color(0xFFFF6A88), Color(0xFFFF99AC)),
        listOf(Color(0xFF4E54C8), Color(0xFF8F94FB)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200))
    )
    val gradient = gradients[index % gradients.size]

    Column(
        Modifier
            .width(150.dp)
            .tapable { onClick() }
    ) {
        Box {
            if (covers.isNotEmpty()) {
                ArtworkImage(
                    url = covers.first(),
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(Dimens.radiusSm)),
                    shape = RoundedCornerShape(Dimens.radiusSm)
                )
            }
            Box(
                Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(
                        Brush.linearGradient(
                            listOf(gradient[0].copy(alpha = 0.72f), gradient[1].copy(alpha = 0.88f))
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.md)
            ) {
                Text(shelf.title, style = T.type.title, color = Color.White)
                Text(
                    shelf.subtitle,
                    style = T.type.micro,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${shelf.tracks.size} tracks · shuffled for you",
            style = T.type.micro,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun HeroCard(
    track: Track,
    playing: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = T.colors
    TunelyCard(onClick = onOpen, modifier = modifier.fillMaxWidth(), glow = playing) {
        Row(Modifier.padding(Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ArtworkImage(
                    url = track.artworkUrl,
                    modifier = Modifier.size(84.dp),
                    shape = RoundedCornerShape(20.dp)
                )
                ProgressRing(progress = progress, modifier = Modifier.size(94.dp))
            }
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    if (playing) "NOW PLAYING" else "PICK UP WHERE YOU LEFT OFF",
                    style = T.type.micro,
                    color = colors.accent.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    track.title,
                    style = T.type.title,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = T.type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Dimens.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(colors.accentGradient))
                            .tapable { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(Dimens.md))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.outlineStrong)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(colors.accentGradient))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = T.colors
    TunelyCard(onClick = onClick, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = colors.accent.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = T.type.caption, color = colors.textPrimary)
        }
    }
}

private fun greetingForHour(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Late night"
    }
}
