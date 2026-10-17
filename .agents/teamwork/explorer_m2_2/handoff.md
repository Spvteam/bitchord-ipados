# Handoff Report: M2 Shared Logic Relocation Analysis

## 1. Observation
1. **Source File Inventory**:
   - Running PowerShell command `(Get-ChildItem -Recurse -File "D:\Bitchord\shared\src\jvmSharedMain").Count` returned verbatim: `90`.
   - All 90 files reside under `shared/src/jvmSharedMain/kotlin/`.
   - `shared/src/commonMain/kotlin/` currently holds 16 files (mostly `data/listentogether/`, `data/model/Models.kt`, `playback/EqualizerCurve.kt`, `playback/smart/TransitionPlanner.kt`).
2. **Dependency & Import Scan**:
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/StreamResolver.kt`: Lines 21–38 import `org.schabi.newpipe.extractor.*` (`MediaFormat`, `NewPipe`, `ServiceList`, `Downloader`, `YoutubeJavaScriptPlayerManager`, etc.) and `okhttp3.*`. NewPipeExtractor is a JVM-only dependency (`compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")` in `shared/build.gradle.kts:66`).
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/Http.kt`: Configures `OkHttpClient` (`okhttp3.OkHttpClient`, `okhttp3.Dispatcher`, `okhttp3.ConnectionPool`) on line 133, shared by ExoPlayer `OkHttpDataSource` and JVM stream calls.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/PlayerClient.kt`: Lines 4–5 import `NewPipe` and `YoutubeParsingHelper`; line 143 references `YoutubeParsingHelper.getVisionOsUserAgent(...)`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/YtMusicRepository.kt`: Line 34 imports `java.util.Locale` (unused); lines 41, 46, 48 instantiate `ConcurrentHashMap` for caches (`moodGenreShelfCache`, `audioVersionCache`, `videoVersionCache`). Delegates API requests to `Innertube.kt`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/Innertube.kt`: Line 7 imports `io.ktor.client.engine.okhttp.OkHttp`; line 464 instantiates `HttpClient(OkHttp)`; lines 1253–1255 use `java.security.MessageDigest.getInstance("SHA-1")` for `SAPISIDHASH`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/BeatTracker.kt`: Lines 24–28 import `ai.onnxruntime.OnnxTensor`, `ai.onnxruntime.OrtEnvironment`, `ai.onnxruntime.OrtSession`, `java.io.File`, `java.nio.FloatBuffer`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/VocalTracker.kt`: Lines 24–30 import `ai.onnxruntime.*`, `java.io.File`, `java.nio.ByteBuffer`, `java.nio.FloatBuffer`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/MelSpectrogram.kt`: Lines 99–107 define `@JvmStatic private external fun nativeCompute(...)`, `@JvmStatic private external fun nativeResample(...)`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/TrackFeatures.kt`: Lines 175–187 define `@JvmStatic private external fun nativeAnalyze(...)`, `@JvmStatic private external fun nativeSampleRate(...)`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/NativeAnalysisLibrary.kt`: Line 16 calls `System.loadLibrary(LIBRARY_NAME)`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/AnalysisStore.kt`: Line 55 takes `directoryOf: () -> java.io.File`; lines 139–146 use `java.nio.file.Files.move` and `AtomicMoveNotSupportedException`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/TransitionFilter.kt`: Lines 109 & 120 use `Math.PI` (Java math constant); the rest of the file is pure mathematical floating-point operations.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/QueueBuilder.kt`: Lines 95 & 103 call `lowercase(Locale.ROOT)`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/QueueTimeline.kt`: Line 46 calls `UUID.randomUUID().toString()`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsHttp.kt`: Lines 44–83 provide `lyricsGet`, `lyricsGetBearer`, `lyricsGetAuthorized` via `Http.client` (OkHttp). 17 lyrics providers call `lyricsGet`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/Genius.kt`: Line 15 imports `org.jsoup.Jsoup` to select `div[data-lyrics-container=true]`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/TtmlLyrics.kt`: Lines 6–7 import `javax.xml.parsers.DocumentBuilderFactory` and `org.w3c.dom.*`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/Musixmatch.kt`: Lines 20–21 import `javax.crypto.Mac` and `SecretKeySpec`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsTranslation.kt`: Lines 4–11 import `java.io.File`, `GZIPInputStream`, `GZIPOutputStream`.
   - `shared/src/jvmSharedMain/kotlin/com/my/kizzy/**` (19 files): Only `DiscordWebSocket.kt` uses `java.util.logging.Logger` and `System.nanoTime()`; `ArtworkCache.kt` uses `ConcurrentHashMap`. The remaining 17 Kizzy files are 100% pure Kotlin data classes and serialization models.

## 2. Logic Chain
1. *From Observation 1 & 2*:
   - 75 of the 90 files in `shared/src/jvmSharedMain/` contain either no JVM dependencies or only easily replaceable stdlib constructs (`Locale.ROOT` -> `lowercase()`, `UUID` -> `kotlin.uuid.Uuid`, `ConcurrentHashMap` -> `Mutex`/`atomicfu`).
   - Specifically, domain models (`DataSyncId`, `LikeState`, `SearchHistoryEntity`, `PresencePlatform`, `SourceTrackKeys`, `StreamFormat`), queue builders (`QueueBuilder`, `QueueSections`, `QueueTimeline`), all 19 Discord Kizzy files, and 25+ lyrics provider/model files can be relocated directly to `commonMain`.
2. *From Observation on Neural Inference & C++ DSP*:
   - `BeatTracker.kt` and `VocalTracker.kt` directly reference Java `ai.onnxruntime.*`, which does not exist on Kotlin/Native iOS.
   - `MelSpectrogram.kt`, `TrackFeatures.kt`, and `VocalSpectrogram` use JNI `external fun`.
   - `AnalysisStore.kt` uses `java.io.File` and `java.nio.file.Files`.
   - *Therefore*: These files cannot be moved directly to `commonMain` as single files. They require an `expect`/`actual` platform abstraction where the shared API sits in `commonMain`, JVM actuals remain in `jvmSharedMain`, and iOS actuals in `iosMain` call `cinterop` C bindings (`bitchord_ios_bridge.h` and `onnxruntime-c`) and Cocoa Foundation filesystem APIs.
3. *From Observation on YtMusicRepository & Innertube*:
   - `YtMusicRepository.kt` is the central catalog API used by UI. Moving it to `commonMain` requires `Innertube.kt` to run on Ktor multiplatform rather than hardcoded `OkHttp`.
   - Google `SAPISIDHASH` requires SHA-1, which must be delegated to `PlatformCrypto.sha1Hex` (`CommonCrypto` `CC_SHA1` on iOS, `MessageDigest` on JVM).
4. *From Observation on StreamResolver & Http*:
   - `StreamResolver.kt` is fundamentally coupled to NewPipeExtractor (`org.schabi.newpipe.*`). NewPipe cannot be compiled to iOS native binaries.
   - `Http.kt` configures `OkHttpClient`, which is JVM-specific.
   - *Therefore*: Both `StreamResolver.kt` and `Http.kt` must remain in `jvmSharedMain` to preserve Android and Desktop builds. On iOS, stream extraction is handled via `InnerTubeXResolver` + `PlaybackStreamResolver` (porting `DesktopPlaybackResolver` with `JavaScriptCore` cipher & `WKWebView` PoToken).

## 3. Caveats
1. `NewPipeExtractor` is only compile-time linked (`compileOnly`) in `:shared`; Android ships a trimmed jar at runtime. iOS does not use NewPipe at all.
2. `Genius.kt` and `TtmlLyrics.kt`: While both can be abstracted via `expect`/`actual` parsers, TTML parsing can alternatively be implemented as a pure-Kotlin tokenizer in `commonMain` since Apple Music TTML format is strictly `<p ...><span ...>...</span></p>`.
3. In `shared/build.gradle.kts`, `api(project(":innertubex"))` must be moved from `jvmSharedMain.dependencies` to `commonMain.dependencies` so that `YtMusicRepository` and `InnerTubeXResolver` can resolve `innertubex` models in `commonMain`.

## 4. Conclusion
The 90 files in `shared/src/jvmSharedMain/` are classified as follows:
- **75 files** relocate directly to `shared/src/commonMain/kotlin/` (domain models, queue builders, 28 lyrics sources/models, 19 Discord Kizzy RPC files, PoToken data models, and canvas query models).
- **12 files** require platform abstractions (`YtMusicRepository`, `Innertube`, `BeatTracker`, `VocalTracker`, `TransitionFilter`, `AnalysisStore`, `MelSpectrogram`, `TrackFeatures`, `NativeAnalysisLibrary`, `TtmlLyrics`, `Genius`, `LyricsTranslation`).
- **3 files** remain in `shared/src/jvmSharedMain/kotlin/` for JVM/Android compatibility (`StreamResolver.kt`, `Http.kt`, and `NEWPIPE` binding in `PlayerClient.kt`).

Full details, migration actions, and the step-by-step implementation plan are documented in `D:\Bitchord\.agents\teamwork\explorer_m2_2\analysis.md`.

## 5. Verification Method
1. **File Inventory Verification**:
   Execute in PowerShell:
   ```powershell
   (Get-ChildItem -Recurse -File "D:\Bitchord\shared\src\jvmSharedMain").Count
   ```
   Must match 90 files.
2. **Analysis File Inspection**:
   Inspect `D:\Bitchord\.agents\teamwork\explorer_m2_2\analysis.md` to confirm every file #1 to #90 is accounted for in Section 2's master inventory table.
3. **Compilation Sanity Check (Post-Implementation)**:
   Once the implementer relocates files according to `analysis.md`:
   ```powershell
   ./gradlew :shared:compileKotlinJvm :shared:compileKotlinAndroid
   ./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
   ```
   Zero unresolved references to `java.*` in `commonMain`.
