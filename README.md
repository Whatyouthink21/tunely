# Tunely 🎵

A premium Android music player with an **Apple Music-inspired glassmorphism UI** that streams audio from multiple sources.
Built with Kotlin, Jetpack Compose, and Media3.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-green) ![Media3](https://img.shields.io/badge/Media3-1.4.1-orange)

## ✨ Features

### 🎨 Premium UI with Glassmorphism
- **Frosted glass effects** on mini player, navigation bar, settings, and cards
- **Animated gradient backgrounds** derived from album artwork
- **Glowing active lyrics** with neon halo and shimmer word-by-word highlighting
- **Pulsing glow** behind artwork and play button during playback
- **Customizable accent colors** (Red, Blue, Purple, Green, Orange, Pink, Teal, Indigo)
- **Dark / Light / System theme** modes
- Smooth spring animations and transitions throughout

### 🎵 Multi-Source Streaming
- **YouTube Music** — primary source with full catalog
- **SoundCloud** — independent artists and remixes
- **Piped** — privacy-friendly YouTube frontend
- **Source filter chips** in search to pick your provider
- Per-source badges on track rows

### 📝 Lyrics
- **Word-by-word highlighting** with smooth color interpolation
- **Line-level synced lyrics** with auto-scroll and centering
- **Glowing active line** with pulsing neon halo (toggleable)
- **3 lyrics providers**: LRCLIB, Lyrics.ovh, and Deezer
- Adjustable font size (18–48sp)
- Tap any line to seek to that position
- Blur/dim on inactive lines for focus

### 🎛️ Apple Music-Style Settings
- **Audio**: Quality selector, playback speed (0.5x–2x), crossfade (0–12s), volume normalization, equalizer presets, gapless playback
- **Appearance**: Theme mode, accent color picker, glassmorphism toggle, animated backgrounds toggle
- **Lyrics**: Font size slider, glow effect toggle
- **Streaming Sources**: Enable/disable each provider independently
- **Playback**: Autoplay, stream caching

### 🎧 Playback
- Background playback with lock-screen and notification controls
- Queue management (play next, add to queue, skip, reorder)
- Shuffle and repeat (off / all / one)
- **Playback speed** control (0.5x – 2x)
- **Sleep timer** with quick presets (5, 10, 15, 30, 45, 60, 90 min)
- **Equalizer** with 9 presets (Flat, Bass Boost, Treble, Vocal, Electronic, Rock, Pop, Jazz, Classical)
- **Crossfade** between tracks (0–12 seconds)
- Volume normalization

### 📚 Library
- **Favorites** — heart any track to save it
- **Recently Played** — automatic history tracking
- **Playlists** — create and manage custom playlists
- **Quick tiles** — Liked, Recent, Downloads, Artists, Albums
- **Home screen** with horizontal artwork carousels

### 🔍 Search
- Multi-source search with filter chips
- Debounced search (350ms)
- Enriched metadata from iTunes (album, genre, year, explicit flag)
- High-res 1200px artwork from iTunes

## 🏗️ Build

1. Open the `Tunely` folder in Android Studio (Koala or newer).
2. Let Gradle sync (or run `gradle wrapper` first).
3. Run on a device or emulator (Android 8.0 / API 26+).

## Architecture

```
com.tunely.app/
├── data/
│   ├── Models.kt              # Track, Lyrics, SleepTimerState
│   ├── SettingsManager.kt     # SharedPreferences + reactive StateFlows
│   ├── YouTubeMusicRepository.kt
│   ├── SoundCloudRepository.kt
│   ├── PipedRepository.kt
│   ├── MetadataRepository.kt  # iTunes enrichment
│   ├── LyricsRepository.kt    # Multi-provider lyrics (LRCLIB, Lyrics.ovh, Deezer)
│   ├── LrcParser.kt           # LRC + enhanced LRC (word timing)
│   ├── AppDatabase.kt         # Room DB (library, history, playlists)
│   └── HttpDownloader.kt      # OkHttp adapter for NewPipe
├── player/
│   ├── PlaybackService.kt     # Media3 background service
│   └── PlayerController.kt    # Compose-friendly player state
├── ui/
│   ├── Theme.kt               # Dynamic theme + accent colors
│   ├── GlassComponents.kt     # Glassmorphism modifiers + components
│   ├── PlayerUi.kt            # Now Playing + Mini Player
│   ├── LyricsView.kt          # Premium lyrics with glow
│   ├── SettingsScreen.kt      # Full settings with bottom sheets
│   └── MainViewModel.kt       # App state + multi-source search
└── MainActivity.kt            # Navigation + Home/Search/Library screens
```

## ⚠️ Disclaimer

- Streaming uses unofficial extraction through **NewPipeExtractor**. This may violate YouTube's Terms of Service and can break when YouTube changes their API. Use for personal use at your own risk.
- The UI is inspired by Apple Music's design language but uses its own name and no Apple assets.
- Not suitable for Play Store distribution due to extraction-based streaming.
