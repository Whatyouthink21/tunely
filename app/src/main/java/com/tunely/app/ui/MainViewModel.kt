package com.tunely.app.ui

import android.app.Application
import coil.imageLoader
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tunely.app.TunelyApp
import com.tunely.app.data.*
import com.tunely.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Everything the screens need: search, library, discovery and playback. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val tunely = app as TunelyApp
    private val registry = tunely.registry
    private val catalog = tunely.catalog
    private val discovery = tunely.discovery
    private val artists = tunely.artists
    private val lyricsRepo = LyricsRepository(tunely.http)
    private val dao = tunely.db.dao()

    val settings = tunely.settings
    val player = PlayerController(app)
    val playerState = player.state
    val position = player.position
    val playerError = player.error

    // ─── Search ──────────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")
    val activeSource = MutableStateFlow(SOURCE_ALL)
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searching = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    // ─── Lyrics ──────────────────────────────────────────────────────
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val lyricsLoading = MutableStateFlow(false)

    // ─── Library ─────────────────────────────────────────────────────
    val library = dao.library().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent = dao.recentlyPlayed().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ─── Discovery ───────────────────────────────────────────────────
    val shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelvesLoading = MutableStateFlow(true)

    /** Explore tab: charts + trending, loaded once and refreshable. */
    val exploreCharts = MutableStateFlow<List<ChartList>>(emptyList())
    val exploreTrending = MutableStateFlow<List<Track>>(emptyList())
    val exploreLoading = MutableStateFlow(true)

    /** Artist pages and category pages open as overlays above the tabs. */
    sealed interface Overlay {
        data class ArtistPage(val name: String) : Overlay
        data class Category(val categoryId: String) : Overlay
    }

    val overlay = MutableStateFlow<Overlay?>(null)
    val artistPage = MutableStateFlow<ArtistProfile?>(null)
    val artistLoading = MutableStateFlow(false)
    val categoryPage = MutableStateFlow<CategoryPage?>(null)
    val categoryLoading = MutableStateFlow(false)

    /** Providers the user has switched on, in registry order. */
    val enabledSources: StateFlow<List<SourceInfo>> = combine(
        listOf(
            settings.sourceYoutube,
            settings.sourceSoundcloud,
            settings.sourceBandcamp,
            settings.sourceAudius,
            settings.sourceRadio,
            settings.sourceDeezer,
            settings.sourceItunes,
            settings.sourcePiped
        )
    ) { flags: Array<Boolean> ->
        Sources.all.filterIndexed { index, _ -> flags.getOrElse(index) { false } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Sources.all.filter { it.defaultEnabled })

    // ─── Sleep timer ─────────────────────────────────────────────────
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

    private var lastTrackUid: String? = null

    init {
        // Position ticker: only the cheap position flow is written, so the rest of
        // the UI is not recomposed 20 times a second.
        viewModelScope.launch {
            while (true) {
                player.refreshPosition()
                delay(150)
            }
        }

        // Track changes: history + lyrics. recordPlay() moves a repeat play to
        // the top of History instead of stacking another identical row.
        viewModelScope.launch {
            playerState.map { it.track }.distinctUntilChangedBy { it?.uid }.collect { track ->
                if (track == null) return@collect
                if (track.uid == lastTrackUid) return@collect
                lastTrackUid = track.uid
                dao.recordPlay(HistoryEntry.from(track))
                loadLyrics(track)
            }
        }

        loadShelves()
        loadExplore()
    }

    /** Home feed: daily mixes first, then personalised shelves. */
    private fun loadShelves() {
        viewModelScope.launch {
            shelvesLoading.value = true
            val history = runCatching { dao.recentlyPlayed().first() }.getOrDefault(emptyList())
            val favorites = library.value.map { it.toTrack() }
            val profile = discovery.profile(history, favorites)
            val home = mutableListOf<Shelf>()
            home += runCatching { discovery.dailyMixes(profile, 4) }.getOrDefault(emptyList())
            home += runCatching { discovery.homeShelves(profile) }.getOrDefault(emptyList())
            shelves.value = home
            shelvesLoading.value = false
        }
    }

    private fun loadExplore() {
        viewModelScope.launch {
            exploreLoading.value = true
            val charts = runCatching { discovery.charts() }.getOrDefault(emptyList())
            val trending = runCatching { audiusTrending() }.getOrDefault(emptyList())
            exploreCharts.value = charts
            exploreTrending.value = trending
            exploreLoading.value = false
        }
    }

    private suspend fun audiusTrending(): List<Track> =
        (registry.source(SourceIds.AUDIUS) as? AudiusSource)?.trending(20).orEmpty()

    fun refreshShelves() {
        loadShelves()
        loadExplore()
    }

    // ─── Overlays: artist pages & category pages ─────────────────────

    fun openArtist(name: String) {
        overlay.value = Overlay.ArtistPage(name)
        viewModelScope.launch {
            artistLoading.value = true
            artistPage.value = null
            artistPage.value = runCatching { artists.load(name) }.getOrNull()
            artistLoading.value = false
        }
    }

    fun refreshArtist() {
        val name = (overlay.value as? Overlay.ArtistPage)?.name ?: return
        viewModelScope.launch {
            artistLoading.value = true
            artistPage.value = runCatching { artists.load(name, force = true) }.getOrNull()
            artistLoading.value = false
        }
    }

    fun openCategory(categoryId: String) {
        val category = Categories.byId(categoryId) ?: return
        overlay.value = Overlay.Category(categoryId)
        viewModelScope.launch {
            categoryLoading.value = true
            categoryPage.value = null
            val history = runCatching { dao.recentlyPlayed().first() }.getOrDefault(emptyList())
            val known = history.map { TrackKey.of(it.toTrack()) }.toSet()
            val tracks = runCatching {
                discovery.categoryTracks(category, exclude = known, limit = 30)
            }.getOrDefault(emptyList())
            categoryPage.value = CategoryPage(category, tracks)
            categoryLoading.value = false
        }
    }

    fun closeOverlay() {
        overlay.value = null
    }

    private fun loadLyrics(track: Track) {
        viewModelScope.launch {
            lyrics.value = null
            if (track.source == SourceIds.RADIO) return@launch
            lyricsLoading.value = true
            lyrics.value = runCatching { lyricsRepo.load(track) }.getOrNull()
            lyricsLoading.value = false
        }
    }

    fun retryLyrics() {
        playerState.value.track?.let { loadLyrics(it) }
    }

    // ─── Search ──────────────────────────────────────────────────────

    fun onQueryChange(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults.value = emptyList()
            searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            runSearch(query)
        }
    }

    fun setActiveSource(sourceId: String) {
        activeSource.value = sourceId
        val query = searchQuery.value
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        searching.value = true
        message.value = null
        val sources = registry.enabledSources(settings).let { enabled ->
            val filter = activeSource.value
            if (filter == SOURCE_ALL) enabled else enabled.filter { it.id == filter }
        }
        if (sources.isEmpty()) {
            searching.value = false
            searchResults.value = emptyList()
            message.value = "No sources are enabled — turn one on in Settings"
            return
        }
        val results = registry.searchAll(query, sources)
        if (searchQuery.value != query) return   // a newer search already won
        searchResults.value = results
        searching.value = false
        if (results.isEmpty()) message.value = "Nothing found for \"$query\""

        // Enrich in the background; results update in place when metadata lands.
        val enriched = catalog.enrichAll(results)
        if (searchQuery.value == query) searchResults.value = enriched
    }

    // ─── Playback ────────────────────────────────────────────────────

    fun play(tracks: List<Track>, index: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        player.playQueue(tracks, index, shuffle)
    }

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        player.playQueue(tracks, 0, shuffle = true)
    }

    fun playNext(track: Track) {
        player.playNext(track)
        message.value = "Playing next"
    }

    fun addToQueue(track: Track) {
        player.addToQueue(track)
        message.value = "Added to queue"
    }

    // ─── Favourites ──────────────────────────────────────────────────

    fun isFavorite(track: Track): Boolean =
        library.value.any { it.id == track.id && SourceIds.normalize(it.source) == SourceIds.normalize(track.source) }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            if (isFavorite(track)) {
                dao.remove(track.id, SourceIds.normalize(track.source))
                message.value = "Removed from favourites"
            } else {
                dao.add(LibraryTrack.from(track))
                message.value = "Added to favourites"
            }
        }
    }

    // ─── Sleep timer ─────────────────────────────────────────────────

    fun startSleepTimer(minutes: Int, finishCurrentSong: Boolean = false) {
        sleepJob?.cancel()
        val endTime = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimerState(
            active = true,
            endTimeMs = if (finishCurrentSong) 0L else endTime,
            finishLastSong = finishCurrentSong
        )
        sleepJob = viewModelScope.launch {
            if (finishCurrentSong) {
                val startUid = playerState.value.track?.uid
                playerState.collect { state ->
                    val uid = state.track?.uid
                    if (uid != null && uid != startUid) {
                        player.pause()
                        _sleepTimer.value = SleepTimerState()
                        sleepJob?.cancel()
                    }
                }
            } else {
                delay(minutes * 60_000L)
                player.pause()
                _sleepTimer.value = SleepTimerState()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState()
    }

    fun sleepTimerRemainingMs(): Long {
        val state = _sleepTimer.value
        if (!state.active || state.finishLastSong) return 0L
        return (state.endTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    // ─── Playlists ───────────────────────────────────────────────────

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.createPlaylist(PlaylistEntity(name = name.trim()))
            message.value = "Playlist created"
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { dao.deletePlaylist(id) }
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val position = dao.playlistTracks(playlistId).first().size
            dao.addToPlaylist(PlaylistTrack.from(playlistId, track, position))
            message.value = "Added to playlist"
        }
    }

    fun removeFromPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            dao.removeFromPlaylist(playlistId, track.id)
            message.value = "Removed from playlist"
        }
    }

    fun playlistTracks(playlistId: Long): Flow<List<PlaylistTrack>> = dao.playlistTracks(playlistId)

    fun clearMessage() { message.value = null }

    /** Drop cached artwork so a re-scan picks up fresh covers. */
    fun clearImageCache() {
        runCatching { getApplication<Application>().imageLoader.memoryCache?.clear() }
        message.value = "Image cache cleared"
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    companion object {
        const val SOURCE_ALL = "all"
    }
}
