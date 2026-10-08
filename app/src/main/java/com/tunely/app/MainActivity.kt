package com.tunely.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tunely.app.data.Track
import com.tunely.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { TunelyTheme(settings = vm.settings) { Root(vm) } }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val state by vm.playerState.collectAsState()
    val msg by vm.message.collectAsState()
    val playerError by vm.player.error.collectAsState()
    val banner = playerError ?: msg
    val glassEnabled by vm.settings.glassEffect.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (banner != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (glassEnabled)
                                        Modifier.glassPanel(
                                            tint = AccentRed.copy(alpha = 0.25f),
                                            borderColor = AccentRed.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(0.dp)
                                        )
                                    else Modifier.background(AccentRed)
                                )
                                .clickable { vm.message.value = null; vm.player.clearError() }
                                .padding(12.dp)
                        ) {
                            Text(banner, color = Color.White, fontSize = 13.sp)
                        }
                    }
                    MiniPlayer(
                        state,
                        { expanded = true },
                        vm.player::togglePlay,
                        vm.player::next,
                        glassEnabled = glassEnabled
                    )
                    // Glass navigation bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (glassEnabled) Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                        )
                                    )
                                    .background(GlassColors.navGlass)
                                    .border(
                                        0.5.dp,
                                        Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                    )
                                else Modifier.background(MaterialTheme.colorScheme.surface)
                            )
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            listOf(
                                "Home" to Icons.Default.Home,
                                "Search" to Icons.Default.Search,
                                "Library" to Icons.Default.LibraryMusic,
                                "Settings" to Icons.Default.Settings
                            ).forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(vm)
                    1 -> SearchScreen(vm)
                    2 -> LibraryScreen(vm)
                    3 -> SettingsScreen(vm)
                }
            }
        }

        // Full-screen player slides up over everything.
        AnimatedVisibility(
            expanded && state.track != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            NowPlayingScreen(state, vm) { expanded = false }
        }
    }
}

@Composable
private fun TrackRow(t: Track, onClick: () -> Unit, glass: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            t.artworkUrl, null,
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.explicit) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("E", color = MaterialTheme.colorScheme.background, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (t.source != "youtube_music" && t.source.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            t.source.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val recent by vm.recent.collectAsState()
    val library by vm.library.collectAsState()
    val recentTracks = recent.take(20).map { Track(it.trackId, it.title, it.artist, artworkUrl = it.artworkUrl) }
    val libraryTracks = library.take(20).map { Track(it.id, it.title, it.artist, it.album, it.durationMs, it.artworkUrl) }

    LazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        // Hero header
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        "Listen Now",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Curated for you",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Recently played (horizontal scroll)
        if (recentTracks.isNotEmpty()) {
            item {
                SectionHeader("Recently Played")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(recentTracks) { i, t ->
                        ArtworkCard(t) { vm.play(recentTracks, i) }
                    }
                }
            }
        }

        // Library picks
        if (libraryTracks.isNotEmpty()) {
            item {
                SectionHeader("From Your Library")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(libraryTracks) { i, t ->
                        ArtworkCard(t) { vm.play(libraryTracks, i) }
                    }
                }
            }
        }

        // Full list
        item {
            SectionHeader("All Recent")
        }
        if (recentTracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♪", fontSize = 60.sp, color = Color.White.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Nothing yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Search for a song to get started", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                }
            }
        } else {
            itemsIndexed(recentTracks) { i, t ->
                TrackRow(t, { vm.play(recentTracks, i) })
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (action != null) {
            TextButton(onAction ?: {}) {
                Text(action, color = AccentRed, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ArtworkCard(t: Track, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                t.artworkUrl, null,
                Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White
        )
        Text(
            t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SearchScreen(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    val results by vm.searchResults.collectAsState()
    val loading by vm.searching.collectAsState()
    val source by vm.searchSource.collectAsState()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Search",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // Search bar
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .glassPanel(
                    tint = Color.White.copy(alpha = 0.08f),
                    borderColor = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            OutlinedTextField(
                q,
                { q = it; vm.search(it) },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Songs, artists, albums", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = AccentRed
                ),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton({ q = ""; vm.search("") }) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Source filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sources = listOf(
                "all" to "All",
                "youtube_music" to "YouTube Music",
                "soundcloud" to "SoundCloud",
                "piped" to "Piped"
            )
            itemsIndexed(sources) { _, (key, label) ->
                val selected = source == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) AccentRed else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            0.6.dp,
                            if (selected) AccentRed else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(50)
                        )
                        .clickable { vm.searchSource.value = key; if (q.isNotBlank()) vm.search(q) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        if (loading) LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp),
            color = AccentRed
        )

        LazyColumn {
            itemsIndexed(results) { i, t ->
                TrackRow(t, { vm.play(results, i) })
            }
            if (results.isEmpty() && !loading && q.isNotBlank()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(60.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No results", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Try different keywords or sources", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel) {
    val lib by vm.library.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val tracks = lib.map { Track(it.id, it.title, it.artist, it.album, it.durationMs, it.artworkUrl) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        item {
            Text(
                "Library",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }

        // Quick action tiles
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LibraryTile(Icons.Default.Favorite, "Liked", AccentRed, "${lib.size}") {
                        // Scroll to songs
                    }
                }
                item {
                    LibraryTile(Icons.Default.History, "Recent", AccentBlue, "") {}
                }
                item {
                    LibraryTile(Icons.Default.FileDownload, "Downloads", AccentGreen, "Soon") {}
                }
                item {
                    LibraryTile(Icons.Default.Person, "Artists", AccentPurple, "") {}
                }
                item {
                    LibraryTile(Icons.Default.Album, "Albums", AccentOrange, "") {}
                }
            }
        }

        // Playlists section
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playlists", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton({ showCreatePlaylist = true }) {
                    Icon(Icons.Default.Add, null, tint = AccentRed)
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "No playlists yet",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        } else {
            itemsIndexed(playlists) { _, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentPurple.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QueueMusic, null, tint = AccentPurple)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.name, color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Playlist", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }

        // Songs
        item {
            SectionHeader("Songs")
        }
        itemsIndexed(tracks) { i, t ->
            TrackRow(t, { vm.play(tracks, i) })
        }
        if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(60.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your library is empty", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap the heart in the player to add songs", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("New Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    newPlaylistName,
                    { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            },
            confirmButton = {
                TextButton({
                    if (newPlaylistName.isNotBlank()) {
                        vm.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreatePlaylist = false
                    }
                }) { Text("Create", color = AccentRed) }
            },
            dismissButton = {
                TextButton({ showCreatePlaylist = false }) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) }
            }
        )
    }
}

@Composable
private fun LibraryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    badge: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(0.6.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (badge.isNotBlank()) {
                Text(badge, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}
