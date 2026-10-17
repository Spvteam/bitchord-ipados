# M2 Shared Logic Relocation Analysis: `shared/src/jvmSharedMain` to `shared/src/commonMain`

## 1. Executive Summary

This report delivers the comprehensive architecture analysis and migration plan for relocating shared business logic in `:shared` from `shared/src/jvmSharedMain/kotlin/` to `shared/src/commonMain/kotlin/` and establishing platform abstractions for Apple iPadOS / iOS (`iosArm64`, `iosSimulatorArm64`), fulfilling Milestone **M2 (WP-02)**.

### Key Metrics
- **Total files audited in `shared/src/jvmSharedMain`**: **90 files** (100% cataloged and analyzed).
- **Direct relocations to `commonMain`**: **75 files** (domain models, 28 lyrics sources/parsers, queue builders, 19 Discord Kizzy RPC files, canvas queries, settings, and PoToken primitives).
- **Files requiring platform abstractions (`expect`/`actual` or interface bridges)**: **12 files** (`YtMusicRepository`, `BeatTracker`, `TransitionFilter`, `AnalysisStore`, `MelSpectrogram`, `VocalTracker`, `TrackFeatures`, `NativeAnalysisLibrary`, `TtmlLyrics`, `Genius`, `LyricsTranslation`, `LocaleTags`).
- **Files retained exclusively in `jvmSharedMain` for JVM/Android backward compatibility**: **3 files** (`StreamResolver.kt` due to `NewPipeExtractor`, `Http.kt` due to `OkHttpClient`, and NewPipe client binding in `PlayerClient.kt`).

---

## 2. Complete Inventory & Classification Table (All 90 Files)

| # | File Path (relative to `jvmSharedMain/kotlin/`) | Category | JVM / Platform Dependencies | Target Disposition | Required Migration Action |
|---|---|---|---|---|---|
| 1 | `com/music/bitchord/auth/DataSyncId.kt` | Domain Model / Auth | None | `commonMain` | Direct move as-is. |
| 2 | `com/music/bitchord/data/DebugLog.kt` | Logging / Diagnostics | None | `commonMain` | Direct move as-is. |
| 3 | `com/music/bitchord/data/Http.kt` | Network Plumbing | `okhttp3.*`, `okio.*`, `ConcurrentHashMap` | **Retain in `jvmSharedMain`** | Keep for JVM/Android `OkHttpClient` & ExoPlayer interceptors. |
| 4 | `com/music/bitchord/data/LikeState.kt` | State Management | `kotlinx.coroutines.flow.*` | `commonMain` | Direct move as-is. |
| 5 | `com/music/bitchord/data/LocaleTags.kt` | Locale Normalization | `java.util.Locale` | `commonMain` + Platform | Replace `Locale.getDefault().toLanguageTag()` with `PlatformClock`/`PlatformString.systemLanguageTag()`. |
| 6 | `com/music/bitchord/data/MonotonicClock.kt` | Time / Telemetry | `System.nanoTime()` | `commonMain` | Bridge via `PlatformClock.nanoTime()` or `kotlin.time.TimeSource.Monotonic`. |
| 7 | `com/music/bitchord/data/NerdStats.kt` | Diagnostics / Audio Telemetry | `java.math.BigDecimal`, `ConcurrentHashMap` | `commonMain` | Replace `BigDecimal` with pure Kotlin string formatting; use `Mutex` for caches. |
| 8 | `com/music/bitchord/data/TrackLog.kt` | Diagnostics / Log Buffer | `SimpleDateFormat`, `Date`, `ThreadLocal`, `ConcurrentHashMap` | `commonMain` | Format timestamp with pure Kotlin helper; use coroutine context for thread local. |
| 9 | `com/music/bitchord/data/YtMusicRepository.kt` | Core Repository | `ConcurrentHashMap`, `Innertube` | `commonMain` + Platform | Use thread-safe map/Mutex; wire to multiplatform `Innertube`. |
| 10 | `com/music/bitchord/data/canvas/CanvasArtwork.kt` | Media Models | `java.text.Normalizer`, `Locale` | `commonMain` | Bridge NFD normalization via `PlatformNormalizer.normalizeNfd()`. |
| 11 | `com/music/bitchord/data/canvas/SpotifyCanvasQuery.kt` | Canvas GraphQL Client | `System.currentTimeMillis()`, `@Synchronized` | `commonMain` | Replace `System.currentTimeMillis()` with `PlatformClock.currentTimeMillis()`; Mutex for locks. |
| 12 | `com/music/bitchord/data/innertube/Innertube.kt` | API Client | `OkHttp` engine, `MessageDigest` (SHA-1), `Base64`, `Locale` | `commonMain` | Decouple Ktor engine to common/injected; swap `MessageDigest` for `PlatformCrypto.sha1Hex`. |
| 13 | `com/music/bitchord/data/innertube/InnertubeParser.kt` | API Response Parser | Unused `import java.util.Locale` | `commonMain` | Remove unused import; direct move to `commonMain`. |
| 14 | `com/music/bitchord/data/innertube/InnerTubeXResolver.kt` | Stream Resolution | `java.io.File`, `ConcurrentHashMap`, `OkHttp` | `commonMain` | Replace `File` with `String` paths / sandbox directory; use Ktor client. |
| 15 | `com/music/bitchord/data/innertube/PlayerClient.kt` | Client Definitions | `NewPipe`, `HttpUrl`, `Locale` | Split / Decouple | Core clients move to `commonMain`; `NEWPIPE` fallback stays in `jvmSharedMain`. |
| 16 | `com/music/bitchord/data/innertube/StreamResolver.kt` | Stream Resolver | `org.schabi.newpipe.extractor.*`, `OkHttp` | **Retain in `jvmSharedMain`** | Keep in `jvmSharedMain` as JVM/Android NewPipe fallback. |
| 17 | `com/music/bitchord/data/innertube/potoken/JavaScriptUtil.kt` | BotGuard Challenge Parser | `okio.ByteString` base64 | `commonMain` | Replace Okio with Kotlin stdlib `kotlin.io.encoding.Base64`. |
| 18 | `com/music/bitchord/data/innertube/potoken/PoTokenException.kt` | Domain Exceptions | None | `commonMain` | Direct move as-is. |
| 19 | `com/music/bitchord/data/innertube/potoken/PoTokenGenerator.kt` | BotGuard Manager | None (already abstract `PoTokenMinter`) | `commonMain` | Direct move as-is (`WKWebView` minter connects on iOS). |
| 20 | `com/music/bitchord/data/innertube/potoken/PoTokenPage.kt` | BotGuard HTML Bundle | None (HTML string) | `commonMain` | Direct move as-is. |
| 21 | `com/music/bitchord/data/innertube/potoken/PoTokenResult.kt` | Domain Model | None | `commonMain` | Direct move as-is. |
| 22 | `com/music/bitchord/data/lyrics/BackgroundVocals.kt` | Lyrics Processing | None | `commonMain` | Direct move as-is. |
| 23 | `com/music/bitchord/data/lyrics/BetterLyrics.kt` | Lyrics Provider | Calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 24 | `com/music/bitchord/data/lyrics/BiniLyrics.kt` | Lyrics Provider | Calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 25 | `com/music/bitchord/data/lyrics/EnhancedLrc.kt` | Lyrics Parser | None | `commonMain` | Direct move as-is. |
| 26 | `com/music/bitchord/data/lyrics/Genius.kt` | Web Scraper | `org.jsoup.*`, `URLEncoder`, `OkHttp` | `commonMain` + Platform | Abstract HTML parsing via `expect`/`actual` or pure-Kotlin regex extraction. |
| 27 | `com/music/bitchord/data/lyrics/KuGou.kt` | Lyrics Provider | `java.util.Base64` | `commonMain` | Use `kotlin.io.encoding.Base64`. |
| 28 | `com/music/bitchord/data/lyrics/LrcLib.kt` | Lyrics Provider | `Http.client`, `toHttpUrl` | `commonMain` | Migrate HTTP call to `LyricsHttp.lyricsGet` and Ktor URL building. |
| 29 | `com/music/bitchord/data/lyrics/LrcRed.kt` | Lyrics Provider | `java.text.Normalizer`, `Locale` | `commonMain` | Use `PlatformNormalizer.normalizeNfd()`. |
| 30 | `com/music/bitchord/data/lyrics/LrcWriter.kt` | Lyrics Formatter | None | `commonMain` | Direct move as-is. |
| 31 | `com/music/bitchord/data/lyrics/LyricAlignments.kt` | Lyrics Alignment | None | `commonMain` | Direct move as-is. |
| 32 | `com/music/bitchord/data/lyrics/LyricGaps.kt` | Lyrics Processing | None | `commonMain` | Direct move as-is. |
| 33 | `com/music/bitchord/data/lyrics/LyricLine.kt` | Domain Model | None | `commonMain` | Direct move as-is. |
| 34 | `com/music/bitchord/data/lyrics/LyricSections.kt` | Lyrics Processing | None | `commonMain` | Direct move as-is. |
| 35 | `com/music/bitchord/data/lyrics/LyricsHttp.kt` | Lyrics Network Transport | `Http.client` (OkHttp) | `commonMain` | Reimplement `lyricsGet` / `lyricsGetBearer` using standard multiplatform Ktor Client. |
| 36 | `com/music/bitchord/data/lyrics/LyricsPlus.kt` | Lyrics Provider | `AtomicReference` | `commonMain` | Replace `AtomicReference` with `kotlinx.atomicfu` or Mutex state. |
| 37 | `com/music/bitchord/data/lyrics/LyricsQuery.kt` | Lyrics Query Model | None | `commonMain` | Direct move as-is. |
| 38 | `com/music/bitchord/data/lyrics/LyricsRepository.kt` | Lyrics Coordinator | `ConcurrentHashMap`, `Collections.synchronizedMap` | `commonMain` | Replace synchronized maps with `Mutex`-protected maps. |
| 39 | `com/music/bitchord/data/lyrics/LyricsSource.kt` | Provider Enum / Config | None | `commonMain` | Direct move as-is. |
| 40 | `com/music/bitchord/data/lyrics/LyricsTranslation.kt` | Translation & Cache | `File`, `GZIPInputStream`, `MessageDigest`, `OkHttp` | `commonMain` + Platform | Abstract disk GZIP cache via interface/expect; convert HTTP to Ktor. |
| 41 | `com/music/bitchord/data/lyrics/Megalobiz.kt` | Lyrics Provider | Calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 42 | `com/music/bitchord/data/lyrics/Musixmatch.kt` | Lyrics Provider | `javax.crypto.Mac` (HMAC), `SimpleDateFormat`, `Date`, `UUID`, `OkHttp` | `commonMain` | Swap `Mac` for `PlatformCrypto.hmacSha1`; pure timestamp formatting. |
| 43 | `com/music/bitchord/data/lyrics/PaxSenix.kt` | Lyrics Provider | `AtomicReference`, calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 44 | `com/music/bitchord/data/lyrics/ProviderLyrics.kt` | Domain Model | None | `commonMain` | Direct move as-is. |
| 45 | `com/music/bitchord/data/lyrics/SimpMusicLyrics.kt` | Lyrics Provider | Calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 46 | `com/music/bitchord/data/lyrics/SyllableRuns.kt` | Lyrics Processing | None | `commonMain` | Direct move as-is. |
| 47 | `com/music/bitchord/data/lyrics/TranslationLanguages.kt` | Language Catalog | `java.util.Locale` | `commonMain` | Clean `Locale.ROOT` to stdlib `lowercase()`. |
| 48 | `com/music/bitchord/data/lyrics/TtmlLyrics.kt` | TTML XML Parser | `javax.xml.parsers.DocumentBuilderFactory`, `w3c.dom` | `commonMain` + Platform | Abstract TTML XML parsing via `expect`/`actual` or pure Kotlin TTML scanner. |
| 49 | `com/music/bitchord/data/lyrics/Unison.kt` | Lyrics Provider | Calls `lyricsGet` | `commonMain` | Direct move (HTTP abstracted in `LyricsHttp`). |
| 50 | `com/music/bitchord/data/lyrics/YouTubeLyrics.kt` | Lyrics Provider | None | `commonMain` | Direct move as-is. |
| 51 | `com/music/bitchord/data/model/ArtistCredits.kt` | Domain Model / Parsing | `Normalizer`, `Locale`, `ConcurrentHashMap` | `commonMain` | Use `PlatformNormalizer.normalizeNfd()`; thread-safe cache. |
| 52 | `com/music/bitchord/data/model/SearchHistoryEntity.kt` | Domain Model | `System.currentTimeMillis()` | `commonMain` | Set default timestamp to 0L or bridge via `PlatformClock`. |
| 53 | `com/music/bitchord/data/presence/Presence.kt` | Heartbeat Service | `Http.client` (OkHttp) | `commonMain` | Reimplement ping via Ktor client; add `Ios("ios")` to enum. |
| 54 | `com/music/bitchord/data/settings/LibraryEnums.kt` | Settings Enums | None | `commonMain` | Direct move as-is. |
| 55 | `com/music/bitchord/data/settings/PlayerEnums.kt` | Settings Enums | None | `commonMain` | Direct move as-is. |
| 56 | `com/music/bitchord/data/sources/SourceTrackKeys.kt` | Track Key Parsing | None | `commonMain` | Direct move as-is. |
| 57 | `com/music/bitchord/data/sources/StreamFormat.kt` | Stream Format Model | `Locale.ROOT` | `commonMain` | Clean `Locale.ROOT` formatting to pure Kotlin. |
| 58 | `com/music/bitchord/data/sources/TrackMatcher.kt` | Fuzzy Matching Engine | `Normalizer`, `Locale` | `commonMain` | Use `PlatformNormalizer.normalizeNfd()`. |
| 59 | `com/music/bitchord/data/spotify/SpotifyImporter.kt` | Spotify Importer | `Http.client`, `AtomicInteger` | `commonMain` | Convert HTTP to Ktor client; use `kotlinx.atomicfu` / AtomicInt. |
| 60 | `com/music/bitchord/data/spotify/SpotifyLibrary.kt` | Spotify Library Client | `Http.client` (OkHttp) | `commonMain` | Convert GraphQL query execution to Ktor client. |
| 61 | `com/music/bitchord/data/webdav/WebDavAuth.kt` | Auth State | None | `commonMain` | Direct move as-is. |
| 62 | `com/music/bitchord/playback/QueueBuilder.kt` | Harmonic Queue Ordering | `Locale.ROOT` | `commonMain` | Clean `Locale.ROOT` to stdlib `lowercase()`. |
| 63 | `com/music/bitchord/playback/QueueSections.kt` | AutoPlay Boundary Math | None | `commonMain` | Direct move as-is. |
| 64 | `com/music/bitchord/playback/QueueTimeline.kt` | Context Queue Projection | `java.util.UUID` | `commonMain` | Use `@OptIn(ExperimentalUuidApi::class) kotlin.uuid.Uuid`. |
| 65 | `com/music/bitchord/playback/TransitionFilter.kt` | Biquad Filter Mathematics | `Math.PI` | `commonMain` + Platform | Replace `Math.PI` with `kotlin.math.PI`; abstract audio engine automation. |
| 66 | `com/music/bitchord/playback/smart/AnalysisStore.kt` | Analysis Persistence | `File`, `java.nio.file.*`, `ConcurrentHashMap` | `commonMain` + Platform | Provide `expect class AnalysisStore(directoryOf: () -> String)` with sandbox path actual on iOS. |
| 67 | `com/music/bitchord/playback/smart/BeatTracker.kt` | Neural Beat Tracking | `ai.onnxruntime.*`, `File`, `FloatBuffer` | `commonMain` + Platform | Provide `expect class BeatTracker`; iOS actual binds CocoaPod `onnxruntime-c`. |
| 68 | `com/music/bitchord/playback/smart/MelSpectrogram.kt` | STFT Mel Front-End | JNI `@JvmStatic external fun nativeCompute` | `commonMain` + Platform | Provide `expect object MelSpectrogram`; iOS calls C bridge `libbitchord_analysis.a`. |
| 69 | `com/music/bitchord/playback/smart/NativeAnalysisLibrary.kt` | Library Loader | `System.loadLibrary` | `commonMain` + Platform | Provide `expect object NativeAnalysisLibrary`; no-op on iOS (statically linked). |
| 70 | `com/music/bitchord/playback/smart/TrackFeatures.kt` | Feature JSON Extraction | JNI `@JvmStatic external fun nativeAnalyze` | `commonMain` + Platform | Move JSON parsing to `commonMain`; bridge native analyze function. |
| 71 | `com/music/bitchord/playback/smart/VocalTracker.kt` | Neural Vocal Activity | JNI external + `ai.onnxruntime.*`, `ByteBuffer` | `commonMain` + Platform | Provide `expect object VocalSpectrogram` & `expect class VocalTracker`. |
| 72 | `com/my/kizzy/gateway/DiscordWebSocket.kt` | Discord Gateway Transport | `java.util.logging.Logger`, `System.nanoTime()` | `commonMain` | Replace `Logger` with `DebugLog`; replace `System.nanoTime()` with monotonic clock. |
| 73 | `com/my/kizzy/gateway/entities/HeartBeat.kt` | Discord Gateway Entity | None | `commonMain` | Direct move as-is. |
| 74 | `com/my/kizzy/gateway/entities/Identify.kt` | Discord Gateway Entity | None | `commonMain` | Direct move as-is. |
| 75 | `com/my/kizzy/gateway/entities/Payload.kt` | Discord Gateway Entity | None | `commonMain` | Direct move as-is. |
| 76 | `com/my/kizzy/gateway/entities/Ready.kt` | Discord Gateway Entity | None | `commonMain` | Direct move as-is. |
| 77 | `com/my/kizzy/gateway/entities/Resume.kt` | Discord Gateway Entity | None | `commonMain` | Direct move as-is. |
| 78 | `com/my/kizzy/gateway/entities/op/OpCode.kt` | Discord Gateway OpCodes | None | `commonMain` | Direct move as-is. |
| 79 | `com/my/kizzy/gateway/entities/op/OpCodesSerializer.kt` | Discord Serialization | None | `commonMain` | Direct move as-is. |
| 80 | `com/my/kizzy/gateway/entities/presence/Activity.kt` | Discord Presence Entity | None | `commonMain` | Direct move as-is. |
| 81 | `com/my/kizzy/gateway/entities/presence/Assets.kt` | Discord Presence Entity | None | `commonMain` | Direct move as-is. |
| 82 | `com/my/kizzy/gateway/entities/presence/Metadata.kt` | Discord Presence Entity | None | `commonMain` | Direct move as-is. |
| 83 | `com/my/kizzy/gateway/entities/presence/Presence.kt` | Discord Presence Entity | None | `commonMain` | Direct move as-is. |
| 84 | `com/my/kizzy/gateway/entities/presence/Timestamps.kt` | Discord Presence Entity | None | `commonMain` | Direct move as-is. |
| 85 | `com/my/kizzy/rpc/ArtworkCache.kt` | Discord Cache | `ConcurrentHashMap` | `commonMain` | Replace with Mutex-guarded map. |
| 86 | `com/my/kizzy/rpc/ExternalAssets.kt` | Discord Assets API | None | `commonMain` | Direct move as-is. |
| 87 | `com/my/kizzy/rpc/KizzyRPC.kt` | Discord RPC Client | None | `commonMain` | Direct move as-is. |
| 88 | `com/my/kizzy/rpc/RpcImage.kt` | Discord RPC Image Model | None | `commonMain` | Direct move as-is. |
| 89 | `com/my/kizzy/rpc/UserInfo.kt` | Discord User Model | None | `commonMain` | Direct move as-is. |
| 90 | `com/my/kizzy/utils/Ext.kt` | JSON / Extension Helpers | None | `commonMain` | Direct move as-is. |

---

## 3. Direct Relocations to `commonMain`

Of the 90 files, **75 files** are either already pure Kotlin Multiplatform code or require only minor substitutions using Kotlin stdlib 2.3+ features:

### 3.1 Domain Models, Enums & Settings (15 files)
- `com/music/bitchord/auth/DataSyncId.kt`
- `com/music/bitchord/data/DebugLog.kt`
- `com/music/bitchord/data/LikeState.kt`
- `com/music/bitchord/data/model/SearchHistoryEntity.kt` (pass timestamp or default to 0L)
- `com/music/bitchord/data/settings/LibraryEnums.kt`
- `com/music/bitchord/data/settings/PlayerEnums.kt`
- `com/music/bitchord/data/sources/SourceTrackKeys.kt`
- `com/music/bitchord/data/sources/StreamFormat.kt` (clean `Locale.ROOT` formatting)
- `com/music/bitchord/data/webdav/WebDavAuth.kt`
- `com/music/bitchord/playback/QueueSections.kt`
- `com/music/bitchord/data/innertube/potoken/PoTokenResult.kt`
- `com/music/bitchord/data/innertube/potoken/PoTokenException.kt`
- `com/music/bitchord/data/innertube/potoken/PoTokenPage.kt`
- `com/music/bitchord/data/innertube/potoken/PoTokenGenerator.kt` (already takes pure callback lambdas)
- `com/music/bitchord/data/canvas/SpotifyCanvasQuery.kt` (pure GraphQL string generator & parser)

### 3.2 Queue Builders (3 files)
- `com/music/bitchord/playback/QueueBuilder.kt`:
  - Replace `raw.lowercase(Locale.ROOT)` with standard KMP `raw.lowercase()`.
- `com/music/bitchord/playback/QueueTimeline.kt`:
  - Replace `java.util.UUID.randomUUID().toString()` with `@OptIn(ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()`.
- `com/music/bitchord/playback/QueueSections.kt`:
  - Zero dependencies, 100% pure Kotlin math.

### 3.3 Lyrics Engine & Providers (28 files)
- **Data Models & Parsing Math**: `LyricLine.kt`, `LyricSections.kt`, `LyricAlignments.kt`, `LyricGaps.kt`, `SyllableRuns.kt`, `BackgroundVocals.kt`, `LrcWriter.kt`, `LyricsQuery.kt`, `ProviderLyrics.kt`, `TranslationLanguages.kt`, `LyricsSource.kt`.
- **Parser & Transport**: `EnhancedLrc.kt`, `KuGou.kt` (use `kotlin.io.encoding.Base64`), `LyricsHttp.kt` (migrate to multiplatform Ktor client), `LyricsRepository.kt` (replace `ConcurrentHashMap` with Mutex/Map).
- **Network Providers**: `LrcRed.kt`, `BiniLyrics.kt`, `BetterLyrics.kt`, `PaxSenix.kt`, `LyricsPlus.kt`, `SimpMusicLyrics.kt`, `Unison.kt`, `Megalobiz.kt`, `YouTubeLyrics.kt`, `LrcLib.kt` (swap internal OkHttp `get` for `LyricsHttp.lyricsGet`).

### 3.4 Discord Kizzy RPC (19 files)
All 19 files under `com/my/kizzy/` (`gateway/`, `gateway/entities/`, `gateway/entities/op/`, `gateway/entities/presence/`, `rpc/`, `utils/`) are 100% Kotlinx Serialization and Ktor WebSockets.
- Replace `java.util.logging.Logger` in `DiscordWebSocket.kt` with `DebugLog`.
- Replace `System.nanoTime()` with `PlatformClock.nanoTime()`.
- Replace `ConcurrentHashMap` in `ArtworkCache.kt` with a Mutex-protected map.

---

## 4. Deep-Dive: Platform Abstractions Required (12 Files)

### 4.1 `YtMusicRepository.kt` & `Innertube.kt`
- **Current Issue**: `YtMusicRepository.kt` delegates all browse, search, and catalog calls to `Innertube.kt`. `Innertube.kt` currently initializes `HttpClient(OkHttp)` directly (line 464) and uses `java.security.MessageDigest.getInstance("SHA-1")` (line 1253) for the Google `SAPISIDHASH` authorization header.
- **Abstraction Design**:
  1. Relocate `YtMusicRepository.kt` to `commonMain`.
  2. In `Innertube.kt`, replace `HttpClient(OkHttp)` with an engine-agnostic `HttpClient` or inject a platform engine (`Darwin` on iOS via `ktor-client-darwin`, `OkHttp` on JVM).
  3. Replace the inline `MessageDigest` SHA-1 generation with `PlatformCrypto.sha1Hex(bytes: ByteArray): String`.
  4. Replace `ConcurrentHashMap` caches (`moodGenreShelfCache`, `audioVersionCache`, `videoVersionCache`) with a lightweight thread-safe cache wrapper or `Mutex`.

### 4.2 `BeatTracker.kt` & `VocalTracker.kt`
- **Current Issue**: Both files load ONNX neural networks (`beat_this_int8.onnx` and `vocals_umxhq_int8.onnx`), creating `OrtSession` and `OnnxTensor` using Java ONNX Runtime (`ai.onnxruntime.*`), `FloatBuffer`, and `ByteBuffer`.
- **Abstraction Design**:
  1. Declare `expect class BeatTracker(modelPath: () -> String, inferenceThreads: () -> Int)` in `commonMain`.
  2. Keep current implementation in `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/BeatTracker.kt` as `actual class BeatTracker`.
  3. In `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/BeatTracker.ios.kt`, implement `actual class BeatTracker` bridging to `onnxruntime-c:1.20.0` CocoaPod via Kotlin/Native `cinterop onnxruntime.def` (`OrtCreateSession`, `OrtRun`).
  4. Keep peak-picking, tempo calculation (`tempoFromBeats`), downbeat alignment, and confidence scoring in `commonMain` so DSP math remains 100% shared.
  5. Apply the identical pattern to `VocalTracker` and `VocalSpectrogram`.

### 4.3 `TransitionFilter.kt`
- **Current Issue**: Calculates 4th-order Butterworth low-pass and high-pass coefficient matrices and performs sample-by-sample filtering (`filter(channel: Int, input: Float)`). Contains `Math.PI` calls.
- **Abstraction Design**:
  1. The filter math itself is pure DSP. Replacing `Math.PI` with `kotlin.math.PI` enables the algorithm class `TransitionFilter` to reside directly in `commonMain`.
  2. On the playback engine integration boundary, Android/Desktop drives `TransitionFilter` via software `AudioProcessor` buffers. On iPadOS, `AVAudioEngine` uses hardware-accelerated `AVAudioUnitEQ` parametric nodes.
  3. Define a bridge interface `AudioFilterController` in `commonMain` allowing the player orchestrator to send `setCutoffs(lowPassHz, highPassHz)` and `open()` directly to either `TransitionFilter` (software DSP) or `AVAudioUnitEQ` (Apple audio engine).

### 4.4 `AnalysisStore.kt`
- **Current Issue**: Reads and writes cached `TrackAnalysis` JSON files to disk using `java.io.File`, `java.nio.file.Files.move`, and `AtomicMoveNotSupportedException`.
- **Abstraction Design**:
  1. Declare `expect class AnalysisStore(directoryOf: () -> String)` in `commonMain`.
  2. Keep JVM implementation in `jvmSharedMain` (using `java.io.File` and `Files.move`).
  3. In `iosMain`, implement `actual class AnalysisStore` using `platform.Foundation.NSFileManager`, `NSApplicationSupportDirectory` / `NSCachesDirectory`, and atomic writing (`NSData.writeToFile(atomically = true)`).
  4. Alternatively, interface `AnalysisStore` so Milestone M6 can back it with `androidx.sqlite:sqlite-bundled:2.5.0`.

### 4.5 Native C++17 DSP Analyzer Bridges (`MelSpectrogram`, `TrackFeatures`, `NativeAnalysisLibrary`)
- **Current Issue**: Upstream uses JNI (`@JvmStatic private external fun nativeCompute(...)` and `System.loadLibrary("bitchord_analysis")`).
- **Abstraction Design**:
  1. In `commonMain`, declare `expect object MelSpectrogram` and `expect object TrackFeatures`.
  2. Keep JNI calls in `jvmSharedMain`.
  3. In `iosMain`, implement `MelSpectrogram.ios.kt` and `TrackFeatures.ios.kt` calling the C functions declared in `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h` (`bitchord_analyze_audio_json`, `bitchord_compute_mel_spectrogram`) via `cinterop bitchord_analysis.def`.
  4. Make `NativeAnalysisLibrary.available` return `true` on iOS since `libbitchord_analysis.a` is statically compiled into the binary.

### 4.6 Specialized Parsers: `TtmlLyrics.kt`, `Genius.kt`, `LyricsTranslation.kt`
- **`TtmlLyrics.kt`**: Uses `javax.xml.parsers.DocumentBuilderFactory`. Either:
  - Provide an `expect fun parseTtml(xml: String): List<LyricLine>` with `DocumentBuilderFactory` in `jvmSharedMain` and `NSXMLParser` in `iosMain`.
  - Or implement a pure-Kotlin lightweight TTML tokenizer in `commonMain` matching `<p>` and `<span>` tags (since Apple Music TTML follows a strict, predictable XML schema).
- **`Genius.kt`**: Uses `org.jsoup.Jsoup` to select `div[data-lyrics-container=true]`. Provide an `expect fun extractGeniusLyrics(html: String): String?` (Jsoup on JVM, pure regex tag-stripper or WebKit on iOS).
- **`LyricsTranslation.kt`**: Uses `GZIPInputStream`/`GZIPOutputStream` and `java.io.File`. Decouple disk caching to an `expect fun loadCachedTranslation` / `saveCachedTranslation` or utilize KMP file persistence.

---

## 5. Files Remaining in `jvmSharedMain` for JVM/Android Compatibility

To ensure existing Android (`:app`) and Desktop (`:desktopApp`) targets continue to compile and function without disruption, **3 files** must remain in `shared/src/jvmSharedMain/kotlin/`:

1. **`com/music/bitchord/data/innertube/StreamResolver.kt`**:
   - Tightly couples to `org.schabi.newpipe.extractor.*` (`NewPipe`, `ServiceList`, `Downloader`, `YoutubeJavaScriptPlayerManager`).
   - NewPipeExtractor is a JVM/Android-only Java library.
   - On iOS, stream resolution is handled natively via `InnerTubeXResolver` + `PlaybackStreamResolver` (`JavaScriptCore` cipher + `WKWebView` PoToken).
2. **`com/music/bitchord/data/Http.kt`**:
   - Configures the central `OkHttpClient` instance, connection pool, dispatcher, and WebDAV interceptors.
   - ExoPlayer (`OkHttpDataSource`) and desktop stream readers rely directly on `Http.client`.
3. **`com/music/bitchord/data/innertube/PlayerClient.kt` (`NEWPIPE` segment)**:
   - Contains `YoutubeParsingHelper.getVisionOsUserAgent(NewPipe.getPreferredLocalization())`.
   - The platform-agnostic client definitions (`IOS`, `ANDROID_VR`, `WEB_REMIX`, `TVHTML5`) can be shared, while the NewPipe property remains in `jvmSharedMain`.

---

## 6. Gradle Build Graph & Source Set Configuration Requirements

To support the relocated files in `shared/src/commonMain/`, `shared/build.gradle.kts` must declare the following dependencies in `commonMain`:

```kotlin
sourceSets {
    commonMain.dependencies {
        // In-tree vendored InnerTubeX with iOS targets
        api(project(":innertubex"))
        
        // Multiplatform Coroutines & Serialization
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
        
        // Multiplatform Ktor Client Core & Features
        api("io.ktor:ktor-client-core:3.5.2")
        api("io.ktor:ktor-client-websockets:3.5.2")
        api("io.ktor:ktor-client-content-negotiation:3.5.2")
        api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    }
    
    jvmSharedMain.dependencies {
        // Kept for JVM/Android specific stream resolution & interceptors
        api("io.ktor:ktor-client-okhttp:3.5.2")
        api("org.jsoup:jsoup:1.22.2")
        compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
        compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    }
    
    iosMain.dependencies {
        // Darwin Ktor engine for Apple Native URLSession
        implementation("io.ktor:ktor-client-darwin:3.5.2")
    }
}
```

---

## 7. Step-by-Step Relocation Execution Plan for Implementers

1. **Step 1: Move Pure Domain Models, Enums, and Kizzy RPC**:
   - Move `com/music/bitchord/auth/DataSyncId.kt`, `DebugLog.kt`, `LikeState.kt`, `LibraryEnums.kt`, `PlayerEnums.kt`, `SourceTrackKeys.kt`, `WebDavAuth.kt`, and `com/my/kizzy/**` to `commonMain`.
   - Update `DiscordWebSocket.kt` to use `DebugLog`.
2. **Step 2: Move Queue Builders**:
   - Move `QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt` to `commonMain`.
   - Replace `Locale.ROOT` with stdlib lowercase; swap `java.util.UUID` for `kotlin.uuid.Uuid`.
3. **Step 3: Move InnertubeParser & PoToken Primitives**:
   - Move `InnertubeParser.kt`, `PoTokenResult.kt`, `PoTokenException.kt`, `PoTokenPage.kt`, `PoTokenGenerator.kt`, `JavaScriptUtil.kt` to `commonMain`.
4. **Step 4: Abstract Platform Primitives (`PlatformCrypto`, `PlatformClock`, `PlatformNormalizer`)**:
   - Create `expect` declarations in `commonMain` and `actual` in `jvmSharedMain` and `iosMain`.
   - Move `CanvasArtwork.kt`, `ArtistCredits.kt`, `TrackMatcher.kt`, `LrcRed.kt` to `commonMain`.
5. **Step 5: Migrate Lyrics Engine**:
   - Refactor `LyricsHttp.kt` to use Ktor client in `commonMain`.
   - Move 25+ lyrics provider and model files to `commonMain`.
   - Provide `expect`/`actual` bridges for `Genius.kt` and `TtmlLyrics.kt`.
6. **Step 6: Abstract Audio DSP & Neural Inference**:
   - Set up `expect`/`actual` declarations for `AnalysisStore`, `BeatTracker`, `VocalTracker`, `MelSpectrogram`, and `TrackFeatures`.
   - Retain JNI and Java ONNX code in `jvmSharedMain`; implement iOS `cinterop` bindings in `iosMain`.
7. **Step 7: Verification**:
   - Compile `:shared:compileKotlinJvm` and `:shared:compileKotlinAndroid` to ensure zero regressions on JVM/Android.
   - Compile `:shared:compileKotlinIosArm64` and `:shared:compileKotlinIosSimulatorArm64` to verify 100% resolution on iOS.
