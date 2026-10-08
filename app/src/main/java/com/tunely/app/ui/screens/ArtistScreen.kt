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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunely.app.data.ArtistAlbum
import com.tunely.app.data.ArtistRef
import com.tunely.app.data.ArtistProfile
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.ArtworkImage
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.FilterChip
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.PillButton
import com.tunely.app.ui.design.SectionHeader
import com.tunely.app.ui.design.SkeletonTrackRow
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackRow
import com.tunely.app.ui.design.tapable

/**
 * Artist pages: hero with picture + stats, popular tracks from every provider,
 * discography, related artists and the artist's own bio. Tapping a related
 * artist swaps the page in place, so browsing feels like one continuous dive.
 */
@Composable
fun ArtistScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val colors = T.colors
    val profile by vm.artistPage.collectAsState()
    val loading by vm.artistLoading.collectAsState()
    val playerState by vm.playerState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + Dimens.md,
            bottom = 180.dp
        )
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Back"
                )
                Spacer(Modifier.width(Dimens.sm))
                Text("Artist", style = T.type.caption, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                GhostIconButton(
                    icon = Icons.Rounded.Refresh,
                    onClick = { vm.refreshArtist() },
                    contentDescription = "Refresh"
                )
            }
        }

        if (loading && profile == null) {
            item { ArtistHeroSkeleton() }
            items(5) { SkeletonTrackRow() }
        }

        profile?.let { p ->
            item {
                Hero(
                    profile = p,
                    onPlayAll = { vm.play(p.topTracks, 0) },
                    onShuffle = { vm.playShuffled(p.topTracks) }
                )
            }

            if (p.topTracks.isNotEmpty()) {
                item {
                    SectionHeader("Popular", subtitle = "Top tracks, all sources")
                }
                itemsIndexed(
                    p.topTracks,
                    key = { _, t -> "artist-track-${t.uid}" }
                ) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isCurrent = playerState.track?.uid == track.uid,
                        isPlaying = playerState.isPlaying,
                        onClick = { vm.play(p.topTracks, index) },
                        modifier = Modifier.padding(horizontal = Dimens.md)
                    )
                }
            }

            if (p.albums.isNotEmpty()) {
                item {
                    SectionHeader("Discography", subtitle = "Albums & singles")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.xl),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        items(p.albums, key = { "album-${it.id}" }) { album ->
                            AlbumTile(album)
                        }
                    }
                }
            }

            if (p.related.isNotEmpty()) {
                item {
                    SectionHeader("Fans also like", subtitle = "Keep exploring")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.xl),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.lg)
                    ) {
                        items(p.related, key = { "rel-${it.name}" }) { ref ->
                            RelatedArtistChip(ref) { onOpenArtist(ref.name) }
                        }
                    }
                }
            }

            if (!p.bio.isNullOrBlank()) {
                item {
                    SectionHeader("About")
                }
                item {
                    Text(
                        p.bio,
                        style = T.type.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Dimens.xl)
                    )
                }
            }

            if (p.isEmpty) {
                item {
                    Text(
                        "Couldn't load this artist right now — check your connection or enable more sources in Settings.",
                        style = T.type.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(Dimens.xl)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.xl)) }
    }
}

@Composable
private fun Hero(
    profile: ArtistProfile,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val colors = T.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.xl, vertical = Dimens.sm)
    ) {
        Box(contentAlignment = Alignment.BottomStart) {
            ArtworkImage(
                url = profile.imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(Dimens.radiusMd)
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.background.copy(alpha = 0.75f))
                        )
                    )
            )
            Column(Modifier.padding(Dimens.lg)) {
                Text(
                    profile.name,
                    style = T.type.display,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                profile.stats?.let {
                    Text(it, style = T.type.caption, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        Spacer(Modifier.height(Dimens.md))
        if (profile.genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                profile.genres.take(3).forEach { g ->
                    FilterChip(label = g, selected = false, onClick = {})
                }
            }
            Spacer(Modifier.height(Dimens.sm))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                "Play",
                onClick = onPlayAll,
                icon = Icons.Rounded.PlayArrow,
                enabled = profile.topTracks.isNotEmpty()
            )
            Spacer(Modifier.width(Dimens.sm))
            PillButton(
                "Shuffle",
                onClick = onShuffle,
                icon = Icons.Rounded.Shuffle,
                primary = false,
                enabled = profile.topTracks.isNotEmpty()
            )
        }
    }
}

@Composable
private fun AlbumTile(album: ArtistAlbum) {
    val colors = T.colors
    Column(Modifier.width(128.dp)) {
        ArtworkImage(
            url = album.artworkUrl,
            modifier = Modifier.size(128.dp),
            shape = RoundedCornerShape(Dimens.radiusSm)
        )
        Spacer(Modifier.height(7.dp))
        Text(
            album.title,
            style = T.type.caption,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.year ?: "Album",
            style = T.type.micro,
            color = colors.textTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun RelatedArtistChip(ref: ArtistRef, onClick: () -> Unit) {
    val colors = T.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .tapable { onClick() }
    ) {
        ArtworkImage(
            url = ref.imageUrl,
            modifier = Modifier.size(76.dp),
            shape = CircleShape
        )
        Spacer(Modifier.height(7.dp))
        Text(
            ref.name,
            style = T.type.caption,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistHeroSkeleton() {
    val colors = T.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(Dimens.xl)
            .height(160.dp)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(colors.surfaceHigh)
    )
    Spacer(Modifier.height(Dimens.md))
}
