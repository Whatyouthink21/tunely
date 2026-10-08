package com.tunely.app.data

/**
 * A browsable bucket of music — a mood ("Chill") or a genre ("Hip-Hop").
 * Each one knows how to feed itself from the keyless providers:
 *  - [itunesGenreId] powers the iTunes top-songs RSS chart for that genre,
 *  - [audiusGenre] powers Audius trending-by-genre (full-length streams),
 *  - [query] powers Deezer/iTunes search as a fallback.
 */
data class BrowseCategory(
    val id: String,
    val label: String,
    val tagline: String,
    val itunesGenreId: Int? = null,
    val audiusGenre: String? = null,
    val query: String? = null,
    /** Which mood this bucket belongs to — used for badge text and mixing. */
    val mood: Mood? = null,
    /** ARGB gradient pair for the category cards. */
    val colors: List<Long>
) {
    val isMood: Boolean get() = mood != null && itunesGenreId == null
}

/** The vibes used for mood mixes and mood-aware recommendations. */
enum class Mood(val id: String, val label: String, val tagline: String) {
    CHILL("mood_chill", "Chill", "Slow down and float"),
    ENERGY("mood_energy", "Energy", "Big hooks, bigger speakers"),
    FOCUS("mood_focus", "Focus", "Keep the head down"),
    PARTY("mood_party", "Party", "Hands up, all night"),
    WORKOUT("mood_workout", "Workout", "One more rep"),
    ROMANCE("mood_romance", "Romance", "Slow dances only"),
    DRIVE("mood_drive", "Drive", "Windows down, volume up"),
    THROWBACK("mood_throwback", "Throwback", "Still got it");

    companion object {
        fun byId(id: String?): Mood? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Genre / keyword → mood inference. Real services train models on audio
 * features; with metadata only, a compact keyword table gets surprisingly far
 * and — unlike a black box — never recommends the same song back at you.
 */
object MoodClassifier {

    private val rules: List<Pair<Regex, Mood>> = listOf(
        Regex("\\b(chill|chillout|lounge|ambient|downtempo|lo-?fi|lofi|acoustic|ballad|piano|sleep|calm|relax)\\b", RegexOption.IGNORE_CASE) to Mood.CHILL,
        Regex("\\b(edm|electro|house|techno|trance|hardstyle|dubstep|dance|club|remix|bass|hyperpop|synth)\\b", RegexOption.IGNORE_CASE) to Mood.ENERGY,
        Regex("\\b(focus|study|classical|instrumental|piano|orchestral|neo-?classical|ambient|jazz)\\b", RegexOption.IGNORE_CASE) to Mood.FOCUS,
        Regex("\\b(party|anthem|fiesta|carnival|reggaeton|latino|latin|disco|funk|bhangra|afrobeat|dance)\\b", RegexOption.IGNORE_CASE) to Mood.PARTY,
        Regex("\\b(workout|gym|run(ning)?|training|pump|hype|motivation|beast|iron|cardio)\\b", RegexOption.IGNORE_CASE) to Mood.WORKOUT,
        Regex("\\b(love|romance|wedding|slow jam|heart|kiss|baby|forever|valentine)\\b", RegexOption.IGNORE_CASE) to Mood.ROMANCE,
        Regex("\\b(road|drive|highway|cruise|ride|travel|summer)\\b", RegexOption.IGNORE_CASE) to Mood.DRIVE,
        Regex("\\b(throwback|classics?|80s|90s|00s|2000s|retro|old school|golden)\\b", RegexOption.IGNORE_CASE) to Mood.THROWBACK
    )

    private val genreRules: List<Pair<Regex, Mood>> = listOf(
        Regex("\\b(ambient|downtempo|chill|lounge|acoustic|singer-songwriter)\\b", RegexOption.IGNORE_CASE) to Mood.CHILL,
        Regex("\\b(electronic|dance|edm|house|techno|trance|drum|dubstep|hardstyle|synth)\\b", RegexOption.IGNORE_CASE) to Mood.ENERGY,
        Regex("\\b(classical|jazz|instrumental|soundtrack|new age)\\b", RegexOption.IGNORE_CASE) to Mood.FOCUS,
        Regex("\\b(pop|reggaeton|latin|salsa|afro|funk|disco|bhangra|reggae)\\b", RegexOption.IGNORE_CASE) to Mood.PARTY,
        Regex("\\b(rock|metal|punk|alternative|hip-hop|rap|trap|grime)\\b", RegexOption.IGNORE_CASE) to Mood.WORKOUT,
        Regex("\\b(r&b|soul|blues|country|folk|ballad)\\b", RegexOption.IGNORE_CASE) to Mood.ROMANCE
    )

    /** Best-effort mood for one track from its genre / title metadata. */
    fun infer(track: Track): Mood? {
        val haystack = listOfNotNull(track.genre, track.title).joinToString(" ")
        rules.firstOrNull { it.first.containsMatchIn(haystack) }?.let { return it.second }
        val genre = track.genre ?: return null
        return genreRules.firstOrNull { it.first.containsMatchIn(genre) }?.second
    }

    fun inferAll(tracks: List<Track>): List<Mood> =
        tracks.mapNotNull { infer(it) }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .map { it.first }
}

/**
 * The Explore catalogue. Moods come first (Spotify-style vibe cards), then the
 * genres with verified iTunes chart feeds. Genre ids were checked against the
 * live `itunes.apple.com/{cc}/rss/topsongs/genre={id}/json` endpoint.
 */
object Categories {

    val moods: List<BrowseCategory> = listOf(
        BrowseCategory("chill", "Chill", "Lo-fi, acoustic & slow jams", audiusGenre = "Chill", query = "chill", mood = Mood.CHILL, colors = listOf(0xFF2B5876, 0xFF4E4376)),
        BrowseCategory("energy", "Energy", "Dance, house & big hooks", itunesGenreId = 17, audiusGenre = "Electronic", query = "energy", mood = Mood.ENERGY, colors = listOf(0xFFFF512F, 0xFFDD2476)),
        BrowseCategory("focus", "Focus", "Jazz, classical & instrumentals", itunesGenreId = 5, audiusGenre = "Classical", query = "instrumental", mood = Mood.FOCUS, colors = listOf(0xFF3A1C71, 0xFF928DAB)),
        BrowseCategory("party", "Party", "Reggaeton, disco & anthems", itunesGenreId = 12, audiusGenre = "Dance", query = "party", mood = Mood.PARTY, colors = listOf(0xFFF7971E, 0xFFFFD200)),
        BrowseCategory("workout", "Workout", "Trap, rock & gym fuel", itunesGenreId = 18, audiusGenre = "Hip-Hop/Rap", query = "workout", mood = Mood.WORKOUT, colors = listOf(0xFF8E2DE2, 0xFF4A00E0)),
        BrowseCategory("romance", "Romance", "Slow jams & serenades", itunesGenreId = 15, audiusGenre = "R&B/Soul", query = "love songs", mood = Mood.ROMANCE, colors = listOf(0xFFFF6A88, 0xFFFF99AC)),
        BrowseCategory("drive", "Drive", "Road-trip favourites", itunesGenreId = 21, audiusGenre = "Rock", query = "road trip", mood = Mood.DRIVE, colors = listOf(0xFF00B4DB, 0xFF0083B0)),
        BrowseCategory("throwback", "Throwback", "Classics that still hit", query = "classics", mood = Mood.THROWBACK, colors = listOf(0xFF606C88, 0xFF3F4C6B))
    )

    val genres: List<BrowseCategory> = listOf(
        BrowseCategory("pop", "Pop", "The world's biggest hooks", itunesGenreId = 14, audiusGenre = "Pop", query = "pop", colors = listOf(0xFFFF5F6D, 0xFFFFC371)),
        BrowseCategory("hiphop", "Hip-Hop & Rap", "Bars, beats & bass", itunesGenreId = 18, audiusGenre = "Hip-Hop/Rap", query = "hip hop", colors = listOf(0xFF833AB4, 0xFF4B1248)),
        BrowseCategory("rock", "Rock", "Guitars turned up loud", itunesGenreId = 21, audiusGenre = "Rock", query = "rock", colors = listOf(0xFFB31217, 0xFFE52D27)),
        BrowseCategory("dance", "Dance & Electronic", "House, techno & EDM", itunesGenreId = 17, audiusGenre = "Electronic", query = "electronic", colors = listOf(0xFF00C6FF, 0xFF7F00FF)),
        BrowseCategory("alternative", "Alternative", "Indie, grunge & beyond", itunesGenreId = 20, audiusGenre = "Alternative", query = "alternative", colors = listOf(0xFF654EA3, 0xFFEAAFC8)),
        BrowseCategory("rnb", "R&B / Soul", "Silk & slow jams", itunesGenreId = 15, audiusGenre = "R&B/Soul", query = "r&b", colors = listOf(0xFF77422D, 0xFFB18276)),
        BrowseCategory("latin", "Latin", "Reggaeton, salsa & more", itunesGenreId = 12, audiusGenre = "Latin", query = "latin", colors = listOf(0xFFF2994A, 0xFFF2C94C)),
        BrowseCategory("country", "Country", "Nashville & backroads", itunesGenreId = 6, audiusGenre = "Country", query = "country", colors = listOf(0xFF93A5CF, 0xFFCFB491)),
        BrowseCategory("jazz", "Jazz", "Brass, brushes & swing", itunesGenreId = 11, audiusGenre = "Jazz", query = "jazz", colors = listOf(0xFF232526, 0xFFB79891)),
        BrowseCategory("classical", "Classical", "Orchestras & piano", itunesGenreId = 5, audiusGenre = "Classical", query = "classical", colors = listOf(0xFF3E5151, 0xFFDECBA4))
    )

    val all: List<BrowseCategory> = moods + genres

    fun byId(id: String?): BrowseCategory? = all.firstOrNull { it.id == id }

    /**
     * Map a free-form genre string from a track onto a known category —
     * powers the "because you've been playing {category}" shelves.
     */
    fun matchGenre(genre: String?): BrowseCategory? {
        if (genre.isNullOrBlank()) return null
        val g = genre.lowercase()
        fun hit(pattern: String) = Regex("\\b$pattern\\b").containsMatchIn(g)
        return when {
            hit("pop") -> genres.first { it.id == "pop" }
            hit("hip.?hop|rap|trap|grime") -> genres.first { it.id == "hiphop" }
            hit("rock|metal|punk|grunge") -> genres.first { it.id == "rock" }
            hit("electronic|dance|edm|house|techno|trance|drum|dubstep|synth") -> genres.first { it.id == "dance" }
            hit("alternative|indie") -> genres.first { it.id == "alternative" }
            hit("r&b|rnb|soul|funk|blues") -> genres.first { it.id == "rnb" }
            hit("latin|reggaeton|salsa|urbano|bachata") -> genres.first { it.id == "latin" }
            hit("country|bluegrass|americana") -> genres.first { it.id == "country" }
            hit("jazz|swing|bossa") -> genres.first { it.id == "jazz" }
            hit("classical|orchestral|piano|baroque") -> genres.first { it.id == "classical" }
            else -> null
        }
    }
}
