package com.tunely.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunely.app.data.Sources
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.EmptyState
import com.tunely.app.ui.design.FilterChip
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.SectionHeader
import com.tunely.app.ui.design.SkeletonTrackRow
import com.tunely.app.ui.design.SourceChip
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackRow
import com.tunely.app.ui.design.TunelyCard
import com.tunely.app.ui.design.TunelyTextField
import com.tunely.app.ui.design.tapable

private val SUGGESTIONS = listOf(
    "Midnight drive", "Indie folk", "Bollywood hits", "Deep house",
    "90s hip hop", "Piano study", "K-pop", "Reggaeton"
)

@Composable
fun SearchScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val colors = T.colors
    val query by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val activeSource by vm.activeSource.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val enabledSources by vm.enabledSources.collectAsState()
    var actionTrack by remember { mutableStateOf<Track?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(Modifier.padding(horizontal = Dimens.xl, top = Dimens.lg)) {
                Text("Search", style = T.type.display, color = colors.textPrimary)
                Spacer(Modifier.height(Dimens.md))
                TunelyTextField(
                    value = query,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = "Songs, artists, stations…",
                    leadingIcon = Icons.Rounded.Search,
                    trailing = {
                        if (query.isNotEmpty()) {
                            GhostIconButton(
                                icon = Icons.Rounded.Close,
                                onClick = { vm.onQueryChange("") },
                                size = 30.dp,
                                iconSize = 15.dp
                            )
                        }
                    }
                )
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.xl, vertical = Dimens.md),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        label = "All sources",
                        selected = activeSource == MainViewModel.SOURCE_ALL,
                        onClick = { vm.setActiveSource(MainViewModel.SOURCE_ALL) }
                    )
                }
                items(enabledSources) { source ->
                    SourceChip(
                        info = source,
                        selected = activeSource == source.id,
                        onClick = { vm.setActiveSource(source.id) }
                    )
                }
            }
        }

        when {
            query.isBlank() -> {
                item {
                    SectionHeader("Try something", subtitle = "Tap a mood to search everywhere")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.xl),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                    ) {
                        items(SUGGESTIONS) { suggestion ->
                            SuggestionPill(suggestion) { vm.onQueryChange(suggestion) }
                        }
                    }
                }
                item {
                    Box(Modifier.padding(top = Dimens.lg)) {
                        EmptyState(
                            icon = Icons.Rounded.Explore,
                            title = "Every source, one search",
                            message = "Tunely fans out to " +
                                "${enabledSources.size} providers and blends the results. " +
                                "Long-press a track for queue and playlist actions."
                        )
                    }
                }
            }

            searching && results.isEmpty() -> items(6) { SkeletonTrackRow() }

            results.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Nothing matched",
                    message = "No provider returned a stream for \"$query\". Try fewer words, " +
                        "or enable more sources in Settings.",
                    actionLabel = "Open Settings",
                    onAction = { vm.setActiveSource(MainViewModel.SOURCE_ALL) }
                )
            }

            else -> {
                item {
                    Text(
                        "${results.size} results",
                        style = T.type.micro,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = Dimens.xl, top = Dimens.sm, bottom = Dimens.xs)
                    )
                }
                itemsIndexed(results) { index, track ->
                    val isCurrent = playerState.track?.uid == track.uid
                    TunelyCard(
                        onClick = {},
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusSm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.md, vertical = 2.dp)
                    ) {
                        TrackRow(
                            track = track,
                            index = index,
                            isCurrent = isCurrent,
                            isPlaying = playerState.isPlaying,
                            onClick = { vm.play(results, index) },
                            onLongClick = { actionTrack = track },
                            trailing = {
                                GhostIconButton(
                                    icon = Icons.Rounded.MoreVert,
                                    onClick = { actionTrack = track },
                                    size = 34.dp,
                                    iconSize = 18.dp,
                                    contentDescription = "Track actions"
                                )
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(Dimens.xxl)) }
            }
        }
    }

    TrackActionsSheet(track = actionTrack, onDismiss = { actionTrack = null }, vm = vm)
}

@Composable
private fun SuggestionPill(label: String, onClick: () -> Unit) {
    val colors = T.colors
    TunelyCard(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape) {
        Text(
            label,
            style = T.type.caption,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

