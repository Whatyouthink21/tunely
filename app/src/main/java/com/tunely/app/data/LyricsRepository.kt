package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A lyrics provider returns timed lyrics or null. Providers are tried in order. */
interface LyricsProvider {
    val name: String
    suspend fun fetch(track: Track): Lyrics?
}

class LyricsRepository(http: OkHttpClient) {

    private val providers: List<LyricsProvider> = listOf(
        LrcLibProvider(http),
        // Add more providers here; each one only needs to implement LyricsProvider.
    )

    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        for (p in providers) {
            val result = runCatching { p.fetch(track) }.getOrNull()
            if (result != null && result.lines.isNotEmpty()) return@withContext result
        }
        null
    }
}

/** LRCLIB: free, open lyrics database with synced (line-level) LRC. */
class LrcLibProvider(private val http: OkHttpClient) : LyricsProvider {
    override val name = "LRCLIB"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Hit(
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null
    )

    override suspend fun fetch(track: Track): Lyrics? {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val url = "https://lrclib.net/api/get?track_name=${enc(track.title)}" +
            "&artist_name=${enc(track.artist)}" +
            (track.album?.let { "&album_name=${enc(it)}" } ?: "") +
            (if (track.durationMs > 0) "&duration=${track.durationMs / 1000}" else "")
        val req = Request.Builder().url(url).header("User-Agent", "Tunely/0.1").build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val hit = json.decodeFromString<Hit>(body)
        hit.syncedLyrics?.let { return LrcParser.parse(it, name) }
        hit.plainLyrics?.let { plain ->
            val lines = plain.lines().mapIndexed { i, t -> LyricLine(i * 1000L, (i + 1) * 1000L, t) }
            return Lyrics(lines, synced = false, wordSynced = false, source = name)
        }
        return null
    }
}
