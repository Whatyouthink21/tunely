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
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunely.app.data.BrowseCategory
import com.tunely.app.data.ChartList
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.ArtworkImage
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.KindChip
import com.tunely.app.ui.design.SectionHeader
import com.tunely.app.ui.design.SkeletonTrackRow
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackTile
import com.tunely.app.ui.design.WaveGlyph
import com.tunely.app.ui.design.tapable

/**
 * Explore — the Spotify/Apple-Music-style browse page: moods up top, live
 * charts in the middle, genre doors below. Everything is keyed off the same
 * keyless feeds as Home, so it works with every provider toggled off except
 * one.
 */
@Composable
fun ExploreScreen(
    vm: MainViewModel,
    onOpenCategory: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val colors = T.colors
    val charts by vm.exploreCharts.collectAsState()
    val trending by vm.exploreTrending.collectAsState()
    val loading by vm.exploreLoading.collectAsState()
    val playerState by vm.playerState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(Modifier.padding(start = Dimens.xl, end = Dimens.xl, top = Dimens.lg)) {
                Text("Explore", style = T.type.display, color = colors.textPrimary)
                Text(
                    "Moods, charts and new corners of music",
                    style = T.type.body,
                    color = colors.textSecondary
                )
            }
        }

        item {
            SectionHeader("Pick a mood", subtitle = "Different vibes, different songs")
        }
        items(com.tunely.app.data.Categories.moods.chunked(2)) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                pair.forEach { cat ->
                    CategoryCard(
                        category = cat,
                        onClick = { onOpenCategory(cat.id) },
                        modifier = Modifier.weight(1f),
                        height = 96.dp
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (loading && charts.isEmpty()) {
            items(4) { SkeletonTrackRow() }
        }

        charts.forEach { chart ->
            item(key = "chart-head-${chart.id}") {
                SectionHeader(
                    title = chart.title,
                    subtitle = chart.subtitle,
                    action = {
                        GhostIconButton(
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { vm.play(chart.tracks, 0) },
                            contentDescription = "Play ${chart.title}",
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                )
            }
            itemsIndexed(
                chart.tracks.take(6),
                key = { _, t -> "chart-${chart.id}-${t.uid}" }
            ) { index, track ->
                ChartRow(
                    rank = index + 1,
                    track = track,
                    isCurrent = playerState.track?.uid == track.uid,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(chart.tracks, index) },
                    onArtistClick = { onOpenArtist(track.artist) },
                    modifier = Modifier.padding(horizontal = Dimens.md)
                )
            }
        }

        item {
            SectionHeader("Browse by genre", subtitle = "Every door leads somewhere new")
        }
        items(com.tunely.app.data.Categories.genres.chunked(2)) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                pair.forEach { cat ->
                    CategoryCard(
                        category = cat,
                        onClick = { onOpenCategory(cat.id) },
                        modifier = Modifier.weight(1f),
                        height = 88.dp
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (trending.isNotEmpty()) {
            item {
                SectionHeader(
                    "Trending on Audius",
                    subtitle = "Full-length, artist-owned uploads"
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.xl),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    itemsIndexed(trending) { index, track ->
                        TrackTile(
                            track = track,
                            index = index,
                            isCurrent = playerState.track?.uid == track.uid,
                            onClick = { vm.play(trending, index) },
                            onArtistClick = { onOpenArtist(track.artist) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.xxl)) }
    }
}

/** Mood / genre door card with its own gradient identity. */
@Composable
fun CategoryCard(
    category: BrowseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp
) {
    val colors = T.colors
    val gradient = remember(category.id) {
        category.colors.map { Color(it) }
    }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(Brush.linearGradient(gradient))
            .tapable { onClick() }
            .padding(Dimens.md)
    ) {
        Icon(
            moodIcon(category),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
        )
        Column(
            Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                category.label,
                style = T.type.subtitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                category.tagline,
                style = T.type.micro,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun moodIcon(category: BrowseCategory): ImageVector = when {
    category.mood?.id == "mood_chill" -> Icons.Rounded.NightsStay
    category.mood?.id == "mood_energy" -> Icons.Rounded.Bolt
    category.mood?.id == "mood_focus" -> Icons.Rounded.AutoAwesome
    category.mood?.id == "mood_party" -> Icons.Rounded.Celebration
    category.mood?.id == "mood_workout" -> Icons.Rounded.FitnessCenter
    category.mood?.id == "mood_romance" -> Icons.Rounded.Favorite
    category.mood?.id == "mood_drive" -> Icons.Rounded.DirectionsCar
    category.mood?.id == "mood_throwback" -> Icons.Rounded.History
    else -> Icons.Rounded.Public
}

/** Numbered chart row. */
@Composable
private fun ChartRow(
    rank: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = T.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .tapable { onClick() }
            .background(
                if (isCurrent) colors.accent.primary.copy(alpha = 0.10f) else Color.Transparent
            )
            .padding(horizontal = Dimens.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank.toString(),
            style = T.type.title,
            color = if (rank <= 3) colors.accent.primary else colors.textTertiary,
            modifier = Modifier.width(30.dp)
        )
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(
                url = track.artworkUrl,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(11.dp)
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.background.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    WaveGlyph(
                        modifier = Modifier.size(20.dp, 18.dp),
                        playing = isPlaying,
                        color = colors.accent.primary
                    )
                }
            }
        }
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = T.type.subtitle,
                color = if (isCurrent) colors.accent.primary else colors.textPrimary,
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
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .tapable { onArtistClick() }
                )
                if (track.kind != com.tunely.app.data.PlaybackKind.FULL) {
                    Spacer(Modifier.width(6.dp))
                    KindChip(track.kind)
                }
            }
        }
    }
}
