<div align="center">

<img src=".github/icon.png" alt="Moon Player logo" width="96"/>

# Moon Player

### An offline player for the music on your phone

Play MP3, FLAC, M4A, OGG, Opus and WAV files from your own folders on Android. No ads, no accounts, no telemetry.

[![Release](https://img.shields.io/github/v/release/Varun-666/MoonPlayer?include_prereleases&label=Release&labelColor=27303D&color=0877d2)](https://github.com/Varun-666/MoonPlayer/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Varun-666/MoonPlayer/build-and-release.yml?labelColor=27303D)](https://github.com/Varun-666/MoonPlayer/actions/workflows/build-and-release.yml)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?labelColor=27303D)](#download)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue?labelColor=27303D)](LICENSE)

</div>

## Download

Grab the newest signed APK from [Releases](https://github.com/Varun-666/MoonPlayer/releases). Every merge to `main` publishes a new pre-release, **Moon Player Alpha N**, which installs over the previous one.

_Requires Android 7.0 (API 24) or higher._

## Features

- **Your folders, nothing else.** Pick one or more folders with Android's folder picker and Moon Player finds every song inside them, subfolders included. No storage permission, and the library loads instantly from its saved scan.
- **Player:** one row of controls (shuffle, previous, play, next, repeat), a large cover, and the song's format, bitrate and sample rate. The accent colour follows each album cover, or pick a preset or your wallpaper colour (Android 12+).
- **Synced lyrics:** tap the cover to swap it for lyrics that follow the song; tap a line to jump there. Lyrics come from a `.lrc` file next to the song or, if you allow it, from [LRCLIB](https://lrclib.net/). Japanese songs can switch between Original, Romaji and Translation. Lyrics are saved after the first lookup and work offline.
- **Queue:** swipe up from the player. Drag (long-press the handle) to reorder, Play Next from a song's menu, played songs fold away, and the whole queue can be saved as a playlist.
- **Playlists:** your own playlists with drag-to-reorder, plus every library folder as a live playlist that always matches its contents.
- **Browse:** all songs, albums, artists, folders, favorites and recently played. A mini player with previous, play/pause and next sits under every screen.
- **Sound:** 5-band equalizer with presets matching Android's own curves, bass boost, playback speed and an A-B loop, all in the player's ⋮ menu.
- **Sleep timer:** stop after a set time or at the end of the current song.
- **Statistics:** total listening time and plays, top tracks, top playlists and your library by format.
- **Plays well with Android:** media notification and lock-screen controls with cover art, headset buttons, pausing when headphones disconnect, and resuming your last queue on launch. Scrobblers such as [Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler) pick up what's playing.

## Supported formats

| Format                     | Status       |
| :------------------------- | :----------- |
| `.mp3`                     | ✅ Supported |
| `.flac`                    | ✅ Supported |
| `.m4a`, `.aac`             | ✅ Supported |
| `.ogg`, `.oga`, `.opus`    | ✅ Supported |
| `.wav`                     | ✅ Supported |

Cover art is read from the file's embedded artwork.

## Lyrics files

To use your own lyrics, put a `.lrc` file with the same name next to the song:

```text
Music/
└── J-pop/
    ├── Yoru ni Kakeru.mp3
    └── Yoru ni Kakeru.lrc
```

A `.lrc` file always wins over online lyrics. Plain text without timestamps is shown as unsynced lyrics.

## Privacy

- Folder access is limited to the folders you pick. The app never requests a storage permission.
- The only network use is the lyrics lookup, and only after you allow it (asked the first time you open lyrics, with a switch in Settings). A lookup sends the song's title, artist, album and length to LRCLIB, nothing else.
- Your library, playlists, favorites, statistics and saved lyrics stay on the device.
- No analytics, ads or crash reporting.

## Building from source

You need JDK 17 or newer and an Android SDK with platform 36.1. The Gradle wrapper (Gradle 9.3.1) is included.

```sh
git clone https://github.com/Varun-666/MoonPlayer.git
cd MoonPlayer
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK (needs debug.keystore at the repo root)
```

Create the debug keystore once with:

```sh
keytool -genkeypair -keystore debug.keystore -alias androiddebugkey -storepass android \
  -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug"
```

Release builds are signed with the key at `KEYSTORE_PATH` (alias `upload`), using `STORE_PASSWORD` and `KEY_PASSWORD` from the environment.

Built with Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer, Room and Coil.

## Contributing

Pull requests are welcome; for larger changes, open an issue first. Development happens on `dev`, and `main` is release-only since every merge to it ships a build. Commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

Distributed under the [MIT License](LICENSE).
