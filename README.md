# Tunely

A free Android music player with an Apple Music-style interface that streams audio from YouTube Music.
Kotlin, Jetpack Compose, Media3.

## Build

1. Open the `Tunely` folder in Android Studio (Koala or newer).
2. Let Gradle sync (a Gradle wrapper is not included; Android Studio creates one, or run `gradle wrapper`).
3. Run on a device or emulator (Android 8.0 / API 26+).

This project has not been compiled or run yet. Expect to fix a few compile errors on first sync.

## What works in this first version

- Search YouTube Music songs, play with background playback, lock-screen and notification controls
- Queue, play next, add to queue, skip, shuffle, repeat
- Home (recently played), Search, Library (favorites) tabs
- Full-screen Now Playing with artwork-tinted animated gradient, mini player, queue sheet
- Lyrics screen: auto-scroll, blur and dim on inactive lines, per-word fill when the source gives word timing
- Metadata and hi-res (1200px) artwork from the iTunes Search API
- Lyrics providers are pluggable (`LyricsProvider`); LRCLIB is wired in

## Not done yet (and honest limits)

- Word-by-word lyrics need a source with word timing (enhanced LRC). LRCLIB mostly has line timing, so words are interpolated across each line. More providers must be added in `LyricsRepository.kt`.
- Apple Music features that depend on Apple's catalog or licensing are not possible: lossless and Dolby Atmos, Apple's editorial playlists, radio stations, and music videos.
- Not yet built: playlists UI, artist and album pages, downloads/offline, crossfade, equalizer, sleep timer, Android Auto, cloud sync.
- Streaming uses unofficial extraction through NewPipeExtractor. This is against YouTube's terms of service, can break whenever YouTube changes, and is not suitable for the Play Store. Use it for personal use at your own risk.
- The UI imitates Apple Music's look and feel, but uses its own name and no Apple logos or assets.
