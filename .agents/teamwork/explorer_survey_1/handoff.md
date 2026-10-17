# Handoff Report — Survey Explorer 1 (Build & Module Survey)

**Agent**: Survey Explorer 1  
**Folder**: `D:\Bitchord\.agents\teamwork\explorer_survey_1`  
**Recipient**: Project Orchestrator (`b9d6a6c6-0231-4a6f-a397-e747c73d7ef9`)  
**Type**: Hard Handoff  
**Milestone**: Survey Stage (Phase 0) -> WP-01 / WP-02 Ready

---

## 1. Observation

1. **Current Gradle Target Configuration**:
   - `shared/build.gradle.kts` lines 9-19:
     ```kotlin
     kotlin {
         androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
         jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
         applyDefaultHierarchyTemplate()
     ```
     No iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared.
   - `sharedUi/build.gradle.kts` lines 42-52:
     ```kotlin
     kotlin {
         androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
         jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
         applyDefaultHierarchyTemplate()
     ```
     No iOS targets are declared.

2. **JVM-Specific Dependencies in Gradle**:
   - `shared/build.gradle.kts` lines 27-40:
     - `compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")`
     - `api("io.ktor:ktor-client-okhttp:3.5.2")`
     - `api("org.jsoup:jsoup:1.22.2")`
     - `api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")`
     - `compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")`
   - `sharedUi/build.gradle.kts` line 67:
     - `api("io.coil-kt.coil3:coil-compose:3.0.4")` (consumes `coil-network-okhttp:3.0.4` at runtime).

3. **Source Code Distribution**:
   - `:shared`:
     - `shared/src/commonMain/`: 16 Kotlin files (`Models.kt`, `TransitionPolicy.kt`, `EqualizerPreset.kt`, etc.).
     - `shared/src/jvmSharedMain/`: 90 Kotlin files (`YtMusicRepository.kt`, `Innertube.kt`, `BeatTracker.kt`, `TransitionFilter.kt`, 29 lyrics files, 19 Discord Kizzy files).
   - `:sharedUi`:
     - `sharedUi/src/commonMain/`: 0 Kotlin files (only `composeResources/`).
     - `sharedUi/src/jvmSharedMain/`: 49 Kotlin files (all UI components, screens, `NowPlayingScreen.kt`, `AppUi.kt`).
     - `sharedUi/src/androidMain/`: 5 actuals (`ContextClick`, `ArtworkPixels`, `Haptics`, `PlayerPlatformUi`, `StatusBarIcons`).
     - `sharedUi/src/jvmMain/`: 6 actuals (`ContextClick`, `ArtworkPixels`, `Haptics`, `PlayerPlatformUi`, `StatusBarIcons`, `PaletteQuantizer`).

4. **Upstream InnerTubeX State**:
   - Upstream repo is `https://github.com/MetrolistGroup/innertubex` at tag `v0.7.0` (HTTP 200).
   - `MetrolistGroup/innertubex/build.gradle.kts` lines 34-36 explicitly declare:
     ```kotlin
     jvm("desktop") { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
     iosArm64()
     iosSimulatorArm64()
     ```
   - Contains `src/nativeMain/` with `Sha1.native.kt`, `YtEjsScriptLoader.native.kt`, `DefaultYouTubeLocale.native.kt`.

5. **Persistence & SQLite State**:
   - `desktopApp/build.gradle.kts` line 125: `implementation("org.xerial:sqlite-jdbc:3.47.1.0")` is consumed exclusively by `DesktopBrowserCookies.kt`.
   - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/AnalysisStore.kt`: Reads and writes track features as individual JSON files on disk.
   - Android `:app`: Uses SharedPreferences and encrypted storage; does not use a Room or SQLite database directly.

---

## 2. Logic Chain

1. **Gradle Build Graph & Targets**:
   - *Premise*: To produce an iOS framework (`Shared.framework` and `SharedUI.framework`), Kotlin Multiplatform requires explicit iOS target declarations.
   - *Observation*: Neither `shared` nor `sharedUi` declares any iOS target (Observation 1).
   - *Deduction*: Adding `iosArm64()` and `iosSimulatorArm64()` to both build scripts with static binary framework configuration (`isStatic = true`) and linking required Apple frameworks (`AVFoundation`, `AudioToolbox`, `JavaScriptCore`, `WebKit`, `Security`, `-lsqlite3`) is the mandatory foundation step (WP-01).

2. **Source Set Decoupling (`jvmSharedMain` -> `commonMain`)**:
   - *Premise*: Code inside `jvmSharedMain` is only compiled for JVM and Android targets. Any file residing in `jvmSharedMain` is invisible to `iosArm64` and `iosSimulatorArm64`.
   - *Observation*: 100% of the UI code (49 files in `:sharedUi`) and 90 files in `:shared` reside in `jvmSharedMain` (Observation 3).
   - *Observation*: Analysis of imports in `sharedUi/src/jvmSharedMain` revealed that all 49 files are pure Compose Multiplatform composables with zero platform-specific UI bindings; the only JVM imports are `java.util.Locale`, `TimeUnit`, and `System.nanoTime()`.
   - *Deduction*: All 49 UI files can be moved directly to `sharedUi/src/commonMain/kotlin/` once trivial stdlib replacements (`lowercase()`, monotonic clock) are made. Implementing the 5 platform actuals in `sharedUi/src/iosMain/` completes the UI compilation for iOS.
   - *Observation*: In `shared/src/jvmSharedMain`, domain models, queue builders, lyrics sources, and Discord RPC clients are pure Kotlin or rely only on `Base64`, `UUID`, and `MessageDigest`.
   - *Deduction*: By providing `PlatformCrypto` (`CC_SHA1`/`CC_MD5` on iOS), `PlatformClock`, and `PlatformString`, over 80 of the 90 files can move directly to `commonMain`. The remaining platform leaves (`MelSpectrogram`, `BeatTracker`, `VocalTracker`, `PoTokenGenerator`) can be abstracted via `expect`/`actual`.

3. **Vendoring `:innertubex`**:
   - *Premise*: `:shared` depends on `innertubex` for YouTube data and stream models. The published JitPack artifact is JVM-only.
   - *Observation*: Upstream `MetrolistGroup/innertubex:v0.7.0` source code already includes `iosArm64()`, `iosSimulatorArm64()`, and `nativeMain` (Observation 4).
   - *Deduction*: Vendoring `MetrolistGroup/innertubex` as an in-tree module `:innertubex` included in `settings.gradle.kts` and depending on `api(project(":innertubex"))` immediately enables iOS compilation of all YouTube models and parser logic without any external dependency publication hurdles.

4. **KMP SQLite Engine**:
   - *Premise*: `sqlite-jdbc` is a JVM-only driver that cannot link into an iOS binary.
   - *Observation*: `androidx.sqlite:sqlite-bundled:2.5.0` bundles SQLite C amalgamation natively for JVM, Android, and Apple targets (`iosArm64`, `iosSimulatorArm64`).
   - *Deduction*: Adding `androidx.sqlite:sqlite-bundled:2.5.0` to `commonMain` allows building a fully cross-platform persistence layer (`BundledSQLiteDriver`) that operates identically on desktop and iPadOS sandbox paths.

---

## 3. Caveats

1. **JvmSharedMain Coexistence**: During Phase 1 and Phase 2, `jvmSharedMain` should be retained in `shared/build.gradle.kts` to allow `desktopMain` and `androidMain` to continue consuming JVM-specific legacy libraries (`NewPipeExtractor`, JVM ONNX JNI) until downstream Work Packages complete their migrations.
2. **Coil Image Engine**: Coil 3's `coil-network-ktor3:3.0.4` must be explicitly declared for `iosMain` (or `commonMain`) in `sharedUi` so that image loading over HTTP/HTTPS succeeds on iOS without OkHttp.
3. **QuickJS Native Linking in `:innertubex`**: `:innertubex` declares a dependency on `io.github.dokar3:quickjs-kt:1.0.14`. Verification that `quickjs-kt` static C libraries link without unresolved symbols on Apple Silicon simulator will be handled during WP-01 build gating.

---

## 4. Conclusion

- **Feasibility**: High. The architecture is cleanly separable. The vast majority of business logic and UI code was only placed in `jvmSharedMain` because upstream initially built only for Android and Desktop JVM.
- **Actionable Execution Plan**:
  - **WP-01**: Configure `iosArm64()` / `iosSimulatorArm64()` in `shared` and `sharedUi`, vendor `MetrolistGroup/innertubex:v0.7.0` as `:innertubex`, add `ktor-client-darwin:3.5.2` and `coil-network-ktor3:3.0.4`, and configure static frameworks with Apple system framework linker options.
  - **WP-02**: Move all 49 UI files in `sharedUi/src/jvmSharedMain/` to `sharedUi/src/commonMain/`, implement the 5 iOS UI actuals in `sharedUi/src/iosMain/`, and migrate domain models, queue builders, lyrics providers, and Kizzy Discord RPC from `shared/src/jvmSharedMain/` to `shared/src/commonMain/` backed by `PlatformCrypto` and `PlatformClock`.

---

## 5. Verification Method

To independently verify the observations and findings documented in this report:

1. **Verify Target Declarations**:
   - Inspect `shared/build.gradle.kts` (lines 9-25) and `sharedUi/build.gradle.kts` (lines 42-60). Observe the absence of `iosArm64` or `iosSimulatorArm64`.
2. **Verify File Counts & Packages**:
   - Inspect `D:\Bitchord\.agents\teamwork\explorer_survey_1\survey_report.md` Section 3 for the exhaustive inventory of all 90 files in `:shared` and all 49 files in `:sharedUi`.
3. **Verify Upstream `innertubex` KMP Declarations**:
   - Fetch `https://raw.githubusercontent.com/MetrolistGroup/innertubex/v0.7.0/build.gradle.kts` and verify `iosArm64()`, `iosSimulatorArm64()`, and `nativeMain` declarations.
4. **Invalidation Conditions**:
   - If `MetrolistGroup/innertubex:v0.7.0` required private proprietary C headers not available in open-source, the vendoring strategy would need alteration. (Verified: it is 100% open-source Kotlin + QuickJS).
