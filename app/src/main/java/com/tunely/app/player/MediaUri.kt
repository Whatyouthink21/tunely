package com.tunely.app.player

import android.net.Uri
import android.util.Base64
import com.tunely.app.data.SourceIds
import com.tunely.app.data.Track

/**
 * Tunely hands ExoPlayer a private URI instead of a provider URL:
 *
 *  * `tunely://direct/<base64 url>` — the provider gave us a playable URL already
 *    (radio, previews, Audius).
 *  * `tunely://track/<source>/<id>` — the URL has to be resolved right before
 *    playback, because provider URLs expire (YouTube, SoundCloud, Bandcamp).
 *
 * Keeping this inside the media item means the player service needs no lookups
 * and a queue survives being handed to the background service.
 */
object MediaUri {

    private const val SCHEME = "tunely"
    private const val HOST_DIRECT = "direct"
    private const val HOST_TRACK = "track"

    data class Parsed(
        val source: String,
        val id: String,
        val directUrl: String? = null
    )

    fun forTrack(track: Track): Uri {
        val direct = track.streamUrl?.takeIf { it.isNotBlank() }
        return if (direct != null) {
            Uri.parse("$SCHEME://$HOST_DIRECT/${encode(direct)}")
        } else {
            Uri.parse("$SCHEME://$HOST_TRACK/${SourceIds.normalize(track.source)}/${Uri.encode(track.id)}")
        }
    }

    fun parse(uri: Uri): Parsed? {
        if (uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        return when (uri.host) {
            HOST_DIRECT -> {
                val url = segments.firstOrNull()?.let { decode(it) } ?: return null
                Parsed(source = SourceIds.YOUTUBE, id = "", directUrl = url)
            }
            HOST_TRACK -> {
                val source = segments.getOrNull(0) ?: return null
                val id = segments.getOrNull(1)?.let { Uri.decode(it) } ?: return null
                Parsed(source = source, id = id)
            }
            else -> null
        }
    }

    /** Rebuild a minimal track that the resolver can work with. */
    fun toTrack(parsed: Parsed): Track = Track(
        id = parsed.id,
        title = "",
        artist = "",
        source = parsed.source,
        sourceUrl = parsed.id.takeIf { it.startsWith("http") },
        streamUrl = parsed.directUrl
    )

    private fun encode(url: String): String =
        Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
    }.getOrNull()
}
