# Tunely 🎵

An Android music player with its **own visual identity** — a drifting aurora backdrop, a floating
dock, vinyl-style artwork and karaoke lyrics — streaming audio from **eight providers**.

Built with Kotlin, Jetpack Compose and Media3. No Apple Music assets, colours or layouts: the look is
a from-scratch "aurora ink" design language driven by the album artwork and one accent pair.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-green) ![Media3](https://img.shields.io/badge/Media3-1.4.1-orange)

---

## 🎨 The interface

| | |
|---|---|
| **Aurora backdrop** | Three slowly drifting radial blobs, tinted from the artwork of the current track (Palette extraction) and your accent, with a vignette so type stays readable. Toggleable. |
| **Floating dock** | The navigation bar is a rounded island with a gradient indicator that springs between tabs; the active icon scales up. |
| **Mini player island** | Draggable pill with a live waveform, a progress hairline and full transport. Tap or flick it up to open the player. |
| **Orbit player** | Artwork on a vinyl-style disc: real grooves, a sweeping sheen, and rotation that *eases* up to speed and coasts to a stop with playback. |
| **Swipe panes** | The player is a 2-page pager: artwork ↔ lyrics, with an animated dot indicator. |
| **Karaoke lyrics** | The active line fills with an accent gradient as it plays (word-accurate when the provider supplies word timings), inactive lines shrink and fade by depth. Tap any line to seek. |
| **Motion vocabulary** | One spring/tween set for the whole app: press-scale on every surface, staggered list entrances, scene transitions per tab, sheet springs. Switchable for accessibility. |
| **Type** | Outfit (variable weight, SIL OFL, bundled in `res/font`). |

Everything is themeable from **Settings → Appearance**: theme (auto/dark/light), 8 accent pairs,
aurora toggle, motion toggle, artwork blur, and vinyl vs. cover artwork.

## 🎧 Streaming sources

Every source is searchable at once and results are interleaved, so one provider being down never
empties the screen. Resolution falls back automatically:

| Source | Content | Notes |
|---|---|---|
| **YouTube Music** | Full catalogue | NewPipe extraction, music-songs filter |
| **SoundCloud** | Remixes, edits, indie | NewPipe, tracks filter — **fixed** (see below) |
| **Bandcamp** | Artist releases | NewPipe |
| **Audius** | Full-length, artist-owned | Keyless public API |
| **Radio** | Live stations worldwide | radio-browser.info, `LIVE` badge |
| **Deezer** | 30 s previews | Keyless, global charts |
| **iTunes** | 30 s previews + hi-res artwork/metadata | Keyless |
| **Piped** | Privacy front-end | Also the automatic YouTube fallback |

Previews and live stations are labelled with `PREVIEW` / `LIVE` chips, so nothing pretends to be
something it is not. Audio quality (data saver → audiophile) actually selects the stream bitrate.

**Fallback chain at play time:** direct stream URL → the track's own provider → Piped (for
YouTube) → a same-title YouTube search. If everything fails you get a readable error, not silence.

## ✨ Everything else that works

- **Search** across all enabled providers in parallel, with per-source filter chips, debounced
  queries, shimmer skeletons and a long-press action sheet (play next, queue, favourite, add to
  playlist — including creating one inline).
- **Home** with a hero "pick up where you left off" card (progress ring), mood chips, and discovery
  shelves: Audius trending, global charts, electronic, hip-hop, live radio, plus a
  "because you listen" shelf built from your history.
- **Library** with favourites, history and playlists (create, fill, play, delete).
- **Real audio processing**: 5-band equalizer with 9 presets mapped onto the device bands,
  loudness-enhancer volume normalisation, fade & blend transitions, gapless playback, playback speed.
- **Sleep timer** that can stop after the current track.
- **Background playback** with a media session: notification and lock-screen controls, audio focus,
  becoming-noisy handling, and 15-minute stream-URL caching.
- **Database v2 migration** that keeps saved tracks playable — v1 stored tracks without their
  provider, so anything from SoundCloud/Audius used to be replayed through YouTube.

## 🐛 Bugs fixed in this rewrite

1. **SoundCloud never played.** `PlaybackService.mediaItemFor()` hard-coded
   `https://www.youtube.com/watch?v=<id>` for *every* track, so a SoundCloud (or Piped) track was
   resolved as a YouTube video id. Media items now carry a private `tunely://` URI and the service
   dispatches resolution by provider.
2. **SoundCloud ids were `url.hashCode()`** — lossy and collision-prone. Tracks now keep their real
   provider url/id, and SoundCloud search uses the `tracks` content filter.
3. **Library/history/playlists lost the source** (same root cause as #1). Schema v2 stores
   `source`, `sourceUrl`, `streamUrl`, `album`, `duration`, `live` with a real migration.
4. **Queue corruption**: `playNext`/`addToQueue` mixed the player's indices with a local list and
   `setShuffleModeEnabled` desynced the two. The queue is now the single source of truth and shuffle
   reorders it explicitly.
5. **50 ms state storm**: a ViewModel ticker republished the whole player state 20×/second. Position
   now lives in its own flow and only the seek bar/lyrics observe it.
6. **Stale search results** could overwrite newer ones when the enrichment pass finished late.
7. **Metadata enrichment** ran N sequential HTTP calls per search; now bounded-parallel and skipped
   when it is not needed.
8. **Dead settings**: crossfade, equalizer and volume normalisation were pure UI. All three are
   implemented (fade & blend via player volume, real `Equalizer`/`LoudnessEnhancer` effects).
9. **Lyrics**: the exact-match LRCLIB endpoint 404'd constantly (now uses search + duration
   matching), `[offset:]` was ignored, multi-timestamp lines dropped, and provider titles such as
   "Artist - Song (Official Video)" were not cleaned before matching.
10. **Light mode was unreadable** — screens hard-coded `Color.White`/`Color.Black`. All colour now
    comes from theme tokens.
11. **Cleartext blocking**: several radio stations stream over `http://`; a network security config
    fixes silent failures.
12. **Playback service leaks / no-op edge cases**: release path, duration fallbacks for live streams,
    friendly error messages, `MODIFY_AUDIO_SETTINGS` permission for the audio effects.
13. **Library quick-action tiles did nothing**, playlists could not be opened, and the "no results"
    banner appeared when sources were simply all disabled. All addressed.

## 🏗️ Build

1. Open the project in Android Studio (Koala or newer) and let Gradle sync.
2. Run on a device or emulator (Android 8.0 / API 26+).
3. Or grab the debug APK from the **Build debug APK** workflow artifact.

## Architecture

```
com.tunely.app/
├── data/
│   ├── Models.kt           # Track (+ source, kind, uid), Lyrics, sleep timer
│   ├── Sources.kt          # StreamingSource + the eight providers
│   ├── SourceRegistry.kt   # parallel fan-out search, fallback resolution, quality
│   ├── MusicCatalog.kt     # home shelves + bounded-parallel metadata enrichment
│   ├── SettingsManager.kt  # SharedPreferences → StateFlows, with legacy migration
│   ├── AppDatabase.kt      # Room v2 (favourites, history, playlists) + migration
│   ├── LyricsRepository.kt # LRCLIB (search + duration match) → Lyrics.ovh, title cleaning
│   ├── LrcParser.kt        # LRC / enhanced LRC (word timings, offsets, multi-tags)
│   └── Http.kt             # shared OkHttp helpers, user agents, result interleaving
├── player/
│   ├── MediaUri.kt         # tunely://direct|track/<source>/<id>
│   ├── PlaybackService.kt  # Media3 session, lazy resolution, 15 min URL cache, fade & blend
│   ├── AudioEffects.kt     # equalizer presets + loudness enhancer
│   └── PlayerController.kt # Compose-facing controller, queue as source of truth
└── ui/
    ├── MainViewModel.kt    # search, discovery, library, sleep timer, playback
    ├── design/             # Tokens, Motion, Aurora, Artwork, Components, Sheets, Accents
    └── screens/            # Home, Search, Library, Settings, NowPlaying, Lyrics, actions
```

## ⚠️ Disclaimer

- Streaming uses unofficial extraction through **NewPipeExtractor** plus public APIs. This may
  violate a provider's terms of service and can break when they change their internals. Personal use,
  at your own risk.
- Chart data, previews, artwork and metadata come from Deezer, the iTunes Search API,
  Audius and radio-browser.info.
- Not suitable for Play Store distribution because of extraction-based streaming.
