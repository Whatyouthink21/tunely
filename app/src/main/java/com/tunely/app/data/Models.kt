package com.tunely.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,              // YouTube video id (or "sc:hash" for SoundCloud)
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,   // best available (iTunes hi-res when matched)
    val genre: String? = null,
    val releaseYear: String? = null,
    val explicit: Boolean = false,
    val source: String = "youtube_music",  // youtube_music | soundcloud | piped
    val sourceUrl: String? = null         // original platform URL for non-YT sources
)

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
