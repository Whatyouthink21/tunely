package com.tunely.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tunely.app.data.PlaybackKind
import com.tunely.app.data.Sources
import com.tunely.app.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of the player. Position lives in its own flow so a 60 fps
 * progress bar never recomposes the rest of the app.
 */
data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1
) {
    val hasNext: Boolean get() = queueIndex in 0 until queue.lastIndex
    val hasPrevious: Boolean get() = queueIndex > 0
    val isLive: Boolean get() = track?.kind == PlaybackKind.LIVE
}

/** Thin wrapper around a MediaController that exposes Compose-friendly state. */
class PlayerController(context: Context) {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val queue = mutableListOf<Track>()
    private var preShuffleOrder: List<Track>? = null
    private var shuffleEnabled = false

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pending: Pending? = null
    private var releasing = false

    private data class Pending(val tracks: List<Track>, val startIndex: Int, val shuffle: Boolean)

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (releasing) return@addListener
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = publish()
                    override fun onPlayerError(e: PlaybackException) {
                        _error.value = friendlyMessage(e)
                    }
                })
                publish()
                pending?.let { p ->
                    pending = null
                    playQueue(p.tracks, p.startIndex, p.shuffle)
                }
            }.onFailure {
                _error.value = "Playback service unavailable"
            }
        }, MoreExecutors.directExecutor())
    }

    // ─── Queue ───────────────────────────────────────────────────────
    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        _error.value = null
        val c = controller
        if (c == null) {
            pending = Pending(tracks, startIndex.coerceIn(0, tracks.lastIndex), shuffle)
            return
        }
        val ordered = if (shuffle) shuffledFrom(tracks, startIndex.coerceIn(0, tracks.lastIndex)) else tracks
        queue.clear()
        queue += ordered
        shuffleEnabled = shuffle
        preShuffleOrder = if (shuffle) tracks.toList() else null
        c.setMediaItems(
            ordered.map(PlaybackService::mediaItemFor),
            if (shuffle) 0 else startIndex.coerceIn(0, tracks.lastIndex),
            0L
        )
        c.prepare()
        c.play()
        publish()
    }

    private fun shuffledFrom(tracks: List<Track>, startIndex: Int): List<Track> {
        val head = tracks[startIndex]
        val rest = tracks.filterIndexed { i, _ -> i != startIndex }.shuffled()
        return listOf(head) + rest
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, queue.size)
        queue.add(at, track)
        c.addMediaItem(at, PlaybackService.mediaItemFor(track))
        publish()
    }

    fun addToQueue(track: Track) {
        val c = controller
        if (c == null) {
            playQueue(listOf(track), 0)
            return
        }
        queue += track
        c.addMediaItem(PlaybackService.mediaItemFor(track))
        publish()
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        c.moveMediaItem(from, to)
        publish()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        queue.removeAt(index)
        c.removeMediaItem(index)
        publish()
    }

    fun clearQueue() {
        val c = controller ?: return
        queue.clear()
        c.clearMediaItems()
        publish()
    }

    // ─── Transport ───────────────────────────────────────────────────
    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        publish()
    }

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.seekTo(0, 0L)
        publish()
    }

    fun previous() {
        val c = controller ?: return
        // Match everyone's expectation: restart the track unless we just skipped.
        if (c.currentPosition > 4_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPrevious()
        publish()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
        _position.value = ms.coerceAtLeast(0L)
    }

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        seekTo(c.currentPosition + deltaMs)
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index in queue.indices) {
            c.seekToDefaultPosition(index)
            publish()
        }
    }

    fun setShuffle(enabled: Boolean) {
        val c = controller ?: return
        if (enabled == shuffleEnabled || queue.isEmpty()) return
        val currentIndex = c.currentMediaItemIndex
        val current = queue.getOrNull(currentIndex)
        shuffleEnabled = enabled
        val ordered: List<Track>
        val startIndex: Int
        if (enabled) {
            preShuffleOrder = queue.toList()
            ordered = current?.let { shuffledFrom(queue.toList(), currentIndex) } ?: queue.shuffled()
            startIndex = 0
        } else {
            ordered = preShuffleOrder ?: queue.toList()
            startIndex = current?.let { t -> ordered.indexOfFirst { it.uid == t.uid } }?.coerceAtLeast(0) ?: 0
        }
        queue.clear()
        queue += ordered
        c.setMediaItems(ordered.map(PlaybackService::mediaItemFor), startIndex, 0L)
        c.prepare()
        publish()
    }

    fun toggleShuffle() = setShuffle(!shuffleEnabled)

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun clearError() { _error.value = null }

    /** Called by the UI ticker; only touches the cheap position flow. */
    fun refreshPosition() {
        val c = controller ?: return
        val position = c.currentPosition
        if (kotlin.math.abs(position - _position.value) >= 100 || position == 0L) {
            _position.value = position
        }
    }

    fun release() {
        releasing = true
        controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
        controllerFuture = null
        controller = null
    }

    private fun publish() {
        val c = controller ?: return
        // The player is the source of truth if the two ever drift apart.
        while (queue.size > c.mediaItemCount && queue.isNotEmpty()) queue.removeAt(queue.lastIndex)
        val index = c.currentMediaItemIndex
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        _state.value = PlayerUiState(
            track = queue.getOrNull(index),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = duration.takeIf { it > 0 } ?: queue.getOrNull(index)?.durationMs ?: 0L,
            shuffle = shuffleEnabled,
            repeatMode = c.repeatMode,
            queue = queue.toList(),
            queueIndex = index
        )
    }

    private fun friendlyMessage(e: PlaybackException): String {
        val sourceName = _state.value.track?.let { Sources.byId(it.source)?.name }
        val detail = e.cause?.message ?: e.message ?: ""
        return when {
            detail.contains("not find a playable", ignoreCase = true) ->
                sourceName?.let { "$it could not provide a stream for this track" }
                    ?: "This track has no playable stream"
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network is too slow to stream this track"
            e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                sourceName?.let { "$it refused to serve this track (the link expired)" }
                    ?: "The stream link expired"
            else -> "Playback failed: ${e.errorCodeName}"
        }
    }
}
