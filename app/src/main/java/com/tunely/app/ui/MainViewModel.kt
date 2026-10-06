package com.tunely.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tunely.app.TunelyApp
import com.tunely.app.data.*
import com.tunely.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val tunely = app as TunelyApp
    private val yt = YouTubeMusicRepository()
    private val meta = MetadataRepository(tunely.http)
    private val lyricsRepo = LyricsRepository(tunely.http)
    private val dao = tunely.db.dao()

    val player = PlayerController(app)
    val playerState = player.state

    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searching = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val library = dao.library().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recent = dao.recentlyPlayed().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var searchJob: Job? = null
    private var lastTrackId: String? = null

    init {
        // Position ticker (smooth lyrics and progress bar).
        viewModelScope.launch {
            while (true) { player.refreshPosition(); delay(50) }
        }
        // Load lyrics + history when the track changes.
        viewModelScope.launch {
            playerState.map { it.track }.distinctUntilChangedBy { it?.id }.collect { t ->
                lyrics.value = null
                if (t == null || t.id == lastTrackId) return@collect
                lastTrackId = t.id
                dao.addHistory(HistoryEntry(trackId = t.id, title = t.title, artist = t.artist, artworkUrl = t.artworkUrl))
                lyrics.value = lyricsRepo.load(t)
            }
        }
    }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            searching.value = true
            message.value = null
            searchResults.value = runCatching { yt.searchSongs(q) }
                .onFailure { message.value = "Search failed: ${it.javaClass.simpleName}: ${it.message}" }
                .getOrDefault(emptyList())
            if (searchResults.value.isEmpty() && message.value == null) message.value = "No results found"
            searching.value = false
            // Upgrade artwork/metadata in the background.
            searchResults.value = searchResults.value.map { meta.enrich(it) }
        }
    }

    fun play(tracks: List<Track>, index: Int) = viewModelScope.launch {
        val enriched = tracks.toMutableList()
        enriched[index] = meta.enrich(enriched[index])
        player.playQueue(enriched, index)
    }

    fun toggleLibrary(t: Track, inLibrary: Boolean) = viewModelScope.launch {
        if (inLibrary) dao.remove(t.id)
        else dao.add(LibraryTrack(t.id, t.title, t.artist, t.album, t.durationMs, t.artworkUrl))
    }

    fun isInLibrary(id: String) = dao.isInLibrary(id)
}
