package com.tunely.app.data

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Everything the UI needs to know about a provider: a label, a colour family
 * for badges, whether it can play full tracks and whether it is on by default.
 */
data class SourceInfo(
    val id: String,
    val name: String,
    val tagline: String,
    val short: String,
    /** ARGB colour used for badges / gradients. */
    val color: Long,
    val defaultEnabled: Boolean = true,
    val previewOnly: Boolean = false,
    val live: Boolean = false,
    /** Shown in Settings → Sources. */
    val notice: String? = null
)

/** A provider that can search and resolve audio. */
interface StreamingSource {
    val info: SourceInfo
    val id: String get() = info.id
    suspend fun search(query: String, limit: Int = 25): List<Track>

    /**
     * Resolve a playable audio URL. Return null when the provider cannot.
     * [targetKbps] comes from the audio-quality setting (0 = best available).
     */
    suspend fun resolve(track: Track, targetKbps: Int = 0): String?
}

object Sources {
    val YOUTUBE = SourceInfo(
        id = SourceIds.YOUTUBE,
        name = "YouTube Music",
        tagline = "The whole catalogue, straight from YouTube",
        short = "YT",
        color = 0xFFFF4D57,
        notice = "Extraction based — may break when YouTube changes its API."
    )
    val SOUNDCLOUD = SourceInfo(
        id = SourceIds.SOUNDCLOUD,
        name = "SoundCloud",
        tagline = "Remixes, edits and independent artists",
        short = "SC",
        color = 0xFFFF7A18,
        notice = "Streams are resolved at play time, like YouTube."
    )
    val BANDCAMP = SourceInfo(
        id = SourceIds.BANDCAMP,
        name = "Bandcamp",
        tagline = "Direct-from-artist releases and hidden gems",
        short = "BC",
        color = 0xFF29B6F6,
        notice = "Plays the artist's own stream."
    )
    val AUDIUS = SourceInfo(
        id = SourceIds.AUDIUS,
        name = "Audius",
        tagline = "Decentralised, artist-owned streaming",
        short = "AU",
        color = 0xFF7B61FF,
        notice = "Full-length, keyless streams."
    )
    val DEEZER = SourceInfo(
        id = SourceIds.DEEZER,
        name = "Deezer",
        tagline = "Global catalogue previews",
        short = "DZ",
        color = 0xFFA238FF,
        defaultEnabled = false,
        previewOnly = true,
        notice = "30 second previews."
    )
    val ITUNES = SourceInfo(
        id = SourceIds.ITUNES,
        name = "iTunes",
        tagline = "Previews plus hi-res artwork and metadata",
        short = "IT",
        color = 0xFFFC3C44,
        defaultEnabled = false,
        previewOnly = true,
        notice = "30 second previews."
    )
    val RADIO = SourceInfo(
        id = SourceIds.RADIO,
        name = "Radio",
        tagline = "Thousands of live stations worldwide",
        short = "FM",
        color = 0xFF00C2A8,
        defaultEnabled = false,
        live = true,
        notice = "Live streams from radio-browser.info."
    )
    val PIPED = SourceInfo(
        id = SourceIds.PIPED,
        name = "Piped",
        tagline = "Privacy front-end, doubles as a YouTube fallback",
        short = "PP",
        color = 0xFF30D158,
        defaultEnabled = false
    )

    /** Order shown in Settings and used for search fan-out. */
    val all = listOf(YOUTUBE, SOUNDCLOUD, BANDCAMP, AUDIUS, RADIO, DEEZER, ITUNES, PIPED)

    fun byId(id: String?): SourceInfo? =
        all.firstOrNull { it.id == SourceIds.normalize(id) }
            ?: if (id == SourceIds.YOUTUBE_MUSIC) YOUTUBE else null
}

/** Shared plumbing for the three providers backed by NewPipeExtractor. */
abstract class NewPipeSource(protected val info0: SourceInfo) : StreamingSource {

    override val info: SourceInfo get() = info0

    protected abstract val service: StreamingService

    /** Content filters passed to the service's search handler. */
    protected open val contentFilters: List<String> = emptyList()

    override suspend fun search(query: String, limit: Int): List<Track> {
        val handler = service.searchQHFactory.fromQuery(query, contentFilters, "")
        val search = SearchInfo.getInfo(service, handler)
        return search.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.streamType != StreamType.NONE }
            .take(limit)
            .mapNotNull { it.toTrack(info.id) }
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? {
        val url = track.sourceUrl?.takeIf { it.startsWith("http") }
            ?: track.id.takeIf { it.startsWith("http") }
            ?: "${service.baseUrl}/watch?v=${track.id}"
        val streamInfo = StreamInfo.getInfo(service, url)
        return bestAudioUrl(streamInfo, targetKbps)
    }

    protected fun bestAudioUrl(stream: StreamInfo, targetKbps: Int = 0): String? {
        val candidates = stream.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
        val chosen = if (targetKbps > 0 && candidates.isNotEmpty()) {
            candidates.filter { it.averageBitrate <= targetKbps }.maxByOrNull { it.averageBitrate }
                ?: candidates.minByOrNull { it.averageBitrate }
        } else {
            candidates.maxByOrNull { it.averageBitrate }
        }
        chosen?.content?.let { return it }
        return stream.hlsUrl?.takeIf { it.isNotBlank() }
    }

    private fun StreamInfoItem.toTrack(sourceId: String): Track? {
        val cleanName = name?.trim().orEmpty()
        if (cleanName.isEmpty()) return null
        return Track(
            id = extractorId(url, sourceId),
            title = cleanName,
            artist = (uploaderName ?: "Unknown artist").removeSuffix(" - Topic").trim(),
            durationMs = duration.coerceAtLeast(0) * 1000,
            artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
            source = sourceId,
            sourceUrl = url
        )
    }

    /** YouTube uses the bare video id, other services keep their own url as id. */
    protected open fun extractorId(url: String, sourceId: String): String =
        if (sourceId == SourceIds.YOUTUBE) {
            url.substringAfter("v=", "").substringBefore("&").ifBlank { url }
        } else {
            url
        }
}

class YouTubeSource(private val music: Boolean = true) : NewPipeSource(Sources.YOUTUBE) {
    override val service: StreamingService get() = ServiceList.YouTube

    override val contentFilters: List<String> = if (music) {
        listOf(org.schabi.newpipe.extractor.services.youtube.linkHandler
            .YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
    } else {
        emptyList()
    }
}

class SoundCloudSource : NewPipeSource(Sources.SOUNDCLOUD) {
    override val service: StreamingService get() = ServiceList.SoundCloud
    override val contentFilters: List<String> = listOf("tracks")
}

class BandcampSource : NewPipeSource(Sources.BANDCAMP) {
    override val service: StreamingService get() = ServiceList.Bandcamp
}

/**
 * Audius — keyless public API over full-length, artist-uploaded audio.
 * Search hits include a ready-to-play stream URL so playback starts instantly.
 */
class AudiusSource(private val http: OkHttpClient) : StreamingSource {

    override val info = Sources.AUDIUS

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Envelope<T>(val data: List<T> = emptyList())

    @kotlinx.serialization.Serializable
    private data class AudiusUser(val name: String? = null, val handle: String? = null)

    @kotlinx.serialization.Serializable
    private data class AudiusArt(val url: String? = null)

    @kotlinx.serialization.Serializable
    private data class AudiusTrack(
        val id: String? = null,
        val title: String? = null,
        val user: AudiusUser? = null,
        val artwork: Map<String, String>? = null,
        val duration: Long = 0,
        val genre: String? = null,
        val release_date: String? = null,
        val permalink: String? = null,
        val is_streamable: Boolean = true,
        val is_delete: Boolean = false
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        val body = http.getBody(
            "https://api.audius.co/v1/tracks/search?query=${encode(query)}" +
                "&app_name=Tunely&limit=$limit"
        ) ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<Envelope<AudiusTrack>>(body)
        }.getOrNull() ?: return emptyList()
        return parsed.data.filter { it.is_streamable && !it.is_delete }.mapNotNull { it.toTrack() }
    }

    suspend fun trending(limit: Int = 24, genre: String? = null): List<Track> {
        val genrePart = genre?.let { "&genre=${encode(it)}" } ?: ""
        val body = http.getBody(
            "https://api.audius.co/v1/tracks/trending?app_name=Tunely&limit=$limit$genrePart"
        ) ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<Envelope<AudiusTrack>>(body)
        }.getOrNull() ?: return emptyList()
        return parsed.data.filter { it.is_streamable && !it.is_delete }.mapNotNull { it.toTrack() }
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? {
        track.streamUrl?.let { return it }
        val id = track.id.substringAfterLast('/')
        // no_redirect gives us the CDN url directly; if it is unsupported the
        // raw endpoint still works because ExoPlayer follows the 302.
        val body = http.getBody(
            "https://api.audius.co/v1/tracks/$id/stream?app_name=Tunely&no_redirect=true"
        )
        if (body != null) {
            val url = runCatching {
                json.decodeFromString<kotlinx.serialization.json.JsonObject>(body)
            }.getOrNull()
                ?.let { obj ->
                    val data = obj["data"] as? kotlinx.serialization.json.JsonObject
                    (data?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            if (!url.isNullOrBlank()) return url
        }
        return "https://api.audius.co/v1/tracks/$id/stream?app_name=Tunely"
    }

    private fun AudiusTrack.toTrack(): Track? {
        val trackId = id ?: return null
        val art = artwork?.entries?.maxByOrNull { it.key }?.value ?: artwork?.values?.first()
        return Track(
            id = trackId,
            title = title ?: "Untitled",
            artist = user?.name ?: user?.handle ?: "Audius artist",
            durationMs = duration * 1000,
            artworkUrl = art,
            genre = genre,
            releaseYear = release_date?.take(4),
            source = SourceIds.AUDIUS,
            sourceUrl = "https://audius.co/${user?.handle ?: "artist"}/${permalink ?: trackId}",
            streamUrl = "https://api.audius.co/v1/tracks/$trackId/stream?app_name=Tunely"
        )
    }
}

/** Internet radio: infinite live stations from the community-run radio-browser.info. */
class RadioSource(private val http: OkHttpClient) : StreamingSource {

    override val info = Sources.RADIO

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Station(
        val stationuuid: String = "",
        val name: String = "",
        val url_resolved: String = "",
        val url: String = "",
        val homepage: String = "",
        val favicon: String = "",
        val tags: String = "",
        val country: String = "",
        val codec: String = "",
        val bitrate: Int = 0,
        val votes: Int = 0
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        val url = if (q.isEmpty()) {
            "https://all.api.radio-browser.info/json/stations/search" +
                "?hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        } else {
            "https://all.api.radio-browser.info/json/stations/search" +
                "?name=${encode(q)}&hidebroken=true&order=votes&reverse=true&limit=$limit"
        }
        return fetch(url)
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? = track.streamUrl

    private suspend fun fetch(url: String): List<Track> {
        val body = http.getBody(url) ?: return emptyList()
        val stations = runCatching { json.decodeFromString<List<Station>>(body) }.getOrNull()
            ?: return emptyList()
        return stations
            .filter { it.url_resolved.isNotBlank() || it.url.isNotBlank() }
            .filter { it.name.isNotBlank() }
            .map { st ->
                Track(
                    id = st.stationuuid.ifBlank { st.url_resolved },
                    title = st.name.trim(),
                    artist = st.country.ifBlank { "Live radio" },
                    album = st.tags.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                    durationMs = 0L,
                    artworkUrl = st.favicon.takeIf { it.startsWith("http") },
                    genre = st.tags.split(",").take(3).joinToString(", ") { it.trim() }
                        .takeIf { it.isNotBlank() },
                    source = SourceIds.RADIO,
                    sourceUrl = st.homepage.takeIf { it.startsWith("http") },
                    streamUrl = st.url_resolved.ifBlank { st.url },
                    live = true
                )
            }
    }
}

/** Deezer — huge catalogue, keyless, 30 second previews. */
class DeezerSource(private val http: OkHttpClient) : StreamingSource {

    override val info = Sources.DEEZER

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Search(val data: List<Item> = emptyList())

    @kotlinx.serialization.Serializable
    private data class Item(
        val id: Long = 0,
        val title: String? = null,
        val title_short: String? = null,
        val duration: Long = 0,
        val preview: String? = null,
        val artist: Artist? = null,
        val album: Album? = null,
        val explicit_lyrics: Boolean = false
    )

    @kotlinx.serialization.Serializable
    private data class Artist(val name: String? = null)

    @kotlinx.serialization.Serializable
    private data class Album(
        val title: String? = null,
        val cover_xl: String? = null,
        val cover_big: String? = null
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        val body = http.getBody("https://api.deezer.com/search?q=${encode(query)}&limit=$limit")
            ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<Search>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.data.mapNotNull { it.toTrack() }
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? = track.streamUrl

    private fun Item.toTrack(): Track? {
        val preview = preview ?: return null
        return Track(
            id = id.toString(),
            title = title_short ?: title ?: return null,
            artist = artist?.name ?: "Unknown artist",
            album = album?.title,
            durationMs = duration * 1000,
            artworkUrl = album?.cover_xl ?: album?.cover_big,
            explicit = explicit_lyrics,
            source = SourceIds.DEEZER,
            streamUrl = preview
        )
    }
}

/** iTunes Search — previews plus the hi-res artwork Tunely uses for enrichment. */
class ITunesSource(private val http: OkHttpClient) : StreamingSource {

    override val info = Sources.ITUNES

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Response(val results: List<Item> = emptyList())

    @kotlinx.serialization.Serializable
    private data class Item(
        val trackId: Long = 0,
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val artworkUrl100: String? = null,
        val previewUrl: String? = null,
        val primaryGenreName: String? = null,
        val releaseDate: String? = null,
        val trackExplicitness: String? = null,
        val trackTimeMillis: Long = 0
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        val body = http.getBody(
            "https://itunes.apple.com/search?term=${encode(query)}&entity=song&limit=$limit"
        ) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<Response>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.results.mapNotNull { it.toTrack() }
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? = track.streamUrl

    private fun Item.toTrack(): Track? {
        val preview = previewUrl ?: return null
        return Track(
            id = trackId.toString(),
            title = trackName ?: return null,
            artist = artistName ?: "Unknown artist",
            album = collectionName,
            durationMs = trackTimeMillis,
            artworkUrl = artworkUrl100?.replace("100x100bb", "1200x1200bb"),
            genre = primaryGenreName,
            releaseYear = releaseDate?.take(4),
            explicit = trackExplicitness == "explicit",
            source = SourceIds.ITUNES,
            streamUrl = preview
        )
    }
}

/** Piped — privacy front-end; also the automatic fallback for YouTube tracks. */
class PipedSource(private val http: OkHttpClient) : StreamingSource {

    override val info = Sources.PIPED

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Search(val items: List<Item> = emptyList())

    @kotlinx.serialization.Serializable
    private data class Item(
        val url: String? = null,
        val title: String? = null,
        val uploaderName: String? = null,
        val duration: Long = 0,
        val thumbnail: String? = null,
        val type: String? = null
    )

    @kotlinx.serialization.Serializable
    private data class Stream(val audioStreams: List<Audio> = emptyList())

    @kotlinx.serialization.Serializable
    private data class Audio(val url: String? = null, val bitrate: Int = 0, val mimeType: String? = null)

    private val hosts = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.yt",
        "https://pipedapi.adminforge.de"
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        for (host in hosts) {
            val body = http.getBody("$host/search?q=${encode(query)}&filter=music_songs")
                ?: continue
            val parsed = runCatching { json.decodeFromString<Search>(body) }.getOrNull()
                ?: continue
            val tracks = parsed.items
                .filter { it.type == "stream" && it.duration > 0 }
                .take(limit)
                .mapNotNull { it.toTrack() }
            if (tracks.isNotEmpty()) return tracks
        }
        return emptyList()
    }

    override suspend fun resolve(track: Track, targetKbps: Int): String? {
        val id = track.id.removePrefix("/watch?v=").substringBefore("&")
        for (host in hosts) {
            val body = http.getBody("$host/streams/$id") ?: continue
            val parsed = runCatching { json.decodeFromString<Stream>(body) }.getOrNull() ?: continue
            val best = parsed.audioStreams
                .filter { !it.url.isNullOrBlank() }
                .maxByOrNull { it.bitrate }
                ?.url
            if (!best.isNullOrBlank()) return best
        }
        return null
    }

    private fun Item.toTrack(): Track? {
        val videoId = url?.removePrefix("/watch?v=")?.substringBefore("&") ?: return null
        return Track(
            id = videoId,
            title = title ?: return null,
            artist = uploaderName ?: "Unknown artist",
            durationMs = duration * 1000,
            artworkUrl = thumbnail,
            source = SourceIds.PIPED
        )
    }
}
