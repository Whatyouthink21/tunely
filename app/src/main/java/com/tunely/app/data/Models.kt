package com.tunely.app.data

import kotlinx.serialization.Serializable

/** Canonical ids for every streaming provider Tunely knows about. */
object SourceIds {
    const val YOUTUBE = "youtube"
    const val SOUNDCLOUD = "soundcloud"
    const val BANDCAMP = "bandcamp"
    const val AUDIUS = "audius"
    const val DEEZER = "deezer"
    const val ITUNES = "itunes"
    const val RADIO = "radio"
    const val PIPED = "piped"

    /** Legacy value that older versions of Tunely wrote into the database. */
    const val YOUTUBE_MUSIC = "youtube_music"

    fun normalize(raw: String?): String = when (raw) {
        null, "", YOUTUBE_MUSIC -> YOUTUBE
        else -> raw
    }
}

/** How a track can be played — drives the badges shown in the UI. */
enum class PlaybackKind { FULL, PREVIEW, LIVE }

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val genre: String? = null,
    val releaseYear: String? = null,
    val explicit: Boolean = false,
    val source: String = SourceIds.YOUTUBE,
    val sourceUrl: String? = null,
    /** Set when the provider hands us a playable URL up-front (radio, previews, …). */
    val streamUrl: String? = null,
    val live: Boolean = false
) {
    /** Stable, collision-free key used by the database and the media session. */
    val uid: String get() = "${SourceIds.normalize(source)}::$id"

    val kind: PlaybackKind
        get() = when {
            live -> PlaybackKind.LIVE
            source == SourceIds.RADIO -> PlaybackKind.LIVE
            source in PREVIEW_SOURCES && durationMs in 1..40_000 -> PlaybackKind.PREVIEW
            else -> PlaybackKind.FULL
        }

    companion object {
        val PREVIEW_SOURCES = setOf(SourceIds.DEEZER, SourceIds.ITUNES)

        fun uidOf(source: String?, id: String) = "${SourceIds.normalize(source)}::$id"
    }
}

/** One timed lyric line; words are filled only when the source provides word timing. */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val isBackground: Boolean = false  // backing vocal lines
)

data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val wordSynced: Boolean,
    val source: String
)

/** Sleep timer state */
data class SleepTimerState(
    val active: Boolean = false,
    val endTimeMs: Long = 0L,
    val finishLastSong: Boolean = false
)
