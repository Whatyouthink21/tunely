package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Owns one instance of every provider and takes care of the two jobs the rest of
 * the app cares about: fan-out search and “give me a playable URL for this track”.
 */
class SourceRegistry(http: OkHttpClient) {

    private val youtube = YouTubeSource()
    private val soundcloud = SoundCloudSource()
    private val bandcamp = BandcampSource()
    private val audius = AudiusSource(http)
    private val radio = RadioSource(http)
    private val deezer = DeezerSource(http)
    private val itunes = ITunesSource(http)
    private val piped = PipedSource(http)

    val all: List<StreamingSource> = listOf(
        youtube, soundcloud, bandcamp, audius, radio, deezer, itunes, piped
    )

    fun source(id: String?): StreamingSource? {
        val normalized = SourceIds.normalize(id)
        return all.firstOrNull { it.id == normalized }
    }

    fun isEnabled(id: String, settings: SettingsManager): Boolean = when (SourceIds.normalize(id)) {
        SourceIds.YOUTUBE -> settings.sourceYoutube.value
        SourceIds.SOUNDCLOUD -> settings.sourceSoundcloud.value
        SourceIds.BANDCAMP -> settings.sourceBandcamp.value
        SourceIds.AUDIUS -> settings.sourceAudius.value
        SourceIds.DEEZER -> settings.sourceDeezer.value
        SourceIds.ITUNES -> settings.sourceItunes.value
        SourceIds.RADIO -> settings.sourceRadio.value
        SourceIds.PIPED -> settings.sourcePiped.value
        else -> false
    }

    fun enabledSources(settings: SettingsManager): List<StreamingSource> =
        all.filter { isEnabled(it.id, settings) }

    /**
     * Resolve a playable URL for [track]. Providers are tried in order and every
     * failure falls through to the next candidate, so a single broken extractor
     * no longer silences playback.
     */
    suspend fun resolve(track: Track, targetKbps: Int = 0): String? = withContext(Dispatchers.IO) {
        // 1. Providers that hand out a ready stream URL never need a round-trip.
        track.streamUrl?.takeIf { it.isNotBlank() }?.let { return@withContext it }

        val primary = source(track.source)
        if (primary != null) {
            withTimeoutOrNull(RESOLVE_TIMEOUT) {
                runCatching { primary.resolve(track, targetKbps) }.getOrNull()
            }
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext it }
        }

        // 2. YouTube ids can be served by Piped when extraction is blocked.
        if (SourceIds.normalize(track.source) == SourceIds.YOUTUBE ||
            SourceIds.normalize(track.source) == SourceIds.PIPED
        ) {
            val videoId = track.id.takeIf { !it.startsWith("http") } ?: track.sourceUrl
            videoId?.let { id ->
                withTimeoutOrNull(RESOLVE_TIMEOUT) { piped.resolve(track.copy(id = id), targetKbps) }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return@withContext it }
            }
        }

        // 3. Last resort for SoundCloud/Audius outages: a YouTube search on the
        //    very same title. Keeps the queue playing instead of stopping dead.
        val query = "${track.artist} ${track.title}".trim()
        if (query.isNotBlank() && SourceIds.normalize(track.source) != SourceIds.YOUTUBE) {
            runCatching {
                val match = youtube.search(query, 3).firstOrNull() ?: return@runCatching null
                youtube.resolve(match, targetKbps)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        null
    }

    /** Search every enabled provider in parallel and interleave the results. */
    suspend fun searchAll(
        query: String,
        sources: List<StreamingSource>,
        perSourceLimit: Int = 12
    ): List<Track> = withContext(Dispatchers.IO) {
        if (sources.isEmpty() || query.isBlank()) return@withContext emptyList()
        coroutineScope {
            val jobs = sources.map { src ->
                async {
                    withTimeoutOrNull(SEARCH_TIMEOUT) {
                        runCatching { src.search(query, perSourceLimit) }
                            .getOrDefault(emptyList())
                    } ?: emptyList()
                }
            }
            interleave(jobs.awaitAll(), limit = 80)
        }
    }

    private companion object {
        const val RESOLVE_TIMEOUT = 25_000L
        const val SEARCH_TIMEOUT = 12_000L
    }
}
