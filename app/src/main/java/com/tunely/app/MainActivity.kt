package com.tunely.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        setContent { TunelyTheme { Root(vm) } }
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (banner != null) {
                        Text(
                            banner,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().background(AccentRed)
                                .clickable { vm.message.value = null; vm.player.clearError() }
                                .padding(10.dp)
                        )
                    }
                    MiniPlayer(state, { expanded = true }, vm.player::togglePlay, vm.player::next)
                    NavigationBar {
                        listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Library" to Icons.Default.LibraryMusic)
                            .forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(tab == i, { tab = i }, { Icon(icon, null) }, label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentRed, selectedTextColor = AccentRed, indicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                            }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(vm)
                    1 -> SearchScreen(vm)
                    else -> LibraryScreen(vm)
                }
            }
        }

        // Full-screen player slides up over everything.
        AnimatedVisibility(expanded && state.track != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            NowPlayingScreen(state, vm) { expanded = false }
        }
    }
}

@Composable
private fun TrackRow(t: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(t.artworkUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val recent by vm.recent.collectAsState()
    LazyColumn {
        item { Text("Listen Now", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        item { Text("Recently Played", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)) }
        val tracks = recent.map { Track(it.trackId, it.title, it.artist, artworkUrl = it.artworkUrl) }
        itemsIndexed(tracks) { i, t -> TrackRow(t) { vm.play(tracks, i) } }
        if (tracks.isEmpty()) item { Text("Search for a song to get started.", modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun SearchScreen(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    val results by vm.searchResults.collectAsState()
    val loading by vm.searching.collectAsState()
    Column {
        Text("Search", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        OutlinedTextField(q, { q = it; vm.search(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Songs, artists, albums") }, singleLine = true, shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = AccentRed)
        LazyColumn { itemsIndexed(results) { i, t -> TrackRow(t) { vm.play(results, i) } } }
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel) {
    val lib by vm.library.collectAsState()
    val tracks = lib.map { Track(it.id, it.title, it.artist, it.album, it.durationMs, it.artworkUrl) }
    LazyColumn {
        item { Text("Library", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        itemsIndexed(tracks) { i, t -> TrackRow(t) { vm.play(tracks, i) } }
        if (tracks.isEmpty()) item { Text("Tap the heart in the player to add songs.", modifier = Modifier.padding(16.dp)) }
    }
}
