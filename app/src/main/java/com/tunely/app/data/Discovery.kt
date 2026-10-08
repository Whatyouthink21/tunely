package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** A track with its identity folded flat — the unit of every dedupe decision. */
object TrackKey {
    /**
     * "danza kuduro" from "Danza Kuduro (Official Video) [feat. ...]".
     * Same song on YouTube, Deezer and iTunes collapses to one key.
     */
    fun of(track: Track): String = norm(track.title) + "|" + norm(track.artist)

    fun of(title: String?, artist: String?): String = norm(title) + "|" + norm(artist)

    fun titleOf(title: String?): String = norm(title)

    private fun norm(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("\\b(official|video|audio|lyrics?|lyric video|visualizer|hd|hq|remastered|explicit|full album|music video)\\b"), " ")
            .replace(Regex("\\b(feat|ft|featuring)\\.?\\b.*"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

/** Everything we know about what the user plays — the recommendation seed. */
data class ListeningProfile(
    val topArtists: List<String> = emptyList(),
    val topCategories: List<BrowseCategory> = emptyList(),
    val topMoods: List<Mood> = emptyList(),
    /** Signature keys of everything already heard or saved. */
    val knownKeys: Set<String> = emptySet(),
    val seedTracks: List<Track> = emptyList()
) {
    val isEmpty: Boolean get() = seedTracks.isEmpty() && topArtists.isEmpty()
}

/** One horizontally scrolling row on Home / Explore. */
data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>
)

/** A numbered chart list (Explore → Charts). */
data class ChartList(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>
)

/** One open category page (mood or genre). */
data class CategoryPage(
    val category: BrowseCategory,
    val tracks: List<Track>
)

/**
 * The discovery engine.
 *
 * The old "because you listen" shelf simply searched the seed artist again, so
 * every "similar" row repeated the same handful of songs. The algorithm here is
 * modelled on how the big services actually do it with metadata alone:
 *
 *  1. **Artist radio** — Deezer's `/artist/{id}/radio` returns a smart mix of
 *     the seed artist *and* related artists (verified: Daft Punk radio pulls in
 *     Madcon, Metronomy…). It is the single best "different songs, different
 *     artists" source that is keyless.
 *  2. **Related-artist fan-out** — `/artist/{id}/related` tops, so a shelf is
 *     never one radio draw.
 *  3. **Hard diversity caps** — the seed artist never dominates its own
 *     "similar" row, no artist repeats more than twice, and no song you have
 *     already heard or saved is ever recommended back to you.
 *  4. **Mood spreading** — picks are bucketed by inferred mood and round-robin
 *     interleaved, so a recommendation row is not 20 copies of the same vibe.
 */
class DiscoveryService(
    private val http: OkHttpClient,
    private val audius: AudiusSource,
    private val deezer: DeezerSource,
    private val itunes: ITunesSource
) {

    // ── Profile ────────────────────────────────────────────────────────────

    fun profile(
        history: List<HistoryEntry>,
        favorites: List<Track>
    ): ListeningProfile {
        val seedTracks = (history.map { it.toTrack() } + favorites)
            .distinctBy { TrackKey.of(it) }
        val artistFreq = LinkedHashMap<String, Int>()
        seedTracks.forEach { t ->
            if (t.artist.isNotBlank()) artistFreq[t.artist] = (artistFreq[t.artist] ?: 0) + 1
        }
        val topArtists = artistFreq.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.isNotBlank() && !it.equals("unknown artist", ignoreCase = true) }
            .distinct()
            .take(6)

        val genreFreq = LinkedHashMap<String, Int>()
        seedTracks.forEach { t ->
            t.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { g ->
                genreFreq[g] = (genreFreq[g] ?: 0) + 1
            }
        }
        val topCategories = genreFreq.entries
            .sortedByDescending { it.value }
            .mapNotNull { Categories.matchGenre(it.key) }
            .distinctBy { it.id }
            .take(3)

        return ListeningProfile(
            topArtists = topArtists,
            topCategories = topCategories,
            topMoods = MoodClassifier.inferAll(seedTracks.take(30)).take(3),
            knownKeys = seedTracks.map { TrackKey.of(it) }.toSet(),
            seedTracks = seedTracks.take(20)
        )
    }

    // ── Similar tracks ─────────────────────────────────────────────────────

    /**
     * Tracks similar to [seed] — deliberately *different* songs by
     * *different* artists. [exclude] holds signature keys already known.
     */
    suspend fun similarTracks(
        seed: Track,
        limit: Int = 12,
        exclude: Set<String> = emptySet()
    ): List<Track> = withContext(Dispatchers.IO) {
        val excluded = HashSet(exclude) + TrackKey.of(seed)
        val pool = mutableListOf<Track>()

        val artist = deezer.artistLookup(seed.artist)

        // 1. Artist radio: seed + similar artists in one draw. Best diversity.
        if (artist != null) {
            withTimeoutOrNull(8_000) {
                runCatching { deezer.artistRadio(artist.id, limit * 3) }.getOrDefault(emptyList<Track>())
            }?.let { pool += it }
        }

        // 2. Related artists' top tracks — same neighbourhood, more names.
        if (artist != null && pool.size < limit * 2) {
            val related = withTimeoutOrNull(8_000) {
                runCatching { deezer.artistRelated(artist.id, 6) }.getOrDefault(emptyList<DeezerArtistInfo>())
            }.orEmpty()
            related.take(4).map { rel ->
                async {
                    withTimeoutOrNull(6_000) {
                        runCatching { deezer.artistTop(rel.id, 4) }.getOrDefault(emptyList<Track>())
                    }.orEmpty()
                }
            }.awaitAll().forEach { pool += it }
        }

        // 3. iTunes as a cross-catalogue safety net (and hi-res metadata).
        if (pool.size < limit) {
            val query = listOfNotNull(seed.artist, seed.genre?.substringBefore(","), seed.title)
                .joinToString(" ")
            withTimeoutOrNull(6_000) {
                runCatching { itunes.search(query, 8) }.getOrDefault(emptyList<Track>())
            }?.let { pool += it }
        }

        diversify(pool, limit, excluded, seedArtist = seed.artist)
    }

    // ── Home feed ──────────────────────────────────────────────────────────

    /** Personalised Home shelves — the Spotify-style "made for you" page. */
    suspend fun homeShelves(profile: ListeningProfile): List<Shelf> =
        coroutineScope {
            val jobs = mutableListOf<DeferredShelf>()

            // Because you listened to <artist>: related music, never that artist's own songs.
            profile.topArtists.take(2).forEachIndexed { i, artist ->
                jobs += DeferredShelf("similar-$i") {
                    val seed = profile.seedTracks.firstOrNull {
                        it.artist.equals(artist, ignoreCase = true)
                    } ?: Track(id = "seed-$i", title = "top tracks", artist = artist)
                    val tracks = similarTracks(seed, 12, profile.knownKeys)
                    Shelf(
                        id = "similar-$i",
                        title = "Because you listened to $artist",
                        subtitle = "Different songs, related artists — never repeats",
                        tracks = tracks
                    )
                }
            }

            // Because you've been playing <category>.
            profile.topCategories.take(2).forEachIndexed { i, cat ->
                jobs += DeferredShelf("category-$i") {
                    val tracks = categoryTracks(cat, exclude = profile.knownKeys, limit = 12)
                    Shelf(
                        id = "category-$i",
                        title = "More ${cat.label}",
                        subtitle = "Since it's been in heavy rotation",
                        tracks = tracks
                    )
                }
            }

            // Mood mixes derived from the listening mood profile.
            profile.topMoods.take(2).forEachIndexed { i, mood ->
                val cat = Categories.moods.firstOrNull { it.mood == mood }
                if (cat != null) {
                    jobs += DeferredShelf("mood-$i") {
                        val tracks = categoryTracks(cat, exclude = profile.knownKeys, limit = 12)
                        Shelf(
                            id = "mood-$i",
                            title = "Your ${cat.label} mix",
                            subtitle = cat.tagline,
                            tracks = tracks
                        )
                    }
                }
            }

            // Always-on discovery, one shelf per provider family.
            jobs += DeferredShelf("trending") {
                Shelf("trending", "Trending now", "Fresh on Audius", audius.trending(18))
            }
            jobs += DeferredShelf("chart") {
                Shelf("chart", "Global charts", "Top of the iTunes charts", topSongs(limit = 18))
            }
            jobs += DeferredShelf("radio") {
                Shelf("radio", "Live radio", "Always on, always live", emptyList<Track>())
            }

            jobs.map { job ->
                async {
                    withTimeoutOrNull(12_000) {
                        runCatching { job.build() }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
                .filter { it.tracks.size >= 3 }
        }

    private class DeferredShelf(val id: String, val build: suspend () -> Shelf)

    // ── Daily mixes ────────────────────────────────────────────────────────

    /**
     * Spotify-style Daily Mixes: each one is seeded from a different slice of
     * the profile (artist / mood / category), and every mix excludes the tracks
     * used by the previous ones so no two mixes feel like the same playlist.
     */
    suspend fun dailyMixes(profile: ListeningProfile, count: Int = 4): List<Shelf> {
        val shared = HashSet<String>()
        val mixes = mutableListOf<Shelf>()

        val seeds = mutableListOf<Pair<String, suspend () -> List<Track>>>()
        profile.topArtists.take(2).forEach { artist ->
            seeds += ("$artist & more") to suspend {
                val seed = profile.seedTracks.firstOrNull { it.artist.equals(artist, true) }
                    ?: Track(id = "a-$artist", title = "hits", artist = artist)
                similarTracks(seed, 14, profile.knownKeys + shared)
            }
        }
        profile.topCategories.take(2).forEach { cat ->
            seeds += ("${cat.label} mix") to suspend { categoryTracks(cat, profile.knownKeys + shared, 14) }
        }
        // Mixes for fresh ears: moods, or straight categories on a new profile.
        val filler = if (profile.topMoods.isNotEmpty()) {
            Categories.moods.filter { it.mood in profile.topMoods }
        } else {
            Categories.genres.shuffled().take(2)
        }
        filler.forEach { cat ->
            seeds += ("${cat.label} mix") to suspend { categoryTracks(cat, profile.knownKeys + shared, 14) }
        }

        seeds.take(count).forEachIndexed { i, (label, loader) ->
            val tracks = withTimeoutOrNull(12_000) { runCatching { loader() }.getOrNull() }
                .orEmpty()
                .filter { shared.add(TrackKey.of(it)) }
                .take(14)
            if (tracks.size >= 5) {
                mixes += Shelf(
                    id = "mix-${i + 1}",
                    title = "Daily Mix ${i + 1}",
                    subtitle = label,
                    tracks = tracks
                )
            }
        }
        return mixes
    }

    // ── Categories & moods ─────────────────────────────────────────────────

    /**
     * Tracks for one mood/genre bucket: chart hits first (they anchor the vibe),
     * then full-length discoveries, with the usual diversity caps.
     */
    suspend fun categoryTracks(
        category: BrowseCategory,
        exclude: Set<String> = emptySet(),
        limit: Int = 20
    ): List<Track> = withContext(Dispatchers.IO) {
        val pool = mutableListOf<Track>()
        coroutineScope {
            val chart = async {
                withTimeoutOrNull(8_000) {
                    runCatching { topSongs(category.itunesGenreId, limit) }.getOrDefault(emptyList<Track>())
                }.orEmpty()
            }
            val trending = async {
                withTimeoutOrNull(8_000) {
                    category.audiusGenre?.let {
                        runCatching { audius.trending(limit, it) }.getOrDefault(emptyList<Track>())
                    }.orEmpty()
                }.orEmpty()
            }
            val search = async {
                withTimeoutOrNull(8_000) {
                    category.query?.let {
                        runCatching { deezer.search(it, limit) }.getOrDefault(emptyList<Track>())
                    }.orEmpty()
                }.orEmpty()
            }
            pool += chart.await()
            pool += trending.await()
            pool += search.await()
        }
        diversify(pool, limit, exclude, seedArtist = null)
    }

    // ── Charts ─────────────────────────────────────────────────────────────

    /** Everything Explore → Charts shows, fetched in parallel. */
    suspend fun charts(): List<ChartList> = coroutineScope {
        val global = async {
            withTimeoutOrNull(10_000) {
                runCatching { topSongs(null, 50) }.getOrDefault(emptyList<Track>())
            }.orEmpty()
        }
        val deezerTop = async {
            withTimeoutOrNull(10_000) {
                runCatching { deezer.chart(25) }.getOrDefault(emptyList<Track>())
            }.orEmpty()
        }
        val audiusTop = async {
            withTimeoutOrNull(10_000) {
                runCatching { audius.trending(25) }.getOrDefault(emptyList<Track>())
            }.orEmpty()
        }
        val hiphop = async {
            withTimeoutOrNull(10_000) {
                runCatching { topSongs(18, 25) }.getOrDefault(emptyList<Track>())
            }.orEmpty()
        }
        val dance = async {
            withTimeoutOrNull(10_000) {
                runCatching { topSongs(17, 25) }.getOrDefault(emptyList<Track>())
            }.orEmpty()
        }
        buildList {
            global.await().takeIf { it.size >= 5 }?.let {
                add(ChartList("top50", "Top 50: Global", "The world's most played songs", it))
            }
            deezerTop.await().takeIf { it.size >= 5 }?.let {
                add(ChartList("deezer", "Deezer Charts", "30 second previews · global", it))
            }
            audiusTop.await().takeIf { it.size >= 5 }?.let {
                add(ChartList("audius", "Trending on Audius", "Full-length, artist-owned", it))
            }
            hiphop.await().takeIf { it.size >= 5 }?.let {
                add(ChartList("hiphop", "Top Hip-Hop/Rap", "iTunes genre chart", it))
            }
            dance.await().takeIf { it.size >= 5 }?.let {
                add(ChartList("dance", "Top Dance", "iTunes genre chart", it))
            }
        }
    }

    /** iTunes top-songs RSS. Genre ids are pre-verified against the live feed. */
    suspend fun topSongs(genreId: Int? = null, limit: Int = 25): List<Track> =
        withContext(Dispatchers.IO) {
            runCatching { itunes.topSongs(genreId, limit) }.getOrDefault(emptyList<Track>())
        }

    // ── Diversity engine ───────────────────────────────────────────────────

    /**
     * Turn a raw pool into a list that never recommends you something you know,
     * never repeats an artist to death, and spreads the moods around.
     */
    private fun diversify(
        pool: List<Track>,
        limit: Int,
        exclude: Set<String>,
        seedArtist: String?
    ): List<Track> {
        val seenKeys = HashSet<String>()
        val perArtist = HashMap<String, Int>()
        val seedNorm = seedArtist?.let { TrackKey.titleOf(it) }

        // Bucket by mood so the final list can be interleaved across vibes.
        val buckets = LinkedHashMap<Mood?, MutableList<Track>>()
        pool.forEach { t ->
            val key = TrackKey.of(t)
            if (key in exclude || !seenKeys.add(key)) return@forEach
            if (t.artist.isBlank()) return@forEach
            // The seed artist is banned outright — this shelf is for discovery.
            if (seedNorm != null && TrackKey.titleOf(t.artist) == seedNorm) return@forEach
            val artistKey = TrackKey.titleOf(t.artist)
            val count = (perArtist[artistKey] ?: 0) + 1
            perArtist[artistKey] = count
            if (count > 2) return@forEach
            buckets.getOrPut(MoodClassifier.infer(t)) { mutableListOf() }.add(t)
        }

        val result = mutableListOf<Track>()
        val rounds = buckets.values.maxOfOrNull { it.size } ?: 0
        for (i in 0 until rounds) {
            for (bucket in buckets.values) {
                bucket.getOrNull(i)?.let { result += it }
                if (result.size >= limit) return result
            }
        }
        return result
    }
}
