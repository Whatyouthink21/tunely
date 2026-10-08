package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** A horizontally scrolling group of tracks on the Home screen. */
data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>
)

/**
 * Discovery + metadata. Everything in here is keyless and cheap, so Home and
 * search enrichment keep working even when YouTube extraction is having a bad day.
 */
class MusicCatalog(
    private val http: OkHttpClient,
    private val audius: AudiusSource,
    private val deezer: DeezerSource,
    private val itunes: ITunesSource,
    private val radio: RadioSource
) {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class DeezerChart(val tracks: ChartTracks = ChartTracks())

    @kotlinx.serialization.Serializable
    private data class ChartTracks(val data: List<ChartItem> = emptyList())

    @kotlinx.serialization.Serializable
    private data class ChartItem(
        val id: Long = 0,
        val title: String? = null,
        val title_short: String? = null,
        val duration: Long = 0,
        val preview: String? = null,
        val explicit_lyrics: Boolean = false,
        val artist: ChartArtist? = null,
        val album: ChartAlbum? = null
    )

    @kotlinx.serialization.Serializable
    private data class ChartArtist(val name: String? = null)

    @kotlinx.serialization.Serializable
    private data class ChartAlbum(
        val title: String? = null,
        val cover_xl: String? = null,
        val cover_big: String? = null
    )

    /** Home shelves. [seedArtists] comes from the user's listening history. */
    suspend fun shelves(seedArtists: List<String> = emptyList()): List<Shelf> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                // Every shelf is independent: one provider being down must never
                // blank the whole Home screen.
                fun shelf(block: suspend () -> List<Track>) = async {
                    withTimeoutOrNull(10_000) { runCatching { block() }.getOrDefault(emptyList()) }
                        ?: emptyList()
                }
                val trending = shelf { audius.trending(24) }
                val electronic = shelf { audius.trending(18, "Electronic") }
                val hipHop = shelf { audius.trending(18, "Hip-Hop/Rap") }
                val chart = shelf { deezerChart(24) }
                val stations = shelf { radio.search("", 20) }
                val forYou = shelf {
                    seedArtists.take(2).flatMap { artist ->
                        withTimeoutOrNull(8_000) { audius.search(artist, 8) }.orEmpty()
                    }.distinctBy { it.uid }
                }

                buildList {
                    trending.await().takeIf { it.isNotEmpty() }?.let {
                        add(Shelf("trending", "Trending on Audius", "Fresh uploads, full length", it))
                    }
                    forYou.await().takeIf { it.size > 3 }?.let {
                        add(Shelf("foryou", "Because you listen", "Picked up from your history", it))
                    }
                    chart.await().takeIf { it.isNotEmpty() }?.let {
                        add(Shelf("charts", "Global charts", "Top 30 second previews on Deezer", it))
                    }
                    electronic.await().takeIf { it.isNotEmpty() }?.let {
                        add(Shelf("electronic", "Electronic", "Synths, house and everything in between", it))
                    }
                    hipHop.await().takeIf { it.isNotEmpty() }?.let {
                        add(Shelf("hiphop", "Hip-Hop & Rap", "Heavy rotation on Audius", it))
                    }
                    stations.await().takeIf { it.isNotEmpty() }?.let {
                        add(Shelf("radio", "Live radio", "Always on, always live", it))
                    }
                }
            }
        }

    private suspend fun deezerChart(limit: Int): List<Track> {
        val body = http.getBody("https://api.deezer.com/chart/0/tracks?limit=$limit")
            ?: return emptyList()
        val chart = runCatching { json.decodeFromString<DeezerChart>(body) }.getOrNull()
            ?: return emptyList()
        return chart.tracks.data.mapNotNull { item ->
            val preview = item.preview ?: return@mapNotNull null
            Track(
                id = if (item.id != 0L) item.id.toString()
                else "${item.artist?.name}-${item.title}".hashCode().toString(16),
                title = item.title_short ?: item.title ?: return@mapNotNull null,
                artist = item.artist?.name ?: "Unknown artist",
                album = item.album?.title,
                durationMs = item.duration * 1000,
                artworkUrl = item.album?.cover_xl ?: item.album?.cover_big,
                explicit = item.explicit_lyrics,
                source = SourceIds.DEEZER,
                streamUrl = preview
            )
        }
    }

    /**
     * Fill in album / genre / year / hi-res artwork for tracks that came from an
     * extractor. Runs bounded-parallel so a page of results never blocks the UI.
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
        if (!needsEnrichment(track)) return track

        val query = "${track.artist} ${track.title}".trim()
        val itunesMatch = runCatching { itunes.search(query, 5) }.getOrNull()
            ?.firstOrNull { similar(it.title, track.title) || similar(it.artist, track.artist) }
        if (itunesMatch != null) {
            return track.copy(
                album = track.album ?: itunesMatch.album,
                artworkUrl = itunesMatch.artworkUrl ?: track.artworkUrl,
                genre = track.genre ?: itunesMatch.genre,
                releaseYear = track.releaseYear ?: itunesMatch.releaseYear,
                explicit = track.explicit || itunesMatch.explicit
            )
        }
        val deezerMatch = runCatching { deezer.search(query, 5) }.getOrNull()
            ?.firstOrNull { similar(it.title, track.title) || similar(it.artist, track.artist) }
        return if (deezerMatch != null) {
            track.copy(
                album = track.album ?: deezerMatch.album,
                artworkUrl = track.artworkUrl ?: deezerMatch.artworkUrl,
                explicit = track.explicit || deezerMatch.explicit
            )
        } else {
            track
        }
    }

    private fun needsEnrichment(track: Track): Boolean =
        track.source in setOf(SourceIds.YOUTUBE, SourceIds.SOUNDCLOUD, SourceIds.BANDCAMP, SourceIds.PIPED) &&
            (track.album == null || track.artworkUrl == null)

    private fun similar(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    private fun normalize(s: String) =
        s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("[^a-z0-9]"), "")
}
