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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunely.app.data.PlaylistEntity
import kotlinx.coroutines.flow.first
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.EmptyState
import com.tunely.app.ui.design.FilterChip
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.PillButton
import com.tunely.app.ui.design.SheetRow
import com.tunely.app.ui.design.SheetScaffold
import com.tunely.app.ui.design.SheetTitle
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TrackRow
import com.tunely.app.ui.design.TunelyCard
import com.tunely.app.ui.design.TunelyTextField

private enum class LibraryTab(val label: String) {
    FAVOURITES("Favourites"),
    RECENT("Recent"),
    PLAYLISTS("Playlists")
}

@Composable
fun LibraryScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val colors = T.colors
    val library by vm.library.collectAsState()
    val recent by vm.recent.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val playerState by vm.playerState.collectAsState()

    var tab by remember { mutableStateOf(LibraryTab.FAVOURITES) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var openPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val favorites = remember(library) { library.map { it.toTrack() } }
    val recentTracks = remember(recent) { recent.map { it.toTrack() } }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = Dimens.xl, top = Dimens.lg)) {
                Text("Library", style = T.type.display, color = colors.textPrimary)
                Text(
                    "${favorites.size} favourites · ${recentTracks.size} played · ${playlists.size} playlists",
                    style = T.type.caption,
                    color = colors.textTertiary
                )
                Spacer(Modifier.height(Dimens.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    LibraryTab.entries.forEach { entry ->
                        FilterChip(
                            label = entry.label,
                            selected = tab == entry,
                            onClick = { tab = entry }
                        )
                    }
                }
            }
        }

        item {
            when (tab) {
                LibraryTab.FAVOURITES -> if (favorites.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Favorite,
                        title = "No favourites yet",
                        message = "Tap the heart in the player or long-press any track to save it here."
                    )
                }
                LibraryTab.RECENT -> if (recentTracks.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "Nothing played yet",
                        message = "Your listening history shows up here automatically."
                    )
                }
                LibraryTab.PLAYLISTS -> if (playlists.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.QueueMusic,
                        title = "No playlists yet",
                        message = "Build a set from any track's long-press menu.",
                        actionLabel = "New playlist",
                        onAction = { showCreate = true }
                    )
                }
            }
        }

        if (tab == LibraryTab.PLAYLISTS && playlists.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.xl, vertical = Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your playlists", style = T.type.title, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    PillButton("New", onClick = { showCreate = true }, icon = Icons.Rounded.Add)
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { openPlaylist = playlist },
                    onDelete = { vm.deletePlaylist(playlist.id) }
                )
            }
        }

        if (tab == LibraryTab.FAVOURITES && favorites.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.xl, vertical = Dimens.sm)
                ) {
                    PillButton(
                        "Play all",
                        onClick = { vm.playShuffled(favorites) },
                        icon = Icons.Rounded.PlayArrow
                    )
                }
            }
            itemsIndexed(favorites, key = { _, t -> t.uid }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    isCurrent = playerState.track?.uid == track.uid,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(favorites, index) },
                    onLongClick = { actionTrack = track },
                    modifier = Modifier.padding(horizontal = Dimens.md),
                    trailing = {
                        GhostIconButton(
                            icon = Icons.Rounded.MoreVert,
                            onClick = { actionTrack = track },
                            size = 34.dp,
                            iconSize = 18.dp
                        )
                    }
                )
            }
        }

        if (tab == LibraryTab.RECENT && recentTracks.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.xl, vertical = Dimens.sm)
                ) {
                    PillButton(
                        "Play all",
                        onClick = { vm.playShuffled(recentTracks) },
                        icon = Icons.Rounded.PlayArrow,
                        primary = false
                    )
                }
            }
            itemsIndexed(recentTracks, key = { index, t -> "${t.uid}-$index" }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    isCurrent = playerState.track?.uid == track.uid,
                    isPlaying = playerState.isPlaying,
                    onClick = { vm.play(recentTracks, index) },
                    onLongClick = { actionTrack = track },
                    modifier = Modifier.padding(horizontal = Dimens.md)
                )
            }
        }

        item { Spacer(Modifier.height(Dimens.xxl)) }
    }

    TrackActionsSheet(track = actionTrack, onDismiss = { actionTrack = null }, vm = vm)

    PlaylistSheet(
        playlist = openPlaylist,
        vm = vm,
        onDismiss = { openPlaylist = null }
    )

    SheetScaffold(visible = showCreate, onDismiss = { showCreate = false }) {
        SheetTitle("New playlist", onClose = { showCreate = false })
        Column(Modifier.padding(horizontal = Dimens.xl)) {
            TunelyTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "Name"
            )
            Spacer(Modifier.height(Dimens.md))
            PillButton(
                "Create playlist",
                onClick = {
                    vm.createPlaylist(newName)
                    newName = ""
                    showCreate = false
                    tab = LibraryTab.PLAYLISTS
                },
                enabled = newName.isNotBlank()
            )
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = T.colors
    TunelyCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors.accentGradient.map { it.copy(alpha = 0.28f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.QueueMusic, null, tint = colors.accent.primary)
            }
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = T.type.subtitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Playlist", style = T.type.caption, color = colors.textTertiary)
            }
            GhostIconButton(
                icon = Icons.Rounded.Delete,
                onClick = onDelete,
                size = 34.dp,
                iconSize = 17.dp,
                contentDescription = "Delete playlist"
            )
        }
    }
}

@Composable
private fun PlaylistSheet(
    playlist: PlaylistEntity?,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    val items by produceState(initialValue = emptyList<Track>(), playlist?.id) {
        value = playlist?.let { p ->
            vm.playlistTracks(p.id).first().map { it.toTrack() }
        }.orEmpty()
    }

    SheetScaffold(visible = playlist != null, onDismiss = onDismiss) {
        if (playlist == null) return@SheetScaffold
        SheetTitle(
            title = playlist.name,
            subtitle = "${items.size} tracks",
            onClose = onDismiss
        )
        if (items.isNotEmpty()) {
            Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.xs)) {
                PillButton("Play", onClick = { vm.play(items, 0) }, icon = Icons.Rounded.PlayArrow)
            }
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(items) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    isCurrent = false,
                    onClick = { vm.play(items, index) },
                    trailing = {
                        GhostIconButton(
                            icon = Icons.Rounded.Delete,
                            onClick = { vm.removeFromPlaylist(playlist.id, track) },
                            size = 32.dp,
                            iconSize = 16.dp
                        )
                    }
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "Empty for now — add tracks from any long-press menu.",
                style = T.type.caption,
                color = T.colors.textTertiary,
                modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.lg)
            )
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}
