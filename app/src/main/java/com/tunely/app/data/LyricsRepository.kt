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
        LyricsOvhProvider(http),
        DeezerPreviewProvider(http)
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
        val req = Request.Builder().url(url).header("User-Agent", "Tunely/0.2").build()
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

/**
 * Lyrics.ovh — free plain lyrics API. Returns unsynced lyrics; we
 * synthesize basic timing so the UI can still show them gracefully.
 */
class LyricsOvhProvider(private val http: OkHttpClient) : LyricsProvider {
    override val name = "Lyrics.ovh"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Result(val lyrics: String? = null)

    override suspend fun fetch(track: Track): Lyrics? {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val url = "https://api.lyrics.ovh/v1/${enc(track.artist)}/${enc(track.title)}"
        val req = Request.Builder().url(url).header("User-Agent", "Tunely/0.2").build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val result = json.decodeFromString<Result>(body)
        val text = result.lyrics ?: return null
        val lines = text.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, t -> LyricLine(i * 3500L, (i + 1) * 3500L, t.trim()) }
        return Lyrics(lines, synced = false, wordSynced = false, source = name)
    }
}

/**
 * Deezer preview — fetches 30-second preview clips for short previews.
 * Also tries to grab lyrics from the Deezer API.
 */
class DeezerPreviewProvider(private val http: OkHttpClient) : LyricsProvider {
    override val name = "Deezer"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DzSearch(val data: List<DzTrack> = emptyList())

    @Serializable
    private data class DzTrack(
        val id: Long = 0,
        val title: String? = null,
        val preview: String? = null
    )

    @Serializable
    private data class DzLyrics(val lyrics: String? = null)

    override suspend fun fetch(track: Track): Lyrics? {
        // First, search for the track on Deezer to get its ID
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val searchUrl = "https://api.deezer.com/search?q=${enc("artist:\"${track.artist}\" track:\"${track.title}\"")}&limit=1"
        val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "Tunely/0.2").build()
        val searchBody = http.newCall(searchReq).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val results = json.decodeFromString<DzSearch>(searchBody)
        val dzTrack = results.data.firstOrNull() ?: return null

        // Fetch lyrics
        val lyricsUrl = "https://api.deezer.com/track/${dzTrack.id}/lyrics"
        val lyricsReq = Request.Builder().url(lyricsUrl).header("User-Agent", "Tunely/0.2").build()
        val lyricsBody = http.newCall(lyricsReq).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val dzLyrics = runCatching { json.decodeFromString<DzLyrics>(lyricsBody) }.getOrNull() ?: return null
        val text = dzLyrics.lyrics ?: return null

        // Parse LRC-style lines if they contain time tags, else synthesize timing
        if (text.contains("[")) {
            return LrcParser.parse(text, name)
        }
        val lines = text.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, t -> LyricLine(i * 3000L, (i + 1) * 3000L, t.trim()) }
        return Lyrics(lines, synced = false, wordSynced = false, source = name)
    }
}
