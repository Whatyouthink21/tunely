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
  playlist — including creating one inline, **go to artist**).
- **Home** personalised from your listening history: a hero "pick up where you left off" card
  (progress ring), mood chips, **Daily Mixes** seeded from your profile, "because you listened to
  *x*" shelves, your-artist shortcuts, and "jump back in".
- **Explore tab** (Spotify/Apple-Music style browse): mood cards, live **charts** (iTunes Top 50 +
  genre charts with embedded previews, Deezer charts, Audius trending), and genre doors — every one
  opens a category page with its own picks.
- **Artist pages**: hero image + audience stats, popular tracks aggregated across providers,
  discography with hi-res covers, "fans also like" (Deezer related artists), and the artist's bio
  from Audius. Related artists swap the page in place.
- **Recommendation engine that never repeats itself** (see below).
- **Library** with favourites, history and playlists (create, fill, play, delete).
  History shows **each song once** — replaying a track moves it to the top instead of stacking
  duplicate rows.
- **Real audio processing**: 5-band equalizer with 9 presets mapped onto the device bands,
  loudness-enhancer volume normalisation, fade & blend transitions, gapless playback, playback speed.
- **Transitions everywhere**: tab slides, overlay pages ease in over the tab content, staggered
  list entrances, springy dock/dock indicator — all switchable via Settings → Motion.
- **Sleep timer** that can stop after the current track.
- **Background playback** with a media session: notification and lock-screen controls, audio focus,
  becoming-noisy handling, and 15-minute stream-URL caching.
- **Database v3 migration**: keeps saved tracks playable (v2) and de-duplicates history while
  adding genre data for the mood engine (v3).

## 🧠 The recommendation engine

Playing *Danza Kuduro* used to produce a "similar" shelf of… Danza Kuduro. The new
`DiscoveryService` is built the way the big services do it with metadata alone:

1. **Artist radio** — Deezer's `/artist/{id}/radio` returns a smart mix of the seed artist *and*
   related artists in one keyless draw (verified live: Daft Punk radio pulls in Madcon,
   Metronomy, …).
2. **Related-artist fan-out** — `/artist/{id}/related` tops broaden the pool beyond one radio draw.
3. **Hard diversity rules** — the seed artist is banned from its own "similar" shelf, no artist
   appears more than twice, no song you've already heard or saved is recommended back, and picks
   are round-robin interleaved across inferred moods so one row isn't 20 copies of the same vibe.
4. **Daily Mixes** — up to four mixes seeded from different slices of your profile (artists,
   categories, moods); every mix excludes the tracks already used by previous mixes.
5. **Mood inference** (`MoodClassifier`) — keyword tables over genre/title metadata bucket tracks
   into Chill / Energy / Focus / Party / Workout / Romance / Drive / Throwback, powering
   "because you've been playing {category}" shelves and the mood grid.
6. **Listening profile** — built from history + favourites: top artists, top categories (genres
   mapped to iTunes chart feeds), top moods, and a signature set (`normalize(title)|artist`) used
   as the global "never repeat" list. The same signature also collapses the same song across
   providers.

## 🖼️ Metadata & artwork quality

Provider thumbnails are rewritten to the biggest rendition each CDN serves — YouTube
`maxresdefault`, iTunes/mzstatic `1200x1200bb`, Deezer `1000x1000`, SoundCloud `t500x500`, Audius
`1000x1000` — with an automatic step-down ladder (`hqdefault`, `600x600`, …) if a CDN 404s, so
covers are sharp but never broken. Enrichment now requires **both** title and artist to match
before trusting external metadata (matching on either alone used to attach the wrong album/year/
cover), and it fills album, genre, year and art in one bounded-parallel pass.

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
14. **History repeated songs** — every play inserted a new row, so history filled with duplicates.
    v3 keeps one row per song (replays bubble it to the top) and migrates existing duplicates away.
15. **"Similar" was the same song** — the because-you-listen shelf searched the seed artist again.
    Replaced by the diversity engine above (artist radio + related-artist fan-out + hard caps).
16. **Wrong metadata / blurry covers** — enrichment matched on title *or* artist (so any same-title
    song could donate its album and art), and provider thumbnails were used as-is. Matching is now
    strict and all artwork is upgraded to hi-res with a fallback ladder.
17. **Audius silently returned nothing** — its JSON grew a `mirrors` array inside `artwork`, which
    broke the old `Map<String, String>` decoder and emptied every Audius list. Now parsed properly
    (plus mood/bpm metadata).

## 🏗️ Build

1. Open the project in Android Studio (Koala or newer) and let Gradle sync.
2. Run on a device or emulator (Android 8.0 / API 26+).
3. Or grab the debug APK from the **Build debug APK** workflow artifact.

## Architecture

```
com.tunely.app/
├── data/
│   ├── Models.kt           # Track (+ source, kind, uid), Lyrics, sleep timer
│   ├── Sources.kt          # StreamingSource + the eight providers (artist radio, charts, lookups)
│   ├── SourceRegistry.kt   # parallel fan-out search, fallback resolution, quality
│   ├── Discovery.kt        # recommendation engine: profiles, daily mixes, diversity, charts
│   ├── Moods.kt            # moods, browse categories, genre → mood/category inference
│   ├── ArtistRepository.kt # artist pages (Deezer identity + iTunes discography + Audius bio)
│   ├── ArtworkUrls.kt      # hi-res artwork URL upgrade + graceful degradation ladder
│   ├── MusicCatalog.kt     # bounded-parallel metadata enrichment (strict matching)
│   ├── SettingsManager.kt  # SharedPreferences → StateFlows, with legacy migration
│   ├── AppDatabase.kt      # Room v3 (favourites, deduped history, playlists) + migrations
│   ├── LyricsRepository.kt # LRCLIB (search + duration match) → Lyrics.ovh, title cleaning
│   ├── LrcParser.kt        # LRC / enhanced LRC (word timings, offsets, multi-tags)
│   └── Http.kt             # shared OkHttp helpers, user agents, result interleaving
├── player/
│   ├── MediaUri.kt         # tunely://direct|track/<source>/<id>
│   ├── PlaybackService.kt  # Media3 session, lazy resolution, 15 min URL cache, fade & blend
│   ├── AudioEffects.kt     # equalizer presets + loudness enhancer
│   └── PlayerController.kt # Compose-facing controller, queue as source of truth
└── ui/
    ├── MainViewModel.kt    # search, discovery, overlays (artist/category), library, playback
    ├── design/             # Tokens, Motion, Aurora, Artwork, Components, Sheets, Accents
    └── screens/            # Home, Explore, Artist, Category, Search, Library, Settings,
                            # NowPlaying, Lyrics, actions
```

## ⚠️ Disclaimer

- Streaming uses unofficial extraction through **NewPipeExtractor** plus public APIs. This may
  violate a provider's terms of service and can break when they change their internals. Personal use,
  at your own risk.
- Chart data, previews, artwork and metadata come from Deezer, the iTunes Search API,
  Audius and radio-browser.info.
- Not suitable for Play Store distribution because of extraction-based streaming.
