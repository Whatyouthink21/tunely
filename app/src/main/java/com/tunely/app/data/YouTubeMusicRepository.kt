package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

/**
 * Search and stream resolution through YouTube Music via NewPipeExtractor.
 * Note: unofficial extraction; see README for the terms-of-service caveat.
 */
class YouTubeMusicRepository {

    private val yt get() = ServiceList.YouTube

    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(yt, handler)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toTrack() }
    }

    /** Resolve the best audio-only stream URL for a video id. */
    suspend fun resolveAudioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$videoId")
        info.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: error("No audio stream available for $videoId")
    }

    private fun StreamInfoItem.toTrack() = Track(
        id = url.substringAfter("v=").substringBefore("&"),
        title = name,
        artist = uploaderName?.removeSuffix(" - Topic") ?: "Unknown",
        durationMs = duration * 1000,
        artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
        source = "youtube_music"
    )
}
