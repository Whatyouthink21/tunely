package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * SoundCloud search + stream resolution via NewPipeExtractor.
 * Complements YouTubeMusicRepository as a second streaming source.
 */
class SoundCloudRepository {

    private val sc get() = ServiceList.SoundCloud

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val handler = sc.searchQHFactory.fromQuery(query, emptyList(), "")
            val info = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(sc, handler)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(20)
                .map { it.toTrack() }
        }.getOrDefault(emptyList())
    }

    suspend fun resolveAudioUrl(url: String): String = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(sc, url)
        info.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: error("No audio stream available")
    }

    private fun StreamInfoItem.toTrack() = Track(
        id = "sc:${url.hashCode()}",
        title = name,
        artist = uploaderName ?: "Unknown",
        durationMs = duration * 1000,
        artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
        source = "soundcloud",
        sourceUrl = url
    )
}
