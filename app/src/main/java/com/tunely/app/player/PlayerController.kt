package com.tunely.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tunely.app.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0
)

/** Thin wrapper around a MediaController that exposes Compose-friendly state. */
class PlayerController(context: Context) {

    private var controller: MediaController? = null
    private val queue = mutableListOf<Track>()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var pending: Pair<List<Track>, Int>? = null

    fun clearError() { _error.value = null }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = publish()
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                        _error.value = "Playback failed: ${e.errorCodeName} - ${e.cause?.message ?: e.message}"
                    }
                })
            }
            publish()
            pending?.let { (t, i) -> pending = null; playQueue(t, i) }
        }, MoreExecutors.directExecutor())
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        _error.value = null
        val c = controller ?: run { pending = tracks to startIndex; return }
        queue.clear(); queue += tracks
        c.setMediaItems(tracks.map(::item), startIndex, 0L)
        c.prepare(); c.play()
        publish()
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        val at = (c.currentMediaItemIndex + 1).coerceAtMost(queue.size)
        queue.add(at, track); c.addMediaItem(at, item(track)); publish()
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        queue += track; c.addMediaItem(item(track)); publish()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun pause() { controller?.pause() }
    fun play() { controller?.play() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun skipTo(index: Int) { controller?.seekToDefaultPosition(index) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun setPlaybackSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Called by a UI ticker so the progress bar and lyrics stay in sync. */
    fun refreshPosition() = publish()

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerUiState(
            track = queue.getOrNull(c.currentMediaItemIndex),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue.toList(),
            queueIndex = c.currentMediaItemIndex
        )
    }

    private fun item(t: Track): MediaItem =
        PlaybackService.mediaItemFor(t.id, t.title, t.artist, t.artworkUrl, t.album)
}
