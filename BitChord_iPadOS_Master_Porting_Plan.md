# BitChord v1.8-beta1 to iPadOS (`.ipa`) — Master Engineering & Porting Plan

> **Document Purpose**: Authoritative Senior-Architect Blueprint and Task Execution Manual for Junior AI Coding Agents
> **Source Repository**: `https://github.com/kushagrasinghx/BitChord` (`1.8.0-beta1` / `versionCode = 113`)
> **Target Platform**: iPadOS 16.0+ (`iosArm64` physical iPad devices & `iosSimulatorArm64` Apple Silicon simulators)
> **Packaging Target**: Unsigned & developer-signed `.ipa` bundle (AltStore / SideStore / Sideloadly / TrollStore / TestFlight compatible)
> **Parity Mandate**: 100% 1:1 Functional, Visual, Audio DSP, Neural Beat/Vocal Analysis, and Smart Automix Parity with Android (`:app`) and Desktop (`:desktopApp`)

---

## 0. How to Use This Blueprint (Orchestrator & Subagent Rules)

This document is designed for an **AI-Orchestrated Engineering Workflow** where a **Senior Architect Agent** delegates self-contained **Work Packages (`WP-01` through `WP-12`)** to **Junior Coding Subagents**. Every subagent assigned a Work Package MUST obey these five ground rules:

1. **Do NOT assume `:shared` or `:sharedUi` code is already in `commonMain`**:
   - In the upstream BitChord v1.8-beta1 repository, **most business logic in `:shared` and 100% of Kotlin UI code in `:sharedUi` currently reside in `src/jvmSharedMain/kotlin/`** (a custom KMP source set shared only between `androidTarget()` and `jvm("desktop")` that still allows `java.*` imports!).
   - Before iOS (`iosArm64`, `iosSimulatorArm64`) can compile `:shared` or `:sharedUi`, files in `jvmSharedMain` must either be purified of `java.*` imports and moved to `commonMain`, or abstracted via `expect`/`actual` declarations in `commonMain` + `jvmSharedMain` + `iosMain`.
2. **Do NOT use `gradle/libs.versions.toml`**:
   - Upstream BitChord **does not have a `gradle/libs.versions.toml` file**. Dependency versions are declared directly inside root `build.gradle.kts`, `shared/build.gradle.kts`, `sharedUi/build.gradle.kts` (`val cmp = "1.10.3"`), and `desktopApp/build.gradle.kts`. Keep this convention or explicitly migrate coordinates in those exact files.
3. **Do NOT rewrite the C++ DSP Analyzer (`native/analyzer/`) in Swift or `Accelerate.framework`**:
   - Upstream BitChord already contains a **100% portable, dependency-free standard C++17 audio analysis engine** in `native/analyzer/` (`audio_analysis.cpp`, `tempo_analysis.cpp`, `resampler.cpp`, `mel_spectrogram.cpp`, `vocal_spectrogram.cpp`) and JNI serialization glue in `app/src/main/cpp/jni/` (`analysis_jni.cpp`, `mel_jni.cpp`, `vocal_jni.cpp`).
   - As `desktopApp/native/CMakeLists.txt` explicitly warns, the STFT, Hann window (`periodic=true`), Slaney mel filterbank, and magnitude scaling in `native/analyzer/` are **mathematically calibrated to match `torchaudio` (`CPJKU/beat_this`) and `open-unmix` (`TorchSTFT`)**. Rewriting them in `vDSP` will shift spectrogram magnitudes, corrupt ONNX inference, and break `TrackFeatures.kt`'s JSON parser. Compile the existing `.cpp` files directly for iOS (`arm64`)!
4. **Never pass raw YouTube webpage URLs (`https://music.youtube.com/watch?v=...`) to `AVPlayer` or `AVAudioFile`**:
   - Every stream must be resolved through `YtMusicRepository` + `DesktopPlaybackResolver` + `DesktopCipher` (porting the JS signature decipherer + `n`-parameter transform + `WKWebView` PoToken generator) and proxied or downloaded with authenticated HTTP headers (`User-Agent`, `Cookie`, `Origin`, `Referer`, `Range`).
5. **Respect each Work Package's File Ownership Boundary**:
   - Do not modify files outside your assigned Work Package's scope without explicit approval from the Senior Architect Agent.

---

## 1. Upstream Repository Architecture Audit (Verified Against Source & Runtime Binary)

### 1.1 Exact Build & Dependency Baseline (`build.gradle.kts` + `BitChord.cfg`)

Verified against the live GitHub repository (`https://github.com/kushagrasinghx/BitChord`) and the installed runtime configuration (`C:\Users\supha\AppData\Local\BitChord\app\BitChord.cfg`):

| Component / Library | Upstream Version / Coordinate | Where Declared | iOS Porting Disposition |
| :--- | :--- | :--- | :--- |
| **Kotlin Multiplatform** | `2.3.20` | Root `build.gradle.kts` | Add `iosArm64()`, `iosSimulatorArm64()` targets to `:shared` & `:sharedUi` |
| **Compose Multiplatform** | `1.10.3` (`val cmp = "1.10.3"`) | Root & `sharedUi/build.gradle.kts` | 100% KMP iOS (`Skiko` / Metal backend) |
| **Material3 / Material3 Adaptive** | `1.9.0` / `1.2.0` | `sharedUi/build.gradle.kts` | 100% KMP iOS compatible |
| **Liquid (Glassmorphism shader)** | `io.github.fletchmckee.liquid:liquid:1.1.1` | `sharedUi/build.gradle.kts` | 100% KMP Compose (uses Skia RuntimeEffect shaders on iOS/Desktop) |
| **Coil 3 Image Loader** | `io.coil-kt.coil3:coil-compose:3.4.0` + `coil-network-okhttp:3.4.0` | `sharedUi/build.gradle.kts` | **Swap `coil-network-okhttp` for `coil-network-ktor3:3.4.0` in `commonMain`** |
| **Ktor Client** | `3.4.0` (`ktor-client-core`, `ktor-client-okhttp`, `ktor-client-cio`, `ktor-client-websockets`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`) | `shared/build.gradle.kts`, `desktopApp/build.gradle.kts` | Move `ktor-client-core`, `websockets`, `content-negotiation`, `json` to `commonMain`; add **`ktor-client-darwin:3.4.0`** to `iosMain` |
| **Kotlinx Serialization** | `1.10.0` (`json`, `protobuf`) | `shared/build.gradle.kts` | Move `protobuf` to `commonMain` alongside `json` |
| **Kotlinx Coroutines** | `1.10.2` (`kotlinx-coroutines-core`, `kotlinx-coroutines-swing`) | `shared/build.gradle.kts` | Move `kotlinx-coroutines-core:1.10.2` to `commonMain` |
| **Brotli Decompression** | `org.brotli:dec:0.1.2` (JVM) | `shared/build.gradle.kts` (`jvmSharedMain`) | Needed by `innertubex`; bridge on iOS via `libcompression` (`COMPRESSION_BROTLI`) or pure-Kotlin/C brotli |
| **InnerTubeX** | `com.github.kushagrasinghx:innertubex:v0.7.0` | `shared/build.gradle.kts` (`jvmSharedMain`) | Currently published as a JVM artifact via JitPack. **Must vendor `kushagrasinghx/innertubex` (`v0.7.0`) as a local KMP module `:innertubex` (or source set) with `commonMain` + `iosMain`** |
| **ONNX Runtime** | `com.microsoft.onnxruntime:onnxruntime:1.20.0` (Desktop JVM) / `onnxruntime-android` | `desktopApp/build.gradle.kts` | Bridge on iOS via CocoaPods `onnxruntime-c` (`1.20.0`) + Kotlin/Native `cinterop` |
| **Native C++ Analyzer** | `native/analyzer/*.cpp` (`bitchord_analysis.dll` / `.so` / `.dylib`) | `desktopApp/native/CMakeLists.txt` & `app/src/main/cpp/CMakeLists.txt` | Compile identical `native/analyzer/*.cpp` + C JSON bridge into static library `libbitchord_analysis.a` for `iosArm64` & `iosSimulatorArm64` |
| **Desktop Audio Engine** | `xt.audio` (`PortAudio` / WASAPI / CoreAudio) + `JavaSound` + `mp3spi` + `jorbis` + `Concentus` (Opus) + `FFmpeg`/`ffprobe` subprocess | `desktopApp/build.gradle.kts` | Replace on iPadOS with native **`AVAudioEngine` + Dual `AVAudioPlayerNode` + `AVAudioUnitEQ` (10-band) + `AVAudioUnitTimePitch` + `ExtAudioFileRead`** |
| **Desktop Persistence** | `org.xerial:sqlite-jdbc:3.49.1.0` (`DesktopDatabase.kt`, `DesktopCacheStore.kt`, `DesktopLibraryStore.kt`) | `desktopApp/build.gradle.kts` | Port `DesktopDatabase.kt` schema & stores (`DesktopCacheStore`, `DesktopLibraryStore`, `DesktopListeningStats`) to KMP SQLite (`androidx.sqlite:sqlite-bundled:2.5.0` or native `sqlite3` on iOS) |

---

### 1.2 Complete File-Level Inventory of the Upstream Repository

Junior AI agents MUST understand the exact layout of all four Gradle modules (`:shared`, `:sharedUi`, `:desktopApp`, `:app`) and `native/analyzer`:

#### Module 1: `:shared` (`shared/build.gradle.kts`)
Currently defines `commonMain`, `jvmSharedMain` (shared by `androidMain` and `desktopMain`), `androidMain`, and `desktopMain`:

* **`shared/src/commonMain/kotlin/com/music/bitchord/`** *(Already pure KMP — compiles on iOS out-of-the-box once targets are added)*:
  * `data/listentogether/`:
    * `ListenTogetherClient.kt` (Pure KMP WebSocket/protocol state machine)
    * `ListenTogetherCodec.kt` (Message framing/serialization)
    * `ListenTogetherModels.kt` (Room state, member models, sync payloads)
    * `ListenTogetherProtocol.kt` (OpCodes, heartbeat, drift sync constants)
    * `ListenTogetherSyncController.kt` (Play/pause/seek/track sync coordinator)
    * `ListenTogetherTransport.kt` (Transport interface for Ktor WebSockets)
  * `data/model/`:
    * `Models.kt` (Core domain models: `Track`, `Album`, `Artist`, `Playlist`, `HomeSection`, `SearchResult`, etc.)
  * `data/settings/`:
    * `LyricsBackgroundEffect.kt`, `PlayerLayoutMode.kt`, `StreamingQuality.kt`, `TimeTransferMode.kt`
  * `playback/`:
    * `EqualizerCurve.kt` (Biquad frequency response math for UI curve rendering)
    * `EqualizerPreset.kt` (22 built-in 10-band EQ presets: `31Hz`–`16kHz`)
    * `smart/PlaybackSmartController.kt` (Core Smart Automix state machine, transition trigger window calculation, time-transfer speed ramp curves, crossfade volume envelopes, and vocal-aware EQ automation)
    * `smart/PlaybackSmartModels.kt` (`SmartTransitionPlan`, `TransitionType`, `CuePoint`, `PhraseBoundary`, `MixWarning`, etc.)

* **`shared/src/jvmSharedMain/kotlin/`** *(CRITICAL: Currently restricted to JVM/Android! Must be migrated to `commonMain` + `iosMain` in `WP-02` & `WP-03`)*:
  * `com/music/bitchord/YtMusicRepository.kt` (Primary repository wrapping `InnerTube` for home feed, search, artist/album/playlist details, player stream extraction, up-next radio, library mutations, and account info)
  * `com/music/bitchord/auth/`:
    * `AccountIdentity.kt`, `YouTubeAuthSession.kt`, `YouTubeCookieAuth.kt` (SAPISIDHASH generator — uses `java.security.MessageDigest("SHA-1")` and `java.lang.System.currentTimeMillis()`, must be replaced with KMP SHA-1 / `Clock.System`)
  * `com/music/bitchord/data/canvas/`:
    * `CanvasArtwork.kt` (Apple Music / Spotify animated video artwork resolver)
    * `SpotifyCanvasQuery.kt` (Spotify Canvas video lookup client)
  * `com/music/bitchord/data/innertube/`:
    * `YouTubeBrowseIds.kt`
  * `com/music/bitchord/data/lyrics/`:
    * `KuGouLyricsSource.kt`, `LrcLibLyricsSource.kt`, `LrcParser.kt`, `LyricsModels.kt`, `LyricsRegistry.kt`, `LyricsSource.kt`, `NetEaseLyricsSource.kt`, `PaxsenixLyricsSource.kt`, `SimpMusicLyricsSource.kt`, `TextSimilarity.kt`, `YouTubeLyricsSource.kt`, `YouTubeSubtitleLyricsSource.kt`
  * `com/music/bitchord/data/presence/`:
    * `Presence.kt` (Rich Presence / status model)
  * `com/music/bitchord/data/sources/`:
    * `SourceTrackKeys.kt`, `StreamFormat.kt`, `TrackMatcher.kt` (Cross-platform ISRC/title/artist/duration fuzzy track matching across YouTube, Spotify, JioSaavn, WebDAV)
  * `com/music/bitchord/data/spotify/`:
    * `SpotifyImporter.kt`, `SpotifyLibrary.kt` (Spotify playlist/library scraping & OAuth/token import)
  * `com/music/bitchord/data/webdav/`:
    * `WebDavAuth.kt` (WebDAV Basic/Digest header helper)
  * `com/music/bitchord/playback/`:
    * `BeatTracker.kt` (Post-processes `beat_this` ONNX logits into beat timestamps & downbeats, invokes `MelSpectrogram`)
    * `MelSpectrogram.kt` (Calls JNI `nativeCompute` -> `mel_spectrogram.cpp` for `128-bin` log-mel spectrogram at `22,050 Hz`)
    * `QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt` (Harmonic Camelot wheel, BPM proximity, and energy-curve queue ordering + timeline projection)
    * `TrackFeatures.kt` (Deserializes the JSON string from `analysis_jni.cpp` into `TrackFeatures`: BPM, key, Camelot code, RMS envelope, onset envelope, spectral flux, phrase boundaries, cue in/out points, and integrates `BeatTracker` + `VocalTracker` results)
    * `TransitionFilter.kt` (Calculates DJ filter sweep / 10-band EQ cut curves during crossfades)
    * `VocalSpectrogram.kt` (Calls JNI `nativeCompute` -> `vocal_spectrogram.cpp` for stereo `44,100 Hz` `n_fft=4096, hop=1024, bins=1487` STFT magnitude tensor)
    * `VocalTracker.kt` (Runs `vocal_activity` ONNX model in 6-second chunks and computes vocal presence probability contour + vocal onset/offset timestamps)
  * `com/my/kizzy/` (`gateway/`, `ipc/`, `models/`, `repository/`, `rpc/`, `utils/`):
    * Discord Rich Presence WebSocket gateway & RPC payload builder.

#### Module 2: `:sharedUi` (`sharedUi/build.gradle.kts`)
Currently defines `commonMain` (resources only!), `jvmSharedMain` (all UI code!), `androidMain`, and `jvmMain`:

* **`sharedUi/src/commonMain/composeResources/`**:
  * `drawable/` (`apple_music_canvas_symbol.xml`, `canvas_source_A.xml`, `canvas_source_S.xml`, `discord.xml`, `google.xml`, `ic_explicit.xml`, `ic_launcher_foreground.xml`, `jiosaavn.xml`, `kugou.xml`, `lastfm.xml`, `lrclib.xml`, `music_note.xml`, `netease.xml`, `paxsenix.xml`, `simpmusic.xml`, `spotify.xml`, `telegram.xml`, `webdav.xml`, `youtube_music.xml`, `yt_music_Load.xml`)
  * `font/` (`bbh_bartle_regular.ttf`, `doto_variable.ttf`, `inter_variable.ttf`, `jetbrains_mono_regular.ttf`, `outfit_bold.ttf`)
* **`sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/`** *(CRITICAL: Must be moved to `commonMain` in `WP-02` & `WP-10`)*:
  * `AppUi.kt` (Root adaptive shell: Navigation Rail, TopBar, Bottom/Floating `PlayerDock`, `NowPlayingScreen` modal/drawer, dialogs, and responsive window breakpoints)
  * `ForegroundState.kt`, `LyricsProviderState.kt`
  * `playback/PlaybackPosition.kt`
  * `ui/components/` (`AccountBadge.kt`, `AppDialogs.kt`, `ArtworkImage.kt`, `Chips.kt`, `ContextMenus.kt`, `ListenTogetherDialog.kt`, `ListenTogetherMembersSheet.kt`, `MediaCards.kt`, `OfflineUnavailableDialog.kt`, `PlaylistPickerSheet.kt`, `PlaylistViews.kt`, `QueuePreviewStrip.kt`, `RepeatIcon.kt`, `SectionHeaders.kt`, `SelectionToolbar.kt`, `ShimmerPlaceholders.kt`, `SortDropdown.kt`, `StatsSheet.kt`, `TrackRow.kt`)
  * `ui/graphics/` (`AtmosphericBlur.kt`, `ColorContrast.kt`, `PaletteExtractor.kt`)
  * `ui/haptics/` (`HapticFeedback.kt`)
  * `ui/icons/` (`BitChordIcons.kt`)
  * `ui/player/` (25 files: `PlayerHost.kt`, `NowPlayingScreen.kt`, `LandscapePlayer.kt`, `PlayerDock.kt`, `PlayerDrawer.kt`, `PlayerLyrics.kt`, `PlayerQueue.kt`, `PlayerSidePanels.kt`, `PlayerStrings.kt`, `PlayerVisualConstants.kt`, `PlayerWavyControls.kt`, `EqualizerSheet.kt`, `SleepTimerSheet.kt`, `TransitionWarningDialog.kt`, `WaveformSeekBar.kt`, `WaveformExtractor.kt`, `VisualizerDataSource.kt`, `ArtworkMeshBackdrop.kt`, `MeshGradient.kt`, `LyricsErrorBanner.kt`, `LyricsReportDialog.kt`, `LyricsSearchDialog.kt`, `LyricsSourceSheet.kt`, `LyricsSyncControl.kt`, `LyricsTranslationBadge.kt`)
  * `ui/replay/` (`ReplayEntryCard.kt`, `ReplayModels.kt`, `ReplayScreen.kt`, `ReplayShareCardGenerator.kt`, `ReplayStoryCards.kt`)
  * `ui/screens/` (`ExploreScreen.kt`, `HomeScreen.kt`, `LibraryScreen.kt`, `SearchScreen.kt`)
* **Platform `expect`/`actual` hooks in `sharedUi/src/{androidMain,jvmMain}/kotlin/com/music/bitchord/`** *(Must implement `iosMain` equivalents in `WP-10`)*:
  * `ui/components/{BackHandlerCompat.kt, PlatformScrollbar.kt, QrCodeBitmap.kt}`
  * `ui/graphics/{BitmapLoader.kt, PlatformBlur.kt}`
  * `ui/haptics/PlatformHaptics.kt`
  * `ui/player/{LockScreenThemeEffect.kt, MeshGradientShader.kt, PlatformCanvasVideo.kt, PlatformKeepScreenOn.kt}`
  * `ui/theme/PlatformFont.kt`

#### Module 3: `native/analyzer/` & `app/src/main/cpp/jni/` (In-Tree C++17 DSP Engine)
* **`native/analyzer/`**:
  * `audio_analysis.h` / `audio_analysis.cpp` (Computes RMS envelope, onset strength envelope, spectral centroid/flux, chromagram, Krumhansl-Schmuckler key detection -> Camelot key, phrase segmentation, and structural DJ cue-in/cue-out points)
  * `tempo_analysis.cpp` (Autocorrelation + comb-filter tempo/BPM estimator and beat grid phase alignment)
  * `resampler.h` / `resampler.cpp` (Polyphase sinc/linear resampler used to convert arbitrary input sample rates to `22,050 Hz` or `44,100 Hz`)
  * `mel_spectrogram.h` / `mel_spectrogram.cpp` (`bitchord::compute_mel_spectrogram`: resamples to `22,050 Hz`, computes STFT with `n_fft=1024, hop=220 (100 fps)`, periodic Hann window, projects onto `128` Slaney-normalized Mel bins `30 Hz–11,025 Hz`, applies `log1p(1000 * mag)` scaling for `CPJKU/beat_this`)
  * `vocal_spectrogram.h` / `vocal_spectrogram.cpp` (`bitchord::compute_vocal_spectrogram`: resamples stereo PCM to `44,100 Hz`, computes stereo center-padded STFT with `n_fft=4096, hop=1024`, periodic Hann window, crops to `1487` frequency bins `[2, 1487, T]` for `open-unmix` `vocal_activity` ONNX model)
* **`app/src/main/cpp/jni/`**:
  * `analysis_jni.cpp` (Contains `result_to_json(const bitchord::AnalysisResult& r)` which serializes the C++ `AnalysisResult` struct to the exact JSON schema expected by `TrackFeatures.kt`!)
  * `mel_jni.cpp` (Calls `bitchord::compute_mel_spectrogram(samples, count, sample_rate, out_frames)` and returns flattened float array `[frames * 128]`)
  * `vocal_jni.cpp` (Calls `bitchord::compute_vocal_spectrogram(left, right, count, sample_rate, out_frames)` and returns flattened float array `[2 * 1487 * frames]`)

#### Module 4: `:desktopApp` (`desktopApp/src/main/kotlin/com/music/bitchord/desktop/` — 119 Files)
In BitChord v1.8-beta1, `:desktopApp` contains the complete non-Android application orchestration layer (`DesktopPlayerState.kt` [137 KB], `Main.kt` [175 KB], `DesktopPlaybackEngine.kt`, `DesktopPlaybackResolver.kt`, `DesktopCipher.kt`, `DesktopPoTokenWebView.kt`, `DesktopAnalysisPipeline.kt`, `DesktopBeatModel.kt`, `DesktopVocalModel.kt`, `DesktopCanvas*.kt`, `DesktopDatabase.kt`, `DesktopCacheStore.kt`, `DesktopLibraryStore.kt`, `DesktopSyncEngine.kt`, `DesktopListenTogetherManager.kt`, `DesktopPartySync.kt`, `DesktopScrobbling.kt`, `DesktopListeningStats.kt`, `DesktopJioSaavn.kt`, `DesktopSourceRegistry.kt`, etc.).
* **Architectural Strategy for iPadOS**: Rather than rewriting `:desktopApp`'s 119 files from scratch in Swift, **85% of `:desktopApp` is pure Kotlin state management, SQLite queries, HTTP stream resolution, and coroutine pipelines**. We will extract/port the platform-agnostic orchestration from `:desktopApp` into `shared/src/commonMain` (or `iosApp/src/iosMain/kotlin/com/music/bitchord/ios/`), and replace only the JVM-specific leaves (JavaFX WebView -> `WKWebView`, `xt.audio`/FFmpeg -> `AVAudioEngine`/`ExtAudioFile`, `sqlite-jdbc` -> KMP SQLite, JNI `.dll` -> Kotlin/Native `cinterop`).

---

## 2. Target iPadOS Architecture & Module Topology

```
BitChord/
├── build.gradle.kts                     # Root KMP configuration (Kotlin 2.3.20, CMP 1.10.3)
├── settings.gradle.kts                  # Includes :shared, :sharedUi, :innertubex, :desktopApp, :app
├── innertubex/                          # Vendored KMP port of kushagrasinghx/innertubex v0.7.0
│   └── src/{commonMain,jvmSharedMain,iosMain}/
├── native/
│   └── analyzer/                        # Unmodified upstream C++17 DSP engine (5 .cpp files)
│       ├── audio_analysis.cpp/.h
│       ├── tempo_analysis.cpp
│       ├── resampler.cpp/.h
│       ├── mel_spectrogram.cpp/.h
│       ├── vocal_spectrogram.cpp/.h
│       └── ios_c_bridge/                # NEW: C ABI wrapper around analysis_jni.cpp/mel_jni.cpp/vocal_jni.cpp
│           ├── bitchord_ios_bridge.h
│           ├── bitchord_ios_bridge.cpp
│           └── build_ios_static.sh      # Compiles libbitchord_analysis.a for ios-arm64 & ios-arm64-simulator
├── shared/
│   ├── build.gradle.kts                 # Adds iosArm64(), iosSimulatorArm64(), cinterop(bitchord_analysis, onnxruntime)
│   └── src/
│       ├── commonMain/                  # Unified models, YtMusicRepository, Lyrics, Queue, TrackFeatures, SmartController
│       ├── jvmSharedMain/               # JVM/Android actuals
│       └── iosMain/                     # Darwin Ktor, cinterop DSP/ONNX actuals, AVAudioEngine controller, WKWebView PoToken/Cipher
├── sharedUi/
│   ├── build.gradle.kts                 # Adds iosArm64(), iosSimulatorArm64(), Coil Ktor3
│   └── src/
│       ├── commonMain/                  # All shared UI screens, NowPlaying, LandscapePlayer, MeshGradient, Replay
│       ├── jvmSharedMain/               # Shared JVM/Android UI helpers
│       ├── androidMain/                 # Android UI actuals
│       ├── jvmMain/                     # Desktop UI actuals
│       └── iosMain/                     # iPadOS UI actuals (Skia blur/bitmap/shader, AVPlayer Canvas video, UIImpactFeedbackGenerator, CoreImage QR)
└── iosApp/                              # Xcode Project & Swift Entry Point for iPadOS (.ipa)
    ├── iosApp.xcodeproj
    ├── Podfile                          # pod 'onnxruntime-c', '1.20.0'
    ├── Resources/
    │   ├── beat_this.onnx               # Bundled neural beat tracker model
    │   ├── vocal_activity.onnx          # Bundled neural vocal detector model
    │   └── Info.plist                   # UIBackgroundModes=[audio,fetch,processing], ProMotion, Stage Manager
    └── iosApp/
        ├── iOSApp.swift                 # @main SwiftUI App + ScenePhase + UIKeyCommand hardware keyboard shortcuts
        ├── ContentView.swift            # ComposeUIViewController wrapper (ignoresSafeArea, 120Hz ProMotion)
        ├── Audio/
        │   ├── IOSDualAudioEngine.swift # AVAudioEngine: Deck A + Deck B + 10-Band EQ + TimePitch + Realtime FFT Tap
        │   └── IOSNowPlayingManager.swift # MPNowPlayingInfoCenter + MPRemoteCommandCenter + AVAudioSession
        └── Video/
            └── IOSCanvasVideoPool.swift # AVQueuePlayer + AVPlayerLooper + AVPlayerItemVideoOutput for Canvas backdrops
```

---

## 3. 5-Phase Parallel Execution Dependency DAG

To maximize velocity when dispatching Junior AI Coding Agents, execute the 12 Work Packages (`WP-01` through `WP-12`) across 5 dependency-ordered phases:

```
Phase 1 (Foundation & Build Graph)
  ├── WP-01: KMP iOS Targets, Xcode Project, & :innertubex KMP Vendoring
  └── WP-04: Native C++17 Analyzer (libbitchord_analysis.a) & C-Bridge Compilation
        │
        ▼
Phase 2 (Core KMP Migration & Neural Inference)
  ├── WP-02: Migrate :shared & :sharedUi from jvmSharedMain to commonMain
  ├── WP-05: ONNX Runtime C-API cinterop (beat_this.onnx & vocal_activity.onnx) + TrackFeatures Pipeline
  └── WP-08: SQLite KMP Persistence (Database, CacheStore, LibraryStore, ListeningStats, Offline Downloads)
        │
        ▼
Phase 3 (Networking, Stream Extraction & Dual-Deck Audio Engine)
  ├── WP-03: YouTube Cipher (JavaScriptCore), Headless WKWebView PoToken Generator & Multi-Account Auth
  └── WP-06: iPadOS Dual-Deck AVAudioEngine, 10-Band Biquad EQ, TimePitch & FFT Visualizer Tap
        │
        ▼
Phase 4 (Smart Automix, Multi-Source Ecosystem & Real-Time Sync)
  ├── WP-07: Smart Automix Controller Wiring, Crossfade/Time-Transfer Envelopes & Lock Screen Media Center
  └── WP-09: Multi-Source Providers (Spotify, JioSaavn, WebDAV), Lyrics Engine (8 Providers + Translation), Listen Together, Party Sync, Discord RPC & Scrobbling
        │
        ▼
Phase 5 (iPadOS UI Parity, Canvas Video, Stage Manager & .ipa Packaging)
  ├── WP-10: :sharedUi iosMain Actuals (Skia Shaders, Canvas Video Backdrop, Haptics, QR) & iPadOS Orchestrator Wiring
  ├── WP-11: iPadOS-Specific Polish (120Hz ProMotion, Stage Manager Resizing, Hardware Keyboard Shortcuts, Share Sheet)
  └── WP-12: Automated .ipa Build Pipeline, AltStore/Sideloadly Packaging & E2E Parity Verification
```

---

## 4. Detailed Work Packages (`WP-01` through `WP-12`)

---

### WP-01: KMP iOS Targets, Xcode Workspace & `:innertubex` KMP Vendoring

#### 1. Objective
Configure the Gradle build graph (`build.gradle.kts`, `settings.gradle.kts`, `shared/build.gradle.kts`, `sharedUi/build.gradle.kts`) to support `iosArm64()` and `iosSimulatorArm64()`, vendor `com.github.kushagrasinghx:innertubex:v0.7.0` as a local KMP module (`:innertubex`) so `YtMusicRepository` compiles for iOS, and scaffold the `iosApp/` Xcode project.

#### 2. Upstream Files to Inspect / Modify
* `settings.gradle.kts`
* `build.gradle.kts`
* `shared/build.gradle.kts`
* `sharedUi/build.gradle.kts`

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Vendor `kushagrasinghx/innertubex` (`v0.7.0`) as Local KMP Module `:innertubex`
1. Clone `https://github.com/kushagrasinghx/innertubex` at tag `v0.7.0` into `<repo-root>/innertubex/`.
2. Register `include(":innertubex")` inside `settings.gradle.kts`.
3. Configure `innertubex/build.gradle.kts` with `kotlin("multiplatform")` and `kotlin("plugin.serialization")`, targeting `androidTarget()`, `jvm("desktop")`, `iosArm64()`, and `iosSimulatorArm64()`.
4. Replace JVM-only primitives inside `innertubex`:
   - Replace `java.security.MessageDigest.getInstance("SHA-1")` with an `expect fun sha1Hex(input: ByteArray): String` (`CommonCrypto` `CC_SHA1` on `iosMain`, `MessageDigest` on `jvmSharedMain`).
   - Replace `org.brotli.dec.BrotliInputStream` (if used in custom response interceptors) or configure Ktor's `ContentEncoding` plugin (`io.ktor:ktor-client-encoding:3.4.0`) + Darwin native decompression (note: `NSURLSession` on iOS automatically decompresses `gzip`, `deflate`, and `br` Brotli responses transparently at the OS level!).
   - Replace `java.net.URLEncoder` / `java.net.URI` with Ktor's `io.ktor.http.Url` and `encodeURLParameter()`.

##### Step 3.2: Update `shared/build.gradle.kts` with iOS Hierarchy & Darwin Dependencies
Modify `shared/build.gradle.kts` to preserve `jvmSharedMain` while adding `iosMain`, `iosArm64Main`, and `iosSimulatorArm64Main`:

```kotlin
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":innertubex"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
                implementation("io.ktor:ktor-client-core:3.4.0")
                implementation("io.ktor:ktor-client-websockets:3.4.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
                implementation("io.ktor:ktor-client-encoding:3.4.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
                implementation("androidx.sqlite:sqlite-bundled:2.5.0")
            }
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.4.0")
                implementation("io.ktor:ktor-client-cio:3.4.0")
                implementation("org.brotli:dec:0.1.2")
            }
        }
        val androidMain by getting { dependsOn(jvmSharedMain) }
        val desktopMain by getting { dependsOn(jvmSharedMain) }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.4.0")
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
```

##### Step 3.3: Update `sharedUi/build.gradle.kts` with iOS Targets & Coil Ktor3
In `sharedUi/build.gradle.kts`:
1. Add `iosArm64()` and `iosSimulatorArm64()` targets, exporting the static framework `SharedUI` (which transitively exports `:shared`).
2. In `commonMain.dependencies`, replace `implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")` with `implementation("io.coil-kt.coil3:coil-network-ktor3:3.4.0")` (or keep `coil-network-okhttp` in `jvmSharedMain` and add `coil-network-ktor3:3.4.0` to `iosMain`).
3. Verify that `io.github.fletchmckee.liquid:liquid:1.1.1` resolves for `iosArm64` and `iosSimulatorArm64` (it is published with KMP iOS targets).

#### 4. Pitfalls & Gotchas
* **Static vs Dynamic Framework**: `isStatic = true` is mandatory when linking static C++ archives (`libbitchord_analysis.a`) and CocoaPods (`onnxruntime-c`) into the Compose Multiplatform umbrella framework (`SharedUI.framework`).
* **Linker Flags**: Add `-lsqlite3`, `-lc++`, `-framework Accelerate`, `-framework AVFoundation`, `-framework AudioToolbox`, `-framework MediaPlayer`, `-framework JavaScriptCore`, `-framework WebKit`, `-framework CoreImage`, and `-framework Security` to `binaries.framework { linkerOpts(...) }`.

---

### WP-02: Migrate `:shared` & `:sharedUi` from `jvmSharedMain` to `commonMain`

#### 1. Objective
In upstream BitChord v1.8-beta1, almost all of `:shared` and 100% of `:sharedUi` Kotlin source files sit in `src/jvmSharedMain/kotlin/`. Move these files to `src/commonMain/kotlin/` and replace every `java.*` / `javax.*` import with KMP `expect`/`actual` declarations or stdlib equivalents.

#### 2. Complete Audit of `jvmSharedMain` Files to Move to `commonMain`

##### In `:shared` (`shared/src/jvmSharedMain/kotlin/` -> `shared/src/commonMain/kotlin/`):
1. `com/music/bitchord/YtMusicRepository.kt`
2. `com/music/bitchord/auth/{AccountIdentity.kt, YouTubeAuthSession.kt, YouTubeCookieAuth.kt}`
3. `com/music/bitchord/data/canvas/{CanvasArtwork.kt, SpotifyCanvasQuery.kt}`
4. `com/music/bitchord/data/innertube/YouTubeBrowseIds.kt`
5. `com/music/bitchord/data/lyrics/{KuGouLyricsSource.kt, LrcLibLyricsSource.kt, LrcParser.kt, LyricsModels.kt, LyricsRegistry.kt, LyricsSource.kt, NetEaseLyricsSource.kt, PaxsenixLyricsSource.kt, SimpMusicLyricsSource.kt, TextSimilarity.kt, YouTubeLyricsSource.kt, YouTubeSubtitleLyricsSource.kt}`
6. `com/music/bitchord/data/presence/Presence.kt`
7. `com/music/bitchord/data/sources/{SourceTrackKeys.kt, StreamFormat.kt, TrackMatcher.kt}`
8. `com/music/bitchord/data/spotify/{SpotifyImporter.kt, SpotifyLibrary.kt}`
9. `com/music/bitchord/data/webdav/WebDavAuth.kt`
10. `com/music/bitchord/playback/{BeatTracker.kt, MelSpectrogram.kt, QueueBuilder.kt, QueueSections.kt, QueueTimeline.kt, TrackFeatures.kt, TransitionFilter.kt, VocalSpectrogram.kt, VocalTracker.kt}`
11. `com/my/kizzy/**` (Discord RPC client)

##### In `:sharedUi` (`sharedUi/src/jvmSharedMain/kotlin/` -> `sharedUi/src/commonMain/kotlin/`):
1. `com/music/bitchord/{AppUi.kt, ForegroundState.kt, LyricsProviderState.kt}`
2. `com/music/bitchord/playback/PlaybackPosition.kt`
3. `com/music/bitchord/ui/components/*` (all 20 files)
4. `com/music/bitchord/ui/graphics/{AtmosphericBlur.kt, ColorContrast.kt, PaletteExtractor.kt}`
5. `com/music/bitchord/ui/haptics/HapticFeedback.kt`
6. `com/music/bitchord/ui/icons/BitChordIcons.kt`
7. `com/music/bitchord/ui/player/*` (all 25 files)
8. `com/music/bitchord/ui/replay/*` (all 5 files)
9. `com/music/bitchord/ui/screens/{ExploreScreen.kt, HomeScreen.kt, LibraryScreen.kt, SearchScreen.kt}`

#### 3. Exact `java.*` Replacements Required During Migration

| JVM Call in `jvmSharedMain` | Files Affected | KMP `commonMain` / `expect` Replacement |
| :--- | :--- | :--- |
| `System.currentTimeMillis()` / `System.nanoTime()` | `YouTubeCookieAuth.kt`, `YtMusicRepository.kt`, `AppUi.kt`, `PlayerLyrics.kt`, `Kizzy` | Create `expect fun currentTimeMillis(): Long` and `expect fun nanoTime(): Long` in `commonMain` (`NSDate().timeIntervalSince1970 * 1000` / `mach_absolute_time()` on `iosMain`) |
| `java.security.MessageDigest.getInstance("SHA-1")` / `"MD5"` | `YouTubeCookieAuth.kt` (`SAPISIDHASH`), `DesktopScrobbling.kt` (Last.fm `api_sig`) | Create `expect fun sha1Hex(bytes: ByteArray): String` and `expect fun md5Hex(bytes: ByteArray): String` (`CC_SHA1` / `CC_MD5` from `platform.CoreCrypto` on `iosMain`) |
| `java.util.Base64` | `KuGouLyricsSource.kt`, `WebDavAuth.kt`, `SpotifyImporter.kt` | Use Kotlin stdlib `@OptIn(ExperimentalEncodingApi::class) kotlin.io.encoding.Base64` |
| `java.net.URLEncoder.encode(s, "UTF-8")` / `java.net.URI` | `LyricsSource` implementations, `SpotifyCanvasQuery.kt`, `SpotifyImporter.kt` | Use Ktor's `io.ktor.http.encodeURLParameter()`, `io.ktor.http.encodeURLPath()`, and `io.ktor.http.Url(string)` |
| `java.util.UUID.randomUUID().toString()` | `ListenTogether`, `Kizzy`, `YtMusicRepository.kt` | Use Kotlin 2.3 stdlib `@OptIn(ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()` |
| `java.util.Locale` / `String.format(Locale.US, ...)` | `AppUi.kt`, `PlayerStrings.kt`, `EqualizerSheet.kt`, `ReplayScreen.kt` | Replace with pure Kotlin helper formatting functions (`formatDurationMs()`, `formatDb()`, `formatBpm()`) |
| `java.util.concurrent.ConcurrentHashMap` / `Collections.synchronizedMap` | `YtMusicRepository.kt`, `LyricsRegistry.kt` | Replace with `kotlinx.coroutines.sync.Mutex` or `atomicfu` thread-safe map wrapper |
| JNI `external fun nativeCompute(...)` | `MelSpectrogram.kt`, `VocalSpectrogram.kt` | Change to `expect object MelSpectrogram { fun compute(...): FloatArray? }` and `expect object VocalSpectrogram { fun compute(...): FloatArray? }` with `jvmSharedMain` keeping JNI and `iosMain` calling `cinterop` (`WP-04`) |

---

### WP-03: YouTube Stream Extraction, Cipher (`JavaScriptCore`), Headless `WKWebView` PoToken Generator & Multi-Account Auth

#### 1. Objective
Port `:desktopApp`'s complete YouTube stream resolution & anti-bot pipeline (`DesktopPlaybackResolver.kt`, `DesktopCipher.kt`, `DesktopPoTokenWebView.kt`, `DesktopAccounts.kt`, `DesktopYouTubeSession.kt`) to iPadOS so that **every YouTube Music stream plays without HTTP 403 Forbidden errors**, supporting both anonymous and logged-in multi-account sessions.

#### 2. Upstream Files to Reference & Port
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlaybackResolver.kt` (49,544 bytes — multi-client fallback, stream URL validation, cipher invocation, PoToken injection, HLS/DASH vs progressive selection)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopCipher.kt` (32,491 bytes — downloads YouTube `base.js` player script, extracts signature decipher & `n`-parameter throttling functions, executes JS)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPoTokenWebView.kt` (22,949 bytes — runs headless WebView on `https://www.youtube.com/embed/...` to mint BotGuard `poToken` + `visitorData`)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopAccounts.kt` & `DesktopYouTubeSession.kt`
* `shared/src/jvmSharedMain/kotlin/com/music/bitchord/auth/{AccountIdentity.kt, YouTubeAuthSession.kt, YouTubeCookieAuth.kt}`

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Port `DesktopCipher.kt` Using Native `JavaScriptCore` (`JSContext`)
In `DesktopCipher.kt`, upstream fetches the YouTube HTML5 player script (`https://www.youtube.com/s/player/<player_id>/player_ias.vflset/en_US/base.js`), uses regexes to extract the signature decipher function and the `n`-parameter transform function, and evaluates them.
* Move the regex extraction and player script caching logic of `DesktopCipher.kt` into `shared/src/commonMain/kotlin/com/music/bitchord/playback/YouTubeCipher.kt`.
* Define an `expect class JsEvaluator()` in `commonMain`:
  ```kotlin
  expect class JsEvaluator() {
      fun evaluateScript(script: String, functionName: String, arg: String): String?
      fun close()
  }
  ```
* In `shared/src/iosMain/kotlin/com/music/bitchord/playback/JsEvaluator.ios.kt`, implement `JsEvaluator` using Apple's native **`platform.JavaScriptCore.JSContext`**:
  ```kotlin
  import platform.JavaScriptCore.*

  actual class JsEvaluator actual constructor() {
      private val context = JSContext()

      actual fun evaluateScript(script: String, functionName: String, arg: String): String? {
          context.evaluateScript(script)
          val fn = context.objectForKeyedSubscript(functionName) ?: return null
          val result = fn.callWithArguments(listOf(arg))
          return if (result != null && !result.isUndefined() && !result.isNull()) {
              result.toString()
          } else null
      }

      actual fun close() {}
  }
  ```
* **Why `JavaScriptCore`?** It runs synchronously in-process in < 1 ms without main-thread UI overhead, supports full ES6+ syntax used by YouTube's `base.js`, and never triggers WebKit process suspension in the background.

##### Step 3.2: Port `DesktopPoTokenWebView.kt` Using Headless `WKWebView`
YouTube's `WEB_REMIX` and `IOS` clients increasingly require a **Proof of Origin Token (`poToken`)** paired with `visitorData` (or authenticated `dataSyncId`) generated by Google's BotGuard VM inside a real browser DOM (`DesktopPoTokenWebView.kt`).
* Create `shared/src/iosMain/kotlin/com/music/bitchord/auth/IOSPoTokenGenerator.kt` using `platform.WebKit.WKWebView`:
  1. Instantiate an off-screen `WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)` on `Dispatchers.Main`.
  2. Port the exact JavaScript BotGuard/Minting injection script from `DesktopPoTokenWebView.kt` (`mintPoToken(identifier)`).
  3. Register a `WKScriptMessageHandlerProtocol` bridge (`userContentController.addScriptMessageHandler(handler, name = "bitchordPoToken")`) to receive the `{ poToken, visitorData, expiresAt }` JSON back into a Kotlin `CompletableDeferred<PoTokenResult>`.
  4. Cache the generated `poToken` for 6 hours (matching `DesktopPoTokenWebView.kt`'s TTL) and automatically attach it to `InnerTube` player requests and stream URLs (`&pot=<poToken>`).

##### Step 3.3: Port `DesktopPlaybackResolver.kt` Multi-Client Stream Resolution
Port `DesktopPlaybackResolver.kt` to `shared/src/commonMain/kotlin/com/music/bitchord/playback/PlaybackStreamResolver.kt`:
1. Preserve the exact client fallback waterfall from `DesktopPlaybackResolver.kt`:
   - `ANDROID_VR` / `IOS` / `TVHTML5_SIMPLY_EMBEDDED_PLAYER` / `WEB_REMIX` (with `poToken` + deciphering).
2. **Critical Audio Format Selection for iPadOS**:
   - On Desktop, `DesktopPlaybackResolver` prefers WebM/Opus (`itag 251`, `250`, `249`) because it decodes Opus via `Concentus` or `FFmpeg`.
   - On iPadOS, **`AVPlayer` and `ExtAudioFile` natively hardware-decode MP4/M4A AAC (`itag 140` 128kbps AAC-LC, `itag 141` 256kbps AAC-LC) and HLS `.m3u8` streams with zero CPU overhead**, whereas WebM container (`matroska`) is NOT natively supported by `ExtAudioFile`!
   - **Rule for iPadOS**:
     1. In `PlaybackStreamResolver`, prioritize **`audio/mp4` (`mp4a.40.2` / `itag 141` / `itag 140`)** first on iOS for direct `ExtAudioFile` / `AVAudioFile` compatibility.
     2. As a fallback when only WebM/Opus (`itag 251`) is returned, include a lightweight WebM/EBML demuxer + Apple `AudioConverterRef` (`kAudioFormatOpus` -> `kAudioFormatLinearPCM`, supported natively in iOS `AudioToolbox` since iOS 11!) so Opus streams also decode cleanly to `.caf`/`.wav` PCM in the cache store!

##### Step 3.4: Multi-Account Google/YouTube Login (`WKWebView` Cookie Extractor)
Port `DesktopAccounts.kt`, `DesktopAccountUi.kt`, and `DesktopYouTubeSession.kt`:
1. Present a modal `WKWebView` pointing to `https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F`.
2. Observe navigation to `music.youtube.com` and read cookies from `WKWebsiteDataStore.defaultDataStore().httpCookieStore`:
   - Extract `SAPISID`, `__Secure-3PAPISID`, `__Secure-1PAPISID`, `SID`, `HSID`, `SSID`, `APISID`, `LOGIN_INFO`, and `VISITOR_INFO1_LIVE`.
3. Persist multi-account profiles (`AccountIdentity` + cookies + `visitorData` + `dataSyncId`) in iOS Keychain (`kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlock`) and support instant account switching identical to `DesktopAccounts.kt`.

---

### WP-04: Native C++17 DSP Analyzer (`libbitchord_analysis.a`) & Kotlin/Native `cinterop`

#### 1. Objective
Compile the **exact existing upstream C++17 analyzer files** in `native/analyzer/` plus a thin `extern "C"` wrapper modeled directly on `app/src/main/cpp/jni/{analysis_jni.cpp, mel_jni.cpp, vocal_jni.cpp}` into an iOS static library (`libbitchord_analysis.a`) and bind it into `:shared`'s `iosMain` via Kotlin/Native `cinterop`.

#### 2. Upstream Files to Compile Verbatim
* `native/analyzer/audio_analysis.h`
* `native/analyzer/audio_analysis.cpp`
* `native/analyzer/tempo_analysis.cpp`
* `native/analyzer/resampler.h`
* `native/analyzer/resampler.cpp`
* `native/analyzer/mel_spectrogram.h`
* `native/analyzer/mel_spectrogram.cpp`
* `native/analyzer/vocal_spectrogram.h`
* `native/analyzer/vocal_spectrogram.cpp`
* Reference for JSON & Float Array Marshaling:
  * `app/src/main/cpp/jni/analysis_jni.cpp`
  * `app/src/main/cpp/jni/mel_jni.cpp`
  * `app/src/main/cpp/jni/vocal_jni.cpp`

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Create the C ABI Header `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h`
Kotlin/Native `cinterop` binds C headers (`extern "C"`), not C++ classes/templates (`std::vector`). Create `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h`:

```c
#ifndef BITCHORD_IOS_BRIDGE_H
#define BITCHORD_IOS_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Runs bitchord::analyze_track() on mono float PCM in [-1.0, 1.0] and serializes
 * the AnalysisResult to the EXACT JSON string format produced by app/src/main/cpp/jni/analysis_jni.cpp.
 * Caller must free the returned string using bitchord_free_string().
 */
char* bitchord_analyze_pcm_json(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t duration_ms
);

/**
 * Runs bitchord::compute_mel_spectrogram() (22050 Hz, n_fft=1024, hop=220, 128 mel bins).
 * Writes the number of frames to *out_frames and returns a heap-allocated float array of length (*out_frames * 128).
 * Caller must free the returned buffer using bitchord_free_floats().
 */
float* bitchord_compute_mel_spectrogram(
    const float* samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

/**
 * Runs bitchord::compute_vocal_spectrogram() (stereo 44100 Hz, n_fft=4096, hop=1024, 1487 bins).
 * Writes the number of frames to *out_frames and returns a heap-allocated float array of length (2 * 1487 * *out_frames).
 * Caller must free the returned buffer using bitchord_free_floats().
 */
float* bitchord_compute_vocal_spectrogram(
    const float* left_samples,
    const float* right_samples,
    int32_t sample_count,
    int32_t sample_rate,
    int32_t* out_frames
);

void bitchord_free_string(char* str);
void bitchord_free_floats(float* ptr);

#ifdef __cplusplus
}
#endif

#endif // BITCHORD_IOS_BRIDGE_H
```

##### Step 3.2: Implement `native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp`
Copy the exact `result_to_json(const bitchord::AnalysisResult& r)` function from `app/src/main/cpp/jni/analysis_jni.cpp` (lines 11–79) into `bitchord_ios_bridge.cpp` and implement the 5 `extern "C"` functions:
1. `bitchord_analyze_pcm_json`: Calls `bitchord::analyze_track(samples, sample_count, sample_rate, duration_ms)`, formats it via `result_to_json(result)`, allocates a C string with `strdup(json.c_str())`, and returns it.
2. `bitchord_compute_mel_spectrogram`: Calls `bitchord::compute_mel_spectrogram(samples, sample_count, sample_rate, frames)`, allocates `float* buf = (float*)malloc(mel.size() * sizeof(float))`, copies `mel.data()`, sets `*out_frames = frames`, and returns `buf`.
3. `bitchord_compute_vocal_spectrogram`: Calls `bitchord::compute_vocal_spectrogram(left_samples, right_samples, sample_count, sample_rate, frames)`, allocates `float* buf = (float*)malloc(spec.size() * sizeof(float))`, copies `spec.data()`, sets `*out_frames = frames`, and returns `buf`.

##### Step 3.3: Build Script `native/analyzer/ios_c_bridge/build_ios_static.sh`
Compile the 6 `.cpp` files (`audio_analysis.cpp`, `tempo_analysis.cpp`, `resampler.cpp`, `mel_spectrogram.cpp`, `vocal_spectrogram.cpp`, `bitchord_ios_bridge.cpp`) with `xcrun clang++ -std=c++17 -O3 -ffast-math -fPIC`:
- Target 1: `-arch arm64 -isysroot $(xcrun --sdk iphoneos --show-sdk-path) -miphoneos-version-min=16.0` -> `build/ios-arm64/libbitchord_analysis.a`
- Target 2: `-arch arm64 -isysroot $(xcrun --sdk iphonesimulator --show-sdk-path) -mios-simulator-version-min=16.0` -> `build/ios-sim-arm64/libbitchord_analysis.a`

##### Step 3.4: Configure Kotlin/Native `cinterop` in `shared/build.gradle.kts`
Create `shared/src/nativeInterop/cinterop/bitchord_analysis.def`:
```ini
headers = bitchord_ios_bridge.h
headerFilter = bitchord_ios_bridge.h
staticLibraries = libbitchord_analysis.a
linkerOpts = -lc++
```
And implement `MelSpectrogram.ios.kt`, `VocalSpectrogram.ios.kt`, and `NativeTrackAnalyzer.ios.kt` in `shared/src/iosMain/kotlin/com/music/bitchord/playback/` using `usePinned` + `bitchord_compute_mel_spectrogram` / `bitchord_compute_vocal_spectrogram` / `bitchord_analyze_pcm_json`.

#### 4. Pitfalls & Gotchas
* **Why This Guarantees 100% Parity**: Because `bitchord_analyze_pcm_json` uses the exact `result_to_json` serializer from `analysis_jni.cpp`, `TrackFeatures.fromJson(jsonString)` in `shared/src/commonMain/kotlin/com/music/bitchord/playback/TrackFeatures.kt` works **with zero changes**!

---

### WP-05: ONNX Runtime C-API `cinterop` (`beat_this` & `vocal_activity`) + Audio Analysis Pipeline

#### 1. Objective
Port `DesktopBeatModel.kt`, `DesktopVocalModel.kt`, and `DesktopAnalysisPipeline.kt` to iPadOS so that `BeatTracker.kt` (`beat_this.onnx`) and `VocalTracker.kt` (`vocal_activity.onnx`) execute on iOS with CoreML / CPU acceleration, feeding `TrackFeatures.kt` and caching analysis results in SQLite.

#### 2. Upstream Files to Reference & Port
* `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/BeatTracker.kt` (139 lines — slices mel spectrogram `[frames, 128]` into 1500-frame windows with 1400-frame hop, calls `BeatModelRunner`, merges overlap predictions with 50-frame border trimming, extracts beat times + downbeats + BPM)
* `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/VocalTracker.kt` (186 lines — slices audio in 6-second stereo chunks at `44,100 Hz` with 4-second hop, calls `VocalSpectrogram.compute`, runs `VocalModelRunner` on `[1, 2, 1487, frames]`, normalizes energy to `[0,1]`, detects vocal start/end timestamps)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopBeatModel.kt` (Loads `beat_this.onnx`, input `"mel"` `[1, T, 128]`, outputs `"beat"` `[1, T]` and `"downbeat"` `[1, T]`)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopVocalModel.kt` (Loads `vocal_activity.onnx`, input `"spectrogram"` `[1, 2, 1487, T]`, output `"vocal_energy"` `[1, T]`)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopAnalysisPipeline.kt` (Decodes cached audio file to mono `22,050 Hz` and stereo `44,100 Hz` float PCM, runs C++ analyzer + `BeatTracker` + `VocalTracker`, saves `TrackFeatures` JSON to `track_analysis` table)

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Bundle ONNX Models & Link `onnxruntime-c` (`1.20.0`)
1. Copy `beat_this.onnx` and `vocal_activity.onnx` from the Android/Desktop assets into `iosApp/Resources/`.
2. Add `onnxruntime-c` (`1.20.0`) via CocoaPods (`Podfile`) or XCFramework in `shared/build.gradle.kts`, and expose `onnxruntime_c_api.h` via Kotlin/Native `cinterop` (`onnxruntime.def`).

##### Step 3.2: Implement `IOSBeatModel.kt` & `IOSVocalModel.kt` in `shared/src/iosMain/`
`BeatTracker.kt` and `VocalTracker.kt` already define clean interfaces:
* `BeatTracker.BeatModelRunner`:
  ```kotlin
  fun interface BeatModelRunner {
      fun predict(melData: FloatArray, frames: Int, melBins: Int): Pair<FloatArray, FloatArray>?
  }
  ```
* `VocalTracker.VocalModelRunner`:
  ```kotlin
  fun interface VocalModelRunner {
      fun predict(specData: FloatArray, frames: Int): FloatArray?
  }
  ```
In `shared/src/iosMain/kotlin/com/music/bitchord/playback/onnx/`:
1. Create `IOSOnnxEnvironment` initializing `OrtGetApiBase()->GetApi(ORT_API_VERSION)` once as a singleton.
2. Implement `IOSBeatModel : BeatTracker.BeatModelRunner`:
   - Loads `NSBundle.mainBundle.pathForResource("beat_this", ofType = "onnx")`.
   - Configures `OrtSessionOptions` with `intra_op_num_threads = 2`.
   - Creates input tensor `"mel"` with shape `longArrayOf(1L, frames.toLong(), 128L)` via `CreateTensorWithDataAsOrtValue`.
   - Calls `Run` requesting outputs `"beat"` and `"downbeat"`.
   - Copies the two output float buffers `[frames]` into Kotlin `FloatArray`s, releases `OrtValue`s (`ReleaseValue`), and returns `Pair(beatLogits, downbeatLogits)`.
3. Implement `IOSVocalModel : VocalTracker.VocalModelRunner`:
   - Loads `NSBundle.mainBundle.pathForResource("vocal_activity", ofType = "onnx")`.
   - Creates input tensor `"spectrogram"` with shape `longArrayOf(1L, 2L, 1487L, frames.toLong())`.
   - Calls `Run` requesting output `"vocal_energy"` `[1, frames]`.
   - Copies the output float buffer into a Kotlin `FloatArray`, releases `OrtValue`s, and returns it.

##### Step 3.3: Port `DesktopAnalysisPipeline.kt` Using Apple's `ExtAudioFile` PCM Decoder
On Desktop, `DesktopAnalysisPipeline.kt` spawns `ffmpeg` to decode cached audio into float PCM. On iPadOS, **spawning subprocesses is forbidden by the iOS sandbox**.
Instead, implement `IOSAudioFileDecoder.kt` in `shared/src/iosMain/kotlin/com/music/bitchord/playback/` using Apple's hardware-accelerated **`AudioToolbox.ExtAudioFile`**:
1. Open the cached audio file URL via `ExtAudioFileOpenURL`.
2. Configure `kExtAudioFileProperty_ClientDataFormat` with an `AudioStreamBasicDescription`:
   - `mFormatID = kAudioFormatLinearPCM`
   - `mFormatFlags = kAudioFormatFlagIsFloat or kAudioFormatFlagIsPacked` (`kAudioFormatFlagIsNonInterleaved` for stereo `44,100 Hz` or 1-channel mono `22,050 Hz`)
   - `mBitsPerChannel = 32`
3. Loop `ExtAudioFileRead` into a pre-allocated buffer to extract:
   - Mono `22,050 Hz` `FloatArray` -> passed to `bitchord_analyze_pcm_json(...)` and `BeatTracker.analyze(...)`.
   - Stereo `44,100 Hz` `Pair<FloatArray, FloatArray>` (`left`, `right`) -> passed to `VocalTracker.analyze(...)`.
4. Merge the C++ `TrackFeatures` with `BeatTracker.Result` (replacing heuristic beats/BPM when `BeatTracker` succeeds) and `VocalTracker.Result` (`vocalPresence`, `vocalStartSec`, `vocalEndSec`), matching `DesktopAnalysisPipeline.kt` line-for-line!
5. Save the merged JSON into the `track_analysis` SQLite table via `CacheStore`.

#### 4. Pitfalls & Gotchas
* **Concurrency & Thermal Guard**: Just like `DesktopAnalysisPipeline.kt`, limit concurrent background track analysis on iPadOS using a `Semaphore(1)` on a dedicated background dispatcher (`Dispatchers.Default.limitedParallelism(1)`) so analyzing the upcoming queue track never causes audio buffer underruns or thermal throttling.
* **Memory Cleanup**: Every `OrtValue*` created per chunk (a 4-minute song runs ~35 chunks in `VocalTracker`) MUST be freed immediately inside a `try / finally` block using `ortApi.ReleaseValue(tensor)`.

---

### WP-06: iPadOS Dual-Deck `AVAudioEngine`, 10-Band Biquad EQ, TimePitch & Real-Time FFT Visualizer

#### 1. Objective
Replace `DesktopPlaybackEngine.kt` (`xt.audio` / `PortAudio` + `JavaSound`) with a rock-solid iPadOS **Dual-Deck `AVAudioEngine` (`IOSDualAudioEngine`)** capable of sample-accurate crossfading, independent per-deck pitch-preserving tempo ramping (`AVAudioUnitTimePitch`), 10-band parametric Biquad EQ (`AVAudioUnitEQ`), loudness normalization (`replayGain`), and 60fps/120fps real-time FFT spectrum & waveform extraction for `WaveformSeekBar` and `VisualizerDataSource`.

#### 2. Upstream Files to Reference & Port
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlaybackEngine.kt` (57,611 bytes — dual deck playback: primary deck + transition/crossfade deck, per-deck gain, per-deck playback rate `0.5x–2.0x`, per-deck 3-band/10-band transition EQ filter, master 10-band EQ + preamp, real-time PCM tap for visualizer)
* `shared/src/commonMain/kotlin/com/music/bitchord/playback/EqualizerPreset.kt` (10 bands: `31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz`, range `-12 dB` to `+12 dB`)
* `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/TransitionFilter.kt` (Calculates dynamic bass/mid/treble EQ gain offsets during Smart Automix crossfades to prevent bass clashing and vocal masking)
* `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/VisualizerDataSource.kt` & `WaveformExtractor.kt`

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Construct the `AVAudioEngine` Node Graph
Build `IOSDualAudioEngine` with two complete, symmetrical decks (`Deck A` and `Deck B`) feeding into a master 10-band EQ and main mixer:

```
[Deck A: AVAudioPlayerNode]
       │
       ▼
[Deck A TimePitch: AVAudioUnitTimePitch]  <-- Controls tempo ramp (rate: 0.5..2.0, pitch: 0 cents)
       │
       ▼
[Deck A Transition EQ: AVAudioUnitEQ (3-band/10-band)] <-- Driven by TransitionFilter.kt during crossfades!
       │
       ▼
[Deck A Mixer: AVAudioMixerNode]          <-- Controls Deck A crossfade volume envelope & ReplayGain
       │
       ├──────────────────────────────┐
       │                              ▼
       │                    [Master 10-Band EQ: AVAudioUnitEQ (10 parametric bands + globalGain preamp)]
       │                              │
       ├──────────────────────────────┘
       │                              ▼
[Deck B Mixer: AVAudioMixerNode]    [engine.mainMixerNode] <-- Install 1024-sample PCM Tap for FFT Visualizer
       ▲                              │
       │                              ▼
[Deck B Transition EQ: AVAudioUnitEQ] [engine.outputNode] (Hardware Speaker / AirPods / USB DAC)
       ▲
       │
[Deck B TimePitch: AVAudioUnitTimePitch]
       ▲
       │
[Deck B: AVAudioPlayerNode]
```

##### Step 3.2: Configure the 10-Band Master EQ (`AVAudioUnitEQ`)
Initialize `masterEqNode = AVAudioUnitEQ(numberOfBands = 10)` to match `EqualizerPreset.kt`:
* Frequencies: `floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)`
* Filter types:
  - Band 0 (`31 Hz`): `AVAudioUnitEQFilterTypeLowShelf` (or `Parametric` with `bandwidth = 1.0` octaves, matching `EqualizerCurve.kt`'s peaking biquad math)
  - Bands 1..8 (`62 Hz`..`8 kHz`): `AVAudioUnitEQFilterTypeParametric`, `bandwidth = 1.0f`
  - Band 9 (`16 kHz`): `AVAudioUnitEQFilterTypeHighShelf` (or `Parametric`, `bandwidth = 1.0f`)
* Preamp: `masterEqNode.globalGain = preampDb` (`-12.0f` to `+12.0f`).

##### Step 3.3: Configure Per-Deck Transition EQ (`TransitionFilter.kt`)
During a Smart Automix transition, `PlaybackSmartController` and `TransitionFilter.kt` compute dynamic EQ cuts on the outgoing and incoming tracks (e.g., cutting low frequencies `< 250 Hz` on the incoming track until the phrase drop, and carving `1 kHz–4 kHz` vocal frequencies when both tracks have active vocals).
* Attach a dedicated `AVAudioUnitEQ(numberOfBands = 10)` (`deckATransitionEq`, `deckBTransitionEq`) to each deck so `TransitionFilter.kt`'s per-band gains are applied independently to the outgoing and incoming decks at 60 Hz during the transition window!

##### Step 3.4: Progressive Stream Caching & Frame-Accurate Seeking (`scheduleSegment`)
1. When a track starts playing:
   - If the track is already cached in `CacheStore` (or downloaded offline), open it directly as an `AVAudioFile(forReading = fileUrl)` and schedule it on the active deck via `playerNode.scheduleSegment(audioFile, startingFrame = seekFrame, frameCount = remainingFrames, atTime = nil)`.
   - If the track is streaming over the network and not yet cached, stream it through a local loopback proxy / incremental file writer that simultaneously writes to `CacheStore` (triggering `IOSAnalysisPipeline` as soon as download completes) while feeding `AVAudioPlayerNode` buffers (`scheduleBuffer`).
2. **Accurate Playback Position Computation**:
   - `AVAudioPlayerNode` resets `sampleTime` whenever `stop()` or `scheduleSegment()` is called. Track `baseSeekFrameOffset: AVAudioFramePosition` per deck and compute current position as:
     ```kotlin
     val nodeTime = playerNode.lastRenderTime ?: return lastKnownPositionMs
     val playerTime = playerNode.playerTimeForNodeTime(nodeTime) ?: return lastKnownPositionMs
     val currentFrame = baseSeekFrameOffset + playerTime.sampleTime
     val currentPositionMs = ((currentFrame.toDouble() / playerTime.sampleRate) * 1000.0).toLong()
     ```

##### Step 3.5: Real-Time FFT Visualizer Tap (`VisualizerDataSource`)
Install an audio tap on `engine.mainMixerNode.installTapOnBus(0u, bufferSize = 1024u, format = format)`:
- Compute RMS level, peak waveform samples, and 64-band FFT magnitude spectrum (using `Accelerate` `vDSP_dft_zrop` or the pure-Kotlin FFT in `DesktopPlaybackEngine.kt`).
- Publish the spectrum and waveform frames to `VisualizerDataSource` (`StateFlow`) so `NowPlayingScreen`, `LandscapePlayer`, and `PlayerWavyControls` animate at 60/120 fps.
- Implement `WaveformExtractor.kt` on iOS by reading the cached `AVAudioFile` via `ExtAudioFileRead` and computing the normalized peak envelope array for `WaveformSeekBar.kt`.

---


### WP-07: Smart Automix Controller Wiring, Crossfade/Time-Transfer Envelopes & Lock Screen Media Center

#### 1. Objective
Connect `PlaybackSmartController.kt`, `PlaybackSmartModels.kt`, `QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt`, and `TransitionFilter.kt` to `IOSDualAudioEngine` (`WP-06`) and wire iOS system media controls (`AVAudioSession`, `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`) so Smart Automix transitions run seamlessly even when the iPad screen is locked or BitChord is in the background.

#### 2. Upstream Files to Reference & Port
* `shared/src/commonMain/kotlin/com/music/bitchord/playback/smart/PlaybackSmartController.kt`
* `shared/src/commonMain/kotlin/com/music/bitchord/playback/smart/PlaybackSmartModels.kt`
* `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt`, `TransitionFilter.kt`
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlayerState.kt` (Specifically the `smartTransitionJob`, pre-buffering of `nextTrack` at `T - 25s`, and the `16ms` (60Hz) transition automation loop)

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Pre-Analysis & Deck Pre-Warming (`T - 25s`)
Match `DesktopPlayerState.kt`'s exact pre-warming lifecycle:
1. As soon as `currentTrack` begins playing on the active deck (say `Deck A`) and the queue has a `nextTrack`:
   - Resolve `nextTrack`'s stream URL via `PlaybackStreamResolver` and begin downloading/caching it via `CacheStore`.
   - Once downloaded, immediately trigger `IOSAnalysisPipeline.analyzeTrack(nextTrack)` on the background analysis dispatcher so both `currentTrackFeatures` and `nextTrackFeatures` are populated in memory well before the transition window.
2. Call `PlaybackSmartController.computeTransitionPlan(currentFeatures, nextFeatures, settings)` to obtain the `SmartTransitionPlan`:
   - `outCueMs` (exact beat/phrase boundary on `Deck A` where the mix begins)
   - `inCueMs` (exact beat/phrase boundary on `Deck B` where `nextTrack` starts)
   - `durationMs` (transition length)
   - `timeTransferMode` (`TimeTransferMode.OFF`, `GRADUAL`, `SYNC_BPM`, etc.)
   - `initialNextRate` & `targetCurrentRate` (BPM ratio clamping between `0.85x` and `1.15x`)
   - `mixWarnings` (key clash, large BPM gap, vocal overlap — surfaced to `TransitionWarningDialog.kt`)

##### Step 3.2: The 60Hz (`16ms`) Transition Automation Loop
When `Deck A` playback position reaches `plan.outCueMs`:
1. Schedule `nextTrack` on `Deck B` starting at `plan.inCueMs` with initial gain `0.0f` and `deckBTimePitch.rate = plan.initialNextRate`.
2. Start a high-priority coroutine loop (`Dispatchers.Default`, `delay(16L)`):
   - Compute normalized transition progress `t = ((deckAPositionMs - plan.outCueMs).toFloat() / plan.durationMs).coerceIn(0f, 1f)`.
   - Query `PlaybackSmartController.evaluateStep(plan, t)` and `TransitionFilter.computeGains(plan, t, currentFeatures, nextFeatures)`:
     - Update `deckAMixer.outputVolume` and `deckBMixer.outputVolume` (equal-power cosine curve `cos(t * PI / 2)` / `sin(t * PI / 2)` or vocal-ducked envelope).
     - Update `deckATimePitch.rate` and `deckBTimePitch.rate` so both tracks beat-match during the overlap and smoothly ramp `Deck B` back to `1.0x` (`originalBpm`) as `t -> 1.0f`.
     - Update `deckATransitionEq` and `deckBTransitionEq` 10-band gains (bass swap at `t = 0.5f` + mid-band vocal ducking if `VocalTracker` reports simultaneous vocals).
3. When `t >= 1.0f`:
   - Stop `Deck A`, reset `deckATimePitch.rate = 1.0f` and `deckATransitionEq` gains to `0 dB`, swap active deck pointer (`activeDeck = DeckB`), and promote `nextTrack` to `currentTrack`.

##### Step 3.3: iOS `AVAudioSession`, Interruption Recovery & `MPNowPlayingInfoCenter`
Create `IOSNowPlayingManager.kt` in `shared/src/iosMain/kotlin/com/music/bitchord/playback/`:
1. **Configure `AVAudioSession`**:
   ```kotlin
   val session = AVAudioSession.sharedInstance()
   session.setCategory(
       AVAudioSessionCategoryPlayback,
       mode = AVAudioSessionModeDefault,
       options = AVAudioSessionCategoryOptionAllowAirPlay or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
       error = null
   )
   session.setActive(true, error = null)
   ```
2. **Observe `AVAudioSessionInterruptionNotification` & `AVAudioSessionRouteChangeNotification`**:
   - On `AVAudioSessionInterruptionTypeBegan`: pause active deck(s).
   - On `AVAudioSessionInterruptionTypeEnded` with `AVAudioSessionInterruptionOptionShouldResume`: restart `AVAudioEngine` (`engine.startAndReturnError(null)`) and resume playback.
   - On `AVAudioSessionRouteChangeReasonOldDeviceUnavailable` (e.g., AirPods disconnected): pause playback immediately per Apple HIG.
3. **Wire `MPRemoteCommandCenter` & `MPNowPlayingInfoCenter`**:
   - Register handlers for `playCommand`, `pauseCommand`, `togglePlayPauseCommand`, `nextTrackCommand`, `previousTrackCommand`, and `changePlaybackPositionCommand` (Control Center / Lock Screen scrubbing).
   - Update `MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo` with `MPMediaItemPropertyTitle`, `MPMediaItemPropertyArtist`, `MPMediaItemPropertyAlbumTitle`, `MPMediaItemPropertyPlaybackDuration`, `MPNowPlayingInfoPropertyElapsedPlaybackTime`, `MPNowPlayingInfoPropertyPlaybackRate`, and `MPMediaItemPropertyArtwork` (`MPMediaItemArtwork` rendered from Coil's cached `UIImage`).

---

### WP-08: SQLite KMP Persistence (`Database`, `CacheStore`, `LibraryStore`, `ListeningStats` & Offline Downloads)

#### 1. Objective
Port `:desktopApp`'s complete SQLite persistence layer (`DesktopDatabase.kt`, `DesktopCacheStore.kt`, `DesktopLibraryStore.kt`, `DesktopListeningStats.kt`, `DesktopDownloads.kt`, `DesktopSyncEngine.kt`) to KMP SQLite (`androidx.sqlite:sqlite-bundled:2.5.0`) so that all tables, schemas, migrations, offline audio caches, and TrackFeatures JSON blobs are 100% compatible with Desktop/Android.

#### 2. Upstream Files to Reference & Port
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopDatabase.kt` (17,746 bytes — SQLite schema initialization, WAL mode, versioned schema migrations)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopCacheStore.kt` (15,041 bytes — LRU audio file cache, `track_analysis` JSON blob persistence, stream metadata cache, waveform cache)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopLibraryStore.kt` (47,853 bytes — local/synced songs, albums, artists, playlists, playlist_songs, liked tracks, search history, queue state persistence)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopListeningStats.kt` (32,850 bytes — play events, total listen time, top tracks/artists/albums/genres by period for `StatsSheet.kt` and `ReplayScreen.kt`)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopDownloads.kt` & `DesktopDownloadService.kt` (Offline track/playlist download manager)
* `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopSyncEngine.kt` (41,667 bytes — bidirectional YouTube Music library sync)

#### 3. Step-by-Step Implementation Instructions

##### Step 3.1: Port `DesktopDatabase.kt` to `androidx.sqlite:sqlite-bundled:2.5.0`
1. Move `DesktopDatabase.kt`, `DesktopCacheStore.kt`, `DesktopLibraryStore.kt`, `DesktopListeningStats.kt`, and `DesktopSyncEngine.kt` into `shared/src/commonMain/kotlin/com/music/bitchord/data/db/` and replace `java.sql.Connection` / `PreparedStatement` / `ResultSet` with `androidx.sqlite.SQLiteConnection` and `SQLiteStatement` (`BundledSQLiteDriver()`).
2. Preserve **100% of the exact SQL DDL statements and column names** from `DesktopDatabase.kt` (`PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;`), including:
   - `tracks`, `albums`, `artists`, `playlists`, `playlist_tracks`
   - `track_analysis` (`video_id TEXT PRIMARY KEY, features_json TEXT, updated_at INTEGER`)
   - `audio_cache` (`video_id TEXT PRIMARY KEY, file_path TEXT, mime_type TEXT, size_bytes INTEGER, last_accessed INTEGER`)
   - `downloads` (`video_id TEXT PRIMARY KEY, file_path TEXT, status TEXT, progress REAL, ...`)
   - `play_history` & `listening_stats` (powers `StatsSheet.kt` and `ReplayScreen.kt`)
   - `search_history`, `saved_queues`, `app_settings`

##### Step 3.2: iPadOS Sandbox File System Paths (`NSFileManager`)
Create `expect object PlatformPaths` in `shared/src/commonMain/kotlin/com/music/bitchord/data/db/PlatformPaths.kt` and implement `PlatformPaths.ios.kt`:
* **Database & Offline Downloads Directory**:
  - `NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, inDomains = NSUserDomainMask).first()!!` -> `<Application Support>/BitChord/bitchord.db` and `<Application Support>/BitChord/downloads/`.
  - **CRITICAL**: Set `NSURLIsExcludedFromBackupKey = true` on the `downloads/` directory so multi-gigabyte offline music downloads do not exhaust the user's iCloud Backup quota (required by Apple App Store & AltStore best practices).
* **Transient Audio Stream & Analysis Cache Directory**:
  - `NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, inDomains = NSUserDomainMask).first()!!` -> `<Caches>/BitChord/audio_cache/`.

---

### WP-09: Multi-Source Ecosystem (Spotify, JioSaavn, WebDAV), 8-Provider Lyrics + Translation, Listen Together, Party Sync, Discord RPC & Scrobbling

#### 1. Objective
Ensure 100% parity with every ecosystem integration present in BitChord v1.8-beta1 (`:shared` and `:desktopApp`). None of these subsystems may be stubbed or omitted!

#### 2. Subsystem-by-Subsystem Porting Instructions

##### Subsystem 9.1: Multi-Source Registry, Track Matcher, JioSaavn, Spotify Importer & WebDAV Cloud Music
* **Upstream Files**:
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/sources/{SourceTrackKeys.kt, StreamFormat.kt, TrackMatcher.kt}`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/{SpotifyImporter.kt, SpotifyLibrary.kt}`
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/webdav/WebDavAuth.kt`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/{DesktopJioSaavn.kt, DesktopSourceConfig.kt, DesktopSourceRegistry.kt, DesktopOriginalVersion.kt, DesktopWebDavFormState.kt}`
* **Porting Actions**:
  1. Move `DesktopJioSaavn.kt` (19,708 bytes — JioSaavn API search, DES-ECB URL decryption for 320kbps MP4/M4A streams, and TrackMatcher integration), `DesktopSourceConfig.kt`, `DesktopSourceRegistry.kt`, and `DesktopOriginalVersion.kt` into `shared/src/commonMain/kotlin/com/music/bitchord/data/sources/`.
  2. In `DesktopJioSaavn.kt`, replace `javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")` (used to decrypt JioSaavn `encrypted_media_url` with key `"38346591"`) with a pure-Kotlin DES-ECB block cipher (or `CCCrypt(kCCDecrypt, kCCAlgorithmDES, kCCOptionPKCS7Padding or kCCOptionECBMode, ...)` from `platform.CoreCrypto` on `iosMain`).
  3. Verify `WebDavAuth.kt` and WebDAV PROPFIND XML directory listing work with `ktor-client-darwin` so users can stream their self-hosted FLAC/MP3/M4A libraries over WebDAV on iPadOS.
  4. Verify `SpotifyImporter.kt` and `SpotifyLibrary.kt` work in `commonMain` to import Spotify playlists/liked tracks and match them via `TrackMatcher.kt`.

##### Subsystem 9.2: 8-Provider Synchronized Lyrics Engine + Real-Time Lyrics Translation
* **Upstream Files**:
  * `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/` (`LyricsRegistry.kt`, `LrcParser.kt`, `KuGouLyricsSource.kt`, `LrcLibLyricsSource.kt`, `NetEaseLyricsSource.kt`, `PaxsenixLyricsSource.kt`, `SimpMusicLyricsSource.kt`, `YouTubeLyricsSource.kt`, `YouTubeSubtitleLyricsSource.kt`)
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/{DesktopTranslationSetting.kt, DesktopTranslationLanguageUi.kt}`
* **Porting Actions**:
  1. In `KuGouLyricsSource.kt`, `.krc` compressed word-by-word lyrics are XOR-decrypted (`charArrayOf('@', 'G', 'a', 'w', '^', '2', 't', 'G', 'Q', '6', '1', '-', 'Î', 'Ò', 'n', 'i')`) and then `zlib`-inflated (`java.util.zip.InflaterInputStream`). Replace `InflaterInputStream` with an `expect fun zlibDecompress(input: ByteArray): ByteArray` implemented on `iosMain` via `platform.zlib.uncompress` / `inflateInit` (`-lz`).
  2. Move `DesktopTranslationSetting.kt` and the Google Translate / lyrics translation pipeline into `commonMain` so `LyricsTranslationBadge.kt` and dual-line translated lyrics in `PlayerLyrics.kt` work identically on iPadOS.

##### Subsystem 9.3: Listen Together & Party Sync (`ListenTogether` + `DesktopPartySync.kt`)
* **Upstream Files**:
  * `shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/*`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/{DesktopListenTogetherManager.kt, DesktopPartySync.kt}`
* **Porting Actions**:
  1. Move `DesktopListenTogetherManager.kt` (WebSocket transport implementation using Ktor `webSocketSession`, room creation/joining, QR code generation, member list sync, host/guest drift compensation) and `DesktopPartySync.kt` (27,138 bytes — NTP clock offset calibration and scheduled start synchronization) into `shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/`.
  2. Enable `CIO` or `Darwin` WebSocket engine in `iosMain` (`HttpClient(Darwin) { install(WebSockets) }`).

##### Subsystem 9.4: Discord Rich Presence (`com/my/kizzy`) & Presence (`DesktopPresence.kt`)
* **Upstream Files**:
  * `shared/src/jvmSharedMain/kotlin/com/my/kizzy/**`
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPresence.kt`
* **Porting Actions**:
  1. On iPadOS, local Unix domain socket IPC (`discord-ipc-0`) does not exist because iOS apps are sandboxed. However, **Kizzy's Discord Gateway WebSocket (`KizzyRepository` + `DiscordWebSocket` token-based Rich Presence)** works 100% over standard WebSockets (`wss://gateway.discord.gg/`)!
  2. Move `com/my/kizzy/**` (Gateway WebSocket mode) and `DesktopPresence.kt` to `commonMain` so users who sign in with their Discord token get live Rich Presence with album art, timestamps, and buttons on iPadOS.

##### Subsystem 9.5: Last.fm / ListenBrainz Scrobbling & BitChord Replay (`DesktopScrobbling.kt` & `DesktopReplayUi.kt`)
* **Upstream Files**:
  * `desktopApp/src/main/kotlin/com/music/bitchord/desktop/{DesktopScrobbling.kt, DesktopScrobbleSettings.kt, DesktopListeningStats.kt, DesktopReplayUi.kt}`
  * `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/replay/{ReplayEntryCard.kt, ReplayModels.kt, ReplayScreen.kt, ReplayShareCardGenerator.kt, ReplayStoryCards.kt}`
* **Porting Actions**:
  1. Move `DesktopScrobbling.kt` (24,132 bytes — sends `track.updateNowPlaying` on track start and `track.scrobble` after 50% or 4 minutes of playback to Last.fm / ListenBrainz) and `DesktopScrobbleSettings.kt` to `commonMain`.
  2. Connect `DesktopListeningStats.kt` and `DesktopReplayUi.kt` to `ReplayScreen.kt` and `ReplayShareCardGenerator.kt` so users can view and share their monthly/yearly BitChord Replay story cards on iPadOS (`UIActivityViewController`).

---

### WP-10: `:sharedUi` `iosMain` Actuals, Animated Canvas Video Backdrops & Application State Orchestrator

#### 1. Objective
Implement all 11 `expect`/`actual` platform UI files in `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/`, implement the **Animated Canvas Video Backdrop (`PlatformCanvasVideo.kt` + `DesktopCanvas*.kt`)** using `AVQueuePlayer` + `AVPlayerLooper`, and wire `DesktopPlayerState.kt` + `Main.kt` orchestration into `AppUi.kt` inside `MainViewController.kt`.

#### 2. Complete Implementation Guide for Every `sharedUi/src/iosMain` File

Inspect `sharedUi/src/jvmMain/kotlin/com/music/bitchord/ui/` and `sharedUi/src/androidMain/kotlin/com/music/bitchord/ui/` and create the exact matching `actual` implementations in `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/`:

1. **`ui/components/BackHandlerCompat.ios.kt`**:
   - Delegate to `@OptIn(ExperimentalComposeUiApi::class) androidx.compose.ui.backhandler.BackHandler(enabled, onBack)` (supported natively in Compose Multiplatform 1.10.3 on iOS for predictive back / edge swipe!).
2. **`ui/components/PlatformScrollbar.ios.kt`**:
   - Match `androidMain` (no-op or subtle fade scrollbar overlay on touch devices).
3. **`ui/components/QrCodeBitmap.ios.kt`**:
   - Used by `ListenTogetherDialog.kt` to render the room invite QR code.
   - Implement using Apple's native **`CoreImage` (`CIFilter.filterWithName("CIQRCodeGenerator")`)**:
     ```kotlin
     val filter = CIFilter.filterWithName("CIQRCodeGenerator")
     filter?.setValue(content.encodeToByteArray().toNSData(), forKey = "inputMessage")
     filter?.setValue("M", forKey = "inputCorrectionLevel")
     // Scale CIImage via CGAffineTransformMakeScale, render via CIContext to RGBA bytes,
     // and wrap in org.jetbrains.skia.Image.makeRaster(...).toComposeImageBitmap()
     ```
4. **`ui/graphics/BitmapLoader.ios.kt`**:
   - Decode `ByteArray` or file path to `ImageBitmap` via `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()`.
5. **`ui/graphics/PlatformBlur.ios.kt`**:
   - Identical to `jvmMain`: use `Modifier.blur(radius)` or Skia `ImageFilter.makeBlur(sigmaX, sigmaY, FilterTileMode.CLAMP).asComposeRenderEffect()`.
6. **`ui/haptics/PlatformHaptics.ios.kt`**:
   - Implement rich iPadOS/iOS haptics using `UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleLight / Medium)` and `UISelectionFeedbackGenerator()`.
7. **`ui/player/LockScreenThemeEffect.ios.kt`**:
   - Update `UIApplication.sharedApplication.connectedScenes` status bar style (`UIStatusBarStyleLightContent` / `DarkContent`) based on current theme.
8. **`ui/player/MeshGradientShader.ios.kt`**:
   - **CRITICAL**: Both `jvmMain` and `iosMain` in Compose Multiplatform run on **Skiko (`org.jetbrains.skia`)**! Copy `sharedUi/src/jvmMain/kotlin/com/music/bitchord/ui/player/MeshGradientShader.kt` (`org.jetbrains.skia.RuntimeEffect.makeForShader(SKSL_SOURCE)` + `RuntimeShaderBuilder`) **verbatim** to `sharedUi/src/iosMain/`! This gives 100% pixel-identical animated Apple Music-style fluid mesh gradients in `NowPlayingScreen` and `LandscapePlayer`.
9. **`ui/player/PlatformCanvasVideo.ios.kt` & Animated Canvas Video Subsystem (`DesktopCanvas*.kt`)**:
   - Upstream has a rich Animated Canvas Video subsystem (`shared/.../data/canvas/{CanvasArtwork.kt, SpotifyCanvasQuery.kt}` and `desktopApp/.../{DesktopCanvas.kt, DesktopCanvasBackdrop.kt, DesktopCanvasDecoder.kt, DesktopCanvasSources.kt, DesktopCanvasVideo.kt, DesktopSpotifyCanvas.kt}`) that displays looping Apple Music animated artwork (`.m3u8` / `.mp4`) and Spotify Canvas (`.mp4`) videos behind `NowPlayingScreen` and `LandscapePlayer`.
   - Move `DesktopCanvas.kt`, `DesktopCanvasSources.kt`, and `DesktopSpotifyCanvas.kt` into `shared/src/commonMain/kotlin/com/music/bitchord/data/canvas/`.
   - In `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/player/PlatformCanvasVideo.ios.kt`:
     - Use an `AVQueuePlayer` + `AVPlayerLooper` with `player.muted = true` and `player.preventsDisplaySleepDuringVideoPlayback = false`.
     - Attach an `AVPlayerItemVideoOutput(pixelBufferAttributes = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA))` and sample frames via `CADisplayLink` (or `withFrameMillis` inside Compose) into an `ImageBitmap` drawn directly onto the Compose `Canvas` (so Compose's blur, liquid glassmorphism, and overlay gradients composite seamlessly on top of the video without `UIKitView` z-ordering clipping issues!).
10. **`ui/player/PlatformKeepScreenOn.ios.kt`**:
    - Toggle `UIApplication.sharedApplication.idleTimerDisabled = keepOn` inside a `DisposableEffect(keepOn)` so the iPad screen never dims while viewing synchronized lyrics or `LandscapePlayer`.
11. **`ui/theme/PlatformFont.ios.kt`**:
    - Load the bundled fonts (`inter_variable.ttf`, `outfit_bold.ttf`, `jetbrains_mono_regular.ttf`, `doto_variable.ttf`, `bbh_bartle_regular.ttf`) from `composeResources/font/`.

---

### WP-11: iPadOS-Specific Polish (120Hz ProMotion, Stage Manager Resizing, Hardware Keyboard Shortcuts & Share Sheet)

#### 1. Objective
Deliver a true flagship iPadOS experience that takes full advantage of iPad hardware (120Hz ProMotion, Stage Manager / Split View / Slide Over dynamic layout transitions, Magic Keyboard shortcuts, and native iPad popover Share Sheets).

#### 2. Step-by-Step Implementation Instructions

##### Step 2.1: Adaptive Layout & Stage Manager Dynamic Window Resizing
`AppUi.kt` and `LandscapePlayer.kt` already support both Portrait/Compact (`PlayerDock` + expandable `NowPlayingScreen`) and Landscape/Expanded (`NavigationRail` + `LandscapePlayer` + `PlayerSidePanels` for Queue/Lyrics).
1. Ensure `ComposeUIViewController` observes live window size changes via `BoxWithConstraints` / `currentWindowAdaptiveInfo()` so resizing BitChord inside **iPadOS Stage Manager** or **Split View (1/3, 1/2, 2/3 width)** dynamically transitions between `LandscapePlayer` (expanded width >= 840dp) and compact `NowPlayingScreen` without restarting or losing scroll/playback state.
2. Set `UIRequiresFullScreen = false` and support all 4 interface orientations (`UIInterfaceOrientationPortrait`, `PortraitUpsideDown`, `LandscapeLeft`, `LandscapeRight`) in `iosApp/Info.plist`.

##### Step 2.2: 120Hz ProMotion Rendering (`Info.plist` + Skiko)
Add `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` to `iosApp/Info.plist` so Skiko/Metal renders mesh gradients, waveform seekbars, and synchronized lyrics scrolling at the full **120Hz ProMotion** refresh rate on iPad Pro models.

##### Step 2.3: iPad Magic Keyboard / Hardware Keyboard Shortcuts (`UIKeyCommand`)
Port the keyboard shortcuts from `desktopApp/src/main/kotlin/com/music/bitchord/desktop/Main.kt` to iPadOS `UIKeyCommand` on the root `UIViewController`:
* `Space`: Play / Pause (`togglePlayPause()`)
* `Command + Right Arrow`: Next Track (`seekToNext()`)
* `Command + Left Arrow`: Previous Track (`seekToPrevious()`)
* `Right Arrow` / `Left Arrow`: Seek `+5s` / `-5s`
* `Up Arrow` / `Down Arrow`: Volume `+5%` / `-5%`
* `Command + F`: Focus Search Screen
* `Command + L`: Toggle Lyrics Panel
* `Command + Q` / `Command + U`: Toggle Queue Panel
* `Command + M`: Toggle Mute

---

### WP-12: Automated `.ipa` Build Pipeline, Sideload Packaging & E2E Parity Verification

#### 1. Objective
Provide a single-command reproducible build script (`scripts/build_ipados_ipa.sh`) and GitHub Actions workflow (`.github/workflows/build-ipados-ipa.yml`) that compiles the C++ analyzer (`libbitchord_analysis.a`), links the Kotlin Multiplatform `SharedUI.framework`, builds the Xcode project (`iosApp`), and packages both an **unsigned `.ipa`** (for AltStore / SideStore / Sideloadly / TrollStore) and a **signed `.ipa`** (when Apple Developer certificates are present).

#### 2. Step-by-Step Build Script (`scripts/build_ipados_ipa.sh`)

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== [1/4] Compiling Native C++17 DSP Analyzer (libbitchord_analysis.a) ==="
bash native/analyzer/ios_c_bridge/build_ios_static.sh

echo "=== [2/4] Linking KMP SharedUI Static Framework (iosArm64) ==="
./gradlew :sharedUi:linkReleaseFrameworkIosArm64 --no-daemon

echo "=== [3/4] Archiving Xcode Project (iosApp) for generic/platform=iOS ==="
cd "$ROOT_DIR/iosApp"
if [ -f "Podfile" ]; then
  pod install
  XCODE_TARGET=(-workspace iosApp.xcworkspace)
else
  XCODE_TARGET=(-project iosApp.xcodeproj)
fi

xcodebuild build \
  "${XCODE_TARGET[@]}" \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$ROOT_DIR/build/ios_derived" \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO

echo "=== [4/4] Packaging Payload/BitChord.app into BitChord-v1.8-beta1-iPadOS.ipa ==="
APP_PATH="$ROOT_DIR/build/ios_derived/Build/Products/Release-iphoneos/BitChord.app"
IPA_STAGE="$ROOT_DIR/build/ipa_stage"
rm -rf "$IPA_STAGE"
mkdir -p "$IPA_STAGE/Payload"
cp -R "$APP_PATH" "$IPA_STAGE/Payload/BitChord.app"

# Apply ad-hoc pseudo-signature with entitlements so TrollStore/AltStore/SideStore handle it cleanly
ldid -S"$ROOT_DIR/iosApp/iosApp.entitlements" "$IPA_STAGE/Payload/BitChord.app/BitChord" || codesign --force --deep --sign - "$IPA_STAGE/Payload/BitChord.app"

cd "$IPA_STAGE"
zip -r -9 "$ROOT_DIR/build/BitChord-v1.8-beta1-iPadOS.ipa" Payload
echo "SUCCESS: Built $ROOT_DIR/build/BitChord-v1.8-beta1-iPadOS.ipa"
```

---

## 5. Junior AI Coding Agent Prompt Templates (Copy-Paste Ready for Orchestrator)

When delegating any Work Package (`WP-01` through `WP-12`) to a subagent, use the following structured prompt template:

```markdown
You are a Junior Coding Subagent executing **[WP-XX: <Title>]** from `D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md`.

### Mandatory Context & Constraints:
1. Read `D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md` Section 0, Section 1, and Section `WP-XX` in full before editing any file.
2. Do NOT create or expect `gradle/libs.versions.toml` — declare dependencies directly in the module `build.gradle.kts` files matching upstream conventions (`Kotlin 2.3.20`, `Compose Multiplatform 1.10.3`, `Ktor 3.4.0`, `Coil 3.4.0`).
3. Remember that upstream `:shared` and `:sharedUi` have most code inside `src/jvmSharedMain/kotlin/`, NOT `src/commonMain/kotlin/`.
4. Do NOT rewrite `native/analyzer/*.cpp` in Swift/Accelerate — compile the exact upstream C++17 files and `result_to_json` serializer via `bitchord_ios_bridge.{h,cpp}`.
5. Only modify files within your assigned Work Package boundary.

### Required Deliverable:
- Implement all files specified in `WP-XX`.
- Run `./gradlew :shared:compileKotlinIosArm64 :sharedUi:compileKotlinIosArm64` (or the relevant verification task) and fix all compiler errors before reporting completion.
```

---

## 6. Senior Architect PR Audit & Verification Checklist

Before approving any Junior Subagent's Pull Request, the Senior Architect Agent MUST verify:

- [ ] **Zero `java.*` / `javax.*` Leaks in `commonMain`**: Run `rg "import java\.|import javax\." shared/src/commonMain sharedUi/src/commonMain` — output MUST be empty.
- [ ] **No Missing `:sharedUi` Screen or Component**: Verify all 25 files in `ui/player/`, 20 files in `ui/components/`, 5 files in `ui/replay/`, and 4 files in `ui/screens/` are in `sharedUi/src/commonMain` and compile for `iosArm64`.
- [ ] **Bit-Exact C++ DSP & JSON Schema**: Feed a known 30-second 44.1kHz test tone/song into `bitchord_analyze_pcm_json`, `bitchord_compute_mel_spectrogram`, and `bitchord_compute_vocal_spectrogram` on iOS and assert that `TrackFeatures.fromJson(json)` produces identical BPM, key, Camelot code, phrase boundaries, and spectrogram dimensions (`[T, 128]` and `[2, 1487, T]`) as Desktop/Android.
- [ ] **ONNX Tensor Lifecycle Safety**: Verify `IOSBeatModel.kt` and `IOSVocalModel.kt` call `ortApi.ReleaseValue()` on every input and output `OrtValue*` inside a `finally` block so memory remains flat across 50+ track transitions.
- [ ] **Dual-Deck Smart Automix Verification**: Verify that during a transition window (`plan.outCueMs` -> `plan.outCueMs + plan.durationMs`), both `Deck A` and `Deck B` play simultaneously on `IOSDualAudioEngine`, `AVAudioUnitTimePitch.rate` ramps smoothly without clicks, and `TransitionFilter.kt` updates `deckATransitionEq` and `deckBTransitionEq` at 60Hz.
- [ ] **YouTube Stream Playback Without HTTP 403**: Verify `PlaybackStreamResolver` + `JsEvaluator` (`JavaScriptCore`) + `IOSPoTokenGenerator` (`WKWebView`) successfully resolves and streams 10 consecutive YouTube Music tracks without HTTP 403 errors.
- [ ] **Background & Lock Screen Continuity**: Lock the iPad during an active Smart Automix transition and verify that `AVAudioEngine` transitions seamlessly to `nextTrack` while `MPNowPlayingInfoCenter` updates the Lock Screen artwork and metadata.
