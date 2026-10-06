package com.tunely.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,              // YouTube video id
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,   // best available (iTunes hi-res when matched)
    val genre: String? = null,
    val releaseYear: String? = null,
    val explicit: Boolean = false
)

/** One timed lyric line; words are filled only when the source provides word timing. */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val wordSynced: Boolean,
    val source: String
)
