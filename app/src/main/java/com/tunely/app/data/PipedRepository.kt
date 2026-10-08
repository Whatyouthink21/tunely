package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Piped (piped.video) backend — privacy-friendly YouTube front-end that
 * exposes a public JSON API. Useful as a fallback when NewPipeExtractor
 * can't resolve a stream, and as an alternative discovery surface.
 *
 * We use the official instance list's first healthy host, or fall back
 * to pipedapi.kavin.rocks.
 */
class PipedRepository(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://pipedapi.kavin.rocks"

    @Serializable
    private data class PipedSearch(val items: List<PipedItem> = emptyList())

    @Serializable
    private data class PipedItem(
        val url: String? = null,
        val title: String? = null,
        val uploaderName: String? = null,
        val duration: Long = 0,
        val thumbnail: String? = null,
        val type: String? = null
    )

    @Serializable
    private data class PipedStream(
        val audioStreams: List<PipedAudio> = emptyList()
    )

    @Serializable
    private data class PipedAudio(
        val url: String? = null,
        val bitrate: Int = 0,
        val mimeType: String? = null
    )

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder().url("$baseUrl/search?q=$q&filter=music_songs").build()
            val body = http.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            json.decodeFromString<PipedSearch>(body).items
                .filter { it.type == "stream" && it.duration > 0 }
                .take(20)
                .map { it.toTrack() }
        }.getOrDefault(emptyList())
    }

    suspend fun resolveAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val id = videoId.removePrefix("/watch?v=")
            val req = Request.Builder().url("$baseUrl/streams/$id").build()
            val body = http.newCall(req).execute().use { it.body?.string() } ?: return@runCatching null
            json.decodeFromString<PipedStream>(body).audioStreams
                .filter { it.url != null }
                .maxByOrNull { it.bitrate }
                ?.url
        }.getOrNull()
    }

    private fun PipedItem.toTrack() = Track(
        id = url?.removePrefix("/watch?v=") ?: "",
        title = title ?: "Unknown",
        artist = uploaderName ?: "Unknown",
        durationMs = duration * 1000,
        artworkUrl = thumbnail,
        source = "piped"
    )
}
