# NovaBeat

A multi-source aggregated Android local music player integrating 8 music platforms: Netease Cloud Music, QQ Music, Kugou, Kuwo, Qishui Music, Bilibili, Spotify, and YouTube Music.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Project Architecture](#project-architecture)
- [Module Specification](#module-specification)
  - [Data Layer (data)](#data-layer-data)
  - [Service Layer (service)](#service-layer-service)
  - [UI Layer (ui)](#ui-layer-ui)
  - [Audio Effect (audio)](#audio-effect-audio)
  - [Download Manager (download)](#download-manager-download)
- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Build and Run](#build-and-run)
  - [Version Management](#version-management)
- [API Service Reference](#api-service-reference)
- [Authentication Mechanism](#authentication-mechanism)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**NovaBeat** is an Android native music player application built entirely with **Kotlin** and the **Jetpack** ecosystem. Its core design philosophy is to aggregate disparate online music sources into a unified search and playback interface while maintaining full local audio library management capabilities.

The application integrates with 8 music platforms through reverse-engineered or semi-public REST APIs, enabling users to search, stream, and download music from any supported platform within a single application. Audio playback is powered by **ExoPlayer (Media3)** with full equalizer support, real-time audio visualization, and customizable audio effects.

---

## Features

### Playback Core

- **Multi-source Aggregated Search**: Unified search across 8 platforms — Netease Cloud Music, QQ Music, Kugou, Kuwo, Qishui Music, Bilibili, Spotify, YouTube Music.
- **Local Music Scanning**: Uses `MediaStore` to scan device-local audio files; automatically filters audio files shorter than 5 seconds.
- **10-band Graphic Equalizer**: 6 built-in presets (Flat, Pop, Rock, Classical, Electronic, Jazz) with full manual fine-tuning.
- **Audio Effects**: 6 real-time effects via `EnvironmentalReverb` and `PresetReverb` — Original, Vocal Remover (simulated), Ethereal, Bass Boost, Studio, Concert.
- **Real-time Spectrum Visualization**: 7-band FFT-based visualizer using `android.media.audiofx.Visualizer`.
- **Variable Speed Playback**: 0.5x to 2.0x continuous speed adjustment via `PlaybackParameters`.

### User Interaction

- **Splash Animation**: `AnimatorSet`-driven elastic scaling and fade-in sequence.
- **Material 3 Dynamic Theming**: `DynamicColors` integration for wallpaper-aware color adaptation.
- **Synchronized Lyrics**: LRC parsing from Netease Cloud Music with real-time current-line highlighting.
- **Lyric Animation**: 5 transition effects — Scroll, Fade, Slide, Zoom, Karaoke.
- **Custom WaveSeekBar**: Custom `View` implementation with animated sine-wave progression indicator.
- **4-tab Navigation**: Player / Library / Search / Equalizer via `ViewPager2` + `TabLayout`.

### Authentication

- **Third-party OAuth Login**: WebView-based login for 10 platforms (QQ, WeChat, Weibo, GitHub, Netease, Bilibili, QQ Music, Kuwo, Kugou, Qishui). Cookies are captured and persisted in `SharedPreferences`.
- **Local Email Authentication**: Registration and login with SHA-256 + Salt password hashing, stored in local `SharedPreferences`.

### Download Management

- **Multi-platform Download**: Download from any supported source to `Music/NovaBeat/` directory.
- **Duplicate Detection**: Skips re-download if the file already exists and is non-empty.

### Diagnostics

- **About Page**: Version information, feature listing, supported sources, donation QR codes (WeChat/Alipay/Appreciation code with full-screen preview).
- **Global Crash Handler**: Automatic uncaught exception capture with device info, stack trace, and timestamp written to `crash_log.txt`.

---

## Technology Stack

| Category            | Technology        | Version   |
|---------------------|-------------------|-----------|
| Language            | Kotlin            | 2.1.0     |
| Build System        | Gradle (AGP)      | 8.13.0    |
| UI Framework        | ViewBinding + Material 3 | 1.13.0 |
| Playback Engine     | ExoPlayer (Media3)| 1.2.1     |
| Image Loading       | Glide             | 4.16.0    |
| HTTP Client         | HttpURLConnection | (native)  |
| JSON Parsing        | org.json + Gson   | 2.11.0    |
| Async Framework     | Kotlin Coroutines | 1.9.0     |
| Lifecycle           | Lifecycle Runtime KTX | 2.8.0  |
| Layout              | ConstraintLayout  | 2.2.1     |
| Min SDK             | Android 7.0 (API 24) | —       |
| Target SDK          | Android 14 (API 36) | —       |

---

## Project Architecture

The project follows a **concise layered architecture** without a formal MVVM framework. Playback state is published via `Service` + `StateFlow`, which UI components subscribe to using `Lifecycle.repeatOnLifecycle` + `collectLatest`.

```
NovaBeat/
├── app/                                    # Application module
│   ├── build.gradle.kts                    # Module build configuration
│   ├── proguard-rules.pro                  # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml             # Manifest (permissions, activities, service)
│       ├── assets/donations/               # Donation QR code images
│       ├── kotlin/com/novabeat/music/
│       │   ├── NovaBeatApp.kt              # Application entry point + crash handler
│       │   ├── audio/
│       │   │   └── AudioEffectManager.kt   # Audio effect manager (reverb, etc.)
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   └── LocalAccountManager.kt  # Local email account manager
│       │   │   ├── model/
│       │   │   │   └── SongModels.kt       # Data models
│       │   │   ├── remote/                 # 8 API service implementations
│       │   │   │   ├── NeteaseApiService.kt
│       │   │   │   ├── QQMusicApiService.kt
│       │   │   │   ├── KugouApiService.kt
│       │   │   │   ├── KuwoApiService.kt
│       │   │   │   ├── QishuiApiService.kt
│       │   │   │   ├── BilibiliApiService.kt
│       │   │   │   ├── SpotifyApiService.kt
│       │   │   │   └── YouTubeMusicApiService.kt
│       │   │   └── repository/
│       │   │       └── MusicRepository.kt  # Unified repository facade
│       │   ├── download/
│       │   │   └── SongDownloadManager.kt  # Download manager
│       │   ├── service/
│       │   │   └── PlayerService.kt        # Foreground playback service
│       │   └── ui/
│       │       ├── AboutActivity.kt        # About page
│       │       ├── BilibiliLoginActivity.kt# Bilibili-specific login
│       │       ├── LoginActivity.kt         # Login hub
│       │       ├── MainActivity.kt          # Main activity (ViewPager + TabLayout)
│       │       ├── ScreenAdapter.kt         # FragmentPagerAdapter
│       │       ├── SongDetailActivity.kt    # Song detail page
│       │       ├── SplashActivity.kt        # Splash screen with animation
│       │       ├── WebLoginActivity.kt      # Generic WebView login
│       │       ├── components/              # Reusable UI components (placeholder)
│       │       ├── screens/
│       │       │   ├── PlayerFragment.kt    # Player tab
│       │       │   ├── LibraryFragment.kt   # Library tab
│       │       │   ├── SearchFragment.kt    # Search tab
│       │       │   └── EqualizerFragment.kt # Equalizer tab
│       │       └── views/
│       │           └── WaveSeekBar.kt       # Custom wave-shaped seek bar
│       ├── res/                            # Resources
│       │   ├── layout/                     # 15 layout XML files
│       │   ├── drawable/                   # Icons and drawables
│       │   ├── values/                     # strings.xml, colors.xml, themes.xml
│       │   └── ...
│       └── ...
├── gradle/
│   ├── libs.versions.toml                  # Version catalog
│   └── wrapper/                            # Gradle wrapper
├── build.gradle.kts                        # Root build file
├── settings.gradle.kts                     # Project settings
├── gradle.properties                       # Gradle properties
├── gradlew / gradlew.bat                   # Wrapper scripts
├── .gitignore                              # Git ignore rules
└── README.md                               # This file
```

---

## Module Specification

### Data Layer (data)

#### model — `SongModels.kt`

Defines all core data structures:

| Class               | Description |
|---------------------|-------------|
| `Song`              | Song entity — id, title, artist, album, coverUrl, url, durationMs, format, bitrate, isLocal, filePath, source |
| `Playlist`          | Named collection of songs with system flag |
| `LyricLine`         | Single lyric line with timestamp, text, and optional translation |
| `LyricResult`       | Parsed lyrics with raw LRC and translation text |
| `PlaybackState`     | Immutable snapshot of playback — isPlaying, currentSong, position, duration, mode, queue, index |
| `PlaybackMode`      | Enum — SEQUENCE, LOOP_ONE, LOOP_ALL, SHUFFLE |
| `RepeatMode`        | Enum — OFF, ONE, ALL |
| `EqualizerPreset`   | Named preset with band gain values |
| `VisualizerConfig`  | Visualizer type, color, and enabled state |
| `VisualizerType`    | Enum — BARS, WAVE, CIRCLE, NONE |
| `RemoteServer`      | Remote storage server configuration (WebDAV/SMB, placeholder) |
| `ServerType`        | Enum — WEBDAV, SMB, LOCAL |
| Platform-specific   | `NeteaseSearchResult`, `NeteaseSong`, `NeteaseArtist`, `NeteaseAlbum`, `MusicUrlResult`, `MusicUrlData` |

#### remote — API Services

Each `*ApiService.kt` follows a consistent contract:

- `searchMusic(keyword: String) -> List<XxxSong>` — Platform-specific search
- `getPlayUrl(id: String) -> String` — Resolve playable audio URL
- `withCookie(cookie: String) -> XxxApiService` — Immutable cookie injection returning a new instance
- Utilizes `HttpURLConnection` with `kotlinx.coroutines.Dispatchers.IO`

| Platform          | Class                   | API Characteristics |
|-------------------|-------------------------|---------------------|
| Netease Cloud     | `NeteaseApiService`     | Official API + Meting fallback; LRC parsing; multi-endpoint failover |
| QQ Music          | `QQMusicApiService`     | Search + play URL resolution |
| Kugou             | `KugouApiService`       | File hash-based URL resolution |
| Kuwo              | `KuwoApiService`        | Resource ID (rid) based resolution |
| Qishui Music      | `QishuiApiService`      | ByteDance-affiliated platform API |
| Bilibili          | `BilibiliApiService`    | BVID-based audio/video stream resolution |
| Spotify           | `SpotifyApiService`     | OAuth Bearer Token authentication |
| YouTube Music     | `YouTubeMusicApiService`| API Key + Cookie hybrid mode |

#### repository — `MusicRepository`

Singleton-style facade aggregating all `ApiService` instances. Provides:

- `scanLocalMusic(): Flow<List<Song>>` — Stream-based local music scanning via `MediaStore`
- `searchNetease`, `searchBilibili`, `searchQQMusic`, `searchKuwo`, `searchKugou`, `searchQishui`, `searchSpotify`, `searchYouTubeMusic`
- `resolveNeteaseUrl`, `resolveBilibiliUrl`, `resolveQQMusicUrl`, `resolveKuwoUrl`, `resolveKugouUrl`, `resolveQishuiUrl`, `resolveSpotifyUrl`, `resolveYouTubeMusicUrl`
- `fetchLyric(songId): LyricResult` — Netease lyric retrieval + LRC parsing
- `fetchAlbumCover(songId): String` — Album cover URL resolution
- `getRandomShareText(): String` — Random share text generator

#### local — `LocalAccountManager`

Local email-based authentication manager:

- Stores credentials in `SharedPreferences("novabeat_accounts")`
- Password hashing: `SHA-256(password + salt)` -> Base64-encoded
- Salt: hardcoded per-application constant
- Email format validation via `android.util.Patterns.EMAIL_ADDRESS`
- Methods: `register(email, password)`, `login(email, password)`, `getNickname(email)`, `accountExists(email)`

### Service Layer (service)

#### `PlayerService` — Foreground Playback Service

The core playback engine wrapped as an Android foreground service.

**Architecture details:**

- **Playback Engine**: `ExoPlayer` (Media3) configured with `AudioAttributes` for media usage and automatic audio-focus handling (`setHandleAudioBecomingNoisy(true)`).
- **MediaSession**: `MediaSession.Builder(this, exoPlayer)` enables system-level media controls (lock screen, notification).
- **Foreground Service**: `startForeground(NOTIFICATION_ID, notification)` with a custom `NotificationCompat.Builder` with playback control actions (play/pause, next, previous, stop).
- **Queue Management**: Mutable list-backed queue with `setQueue()`, `playSong()`, `playNext()`, `playPrev()`, `playFromQueue()`.
- **State Publication**: `MutableStateFlow<PlaybackState>` exposes playback state; UI subscribes via `StateFlow.collectLatest()`.
- **Equalizer Integration**: Reflectively accesses ExoPlayer's `audioSessionId` to instantiate `android.media.audiofx.Equalizer`. Contains 6 built-in presets as hardcoded band-gain maps.
- **Speed Control**: Set via `PlaybackParameters(speed)`.
- **Progress Tracking**: `Handler(Looper.getMainLooper())` runs a `Runnable` at 500ms intervals to emit `currentPositionMs`.
- **Favorite Management**: In-memory `MutableSet<String>` tracking favorited song IDs.

**Notification Actions:**
- `ACTION_PLAY_PAUSE` — Toggle playback
- `ACTION_NEXT` — Next track
- `ACTION_PREV` — Previous track
- `ACTION_STOP` — Stop service

### UI Layer (ui)

#### Activities

| Activity               | Purpose | Key Technical Details |
|------------------------|---------|----------------------|
| `SplashActivity`       | Launch splash | `AnimatorSet` with `ObjectAnimator` (scaleX, scaleY, alpha, translationY); `OvershootInterpolator`; 2.5s delay before transition |
| `MainActivity`         | Main container | `DynamicColors.applyToActivityIfAvailable()`; `ViewPager2` + `TabLayout` with `TabLayoutMediator`; 4 tabs |
| `SongDetailActivity`   | Song detail | Playback control, synchronized lyrics, EQ, audio effects, download, favorite toggle, share (clipboard) |
| `LoginActivity`        | Auth hub | 10 platform buttons launching `WebLoginActivity`; collapsible email login section |
| `WebLoginActivity`     | Generic WebView login | Loads platform OAuth URL; intercepts navigation to capture `Set-Cookie` |
| `BilibiliLoginActivity`| Bilibili login | QR code scan + manual SESSDATA input |
| `AboutActivity`        | About page | Version from `PackageManager`; donation QR code loading from assets with full-screen dialog |

#### Fragments

| Fragment            | Tab Label | Functionality |
|---------------------|-----------|---------------|
| `PlayerFragment`    | Player    | Current playing info, 7-band FFT visualizer (`android.media.audiofx.Visualizer`), queue list, share |
| `LibraryFragment`   | Library   | Sectioned display — local music / favorites / recent / queue; login button with `startActivityForResult` |
| `SearchFragment`    | Search    | Keyword search with 8-source chip selector; recommendation button; `SearchResultAdapter` with Glide cover loading |
| `EqualizerFragment` | Equalizer | 10 SeekBar sliders for fine EQ control; 6 preset chips; reset button; retry logic for EQ initialization |

#### Custom Views

`WaveSeekBar` — Custom `View` subclass for animated progress indication:

- Dynamic sine-wave animation via `ValueAnimator.INFINITE`
- Played portion rendered with `LinearGradient` shader
- Touch-based drag interaction with `OnWaveProgressChangeListener` callback
- Visual feedback with thumb halo during drag

### Audio Effect (audio)

#### `AudioEffectManager`

Manages `android.media.audiofx.EnvironmentalReverb` and `PresetReverb` for 6 audio effects:

| Effect           | Implementation |
|------------------|----------------|
| Original (NONE)  | Disables all effects |
| Vocal Remover    | EQ-based mid-frequency boost simulation |
| Ethereal         | EnvironmentalReverb with large room size, 8000ms decay time, HF EQ boost |
| Bass Boost       | Low-frequency reverb + LF EQ boost |
| Studio           | `PresetReverb.PRESET_SMALLROOM` |
| Concert          | `PresetReverb.PRESET_LARGEHALL` |

### Download Manager (download)

#### `SongDownloadManager`

- **Output Path**: `Music/NovaBeat/{artist} - {title}.{ext}`
- **Duplicate Handling**: Skips download if `{path}` exists and file length > 0
- **HTTP Details**: GET request with 30s connect/read timeouts, 8KB buffer, progress logging
- **No Platform Restrictions**: Works with any URL resolved by the API services

---

## Quick Start

### Prerequisites

| Tool              | Requirement       |
|-------------------|-------------------|
| JDK               | 17 or later       |
| Android SDK       | API 36 (compile), API 24 (runtime) |
| Gradle            | 8.13.0+ (wrapper-managed) |
| Android Studio / AndroidIDE | Latest stable |

### Build and Run

```bash
# 1. Clone the repository
git clone https://github.com/2386866276/NovaBeat.git
cd NovaBeat

# 2. (Optional) Set Android SDK path
# echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 3. Build debug APK
./gradlew assembleDebug

# 4. Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

For development on **AndroidIDE**, open the project directory directly; the IDE will automatically detect the SDK path.

### Version Management

Version is defined in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 1          // Internal version, increment per release
    versionName = "26.1.0"   // Display version name
}
```

---

## API Service Reference

All network APIs in this project are **unofficial, reverse-engineered implementations** provided for educational and research purposes only.

| Platform          | Search Endpoint                          | Play URL Resolution       | Auth Required |
|-------------------|------------------------------------------|---------------------------|---------------|
| Netease Cloud     | `music.163.com/api/search/get/web`       | Meting proxy + 302 redirect | Cookie (optional, unlocks higher bitrate) |
| QQ Music          | QQ Music internal search API             | Play URL forwarding       | Cookie        |
| Kugou             | Kugou search API                         | File hash-based resolution| Cookie (optional) |
| Kuwo              | Kuwo search API                          | Resource ID (rid) parsing | Cookie (optional) |
| Qishui            | Qishui Music search API                  | Direct play URL           | Cookie (optional) |
| Bilibili          | Bilibili search API (BVID)               | Video/audio stream URL    | SESSDATA (required for high quality) |
| Spotify           | Spotify Web API (`/v1/search`)           | Track ID to play URL      | OAuth Bearer Token |
| YouTube Music     | YouTube search interface                 | Video ID to audio stream  | API Key + Cookie   |

**Note**: These endpoints are subject to change without notice. If a source becomes unavailable, inspect the corresponding `*ApiService.kt` for necessary endpoint updates.

---

## Authentication Mechanism

### 1. Third-Party OAuth Login (Recommended)

1. User taps a platform button in `LoginActivity`.
2. `WebLoginActivity` opens the platform's OAuth/login page in a `WebView`.
3. After user authentication, the WebView navigates to a redirect URL.
4. `WebLoginActivity` intercepts the navigation and extracts `Set-Cookie` / token parameters from the URL or response headers.
5. Credentials are returned to `LoginActivity` via `Intent.putExtra()`.
6. `LoginActivity` passes the result to the calling `Fragment`, which stores it in `SharedPreferences("novabeat")` with key `{platform}_cookie`.

Supported platforms: QQ, WeChat, Weibo, GitHub, Netease Cloud Music, QQ Music, Bilibili, Kuwo, Kugou, Qishui.

### 2. Local Email Registration/Login

- Password hashing: SHA-256 with a hardcoded per-app salt.
- Storage: `SharedPreferences("novabeat_accounts")`, keyed by email address.
- No plaintext password is persisted.
- Suitable for offline or anonymous usage scenarios.

---

## Contributing

Contributions are welcome. Please refer to [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## License

This project is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

```
NovaBeat - A multi-source aggregated Android music player
Copyright (C) 2024-2026 2386866276

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```