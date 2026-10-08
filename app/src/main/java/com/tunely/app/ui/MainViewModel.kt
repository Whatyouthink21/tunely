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
    private val sc = SoundCloudRepository()
    private val piped = PipedRepository(tunely.http)
    private val meta = MetadataRepository(tunely.http)
    private val lyricsRepo = LyricsRepository(tunely.http)
    private val dao = tunely.db.dao()

    val settings = tunely.settings
    val player = PlayerController(app)
    val playerState = player.state

    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searching = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val library = dao.library().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recent = dao.recentlyPlayed().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val searchSource = MutableStateFlow("all")

    // Sleep timer
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

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
        // Apply playback speed whenever setting changes
        viewModelScope.launch {
            settings.playbackSpeed.collect { speed ->
                player.setPlaybackSpeed(speed)
            }
        }
    }

    /**
     * Search across all enabled sources. "all" merges results; otherwise
     * restricts to a specific provider.
     */
    fun search(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            searching.value = true
            message.value = null

            val useYt = settings.sourceYoutube.value || settings.sourceYtmusic.value
            val useSc = settings.sourceSoundcloud.value
            val usePiped = settings.sourcePiped.value
            val source = searchSource.value

            val results = mutableListOf<Track>()
            try {
                if ((source == "all" || source == "youtube_music") && useYt) {
                    results += runCatching { yt.searchSongs(q) }.getOrDefault(emptyList())
                }
                if ((source == "all" || source == "soundcloud") && useSc) {
                    results += runCatching { sc.search(q) }.getOrDefault(emptyList())
                }
                if ((source == "all" || source == "piped") && usePiped) {
                    results += runCatching { piped.search(q) }.getOrDefault(emptyList())
                }
            } catch (e: Exception) {
                message.value = "Search failed: ${e.javaClass.simpleName}: ${e.message}"
            }

            searchResults.value = results
            if (results.isEmpty() && message.value == null) message.value = "No results found"
            searching.value = false

            // Enrich YouTube results with iTunes metadata in the background
            searchResults.value = searchResults.value.map {
                if (it.source == "youtube_music") meta.enrich(it) else it
            }
        }
    }

    fun play(tracks: List<Track>, index: Int) = viewModelScope.launch {
        val enriched = tracks.toMutableList()
        if (enriched[index].source == "youtube_music") {
            enriched[index] = meta.enrich(enriched[index])
        }
        player.playQueue(enriched, index)
    }

    fun toggleLibrary(t: Track, inLibrary: Boolean) = viewModelScope.launch {
        if (inLibrary) dao.remove(t.id)
        else dao.add(LibraryTrack(t.id, t.title, t.artist, t.album, t.durationMs, t.artworkUrl))
    }

    fun isInLibrary(id: String) = dao.isInLibrary(id)

    // ─── Sleep Timer ─────────────────────────────────────────────────
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endTime = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimerState(active = true, endTimeMs = endTime)
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _sleepTimer.value = SleepTimerState()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState()
    }

    // ─── Playlists ───────────────────────────────────────────────────
    fun createPlaylist(name: String) = viewModelScope.launch {
        dao.createPlaylist(PlaylistEntity(name = name))
    }

    fun getPlaylistTracks(id: Long) = dao.playlistTracks(id)

    fun addToPlaylist(playlistId: Long, track: Track) = viewModelScope.launch {
        val current = dao.playlistTracks(playlistId).first()
        dao.addToPlaylist(
            PlaylistTrack(
                playlistId = playlistId,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                position = current.size
            )
        )
        message.value = "Added to playlist"
    }
}
