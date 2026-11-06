# AGENTS.md

Guidance for AI coding agents working in this repo.

## Project

Moon Player is an offline Android music player for songs in folders the user picks (MP3, FLAC, M4A/AAC, Ogg/Opus, WAV). It goes online for two optional things only: LRCLIB lyrics lookups and reading Last.fm / Libre.fm scrobble stats.

- Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer + MediaSession, Room, DataStore, Coil 2.7.
- AGP 9.5, Gradle 9.9.0 (wrapper included), Kotlin 2.4, minSdk 24, targetSdk 36, compileSdk 37.
- Single module: `app`. Package `com.example`; applicationId `com.aistudio.moonplayer.mzrvqk`. The project started as an AI Studio export.
- Don't rename the applicationId, the `moon_player.db` database or the `moon_player_settings` DataStore: renaming wipes user data or splits installs.

## Layout

```
app/src/main/java/com/example/
  MainActivity.kt            entry point
  MoonPlayerApplication.kt   Application class (Coil ImageLoader with embedded-art fetcher)
  service/
    PlaybackService.kt       MediaSessionService: ExoPlayer, queue, shuffle order, sleep timer, A-B loop,
                             saving the playback position
    EqualizerHelper.kt       5-band equalizer and bass boost
  viewmodel/MainViewModel.kt the single app ViewModel (library, queue, lyrics, stats, settings)
  data/
    db/                      Room: MoonPlayerDatabase, Entities, Daos
    repository/              MusicRepository (folder scanning, playlists, favorites, history, saved queue)
    settings/                SettingsManager (DataStore) and PlayerSettings
    lyrics/                  .lrc parsing (Lyrics.kt), ID3 USLT/SYLT (Id3Lyrics.kt), MP4/Vorbis tags
                             (TagLyrics.kt), LyricsRepository (source pick, LRCLIB, cache)
    scrobble/                Last.fm / Libre.fm public stats
    stats/                   listening statistics maths
    SongInfo.kt, EmbeddedArt.kt
  ui/components/             screens and shared composables (player, lyrics, queue, library, settings...)
  ui/theme/
app/src/test/                JVM/Robolectric unit tests
.github/assets/screenshots/  README screenshots
```

## Build, test and install

Use the Gradle wrapper with JDK 17 or newer and an Android SDK that has platform 37.

```sh
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleRelease     # signed release APK
./gradlew :app:assembleDebug       # debug APK
```

On this machine the JDK, SDK and Gradle home live in `~/.cache/mangareader-build`, so pass them in:

```sh
B=~/.cache/mangareader-build
JAVA_HOME=$B/jdk ANDROID_HOME=$B/sdk GRADLE_USER_HOME=$B/gradle-home \
  ./gradlew -q :app:testDebugUnitTest :app:assembleRelease
```

Install on the connected phone:

```sh
adb devices                                                        # the phone is on wireless adb
timeout 120 adb install -r app/build/outputs/apk/release/app-release.apk
```

- Don't build, test or install unless asked. A quick build skips `testDebugUnitTest`.
- If `adb` reports `no devices/emulators found`, the wireless connection dropped. Ask the user to reconnect, then retry the install; don't rebuild.
- Install the **release** APK: it's signed with the upload key, so `install -r` updates the installed app and keeps the user's data. A debug APK has a different signature and can't install over it.
- Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` from the environment (CI), else `signing.storeFile`, `signing.storePasswordFile` and `signing.keyAlias` from `local.properties` (key alias `upload`). Never print these values.
- `:app:assembleDebug` needs `debug.keystore` at the repo root. It isn't committed.
- `versionCode` is the git commit count, and `versionName` comes from `-PappVersionName` (`dev` locally).
- Test results are written to `app/build/test-results/testDebugUnitTest/*.xml`.

## Git workflow

- Work on `dev` and open PRs from `dev` to `main`. `main` is release-only.
- `.github/workflows/build-and-release.yml` builds an APK on PRs to `main` (no release), publishes a pre-release **Moon Player Alpha N** on every push or merge to `main`, and a full release on a `v*` tag, with `checksums-sha256.txt`. CI doesn't run the unit tests, so run them locally.
- Don't push, open PRs or tag unless asked. Check whether a PR is already merged before pushing more commits to it.
- Use Conventional Commits: `feat(lyrics): ...`, `fix(stats): ...`, `docs: ...`, `build: ...`, `ci: ...`.
- Keep PR descriptions accurate; rewrite them when new commits land on an open PR.
- Never commit stray local files: `.codegraph/`, test audio, screen recordings, `debug.keystore`.

## Conventions and gotchas

- **Room schema:** `MoonPlayerDatabase` is at version 11 with explicit migrations from 4 upward (`MIGRATION_4_5` ... `MIGRATION_10_11`).
  - If you change an entity, bump the version and add a migration.
  - Only versions 1-3 fall back to a destructive migration. A missing migration crashes on upgrade on purpose, rather than wiping playlists, favorites and stats.
- **Settings:** add a field to `PlayerSettings`, a key in `SettingsManager`, a setter there and in `MainViewModel`, then a row in `SettingsAndAboutScreen.kt` (tabs: Player, Lyrics, Look, Library). Keep subtitles to one short line.
- **Storage:** library access goes through the Storage Access Framework (persisted tree URIs) only, with no storage permission.
- **Playback:**
  - The service saves the song and position every 5 s, on seek, in `onTaskRemoved` and in `onDestroy`. Never block in `onDestroy` (a `runBlocking` there caused an ANR); launch on `Dispatchers.IO`.
  - Turning shuffle on sets a fresh `DefaultShuffleOrder` from `freshShuffleOrder` before enabling it, otherwise Media3 replays the old order.
- **Media3 metadata:** `MetadataRetriever` lives in `media3-inspector` (`androidx.media3.inspector`). Build it with `MetadataRetriever.Builder(...)` and close it with `use { }`. `VorbisComment` is in `extractor.metadata.vorbis`, and its keys come back upper-case.
- **Lyrics:** three sources, `.lrc` beside the song, tags in the song file, and LRCLIB.
  - For each kind (Original, Romaji, Translation), the pick is the song's own choice from the source sheet, else a synced source if Prefer Synced is on, else the first in the user's source order.
  - LRCLIB has two settings: Use LRCLIB (lookups allowed on tap) and Look Up Lyrics Automatically. Don't contact LRCLIB when the first is off.
  - Every LRCLIB answer, "nothing found" included, is cached in Room so a song is never asked twice.
- **Network:** calls use `HttpURLConnection` and `org.json`. Don't add a network library.
- **Compose test tags:** UI elements use `Modifier.testTag(...)`, for example `shuffle_button`. Keep the existing tags when you change a composable.
- Match the surrounding code style: Compose state lives in `MainViewModel` as `StateFlow`, and screens collect it.
- When you add or change a user-facing feature, update its entry in `README.md`.
