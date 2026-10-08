package com.tunely.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tunely.app.data.Track
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.PillButton
import com.tunely.app.ui.design.SheetRow
import com.tunely.app.ui.design.SheetScaffold
import com.tunely.app.ui.design.SheetTitle
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TunelyTextField

/**
 * One shared long-press sheet for every track in the app: queue actions,
 * favourites and “add to playlist”, including creating a playlist on the spot.
 */
@Composable
fun TrackActionsSheet(
    track: Track?,
    onDismiss: () -> Unit,
    vm: MainViewModel
) {
    val playlists by vm.playlists.collectAsState()
    var showPlaylists by remember(track?.uid) { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val favorite = track?.let { vm.isFavorite(it) } ?: false

    SheetScaffold(visible = track != null, onDismiss = onDismiss) {
        if (track == null) return@SheetScaffold
        SheetTitle(title = track.title, subtitle = track.artist, onClose = onDismiss)

        if (!showPlaylists) {
            SheetRow(
                icon = Icons.Rounded.PlaylistPlay,
                label = "Play next",
                onClick = {
                    vm.playNext(track)
                    onDismiss()
                }
            )
            SheetRow(
                icon = Icons.Rounded.QueueMusic,
                label = "Add to queue",
                onClick = {
                    vm.addToQueue(track)
                    onDismiss()
                }
            )
            SheetRow(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (favorite) "Remove from favourites" else "Add to favourites",
                onClick = {
                    vm.toggleFavorite(track)
                    onDismiss()
                },
                tint = T.colors.accent.secondary
            )
            SheetRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to playlist",
                value = if (playlists.isEmpty()) "Create one" else null,
                onClick = { showPlaylists = true }
            )
            SheetRow(
                icon = Icons.Rounded.Person,
                label = "Go to artist",
                value = track.artist,
                onClick = {
                    vm.openArtist(track.artist)
                    onDismiss()
                }
            )
        } else {
            Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                TunelyTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = "New playlist name",
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        vm.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                    }
                )
                Spacer(Modifier.height(Dimens.sm))
                Row {
                    PillButton(
                        "Create & add",
                        onClick = {
                            vm.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showPlaylists = false
                            onDismiss()
                        },
                        enabled = newPlaylistName.isNotBlank()
                    )
                }
                Spacer(Modifier.height(Dimens.md))
            }
            playlists.forEach { playlist ->
                SheetRow(
                    icon = Icons.Rounded.QueueMusic,
                    label = playlist.name,
                    onClick = {
                        vm.addToPlaylist(playlist.id, track)
                        showPlaylists = false
                        onDismiss()
                    }
                )
            }
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet — name one above.",
                    style = T.type.caption,
                    color = T.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = Dimens.xl)
                )
            }
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

/** Small helper so the sheet keeps its rounded top corners on all densities. */