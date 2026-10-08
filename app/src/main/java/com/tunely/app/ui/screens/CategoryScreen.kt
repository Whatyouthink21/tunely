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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
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
import com.tunely.app.data.CategoryPage
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.PillButton
import com.tunely.app.ui.design.SkeletonTrackRow
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackRow

/**
 * One mood or genre, opened from the Explore grid or the Home mood chips:
 * a gradient masthead plus the bucket's picks — chart anchors mixed with
 * full-length discoveries, diversified so no two rows feel the same.
 */
@Composable
fun CategoryScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val colors = T.colors
    val page by vm.categoryPage.collectAsState()
    val loading by vm.categoryLoading.collectAsState()
    val playerState by vm.playerState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + Dimens.md,
            bottom = 180.dp
        )
    ) {
        page?.let { p ->
            item {
                Masthead(
                    page = p,
                    loading = loading,
                    onBack = onBack,
                    onPlayAll = { vm.play(p.tracks, 0) },
                    onShuffle = { vm.playShuffled(p.tracks) }
                )
            }
            itemsIndexed(
                p.tracks,
                key = { _, t -> "cat-${t.uid}" }
            ) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    isCurrent = playerState.track?.uid == track.uid,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(p.tracks, index) },
                    onArtistClick = { onOpenArtist(track.artist) },
                    modifier = Modifier.padding(horizontal = Dimens.md)
                )
            }
            if (p.tracks.isEmpty() && !loading) {
                item {
                    Text(
                        "Nothing landed here this time — try again or enable more sources in Settings.",
                        style = T.type.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(Dimens.xl)
                    )
                }
            }
        }

        if (loading) {
            items(6) { SkeletonTrackRow() }
        }

        item { Spacer(Modifier.height(Dimens.xl)) }
    }
}

@Composable
private fun Masthead(
    page: CategoryPage,
    loading: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val colors = T.colors
    val category = page.category
    val gradient = category.colors.map { Color(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostIconButton(
                icon = Icons.Rounded.ArrowBack,
                onClick = onBack,
                contentDescription = "Back"
            )
            Spacer(Modifier.width(Dimens.sm))
            Text("Explore", style = T.type.caption, color = colors.textTertiary)
        }
        Spacer(Modifier.height(Dimens.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(Brush.linearGradient(gradient))
                .padding(Dimens.xl)
        ) {
            Column {
                Text(
                    category.label,
                    style = T.type.display,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    category.tagline,
                    style = T.type.body,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(Dimens.lg))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(
                        "Play all",
                        onClick = onPlayAll,
                        icon = Icons.Rounded.PlayArrow,
                        enabled = !loading && page.tracks.isNotEmpty()
                    )
                    PillButton(
                        "Shuffle",
                        onClick = onShuffle,
                        icon = Icons.Rounded.Shuffle,
                        primary = false,
                        enabled = !loading && page.tracks.isNotEmpty()
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${page.tracks.size} tracks",
                        style = T.type.caption,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(end = Dimens.xs)
                    )
                }
            }
        }
    }
}
