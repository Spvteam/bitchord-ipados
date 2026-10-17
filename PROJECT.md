# Project: BitChord iPadOS / iOS Port

## Architecture
BitChord is ported from a JVM/Android Kotlin Multiplatform architecture to an Apple-native iPadOS/iOS stack (`iosArm64` physical iPad and `iosSimulatorArm64` Apple Silicon simulator), preserving 1:1 functional, audio, and visual parity with the upstream desktop and Android clients.

### Module & Package Boundaries
- `:innertubex`: In-tree vendored KMP library for YouTube Music API client, player models, and extractor contracts. Configured with `commonMain`, `jvmMain`, `androidMain`, `iosArm64()`, and `iosSimulatorArm64()`.
- `:shared`: Core business logic, repositories (`YtMusicRepository`), stream resolution, audio analysis, lyrics registry, and state synchronization. Purified into `commonMain` with Apple platform actuals in `iosMain` (`PlatformCrypto`, `PlatformClock`, `PlatformUrl`, `PlatformNormalizer`, `IOSKeychainAuthStore`).
- `:sharedUi`: Compose Multiplatform user interface, adaptive layouts (Stage Manager, Split View, Navigation Rail), Skia Metal shaders (Liquid Glass / Haze, animated mesh gradients), lyrics presentation, and player controls. 100% Kotlin UI code in `commonMain` with platform actuals in `iosMain` (`ComposeUIViewController`, `Haptics`, `ShareSheet`).
- `native/analyzer`: In-tree dependency-free C++17 audio DSP engine (`audio_analysis.cpp`, `tempo_analysis.cpp`, `resampler.cpp`, `mel_spectrogram.cpp`, `vocal_spectrogram.cpp`). Compiled to `libbitchord_analysis.a` and linked to `:shared` via Kotlin/Native `cinterop`.
- `iosApp`: Native Xcode project hosting the `ComposeUIViewController`, configuring `Info.plist`, entitlements, background audio modes (`audio`), and producing the final sideload-ready `.ipa` bundle.

### Data Flow
1. **Catalog & Stream Resolution**: `UI` -> `YtMusicRepository` (`:shared/commonMain`) -> `InnerTubeXResolver` -> Innertube waterfall (`ANDROID_VR -> IOS -> WEB_REMIX`).
2. **Cipher & BotGuard**: `JSContext` (`JavaScriptCore`) deciphering `s` signature and `n` throttling parameter; headless `WKWebView` generating BotGuard `poToken`.
3. **Audio Streaming & Decks**: Resolved stream -> `bitchord-chunked://` intercepted by `AVAssetResourceLoaderDelegate` (512 KB chunk requests, headers injected, progressive cache) -> Dual `AVAudioPlayerNode` (Deck A & B) in `AVAudioEngine` -> `AVAudioUnitTimePitch` -> `AVAudioUnitEQ` (10-band) -> Equal-power $\sin/\cos$ crossfade -> Master output & `VisualizerDataSource` (vDSP FFT tap).
4. **Neural Analysis**: Audio PCM via `AudioToolbox.ExtAudioFileRead` -> `libbitchord_analysis.a` (C++ DSP) + `onnxruntime-c:1.20.0` (`beat_this_int8.onnx`, `vocals_umxhq_int8.onnx`) -> `TrackFeatures` JSON -> Smart Automix transition plans.
5. **System Integration**: `AVAudioSessionCategoryPlayback` (background audio continuity), `MPNowPlayingInfoCenter` (artwork, scrubbing, metadata), `MPRemoteCommandCenter` (lock screen and Control Center controls).

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | iOS Gradle Targets | Add `iosArm64()` and `iosSimulatorArm64()` to `:shared` and `:sharedUi` with static framework binaries | M1 (WP-01) | Survey-1, ORIGINAL_REQUEST §R1 |
| 2 | Dependency Decoupling | Wire `ktor-client-darwin:3.5.2` in `:shared/iosMain` and `coil-network-ktor3:3.0.4` in `:sharedUi` | M1 (WP-01) | Survey-1, ORIGINAL_REQUEST §R1 |
| 3 | Vendor `:innertubex` | Vendor `MetrolistGroup/innertubex:v0.7.0` in-tree with iOS target support | M1 (WP-01) | Survey-1, Porting Plan §1.1 |
| 4 | UI Relocation | Move 49 Kotlin UI files from `sharedUi/src/jvmSharedMain/` to `sharedUi/src/commonMain/` | M2 (WP-02) | Survey-1, Porting Plan §WP-02 |
| 5 | Shared Logic Relocation | Move domain models, queue builders, 28 lyrics sources, and Discord Kizzy to `shared/src/commonMain/` | M2 (WP-02) | Survey-1, Porting Plan §WP-02 |
| 6 | Platform Primitives | Provide `PlatformCrypto` (`CC_SHA1`/`CC_MD5`), `PlatformClock`, `PlatformString`, `PlatformUrl` in `commonMain` + `iosMain` | M2 (WP-02) | Survey-1, ORIGINAL_REQUEST §R1 |
| 7 | C++17 DSP Analyzer Bridge | Compile `native/analyzer/*.cpp` to `libbitchord_analysis.a` and bridge C ABI via Kotlin/Native `cinterop` | M3 (WP-04) | Survey-2, Porting Plan §WP-04 |
| 8 | Innertube Waterfall & M4A | Implement multi-client waterfall (`ANDROID_VR -> IOS -> WEB_REMIX`), prioritize AAC/M4A (`itag 140/141`), 1 MiB bounded range probe | M4 (WP-03) | Survey-2, ORIGINAL_REQUEST §R2 |
| 9 | JavaScriptCore Cipher | Synchronous `s` signature deciphering and `n` throttling transform via native Apple `JSContext` | M4 (WP-03) | Survey-2, ORIGINAL_REQUEST §R2 |
| 10 | Headless WKWebView PoToken | 3-stage BotGuard handshake (`Create` -> VM -> `GenerateIT` -> `obtainPoToken`) via `WKScriptMessageHandler` with 6h caching | M4 (WP-03) | Survey-2, ORIGINAL_REQUEST §R2 |
| 11 | Authentication & Keychain | Modal `WKWebView` Google/Spotify login, `SAPISIDHASH` generation, and hardware-backed iOS Keychain storage | M5 (WP-07) | Survey-2, ORIGINAL_REQUEST §R2 |
| 12 | KMP SQLite & Sandbox Paths | Port persistence to `androidx.sqlite:sqlite-bundled:2.5.0` with `NSApplicationSupportDirectory` / `NSCachesDirectory` | M6 (WP-08) | Survey-1, Porting Plan §WP-08 |
| 13 | ONNX Runtime & PCM Decoder | Bridge `onnxruntime-c:1.20.0` for `beat_this_int8.onnx` & `vocals_umxhq_int8.onnx`, PCM via `ExtAudioFileRead` | M7 (WP-05) | Survey-3, Porting Plan §WP-05 |
| 14 | Dual-Deck `AVAudioEngine` | Dual `AVAudioPlayerNode` (Deck A & B), independent `AVAudioUnitTimePitch`, equal-power $\sin/\cos$ crossfade (0–12s), 10-band EQ | M8 (WP-06) | Survey-3, ORIGINAL_REQUEST §R3 |
| 15 | Chunked Stream Loader | `AVAssetResourceLoaderDelegate` (`bitchord-chunked://`), 512 KB chunk windowing, authenticated headers, 403 refusal recovery | M8 (WP-06) | Survey-3, ORIGINAL_REQUEST §R3 |
| 16 | Background Audio & Lock Screen | `AVAudioSessionCategoryPlayback`, `MPNowPlayingInfoCenter` (artwork, scrub), `MPRemoteCommandCenter` controls | M8 (WP-06) | Survey-3, ORIGINAL_REQUEST §R3 |
| 17 | Multi-Source Lyrics & RPC | 12+ lyrics sources with word/syllable sync, translation, Spotify `sp_dc`, Discord RPC, Last.fm scrobbling | M9 (WP-09) | Survey-1, Porting Plan §WP-09 |
| 18 | Compose iPadOS UI & Shaders | `ComposeUIViewController`, Stage Manager / Split View responsive breakpoints, Liquid Glass / Haze Skia shaders, 120Hz ProMotion, 9:16 story export | M10 (WP-10) | Survey-3, ORIGINAL_REQUEST §R4 |
| 19 | Automated `.ipa` Packaging | Setup `scripts/build_ipados_ipa.sh`, Xcode project, entitlements (`keychain-access-groups`), `ldid` pseudo-signing, build `.ipa` | M11 (WP-12) | Survey-3, ORIGINAL_REQUEST §R5 |
| 20 | E2E Testing Suite Verification | Pass 100% of Tiers 1-4 opaque-box E2E tests, followed by Tier 5 adversarial coverage hardening | M12 (WP-11) | Survey-1/2/3, Plan §Dual-Track |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | KMP Gradle Targets & Vendored InnerTubeX | Add iOS targets to `:shared` and `:sharedUi`, vendor `:innertubex`, wire `ktor-client-darwin` and `coil-network-ktor3` | none | DONE |
| M2 | JVM API Decoupling & Source Relocation | Move 49 UI files to `sharedUi/commonMain` and 80+ files to `shared/commonMain`, implement `PlatformCrypto`, `PlatformClock`, `PlatformUrl` | M1 | DONE |
| M3 | C++17 DSP Analyzer iOS Bridge | C ABI bridge for `native/analyzer/`, Clang build script for `libbitchord_analysis.a`, `cinterop` definition | none | DONE |
| M4 | Native YouTube Stream Resolution & JS Cipher | Innertube waterfall, AAC/M4A prioritization, `JSContext` cipher deobfuscation, headless `WKWebView` BotGuard `poToken` | M1, M2 | DONE |
| M5 | Authentication & iOS Keychain Storage | `WKWebView` Google & Spotify OAuth, `SAPISIDHASH` generator, iOS Keychain service wrapper | M1, M2 | DONE |
| M6 | KMP SQLite & Sandbox File Storage | `androidx.sqlite:sqlite-bundled` engine, iOS sandbox directories, cache manager | M1, M2 | DONE |
| M7 | ONNX Neural Tracker & Hardware PCM Decoder | `onnxruntime-c:1.20.0` bridge, `ExtAudioFileRead` decoder, beat & vocal activity estimation | M1, M3 | DONE |
| M8 | Dual-Deck `AVAudioEngine` & Background Playback | Dual `AVAudioPlayerNode`, equal-power crossfade, `bitchord-chunked://` stream loader, `AVAudioSession`, `MPNowPlayingInfoCenter` | M4, M6 | DONE |
| M9 | Multi-Source Lyrics & Services Integration | Lyrics engine (12+ sources, word/syllable sync), Discord RPC, Spotify import, Last.fm | M2, M6 | DONE |
| M10 | Compose Multiplatform UI & iPadOS Adaptive Layout | `ComposeUIViewController`, Stage Manager / Split View breakpoints, Skia shaders, 120Hz ProMotion, 9:16 story export | M1, M2, M8 | DONE |
| M11 | Automated `.ipa` Packaging & Release Pipeline | `scripts/build_ipados_ipa.sh`, Xcode project, entitlements, `ldid` signing, generate `.ipa` | M1–M10 | DONE |
| M12 | E2E Integration & Final Test Verification | 100% pass of Tiers 1–4 E2E tests + Tier 5 adversarial hardening | M1–M11, TEST_READY | DONE |

---

## Interface Contracts

### 1. `innertubex` ↔ `:shared`
- `InnerTubeXResolver`:
  - `suspend fun resolveStream(videoId: String, clientPriority: List<PlayerClient>, requireM4a: Boolean): ResolvedStreamResult`
  - `ResolvedStreamResult`: `(streamUrl: String, itag: Int, mimeType: String, bitrate: Int, contentLength: Long, poTokenUsed: Boolean)`

### 2. Native JS Cipher ↔ `:shared`
- `JsEvaluator`:
  - `fun evaluateScript(script: String): String`
  - `fun decipherSignature(s: String, script: String): String`
  - `fun transformNParam(n: String, script: String): String`
  - iOS implementation uses `platform.JavaScriptCore.JSContext` synchronously.

### 3. C++17 DSP Bridge ↔ `:shared`
- `bitchord_ios_bridge.h`:
  - `const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)`
  - `void bitchord_free_string(const char* str)`
  - `void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)`
  - `void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)`
  - Kotlin/Native binds via `cinterop bitchord_analysis.def`. JSON string parsed by `TrackFeatures.kt`.

### 4. Audio Engine ↔ Application State
- `AudioEngineController`:
  - `fun loadTrack(deck: DeckId, streamUrl: String, initialPositionMs: Long)`
  - `fun play(deck: DeckId)`
  - `fun pause(deck: DeckId)`
  - `fun seek(deck: DeckId, positionMs: Long)`
  - `fun startCrossfade(durationMs: Long, transitionPlan: SmartTransitionPlan)`
  - `fun setEqualizer(gains: FloatArray)` (10 bands)
  - `fun setTempo(deck: DeckId, rate: Float)`
  - `val visualizerFftFlow: StateFlow<FloatArray>` (1024-bin FFT)

---

## Code Layout
- `shared/src/commonMain/kotlin/com/music/bitchord/`: Multiplatform domain logic, repositories, models, audio math.
- `shared/src/iosMain/kotlin/com/music/bitchord/`: iOS platform actuals (`PlatformCrypto.kt`, `PlatformClock.kt`, `PlatformUrl.kt`, `IOSKeychainAuthStore.kt`, `IOSCipher.kt`, `IOSAudioEngine.kt`).
- `sharedUi/src/commonMain/kotlin/com/music/bitchord/ui/`: 100% Compose Multiplatform UI components, screens, dialogs, player layouts.
- `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/`: iOS UI actuals (`ComposeUIViewController.kt`, `PlatformHaptics.kt`, `ShareSheet.kt`).
- `innertubex/`: In-tree KMP InnerTube module with `commonMain`, `jvmMain`, `androidMain`, `iosArm64`, `iosSimulatorArm64`.
- `native/analyzer/`: C++17 audio analysis engine source code and `bitchord_ios_bridge.cpp`.
- `iosApp/`: Xcode project (`iosApp.xcodeproj`), `Info.plist`, entitlements, bridging header, launch screen.
- `scripts/`: Packaging and build scripts (`build_ipados_ipa.sh`, `compile_dsp_ios.sh`).
- `tests/e2e/`: Requirement-driven opaque-box E2E test suites (Tiers 1-4).
