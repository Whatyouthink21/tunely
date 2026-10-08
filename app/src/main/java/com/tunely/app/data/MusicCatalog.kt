package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Metadata enrichment: album, genre, year and hi-res artwork for tracks that
 * came from an extractor with thin metadata (YouTube, SoundCloud, Bandcamp,
 * Piped). Everything in here is keyless and cheap, so Home and search keep
 * working even when YouTube extraction is having a bad day.
 */
class MusicCatalog(
    private val deezer: DeezerSource,
    private val itunes: ITunesSource
) {

    /**
     * Fill in album / genre / year / hi-res artwork. Runs bounded-parallel so a
     * page of results never blocks the UI.
     */
    suspend fun enrichAll(tracks: List<Track>, limit: Int = 24): List<Track> =
        withContext(Dispatchers.IO) {
            val semaphore = Semaphore(5)
            coroutineScope {
                val jobs = tracks.mapIndexed { index, track ->
                    async {
                        if (index >= limit) return@async track
                        withTimeoutOrNull(6_000) { semaphore.withPermit { enrich(track) } } ?: track
                    }
                }
                jobs.awaitAll()
            }
        }

    suspend fun enrich(track: Track): Track {
        if (track.source in Track.PREVIEW_SOURCES) return track
        if (!needsEnrichment(track)) return track.copy(artworkUrl = ArtworkUrls.upgrade(track.artworkUrl))

        val query = "${track.artist} ${track.title}".trim()

        // A metadata match must agree on BOTH title and artist. Matching on
        // either alone is how albums, years and covers ended up belonging to a
        // completely different song.
        val itunesMatch = runCatching { itunes.search(query, 5) }.getOrNull()
            ?.firstOrNull { similar(it.title, track.title) && similar(it.artist, track.artist) }
        if (itunesMatch != null) {
            return track.copy(
                album = track.album ?: itunesMatch.album,
                artworkUrl = ArtworkUrls.upgrade(itunesMatch.artworkUrl ?: track.artworkUrl),
                genre = track.genre ?: itunesMatch.genre,
                releaseYear = track.releaseYear ?: itunesMatch.releaseYear,
                explicit = track.explicit || itunesMatch.explicit
            )
        }
        val deezerMatch = runCatching { deezer.search(query, 5) }.getOrNull()
            ?.firstOrNull { similar(it.title, track.title) && similar(it.artist, track.artist) }
        return if (deezerMatch != null) {
            track.copy(
                album = track.album ?: deezerMatch.album,
                artworkUrl = ArtworkUrls.upgrade(deezerMatch.artworkUrl ?: track.artworkUrl),
                genre = track.genre ?: deezerMatch.genre,
                releaseYear = track.releaseYear ?: deezerMatch.releaseYear,
                explicit = track.explicit || deezerMatch.explicit
            )
        } else {
            track.copy(artworkUrl = ArtworkUrls.upgrade(track.artworkUrl))
        }
    }

    private fun needsEnrichment(track: Track): Boolean {
        if (track.source !in ENRICHABLE) return false
        return track.album == null || track.artworkUrl == null ||
            track.genre == null || track.releaseYear == null
    }

    private val ENRICHABLE = setOf(
        SourceIds.YOUTUBE, SourceIds.SOUNDCLOUD, SourceIds.BANDCAMP, SourceIds.PIPED
    )

    private fun similar(a: String?, b: String?): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    private fun normalize(s: String?) =
        (s ?: "").lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("[^a-z0-9]"), "")
}
