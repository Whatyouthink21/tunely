package com.tunely.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tunely.app.TunelyApp
import com.tunely.app.data.AudioQuality
import com.tunely.app.data.PlaybackKind
import com.tunely.app.data.SettingsManager
import com.tunely.app.data.Track
import com.tunely.app.data.UserAgents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.math.min

/**
 * Background playback. Provider URLs are resolved lazily right before playback
 * (they expire), the equalizer / loudness / fade settings are applied here, and
 * a MediaSession drives the notification and lock screen controls.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var effects: AudioEffects? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fadeJob: Job? = null

    private val settings: SettingsManager get() = (application as TunelyApp).settings
    private val registry get() = (application as TunelyApp).registry

    /** Cached provider URLs — extractor links expire, so they are re-resolved. */
    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    override fun onCreate() {
        super.onCreate()

        val resolver = ResolvingDataSource.Resolver { spec ->
            val parsed = MediaUri.parse(spec.uri) ?: return@Resolver spec
            parsed.directUrl?.let { return@Resolver spec.withUri(android.net.Uri.parse(it)) }

            val key = "${parsed.source}::${parsed.id}"
            val cached = urlCache[key]
            if (cached != null && System.currentTimeMillis() - cached.second < URL_TTL_MS) {
                return@Resolver spec.withUri(android.net.Uri.parse(cached.first))
            }
            val target = AudioQuality.targetKbps(settings.audioQuality.value)
            val url = runBlocking { registry.resolve(MediaUri.toTrack(parsed), target) }
                ?: throw IOException(
                    "No playable stream found on ${parsed.source.replaceFirstChar(Char::uppercase)}"
                )
            urlCache[key] = url to System.currentTimeMillis()
            spec.withUri(android.net.Uri.parse(url))
        }

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UserAgents.NEWPIPE)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory: DataSource.Factory =
            ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http), resolver)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.setPauseAtEndOfMediaItems(!settings.gapless.value)
        exo.setPlaybackSpeed(settings.playbackSpeed.value)
        player = exo
        effects = AudioEffects(exo, scope, settings)

        session = MediaSession.Builder(this, exo).build()

        scope.launch {
            settings.gapless.collect { gapless -> player?.setPauseAtEndOfMediaItems(!gapless) }
        }
        scope.launch {
            settings.playbackSpeed.collect { speed -> player?.setPlaybackSpeed(speed) }
        }
        scope.launch {
            settings.cacheEnabled.collect { enabled -> if (!enabled) urlCache.clear() }
        }
        startCrossfadeLoop()
    }

    /**
     * Fade & blend. With the crossfade setting on, the outgoing track fades out
     * over the final seconds and the incoming one fades in, using ExoPlayer's own
     * volume so the lock screen reflects it as well.
     */
    private fun startCrossfadeLoop() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            var lastIndex = -1
            while (true) {
                val exo = player
                if (exo == null) break
                val fadeMs = settings.crossfadeSeconds.value * 1000L
                if (fadeMs <= 0L) {
                    if (exo.volume != 1f) exo.volume = 1f
                    delay(400)
                    continue
                }
                if (exo.currentMediaItemIndex != lastIndex) lastIndex = exo.currentMediaItemIndex
                if (exo.isPlaying && exo.mediaItemCount > 0) {
                    val position = exo.currentPosition
                    val duration = exo.duration
                    val durationKnown = duration > 0 && duration != C.TIME_UNSET
                    val hasNext = exo.currentMediaItemIndex < exo.mediaItemCount - 1
                    val fadeOut = if (hasNext && durationKnown) {
                        ((duration - position).toFloat() / fadeMs).coerceIn(0f, 1f)
                    } else 1f
                    val fadeIn = (position.toFloat() / fadeMs).coerceIn(0f, 1f)
                    val target = min(fadeOut, fadeIn)
                    if (kotlin.math.abs(target - exo.volume) > 0.01f) exo.volume = target
                }
                delay(120)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        scope.cancel()
        effects?.release()
        urlCache.clear()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val URL_TTL_MS = 15 * 60_000L

        fun mediaItemFor(track: Track): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)

            val builder = MediaItem.Builder()
                .setMediaId(track.uid)
                .setUri(MediaUri.forTrack(track))
                .setMediaMetadata(metadata.build())
            if (track.kind == PlaybackKind.LIVE) {
                builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            }
            return builder.build()
        }
    }
}
