# BitChord iPadOS Port — Build System, Gradle Configuration, & Module Boundary Survey

**Explorer**: Survey Explorer 1 (Build & Module Survey Explorer)  
**Date**: 2026-10-16  
**Target Repository**: BitChord v1.8-beta1 (`kushagrasinghx/BitChord`)  
**Scope**: Root build, `:shared`, `:sharedUi`, `:desktopApp`, `:innertubex` vendoring, and WP-01 / WP-02 execution mapping.

---

## Executive Summary

1. **Current Targets**: Neither `:shared` nor `:sharedUi` configures any iOS targets. Both declare only `androidTarget` (JVM 17) and `jvm` (JVM 17), bridged by a custom intermediate source set `jvmSharedMain`.
2. **Source Code Location**:
   - In `:shared`: 90 Kotlin files sit in `shared/src/jvmSharedMain/`, while only 16 files are in `shared/src/commonMain/`.
   - In `:sharedUi`: 49 Kotlin files sit in `sharedUi/src/jvmSharedMain/`, while `sharedUi/src/commonMain/` contains 0 Kotlin files (only Compose resources).
   - In `:desktopApp`: 119 Kotlin files manage desktop orchestration, caching, and playback.
3. **JVM Dependencies**:
   - In `:shared`: OkHttp (`ktor-client-okhttp:3.5.2`), Jsoup (`org.jsoup:jsoup:1.22.2`), ONNX Runtime JVM (`onnxruntime:1.20.0`), NewPipe Extractor (`NewPipeExtractor:v0.26.3`), and JitPack InnerTubeX (`com.github.MetrolistGroup.innertubex:innertubex:v0.7.0`).
   - In `:sharedUi`: Coil OkHttp network engine (`coil-network-okhttp:3.0.4`), plus localized `java.util.Locale`, `TimeUnit`, and `System.nanoTime()`.
4. **Vendoring `:innertubex`**: Upstream coordinate is `com.github.MetrolistGroup.innertubex:innertubex:v0.7.0` (from `https://github.com/MetrolistGroup/innertubex`). Crucially, its source code already possesses Kotlin Multiplatform declarations including `iosArm64()`, `iosSimulatorArm64()`, and `nativeMain` (`Sha1.native.kt`, `YtEjsScriptLoader.native.kt`, `DefaultYouTubeLocale.native.kt`). Vendoring it in-tree as `:innertubex` resolves the missing iOS binary issue cleanly.
5. **SQLite & Persistence**:
   - `sqlite-jdbc:3.47.1.0` in `desktopApp` is currently used for `DesktopBrowserCookies.kt`.
   - `AnalysisStore.kt` in `:shared` writes individual JSON files per track.
   - `androidx.sqlite:sqlite-bundled:2.5.0` provides an in-tree native SQLite C-amalgamation driver (`BundledSQLiteDriver`) that compiles on iOS (`iosArm64`, `iosSimulatorArm64`) without external JDBC or JVM drivers, enabling unified persistence for WP-08.
6. **WP-01 & WP-02 Feasibility**:
   - All 49 files in `sharedUi/src/jvmSharedMain/` are pure Compose Multiplatform code with minimal JVM imports (`java.util.Locale`, `System.nanoTime()`). They can be moved directly to `sharedUi/src/commonMain/`.
   - In `:shared`, the vast majority of files (data models, lyrics sources, Spotify/WebDAV, queue builders, Discord RPC) can move directly to `commonMain` once `expect`/`actual` abstractions are created for crypto (`CC_SHA1` / `CC_MD5`), monotonic clock, and string normalization.

---

## 1. Current Target & Gradle Baseline

### 1.1 Root `build.gradle.kts` & `settings.gradle.kts`
- **Gradle Plugins**:
  - `org.jetbrains.kotlin.multiplatform` version `2.3.20`
  - `org.jetbrains.compose` version `1.10.3`
  - `com.android.application` / `com.android.library` version `8.10.1`
  - `org.jetbrains.kotlin.plugin.compose` version `2.3.20`
  - `org.jetbrains.kotlin.plugin.serialization` version `2.3.20`
  - R8 override: `com.android.tools:r8:8.13.23` in buildscript classpath
- **Settings**:
  - `include(":shared")`
  - `include(":sharedUi")`
  - `include(":desktopApp")`
  - Conditional `include(":app")` when Android SDK is detected via `local.properties` or environment variables.

### 1.2 `shared/build.gradle.kts` Audit
- **Targets Configured**:
  ```kotlin
  kotlin {
      androidTarget {
          compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
      }
      jvm {
          compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
      }
      applyDefaultHierarchyTemplate()
      ...
  }
  ```
  *Deficiency*: **Zero iOS targets**. `iosArm64()` and `iosSimulatorArm64()` are not declared.
- **Source Set Topology**:
  - `commonMain` (currently only declares `kotlinx-serialization-json:1.7.3`)
  - `jvmSharedMain` (custom source set created via `by creating { dependsOn(commonMain.get()) }`)
  - `androidMain` depends on `jvmSharedMain`
  - `jvmMain` depends on `jvmSharedMain`

### 1.3 `sharedUi/build.gradle.kts` Audit
- **Targets Configured**:
  - `androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`
  - `jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`
  *Deficiency*: **Zero iOS targets**.
- **Source Set Topology**:
  - `commonMain` (declares Compose runtime, foundation, animation, ui, material3:1.7.3, haze:1.3.1, coil-compose:3.0.4, but has 0 Kotlin files)
  - `jvmSharedMain` (contains all 49 shared UI Kotlin files)
  - `androidMain` (contains 5 actual implementation files)
  - `jvmMain` (contains 6 actual implementation files)

---

## 2. JVM-Specific Dependencies & Migration Strategy

### 2.1 Dependencies in `shared/build.gradle.kts`

| Upstream Dependency | Source Set | Status / Platform Issue | Target iOS Solution (WP-01) |
|---|---|---|---|
| `io.ktor:ktor-client-okhttp:3.5.2` | `jvmSharedMain` | JVM only (Square OkHttp) | Keep in `jvmSharedMain`; add `io.ktor:ktor-client-darwin:3.5.2` in `iosMain` |
| `io.ktor:ktor-client-core:3.5.2` | `jvmSharedMain` | Pure KMP | Promote to `commonMain.dependencies` |
| `io.ktor:ktor-client-websockets:3.5.2` | `jvmSharedMain` | Pure KMP | Promote to `commonMain.dependencies` |
| `io.ktor:ktor-client-content-negotiation:3.5.2` | `jvmSharedMain` | Pure KMP | Promote to `commonMain.dependencies` |
| `io.ktor:ktor-serialization-kotlinx-json:3.5.2` | `jvmSharedMain` | Pure KMP | Promote to `commonMain.dependencies` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | `jvmSharedMain` | Pure KMP | Promote to `commonMain.dependencies` |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0` | `jvmSharedMain` | Pure KMP (commonMain had 1.7.3) | Align on `1.11.0` in `commonMain.dependencies` |
| `com.github.MetrolistGroup.innertubex:innertubex:v0.7.0` | `jvmSharedMain` | JitPack JVM artifact | Vendor as local KMP module `:innertubex` with `iosArm64`/`iosSimulatorArm64` |
| `org.jsoup:jsoup:1.22.2` | `jvmSharedMain` | JVM HTML Parser (`Genius.kt`) | Abstract via `expect`/`actual` or replace with regex/Ktor parser |
| `com.microsoft.onnxruntime:onnxruntime:1.20.0` | `jvmSharedMain` (compileOnly) | JVM ONNX JNI artifact | Isolate in `jvmSharedMain`; iOS uses CocoaPods `onnxruntime-c:1.20.0` + `cinterop` |
| `com.github.TeamNewPipe:NewPipeExtractor:v0.26.3` | `jvmSharedMain` (compileOnly) | JVM only NewPipe fallback | Keep in `jvmSharedMain`; iOS uses native InnerTube extraction |
| `com.squareup.okhttp3:mockwebserver:5.3.2` | `jvmTest` | JVM test dependency | Stays in `jvmTest` |

### 2.2 Dependencies in `sharedUi/build.gradle.kts`

| Upstream Dependency | Source Set | Status / Platform Issue | Target iOS Solution (WP-01) |
|---|---|---|---|
| `io.coil-kt.coil3:coil-compose:3.0.4` | `commonMain` | Pure KMP | Stays in `commonMain` |
| `io.coil-kt.coil3:coil-network-okhttp:3.0.4` (used in apps) | App / Desktop | OkHttp engine JVM only | Use `io.coil-kt.coil3:coil-network-ktor3:3.0.4` in `iosMain` (or `commonMain`) |
| `dev.chrisbanes.haze:haze:1.3.1` | `commonMain` | Pure KMP (CMP Skiko Metal backend) | Stays in `commonMain` |
| `dev.chrisbanes.haze:haze-materials:1.3.1` | `commonMain` | Pure KMP | Stays in `commonMain` |
| `org.jetbrains.compose.*:1.10.3` | `commonMain` | Pure KMP | Full iOS support via Skiko / Metal |

---

## 3. Inventory of `jvmSharedMain` Source Sets

### 3.1 `:shared` (`shared/src/jvmSharedMain/kotlin/`) — 90 Files

A complete audit of all 90 files reveals the following categorization:

#### 1. Pure or Near-Pure Logic (Ready for direct move to `commonMain`):
- **Discord Gateway & RPC (`com.my.kizzy.*` — 19 files)**:
  - `gateway/DiscordWebSocket.kt`, `gateway/entities/*` (6 files), `rpc/*` (4 files), `utils/Ext.kt`.
  - *Analysis*: Built purely on Ktor WebSockets and Kotlinx Serialization. Zero JVM imports. Directly portable.
- **Playback Queue & Filters (4 files)**:
  - `QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt`, `TransitionFilter.kt`.
  - *Analysis*: Contains Camelot wheel mathematics, BPM proximity calculations, and DJ filter automation envelopes. Pure Kotlin.
- **Domain Models & Enums (4 files)**:
  - `ArtistCredits.kt`, `SearchHistoryEntity.kt`, `LibraryEnums.kt`, `PlayerEnums.kt`.
- **Canvas Artwork (2 files)**:
  - `CanvasArtwork.kt`, `SpotifyCanvasQuery.kt`.
- **Presence (1 file)**:
  - `Presence.kt`.
- **Smart Automix Data (1 file)**:
  - `TrackFeatures.kt` (JSON deserialization of analysis results).

#### 2. Logic with Localized `java.*` Primitives (Requires lightweight expect/actual or stdlib replacements):
- **Auth & Session**:
  - `DataSyncId.kt`: UUID generation -> replace with Kotlin stdlib `kotlin.uuid.Uuid.random().toString()`.
  - `Innertube.kt`: SAPISIDHASH generation uses `java.security.MessageDigest.getInstance("SHA-1")` and `java.util.Base64` -> replace with `PlatformCrypto.sha1Hex()` and `kotlin.io.encoding.Base64`.
- **Data & Logging**:
  - `MonotonicClock.kt`: Uses `System.nanoTime()` -> replace with `kotlin.time.TimeSource.Monotonic`.
  - `LocaleTags.kt`, `TrackLog.kt`, `DebugLog.kt`: `java.util.Locale` -> replace with Kotlin `lowercase()` / `uppercase()`.
- **Repository**:
  - `YtMusicRepository.kt` (1032 lines): Only uses `java.util.Locale` and `java.util.concurrent.ConcurrentHashMap`. Replace `ConcurrentHashMap` with thread-safe atomic map wrapper or mutex.
- **Spotify & WebDAV**:
  - `SpotifyImporter.kt`, `SpotifyLibrary.kt`, `WebDavAuth.kt`: Uses `java.util.Base64` -> replace with `kotlin.io.encoding.Base64`.
  - `TrackMatcher.kt`: Uses `java.text.Normalizer` -> abstract diacritic removal with `expect fun normalizeDiacritics(text: String): String`.
- **Lyrics Providers (29 files)**:
  - `KuGou.kt`: Uses `java.util.zip.InflaterInputStream` -> abstract via `expect fun decompressZlib(bytes: ByteArray): ByteArray` (implemented on iOS via `platform.zlib.uncompress`).
  - `Genius.kt`: Uses `org.jsoup.Jsoup` -> keep in `jvmSharedMain` or parse via regex/Ktor.
  - Other 27 lyrics files (`LrcLib.kt`, `YouTubeLyrics.kt`, `SimpMusicLyrics.kt`, etc.) are pure Ktor/Json or LRC parsers.

#### 3. Platform-Bound Leaf Nodes (Requires `expect`/`actual`):
- `BeatTracker.kt` & `VocalTracker.kt`: Depend on `ai.onnxruntime.*`. Define runner interfaces in `commonMain`; JVM uses ONNX Java JNI, iOS uses `onnxruntime-c` cinterop.
- `MelSpectrogram.kt`: Calls JNI `nativeCompute`. In `commonMain`, declare `expect object MelSpectrogram`; JVM actual keeps JNI, iOS actual calls C bridge `bitchord_compute_mel_spectrogram`.
- `AnalysisStore.kt`: File I/O for cached track features. Adapt to use iOS sandbox directories (`NSApplicationSupportDirectory`).
- `InnerTubeXResolver.kt` & `PoTokenGenerator.kt`: Stream extraction and BotGuard minter. Port `PoTokenGenerator` to use `WKWebView` on iOS.

---

### 3.2 `:sharedUi` (`sharedUi/src/jvmSharedMain/kotlin/`) — 49 Files

A complete audit of all 49 files confirms:
- **100% of these files are Compose Multiplatform composables and state holders**:
  - `ui/AppUi.kt`, `ForegroundState.kt`, `LyricsProviderState.kt`, `PlaybackPosition.kt`
  - `ui/components/`: `Common.kt`, `ContextClick.kt`, `LongPressOrigin.kt`, `OptimizedHaze.kt`, `SearchField.kt`, `ShelfRow.kt`, `Skeletons.kt`, `SongText.kt`
  - `ui/graphics/`: `ArtworkPixels.kt`, `ColorUtils.kt`
  - `ui/haptics/`: `Haptics.kt`
  - `ui/icons/`: `BitChordIcons.kt`
  - `ui/player/` (25 files): `NowPlayingScreen.kt` (3665 lines), `LandscapePlayer.kt`, `PlayerControls.kt`, `PlayerDock.kt`, `PlayerDrawer.kt`, `PlayerHost.kt`, `PlayerLyrics.kt`, `PlayerPlatformUi.kt`, `PlayerQueue.kt`, `PlayerSidePanels.kt`, `PlayerState.kt`, `MeshGradient.kt`, `ArtworkMeshBackdrop.kt`, `LyricClock.kt`, `LyricFocus.kt`, etc.
  - `ui/replay/`: `ReplayCard.kt`
  - `ui/screens/`: `ExploreScreen.kt`, `HomeScreen.kt` (1076 lines), `LibraryScreen.kt`, `SearchScreen.kt`
  - `ui/theme/`: `ArtworkPalette.kt`, `StatusBarIcons.kt`
  - `ui/utils/`: `SheetGestureGuard.kt`
- **JVM Dependencies Found in `:sharedUi`**:
  - `java.util.Locale` in 7 files (`SearchScreen.kt`, `PlayerControls.kt`, `PlayerLyrics.kt`, `ReplayCard.kt`, `HomeScreen.kt`, `LibraryScreen.kt`, `ExploreScreen.kt`): Used only for string case conversion.
  - `java.util.concurrent.TimeUnit` in `PlayerControls.kt`: Used for simple duration conversions.
  - `System.nanoTime()` in `LyricClock.kt`, `LyricsControlsGesture.kt`, etc.
- **Platform Hooks already abstracted**:
  The repository already establishes 5 expect/actual hooks between `androidMain` and `jvmMain`:
  1. `ContextClick`
  2. `ArtworkPixels`
  3. `Haptics`
  4. `PlayerPlatformUi`
  5. `StatusBarIcons`
  *(On iOS, `sharedUi/src/iosMain` simply provides the matching actuals)*.

---

## 4. Plan for Vendoring `:innertubex` as a Local KMP Module

### 4.1 Upstream Repository State
- Upstream coordinate in `shared/build.gradle.kts`: `com.github.MetrolistGroup.innertubex:innertubex:v0.7.0`
- GitHub repository: `https://github.com/MetrolistGroup/innertubex`
- Release tag: `v0.7.0`
- **Critical Discovery**: `MetrolistGroup/innertubex` is ALREADY a Kotlin Multiplatform project!
  - Targets declared in its `build.gradle.kts`: `android`, `jvm("desktop")`, `iosArm64()`, and `iosSimulatorArm64()`.
  - Source sets: `commonMain`, `desktopMain`, `nativeMain`.
  - `src/nativeMain` already provides:
    - `YtEjsScriptLoader.native.kt`
    - `DefaultYouTubeLocale.native.kt`
    - `Sha1.native.kt` (using native CommonCrypto / platform crypto)
  - Dependencies: `ktor-client-core:3.5.2`, `kotlinx-serialization-json:1.11.0`, `kotlinx-coroutines-core:1.11.0`, `quickjs-kt:1.0.14`.

### 4.2 Vendoring Execution Plan
1. **Clone / Place Source**:
   Clone or copy `https://github.com/MetrolistGroup/innertubex` at tag `v0.7.0` into `D:\Bitchord\innertubex\`.
2. **Settings Declaration**:
   Add `include(":innertubex")` to `settings.gradle.kts`.
3. **Module Configuration (`innertubex/build.gradle.kts`)**:
   - Align plugins with root project (`org.jetbrains.kotlin.multiplatform` 2.3.20, `org.jetbrains.kotlin.plugin.serialization` 2.3.20).
   - Configure targets: `androidTarget`, `jvm("desktop")`, `iosArm64()`, `iosSimulatorArm64()`.
   - Remove publish plugins (`mavenPublish`, `dokka`, `binaryCompatibilityValidator`) to keep build lean.
4. **Wiring into `:shared`**:
   In `shared/build.gradle.kts`:
   ```kotlin
   commonMain.dependencies {
       api(project(":innertubex"))
   }
   ```
   Remove the JitPack coordinate `api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")`.

---

## 5. SQLite & Database Configuration

### 5.1 Current Usage Analysis
- In `desktopApp/build.gradle.kts`: `implementation("org.xerial:sqlite-jdbc:3.47.1.0")` is consumed by `DesktopBrowserCookies.kt` via `java.sql.DriverManager.getConnection("jdbc:sqlite:...").use { ... }`.
- In `:app`: Data persistence is handled via Android SharedPreferences, EncryptedSharedPreferences, and file caches.
- In `:shared`: `AnalysisStore.kt` manages cached DSP audio analysis as individual JSON files in `<dir>/analyses/<trackId>.json`.
- The Master Porting Plan (WP-08) specifies consolidating persistent storage (track analysis, offline audio cache index, search history, saved queues) onto `androidx.sqlite:sqlite-bundled:2.5.0`.

### 5.2 `androidx.sqlite:sqlite-bundled` Replacement
`androidx.sqlite:sqlite-bundled:2.5.0` is an official AndroidX multiplatform library that statically bundles the SQLite C-amalgamation:
- **Platform Compatibility**: Fully supports JVM, Android, `iosArm64`, `iosSimulatorArm64`, and macOS.
- **No JDBC Dependency**: Does not use `java.sql.*` or external JNI `.dll`/`.dylib` files.
- **iOS Linking**: Embeds SQLite directly into the generated binary framework.
- **Code Pattern**:
  ```kotlin
  import androidx.sqlite.SQLiteConnection
  import androidx.sqlite.driver.bundled.BundledSQLiteDriver

  val driver = BundledSQLiteDriver()
  val connection: SQLiteConnection = driver.open(databasePath)
  try {
      connection.prepare("SELECT value FROM settings WHERE key = ?").use { stmt ->
          stmt.bindText(1, key)
          if (stmt.step()) {
              val result = stmt.getText(0)
          }
      }
  } finally {
      connection.close()
  }
  ```
- **iOS Sandbox Paths**:
  ```kotlin
  // Database: Application Support
  val appSupport = NSFileManager.defaultManager.URLsForDirectory(
      NSApplicationSupportDirectory, NSUserDomainMask
  ).first() as NSURL
  val dbPath = "${appSupport.path}/BitChord/bitchord.db"

  // Audio Cache: Caches
  val caches = NSFileManager.defaultManager.URLsForDirectory(
      NSCachesDirectory, NSUserDomainMask
  ).first() as NSURL
  val cachePath = "${caches.path}/BitChord/audio_cache"
  ```

---

## 6. Concrete Execution Roadmap for WP-01 & WP-02

### 6.1 WP-01: Build Graph & KMP Setup
1. **`settings.gradle.kts`**:
   ```kotlin
   include(":shared")
   include(":sharedUi")
   include(":innertubex")
   include(":desktopApp")
   ```
2. **`shared/build.gradle.kts`**:
   - Add iOS targets:
     ```kotlin
     val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
     iosTargets.forEach { target ->
         target.binaries.framework {
             baseName = "Shared"
             isStatic = true
             linkerOpts(
                 "-lsqlite3",
                 "-framework", "AVFoundation",
                 "-framework", "AudioToolbox",
                 "-framework", "JavaScriptCore",
                 "-framework", "WebKit",
                 "-framework", "Security",
             )
         }
     }
     ```
   - Restructure `sourceSets`:
     - `commonMain`: add `innertubex`, `ktor-client-core`, `websockets`, `content-negotiation`, `json`, `coroutines-core`, `androidx.sqlite:sqlite-bundled`.
     - `iosMain`: depends on `commonMain`, add `ktor-client-darwin:3.5.2`.
     - `jvmSharedMain`: depends on `commonMain`, holds `ktor-client-okhttp`, `onnxruntime`, `NewPipeExtractor`, `jsoup`.
     - `androidMain` and `jvmMain` depend on `jvmSharedMain`.
3. **`sharedUi/build.gradle.kts`**:
   - Add iOS targets:
     ```kotlin
     val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
     iosTargets.forEach { target ->
         target.binaries.framework {
             baseName = "SharedUI"
             isStatic = true
             export(project(":shared"))
         }
     }
     ```
   - In `commonMain`: add `coil-network-ktor3:3.0.4`.
   - In `iosMain`: depends on `commonMain`.
4. **Vendor `:innertubex`**:
   - Place `MetrolistGroup/innertubex` tag `v0.7.0` into `innertubex/`.
   - Configure `innertubex/build.gradle.kts` targeting JVM, Android, `iosArm64`, `iosSimulatorArm64`.

### 6.2 WP-02: Source Migration (`jvmSharedMain` -> `commonMain`)
1. **Move all 49 files from `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/` to `sharedUi/src/commonMain/kotlin/com/music/bitchord/`**:
   - In 7 files, replace `java.util.Locale` with `lowercase()` / `uppercase()`.
   - In `PlayerControls.kt`, replace `java.util.concurrent.TimeUnit` with duration math.
   - Replace `System.nanoTime()` calls with `MonotonicClock`.
2. **Implement `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/` actuals**:
   - `ContextClick.ios.kt` (No-op or secondary touch gesture)
   - `ArtworkPixels.ios.kt` (CoreGraphics / Skia bitmap pixel extraction)
   - `Haptics.ios.kt` (Invokes `UIImpactFeedbackGenerator(UIImpactFeedbackStyleMedium)`)
   - `PlayerPlatformUi.ios.kt` (iOS status bar & window safe-insets)
   - `StatusBarIcons.ios.kt` (iOS status bar style)
3. **Move pure domain files from `shared/src/jvmSharedMain/` to `shared/src/commonMain/`**:
   - Move `com/my/kizzy/**` (19 files)
   - Move `data/sources/**` (`SourceTrackKeys.kt`, `StreamFormat.kt`, `TrackMatcher.kt`)
   - Move `playback/` (`QueueBuilder.kt`, `QueueSections.kt`, `QueueTimeline.kt`, `TransitionFilter.kt`)
   - Move `data/model/*` (`ArtistCredits.kt`, `SearchHistoryEntity.kt`)
   - Move `data/settings/*` (`LibraryEnums.kt`, `PlayerEnums.kt`)
   - Move `data/presence/*` (`Presence.kt`)
   - Move `data/lyrics/*` (28 pure files)
   - Move `data/spotify/*` and `data/webdav/*`
4. **Implement KMP abstraction primitives**:
   - `PlatformCrypto`: `sha1Hex(bytes: ByteArray): String`, `md5Hex(bytes: ByteArray): String` (`CC_SHA1` / `CC_MD5` via `platform.CoreCrypto` on iOS, `MessageDigest` on JVM).
   - `PlatformString`: `normalizeDiacritics(s: String): String` (`CFStringTransform` on iOS, `java.text.Normalizer` on JVM).
   - `PlatformClock`: `monotonicNowMs(): Long` (`mach_absolute_time` / `NSDate` on iOS, `System.nanoTime` on JVM).
5. **Decouple Stream Resolution & Analysis**:
   - Move `YtMusicRepository.kt` to `commonMain`.
   - Move `Innertube.kt` to `commonMain` (using multiplatform `HttpClient` and `PlatformCrypto`).
   - Abstract `MelSpectrogram` and `VocalTracker` as expect/actual interfaces for WP-04 / WP-05.

---

## 7. Risk Analysis & Recommendations

1. **JitPack vs Local Vendoring**: Relying on JitPack for `innertubex` is a guaranteed failure for iOS because JitPack does not run macOS runners to produce Apple Silicon simulator / device `.klib` and framework binaries. Vendoring `:innertubex` in-tree is mandatory and straightforward since upstream already contains `nativeMain`.
2. **Coil 3 Network Engine**: Upstream uses `coil-network-okhttp:3.0.4`. Ensure `coil-network-ktor3:3.0.4` is configured on iOS to prevent runtime `ClassNotFoundException` or missing image loaders.
3. **Static Framework Linkage**: Because BitChord will incorporate static C++ DSP archives (`libbitchord_analysis.a`) and CocoaPods (`onnxruntime-c`), setting `isStatic = true` in both `Shared` and `SharedUI` framework binaries is strictly required.
