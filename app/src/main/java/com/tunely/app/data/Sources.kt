package com.tunely.app.data

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            artworkUrl = ArtworkUrls.upgrade(thumbnails.maxByOrNull { it.width }?.url),
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

    /**
     * Audius ships every image as `{150x150, 480x480, 1000x1000, mirrors[]}`.
     * It used to decode as `Map<String, String>` — the `mirrors` array made the
     * whole response fail to parse and silently emptied every Audius list.
     */
    @kotlinx.serialization.Serializable
    private data class AudiusArt(
        @kotlinx.serialization.SerialName("150x150") val small: String? = null,
        @kotlinx.serialization.SerialName("480x480") val medium: String? = null,
        @kotlinx.serialization.SerialName("1000x1000") val large: String? = null
    ) {
        fun best(): String? = large ?: medium ?: small
    }

    @kotlinx.serialization.Serializable
    private data class AudiusUser(
        val id: String? = null,
        val name: String? = null,
        val handle: String? = null,
        val bio: String? = null,
        val profile_picture: AudiusArt? = null,
        val track_count: Int = 0
    )

    @kotlinx.serialization.Serializable
    private data class AudiusTrack(
        val id: String? = null,
        val title: String? = null,
        val user: AudiusUser? = null,
        val artwork: AudiusArt? = null,
        val duration: Long = 0,
        val genre: String? = null,
        val mood: String? = null,
        val bpm: Int? = null,
        val release_date: String? = null,
        val permalink: String? = null,
        val is_streamable: Boolean = true,
        val is_delete: Boolean = false
    )

    override suspend fun search(query: String, limit: Int): List<Track> =
        rawSearch(query, limit).mapNotNull { it.toTrack() }

    private suspend fun rawSearch(query: String, limit: Int): List<AudiusTrack> {
        val body = http.getBody(
            "https://api.audius.co/v1/tracks/search?query=${encode(query)}" +
                "&app_name=Tunely&limit=$limit"
        ) ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<Envelope<AudiusTrack>>(body)
        }.getOrNull() ?: return emptyList()
        return parsed.data.filter { it.is_streamable && !it.is_delete }
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

    /** Artist page material: bio, profile picture and the artist's uploads. */
    suspend fun artistProfile(name: String): AudiusArtistInfo? {
        val raw = rawSearch(name, 20)
        if (raw.isEmpty()) return null
        val norm = name.trim()
        val byUser = raw.filter { it.user?.name?.trim().equals(norm, ignoreCase = true) }
            .ifEmpty { raw.filter { TrackKey.titleOf(it.user?.name) == TrackKey.titleOf(name) } }
            .ifEmpty { raw }
        val user = byUser.firstOrNull()?.user ?: return null

        val moreTracks = user.id?.let { uid ->
            withTimeoutOrNull(6_000) {
                runCatching {
                    val body = http.getBody(
                        "https://api.audius.co/v1/users/$uid/tracks?app_name=Tunely&limit=20"
                    ) ?: return@runCatching emptyList<AudiusTrack>()
                    json.decodeFromString<Envelope<AudiusTrack>>(body).data
                        .filter { it.is_streamable && !it.is_delete }
                }.getOrDefault(emptyList())
            }
        }.orEmpty()

        val tracks = (byUser + moreTracks)
            .mapNotNull { it.toTrack() }
            .distinctBy { TrackKey.of(it) }
            .take(15)

        return AudiusArtistInfo(
            name = user.name ?: norm,
            bio = user.bio?.trim()?.takeIf { it.isNotBlank() },
            imageUrl = ArtworkUrls.upgrade(user.profile_picture?.best()),
            tracks = tracks
        )
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
        val art = ArtworkUrls.upgrade(artwork?.best())
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
    private data class Artist(
        val id: Long = 0,
        val name: String? = null,
        val picture_xl: String? = null,
        val picture_big: String? = null,
        val nb_fan: Long = 0,
        val nb_album: Int = 0
    )

    @kotlinx.serialization.Serializable
    private data class Album(
        val title: String? = null,
        val cover_xl: String? = null,
        val cover_big: String? = null,
        val release_date: String? = null
    )

    @kotlinx.serialization.Serializable
    private data class TrackList(val data: List<Item> = emptyList())

    @kotlinx.serialization.Serializable
    private data class ArtistList(val data: List<Artist> = emptyList())

    @kotlinx.serialization.Serializable
    private data class AlbumList(val data: List<AlbumItem> = emptyList())

    @kotlinx.serialization.Serializable
    private data class AlbumItem(
        val id: Long = 0,
        val title: String? = null,
        val cover_xl: String? = null,
        val cover_big: String? = null,
        val release_date: String? = null
    )

    override suspend fun search(query: String, limit: Int): List<Track> {
        val body = http.getBody("https://api.deezer.com/search?q=${encode(query)}&limit=$limit")
            ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<Search>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.data.mapNotNull { it.toTrack() }
    }

    /** Global chart — 30 s previews, but real popularity ranking. */
    suspend fun chart(limit: Int = 25): List<Track> =
        fetchTracks("https://api.deezer.com/chart/0/tracks?limit=$limit")

    /**
     * Artist lookup ranked like a human would pick: exact name match first,
     * then the biggest audience. (Search's own ranking happily returns tribute
     * acts before the real thing.)
     */
    suspend fun artistLookup(name: String): DeezerArtistInfo? {
        val norm = name.trim()
        val body = http.getBody(
            "https://api.deezer.com/search/artist?q=${encode(norm)}&limit=8"
        ) ?: return null
        val parsed = runCatching { json.decodeFromString<ArtistList>(body) }.getOrNull()
            ?: return null
        val best = parsed.data
            .filter { !it.name.isNullOrBlank() }
            .sortedWith(
                compareByDescending<Artist> { if (it.name.equals(norm, true)) 1 else 0 }
                    .thenByDescending { it.nb_fan }
            )
            .firstOrNull() ?: return null
        return best.toInfo()
    }

    suspend fun artistTop(id: Long, limit: Int = 12): List<Track> =
        fetchTracks("https://api.deezer.com/artist/$id/top?limit=$limit")

    /**
     * The smart radio mix: seed artist *plus* related artists in one draw —
     * the single best keyless source of "different songs, different people".
     */
    suspend fun artistRadio(id: Long, limit: Int = 25): List<Track> =
        fetchTracks("https://api.deezer.com/artist/$id/radio?limit=$limit")

    suspend fun artistRelated(id: Long, limit: Int = 10): List<DeezerArtistInfo> {
        val body = http.getBody("https://api.deezer.com/artist/$id/related?limit=$limit")
            ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<ArtistList>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.data.mapNotNull { it.toInfo() }
    }

    suspend fun artistAlbums(id: Long, limit: Int = 12): List<ArtistAlbum> {
        val body = http.getBody("https://api.deezer.com/artist/$id/albums?limit=$limit")
            ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<AlbumList>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.data.mapNotNull { item ->
            val title = item.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ArtistAlbum(
                id = item.id.toString(),
                title = title,
                artworkUrl = ArtworkUrls.upgrade(item.cover_xl ?: item.cover_big),
                year = item.release_date?.take(4)
            )
        }
    }

    private suspend fun fetchTracks(url: String): List<Track> {
        val body = http.getBody(url) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<TrackList>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.data.mapNotNull { it.toTrack() }
    }

    private fun Artist.toInfo(): DeezerArtistInfo? {
        val n = name?.takeIf { it.isNotBlank() } ?: return null
        val stats = buildList {
            if (nb_album > 0) add("$nb_album releases")
            if (nb_fan > 0) add("${formatFans(nb_fan)} fans")
        }.joinToString(" · ")
        return DeezerArtistInfo(
            id = id,
            name = n,
            imageUrl = ArtworkUrls.upgrade(picture_xl ?: picture_big),
            stats = stats.takeIf { it.isNotBlank() }?.plus(" on Deezer")
        )
    }

    private fun formatFans(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.0fK".format(n / 1_000.0)
        else -> n.toString()
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
            artworkUrl = ArtworkUrls.upgrade(album?.cover_xl ?: album?.cover_big),
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
        val wrapperType: String? = null,
        val kind: String? = null,
        val trackId: Long = 0,
        val artistId: Long = 0,
        val collectionId: Long = 0,
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

    /**
     * Top-songs RSS chart. Genre ids (14 Pop, 18 Hip-Hop, 21 Rock, 17 Dance,
     * 20 Alternative, 6 Country, 15 R&B, 12 Latin, 11 Jazz, 5 Classical) were
     * verified against the live feed. The feed even embeds the 30 s preview.
     */
    suspend fun topSongs(genreId: Int? = null, limit: Int = 25): List<Track> {
        val genrePart = genreId?.let { "genre=$it/" } ?: ""
        val safeLimit = limit.coerceIn(2, 100)
        val body = http.getBody(
            "https://itunes.apple.com/us/rss/topsongs/${genrePart}limit=$safeLimit/json"
        ) ?: return emptyList()
        return parseRssChart(body)
    }

    private fun parseRssChart(body: String): List<Track> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val feed = (root as? JsonObject)?.get("feed") as? JsonObject ?: return emptyList()
        val entries = when (val entry = feed["entry"]) {
            is JsonArray -> entry.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(entry)
            else -> emptyList()
        }
        return entries.mapNotNull { rssEntryToTrack(it) }
    }

    private fun rssEntryToTrack(e: JsonObject): Track? {
        fun prim(obj: JsonObject?, key: String): String? =
            (obj?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun objOf(key: String): JsonObject? = e[key] as? JsonObject

        fun labelOf(key: String): String? = prim(objOf(key), "label")

        val title = labelOf("im:name") ?: return null
        val artist = labelOf("im:artist") ?: "Unknown artist"

        val idAttrs = objOf("id")?.get("attributes") as? JsonObject
        val trackId = prim(idAttrs, "im:id") ?: TrackKey.of(title, artist)

        val art = (e["im:image"] as? JsonArray)
            ?.mapNotNull { img -> prim(img as? JsonObject, "label") }
            ?.lastOrNull()

        val album = prim(objOf("im:collection")?.get("im:name") as? JsonObject, "label")

        // The feed lists two links; the one with im:assetType "preview" is audio.
        val preview = (e["link"] as? JsonArray)?.firstNotNullOfOrNull { link ->
            val l = link as? JsonObject ?: return@firstNotNullOfOrNull null
            val attrs = l["attributes"] as? JsonObject ?: return@firstNotNullOfOrNull null
            if (prim(attrs, "im:assetType") == "preview") prim(attrs, "href") else null
        }

        val catAttrs = objOf("category")?.get("attributes") as? JsonObject
        val genre = prim(catAttrs, "term") ?: prim(catAttrs, "label")
        val year = labelOf("im:releaseDate")?.take(4)

        return Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = 30_000L,
            artworkUrl = ArtworkUrls.upgrade(art),
            genre = genre,
            releaseYear = year,
            source = SourceIds.ITUNES,
            sourceUrl = labelOf("id") ?: prim(objOf("link") as? JsonObject, "href"),
            streamUrl = preview
        )
    }

    /** Artist page half of iTunes: top songs + discography with hi-res covers. */
    suspend fun artistLookup(name: String): ITunesArtistInfo? {
        val searchBody = http.getBody(
            "https://itunes.apple.com/search?term=${encode(name)}&entity=musicArtist&limit=5"
        ) ?: return null
        val artists = runCatching { json.decodeFromString<Response>(searchBody) }.getOrNull()
            ?.results.orEmpty()
        val norm = name.trim()
        val best = artists.firstOrNull { it.artistName.equals(norm, true) }
            ?: artists.firstOrNull { it.artistName != null }
            ?: return null
        val artistId = best.artistId.takeIf { it != 0L } ?: return null

        val songsBody = http.getBody(
            "https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=25"
        )
        val albumsBody = http.getBody(
            "https://itunes.apple.com/lookup?id=$artistId&entity=album&limit=20"
        )

        val songs = songsBody
            ?.let { runCatching { json.decodeFromString<Response>(it) }.getOrNull() }
            ?.results.orEmpty()
            .mapNotNull { it.toTrack() }

        val albums = albumsBody
            ?.let { runCatching { json.decodeFromString<Response>(it) }.getOrNull() }
            ?.results.orEmpty()
            .filter { it.wrapperType == "collection" || it.collectionId != 0L }
            .mapNotNull { item ->
                val title = item.collectionName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ArtistAlbum(
                    id = item.collectionId.toString(),
                    title = title,
                    artworkUrl = ArtworkUrls.upgrade(item.artworkUrl100),
                    year = item.releaseDate?.take(4)
                )
            }

        return ITunesArtistInfo(
            name = best.artistName ?: norm,
            imageUrl = albums.firstOrNull()?.artworkUrl,
            genre = best.primaryGenreName,
            tracks = songs,
            albums = albums
        )
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
            artworkUrl = ArtworkUrls.upgrade(artworkUrl100),
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
