package com.tunely.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tunely.app.data.YouTubeMusicRepository
import kotlinx.coroutines.runBlocking

/**
 * Background playback service. Each queued item carries only a YouTube video id;
 * the real stream URL is resolved lazily right before playback, because
 * YouTube stream URLs expire.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val repo = YouTubeMusicRepository()

    override fun onCreate() {
        super.onCreate()

        val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
        val resolver = ResolvingDataSource.Resolver { spec ->
            val videoId = spec.uri.getQueryParameter("v") ?: return@Resolver spec
            val hit = cache[videoId]
            val url = if (hit != null && System.currentTimeMillis() - hit.second < 20 * 60_000) {
                hit.first
            } else {
                try {
                    runBlocking { repo.resolveAudioUrl(videoId) }
                        .also { cache[videoId] = it to System.currentTimeMillis() }
                } catch (e: Exception) {
                    throw java.io.IOException("Stream lookup failed: ${e.javaClass.simpleName}: ${e.message}", e)
                }
            }
            spec.withUri(android.net.Uri.parse(url))
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory: DataSource.Factory =
            ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http), resolver)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        fun mediaItemFor(videoId: String, title: String, artist: String, art: String?, album: String?) =
            MediaItem.Builder()
                .setMediaId(videoId)
                .setUri("https://www.youtube.com/watch?v=$videoId")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(art?.let(android.net.Uri::parse))
                        .build()
                )
                .build()
    }
}
