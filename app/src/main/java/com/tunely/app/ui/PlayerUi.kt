package com.tunely.app.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tunely.app.data.Track
import com.tunely.app.player.PlayerUiState

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}

/** Extracts a dominant color from the artwork to tint the whole player. */
@Composable
fun rememberArtworkColor(url: String?): Color {
    val ctx = LocalContext.current
    var color by remember(url) { mutableStateOf(Color(0xFF3A3A3C)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(200).build()
        val res = ImageLoader(ctx).execute(req)
        if (res is SuccessResult) {
            val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: res.drawable.toBitmap()
            val p = Palette.from(bmp).generate()
            val rgb = p.getDarkVibrantColor(p.getDominantColor(0xFF3A3A3C.toInt()))
            color = Color(rgb)
        }
    }
    return animateColorAsState(color, tween(900), label = "artColor").value
}

@Composable
fun MiniPlayer(state: PlayerUiState, onTap: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val t = state.track ?: return
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .clickable(onClick = onTap)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(t.artworkUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Text(t.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        IconButton(onToggle) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        IconButton(onNext) { Icon(Icons.Default.SkipNext, null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    vm: MainViewModel,
    onClose: () -> Unit
) {
    val t = state.track ?: return
    val tint = rememberArtworkColor(t.artworkUrl)
    val lyrics by vm.lyrics.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    val inLibrary by vm.isInLibrary(t.id).collectAsState(false)
    var showQueue by remember { mutableStateOf(false) }

    // Slowly drifting gradient, like Apple Music's animated backdrop.
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "d"
    )
    val artScale by animateFloatAsState(if (state.isPlaying) 1f else 0.82f, spring(dampingRatio = 0.6f), label = "art")

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(tint, Color.Black, tint.copy(alpha = 0.6f)),
                start = androidx.compose.ui.geometry.Offset(0f, 2000f * drift),
                end = androidx.compose.ui.geometry.Offset(1500f * (1 - drift), 0f)
            )
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).statusBarsPadding().navigationBarsPadding()) {
            IconButton(onClose, Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.8f))
            }

            if (showLyrics) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(t.artworkUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(t.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                Box(Modifier.weight(1f)) {
                    LyricsView(lyrics, state.positionMs, onSeek = vm.player::seekTo)
                }
            } else {
                Spacer(Modifier.weight(1f))
                AsyncImage(
                    t.artworkUrl, null,
                    Modifier.fillMaxWidth().aspectRatio(1f).scale(artScale).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.weight(1f))
                Text(t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, maxLines = 1)
                listOfNotNull(t.album, t.genre, t.releaseYear).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(16.dp))
            Slider(
                value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                onValueChange = { vm.player.seekTo((it * state.durationMs).toLong()) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(state.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("-" + fmt(state.durationMs - state.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(vm.player::previous) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
                IconButton(vm.player::togglePlay, Modifier.size(72.dp)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                IconButton(vm.player::next) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ showLyrics = !showLyrics }) { Icon(Icons.Default.Lyrics, null, tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f)) }
                IconButton(vm.player::toggleShuffle) { Icon(Icons.Default.Shuffle, null, tint = if (state.shuffle) AccentRed else Color.White.copy(alpha = 0.5f)) }
                IconButton(vm.player::cycleRepeat) { Icon(Icons.Default.Repeat, null, tint = if (state.repeatMode != 0) AccentRed else Color.White.copy(alpha = 0.5f)) }
                IconButton({ vm.toggleLibrary(t, inLibrary) }) { Icon(if (inLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (inLibrary) AccentRed else Color.White.copy(alpha = 0.5f)) }
                IconButton({ showQueue = true }) { Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(alpha = 0.5f)) }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Up Next", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                state.queue.forEachIndexed { i, q ->
                    Row(Modifier.fillMaxWidth().clickable { vm.player.skipTo(i); showQueue = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(q.artworkUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(q.title, color = if (i == state.queueIndex) AccentRed else Color.Unspecified, maxLines = 1)
                            Text(q.artist, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
