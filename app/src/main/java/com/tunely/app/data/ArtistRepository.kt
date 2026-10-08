package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ArtistRef(
    val name: String,
    val imageUrl: String? = null
)

data class ArtistAlbum(
    val id: String,
    val title: String,
    val artworkUrl: String? = null,
    val year: String? = null
)

/** Deezer's view of an artist: canonical id, picture and audience stats. */
data class DeezerArtistInfo(
    val id: Long,
    val name: String,
    val imageUrl: String? = null,
    val genre: String? = null,
    val stats: String? = null
)

/** Audius's view: the artist's own bio and uploads. */
data class AudiusArtistInfo(
    val name: String,
    val bio: String? = null,
    val imageUrl: String? = null,
    val tracks: List<Track> = emptyList()
)

/** iTunes's view: discography with hi-res covers and genre metadata. */
data class ITunesArtistInfo(
    val name: String,
    val imageUrl: String? = null,
    val genre: String? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList()
)

/** Everything an artist page needs, aggregated across the keyless providers. */
data class ArtistProfile(
    val name: String,
    val imageUrl: String? = null,
    val bio: String? = null,
    val genres: List<String> = emptyList(),
    val stats: String? = null,
    val topTracks: List<Track> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList(),
    val related: List<ArtistRef> = emptyList()
) {
    val isEmpty: Boolean get() = topTracks.isEmpty() && albums.isEmpty()
}

/**
 * Artist pages. Three providers cover the angles the others miss:
 *  - Deezer: canonical artist identity, picture, top tracks, albums, related artists,
 *  - iTunes: hi-res artwork + genre metadata + another take on the discography,
 *  - Audius: the artist's own words (bio) and full-length uploads.
 */
class ArtistRepository(
    private val audius: AudiusSource,
    private val deezer: DeezerSource,
    private val itunes: ITunesSource
) {

    private var cachedName: String? = null
    private var cached: ArtistProfile? = null

    suspend fun load(name: String, force: Boolean = false): ArtistProfile = withContext(Dispatchers.IO) {
        val key = name.trim().lowercase()
        if (!force && key == cachedName && cached != null) return@withContext cached!!

        val profile = coroutineScope {
            val dz = async {
                withTimeoutOrNull(9_000) { runCatching { deezer.artistLookup(name) }.getOrNull() }
            }
            val it = async {
                withTimeoutOrNull(9_000) { runCatching { itunes.artistLookup(name) }.getOrNull() }
            }
            val au = async {
                withTimeoutOrNull(9_000) { runCatching { audius.artistProfile(name) }.getOrNull() }
            }

            val dzArtist = dz.await()
            val itArtist = it.await()
            val auArtist = au.await()

            var dzTop: List<Track> = emptyList()
            var dzAlbums: List<ArtistAlbum> = emptyList()
            var dzRelated: List<DeezerArtistInfo> = emptyList()
            if (dzArtist != null) {
                val top = async {
                    withTimeoutOrNull(8_000) {
                        runCatching { deezer.artistTop(dzArtist.id, 12) }.getOrDefault(emptyList())
                    }.orEmpty()
                }
                val albums = async {
                    withTimeoutOrNull(8_000) {
                        runCatching { deezer.artistAlbums(dzArtist.id, 12) }.getOrDefault(emptyList())
                    }.orEmpty()
                }
                val related = async {
                    withTimeoutOrNull(8_000) {
                        runCatching { deezer.artistRelated(dzArtist.id, 10) }.getOrDefault(emptyList())
                    }.orEmpty()
                }
                dzTop = top.await()
                dzAlbums = albums.await()
                dzRelated = related.await()
            }

            // Popular list: Deezer top first (proper ranking), then iTunes &
            // Audius fills, deduped by signature so no row is a repeat.
            val seen = HashSet<String>()
            val top = (dzTop + itArtist?.tracks.orEmpty() + auArtist?.tracks.orEmpty())
                .filter { seen.add(TrackKey.of(it)) }
                .take(15)

            val albums = (itArtist?.albums.orEmpty() + dzAlbums)
                .distinctBy { TrackKey.titleOf(it.title) + "|" + it.year }
                .take(15)

            val related = dzRelated
                .map { ArtistRef(it.name, it.imageUrl) }
                .distinctBy { TrackKey.titleOf(it.name) }
                .filterNot { TrackKey.titleOf(it.name) == TrackKey.titleOf(name) }
                .take(12)

            ArtistProfile(
                name = name.trim(),
                imageUrl = dzArtist?.imageUrl ?: auArtist?.imageUrl ?: itArtist?.imageUrl,
                bio = auArtist?.bio,
                genres = buildList {
                    itArtist?.genre?.let { add(it) }
                    dzArtist?.genre?.let { if (it !in this) add(it) }
                },
                stats = dzArtist?.stats,
                topTracks = top,
                albums = albums,
                related = related
            )
        }

        cachedName = key
        cached = profile
        profile
    }

    /** Deezer's search result ranked like a human would: exact name, then biggest. */
    suspend fun resolveDeezer(name: String): DeezerArtistInfo? = deezer.artistLookup(name)
}
