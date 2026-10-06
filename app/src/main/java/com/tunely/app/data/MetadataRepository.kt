package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Enriches a YouTube track with high-quality metadata and artwork
 * using the public iTunes Search API (no key required).
 */
class MetadataRepository(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ItunesResponse(val results: List<ItunesResult> = emptyList())

    @Serializable
    private data class ItunesResult(
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val artworkUrl100: String? = null,
        val primaryGenreName: String? = null,
        val releaseDate: String? = null,
        val trackExplicitness: String? = null
    )

    suspend fun enrich(track: Track): Track = withContext(Dispatchers.IO) {
        runCatching {
            val term = java.net.URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$term&entity=song&limit=5")
                .build()
            val body = http.newCall(req).execute().use { it.body?.string() } ?: return@runCatching track
            val match = json.decodeFromString<ItunesResponse>(body).results.firstOrNull {
                it.trackName.normalized().contains(track.title.normalized()) ||
                    track.title.normalized().contains(it.trackName.normalized())
            } ?: return@runCatching track

            track.copy(
                album = match.collectionName ?: track.album,
                // Swap the 100px thumbnail for a 1200px render of the same cover.
                artworkUrl = match.artworkUrl100?.replace("100x100bb", "1200x1200bb") ?: track.artworkUrl,
                genre = match.primaryGenreName,
                releaseYear = match.releaseDate?.take(4),
                explicit = match.trackExplicitness == "explicit"
            )
        }.getOrDefault(track)
    }

    private fun String?.normalized() =
        (this ?: "").lowercase().replace(Regex("[^a-z0-9]"), "")
}
