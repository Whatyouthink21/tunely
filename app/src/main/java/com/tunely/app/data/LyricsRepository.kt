package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Turns messy stream titles into something a lyrics database can match. */
object TrackQuery {
    private val noise = Regex(
        "(?i)\\(?(official|lyric|lyrics|audio|video|visualizer|hd|hq|4k|explicit|mv|m/v|" +
            "remaster(ed)?|live|performance|clip|full song|free download)[^)]*\\)?"
    )

    /** Best-effort artist/title split for titles like "Artist - Song (Official Video)". */
    fun split(track: Track): Pair<String, String> {
        var artist = track.artist
        var title = track.title
        val dash = title.split(" - ", limit = 2)
        if (dash.size == 2 && artist.length < 3) {
            artist = dash[0]
            title = dash[1]
        }
        return clean(artist) to clean(title)
    }

    fun clean(raw: String): String = raw
        .replace(Regex("(?i)\\s*[\\[(][^)\\]]*(official|lyric|audio|video|visuali[sz]er|" +
            "remaster|hd|hq|4k|mv|clip)[^)\\]]*[)\\]]"), "")
        .replace(noise, " ")
        .replace(Regex("(?i)\\s*(feat|ft|featuring)\\.?\\s+[^,()\\-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '–', '|', ',')
}

/** A lyrics provider returns timed lyrics or null. Providers are tried in order. */
interface LyricsProvider {
    val name: String
    suspend fun fetch(track: Track): Lyrics?
}

class LyricsRepository(http: OkHttpClient) {

    private val providers: List<LyricsProvider> = listOf(
        LrcLibProvider(http),
        LyricsOvhProvider(http)
    )

    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        for (p in providers) {
            val result = withTimeoutOrNull(9_000) { runCatching { p.fetch(track) }.getOrNull() }
            if (result != null && result.lines.isNotEmpty()) return@withContext result
        }
        null
    }
}

/**
 * LRCLIB: free, open lyrics database with synced (line + word level) LRC.
 * The /search endpoint is far more forgiving than /get, so it is tried first
 * and the closest duration wins.
 */
class LrcLibProvider(private val http: OkHttpClient) : LyricsProvider {
    override val name = "LRCLIB"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Hit(
        val id: Long = 0,
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double = 0.0,
        val instrumental: Boolean = false,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null
    )

    override suspend fun fetch(track: Track): Lyrics? {
        val (artist, title) = TrackQuery.split(track)
        if (title.isBlank()) return null
        val url = "https://lrclib.net/api/search?track_name=${encode(title)}" +
            "&artist_name=${encode(artist)}"
        val req = Request.Builder().url(url).header("User-Agent", UserAgents.TUNELY).build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val hits = runCatching { json.decodeFromString<List<Hit>>(body) }.getOrNull()
            ?: return null
        val target = track.durationMs / 1000.0
        val best = hits
            .filter { !it.instrumental && (it.syncedLyrics != null || it.plainLyrics != null) }
            .minByOrNull { hit ->
                val namePenalty = if (
                    hit.trackName?.lowercase()?.contains(title.lowercase().take(12)) == true
                ) 0.0 else 3.0
                val durationPenalty = if (target > 0 && hit.duration > 0) {
                    kotlin.math.abs(hit.duration - target) / 10.0
                } else 0.0
                namePenalty + durationPenalty
            } ?: return null

        best.syncedLyrics?.let { return LrcParser.parse(it, name) }
        best.plainLyrics?.let { return LrcParser.plain(it, name) }
        return null
    }
}

/** Lyrics.ovh — plain lyrics fallback. */
class LyricsOvhProvider(private val http: OkHttpClient) : LyricsProvider {
    override val name = "Lyrics.ovh"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Result(val lyrics: String? = null)

    override suspend fun fetch(track: Track): Lyrics? {
        val (artist, title) = TrackQuery.split(track)
        if (artist.isBlank() || title.isBlank()) return null
        val url = "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}"
        val req = Request.Builder().url(url).header("User-Agent", UserAgents.TUNELY).build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return null
        val text = runCatching { json.decodeFromString<Result>(body) }.getOrNull()?.lyrics ?: return null
        // Lyrics.ovh prepends a "Paroles de la chanson ... par ..." header line.
        val cleaned = text.lines()
            .filterNot { it.startsWith("Paroles de la chanson", ignoreCase = true) }
            .joinToString("\n")
        return LrcParser.plain(cleaned, name)
    }
}
