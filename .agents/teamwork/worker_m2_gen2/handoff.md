# Milestone M2 Handoff Report: WP-02 JVM API Decoupling & Source Relocation

**Agent**: `worker_m2_gen2`  
**Working Directory**: `D:\Bitchord\.agents\teamwork\worker_m2_gen2`  
**Timestamp**: 2026-10-16T13:15:00Z  
**Recipient**: `parent` (`8ceaa9c4-ff79-4a51-ab9e-9824605a03b5`)  

---

## 1. Observation

1. **Platform Primitives in `:shared`**:
   - `shared/src/commonMain/kotlin/com/music/bitchord/platform/`:
     - `PlatformCrypto.kt`: `expect object PlatformCrypto` declaring `md5Hex`, `md5Bytes`, `sha1Bytes`, `sha1Hex`, `hmacSha1Bytes`, `hmacSha1Hex`.
     - `PlatformClock.kt`: `expect object PlatformClock` declaring `currentTimeMillis`, `nanoTime`, `uptimeMillis`.
     - `PlatformUrl.kt`: `expect object PlatformUrl` declaring `encodeURIComponent`, `decodeURIComponent`.
     - `PlatformNormalizer.kt`: `expect object PlatformNormalizer` declaring `nfdNormalize`.
     - `PlatformLock.kt`: `expect class PlatformLock` declaring `lock()`, `unlock()`, and inline `withLock`.
   - `shared/src/jvmMain/kotlin/com/music/bitchord/platform/`:
     - `actual object PlatformCrypto` implemented via `java.security.MessageDigest` and `javax.crypto.Mac`.
     - `actual object PlatformClock` implemented via `System.currentTimeMillis()` and `System.nanoTime()`.
     - `actual object PlatformUrl` implemented via `java.net.URLEncoder` and `URLDecoder`.
     - `actual object PlatformNormalizer` implemented via `java.text.Normalizer`.
     - `actual class PlatformLock` implemented via JVM synchronization monitor (`Any()`).
   - `shared/src/iosMain/kotlin/com/music/bitchord/platform/`:
     - `actual object PlatformCrypto` implemented via Apple CommonCrypto (`CC_MD5`, `CC_SHA1`, `CCHmac`).
     - `actual object PlatformClock` implemented via POSIX `gettimeofday` and `clock_gettime(CLOCK_MONOTONIC)`.
     - `actual object PlatformUrl` implemented via Foundation `NSString` (`stringByAddingPercentEncodingWithAllowedCharacters`, `stringByRemovingPercentEncoding`).
     - `actual object PlatformNormalizer` implemented via Foundation `NSString.precomposedStringWithCanonicalMapping`.
     - `actual class PlatformLock` implemented via Apple `NSLock`.

2. **Decoupled Clocks**:
   - `shared/src/commonMain/kotlin/com/music/bitchord/data/ServerClock.kt`: Swapped `System.currentTimeMillis()` for `PlatformClock.currentTimeMillis()`.
   - `shared/src/commonMain/kotlin/com/music/bitchord/data/MonotonicClock.kt`: Swapped `System.nanoTime()` for `PlatformClock.nanoTime()`.

3. **Relocated Shared Logic Files (75 files)**:
   - Relocated 75 pure KMP shared logic files from `shared/src/jvmSharedMain/` into `shared/src/commonMain/kotlin/com/music/bitchord/data/` and `com/my/kizzy/`.
   - Decoupled `Dispatchers.IO` to `Dispatchers.Default` in `YtMusicRepository.kt`, `Megalobiz.kt`, and 11 lyrics provider files (`BetterLyrics.kt`, `BiniLyrics.kt`, `KuGou.kt`, `LrcLib.kt`, `LrcRed.kt`, `LyricsPlus.kt`, `LyricsRepository.kt`, `PaxSenix.kt`, `SimpMusicLyrics.kt`, `Unison.kt`, `YouTubeLyrics.kt`).
   - Eliminated Java-only `RegexOption.DOT_MATCHES_ALL` by inserting `(?s)` in regex strings.
   - Eliminated `Character.UnicodeBlock` in `TrackMatcher.kt` by using pure Kotlin Unicode Char range checks (`\u4E00..\u9FFF`, `\u3040..\u309F`, `\u30A0..\u30FF`, `\uAC00..\uD7AF`).
   - Replaced Java `Map.putIfAbsent` with idiomatic Kotlin map assignment across `LikeState.kt`, `YtMusicRepository.kt`, and `InnertubeParser.kt`.
   - Retained JVM-specific files (`StreamResolver.kt`, `Http.kt`, `NEWPIPE`) in `shared/src/jvmSharedMain/` as required.

4. **Relocated UI Files (49 files)**:
   - Relocated 49 UI files from `sharedUi/src/jvmSharedMain/` into `sharedUi/src/commonMain/kotlin/com/music/bitchord/ui/`.
   - Eliminated `java.util.Locale` in favor of standard Kotlin `.lowercase()`.
   - Eliminated `java.util.concurrent.TimeUnit` in `Formatters.kt` and `PlayerHost.kt` in favor of basic arithmetic (`totalSeconds / 60`, `totalSeconds % 60`).
   - Replaced final KMP `LinkedHashMap` subclassing in `ArtworkMeshBackdrop.kt` with an internal `LruCache` delegation class.
   - Decoupled `seen.getOrDefault(id, 0)` in `PlayerQueue.kt` to `(seen[id] ?: 0)`.
   - Updated `sharedUi/build.gradle.kts` to reference `androidx.lifecycle:lifecycle-runtime-compose:2.9.4`, fixing missing common and iOS klibs in `org.jetbrains.androidx.lifecycle:2.8.4`.

5. **Implemented iOS UI Actuals (5 files)**:
   - `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/components/PlatformHaptics.ios.kt`: Implemented using Apple `UIImpactFeedbackGenerator` and `UINotificationFeedbackGenerator`.
   - `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/components/ContextClick.ios.kt`: Implemented using Compose `Modifier.pointerInput` and `detectTapGestures(onLongPress = ...)`.
   - `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/player/ArtworkPixels.ios.kt`: Implemented using Apple CoreGraphics `CGImageGetDataProvider` and `CGDataProviderCopyData` buffer inspection.
   - `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/player/PlayerPlatformUi.ios.kt`: Implemented `PlatformDropTarget`, `UIWindow` window-size hooks, and `composePlatformUi`.
   - `sharedUi/src/iosMain/kotlin/com/music/bitchord/ui/screens/StatusBarIcons.ios.kt`: Implemented safe no-op on iOS (system handles status bar appearance via UIKit view controller).

6. **Target Verification Output**:
   - `.\gradlew.bat :shared:compileCommonMainKotlinMetadata`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :shared:compileKotlinIosSimulatorArm64`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :shared:compileKotlinIosArm64`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :shared:compileKotlinJvm`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :sharedUi:compileCommonMainKotlinMetadata`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :sharedUi:compileKotlinIosSimulatorArm64`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :sharedUi:compileKotlinIosArm64`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :sharedUi:compileKotlinJvm`: Exit code 0 (BUILD SUCCESSFUL).
   - `.\gradlew.bat :desktopApp:compileKotlin`: Exit code 0 (BUILD SUCCESSFUL).
   - `python tests\e2e\run_e2e_tests.py`: Exit code 0 (ALL TESTS PASSED — 279/279 test cases, 100% success).

7. **Progress File Updates**:
   - `D:\Bitchord\PORTING_PROGRESS.md` updated to 17% (2 / 12 Work Packages Complete, WP-02 checked off).

---

## 2. Logic Chain

1. **Decoupling Primitives**: Multiplatform architecture requires shared common code to avoid direct JVM standard library calls. By abstracting crypto, clocks, URL encoding/decoding, string normalization, and concurrency locks into multiplatform `expect`/`actual` declarations, common logic gains portable primitives implemented natively on both JVM (via Java stdlib) and iOS (via Apple CommonCrypto, POSIX, and Foundation).
2. **KMP CommonMain Coroutine Dispatch**: In KotlinX Coroutines 1.10.2 metadata, `Dispatchers.IO` is not exported in the commonMain interface. Substituting `Dispatchers.Default` for asynchronous background network/parsing pipelines in `commonMain` preserves asynchronous execution while adhering strictly to KMP common API constraints.
3. **Decoupling UI Logic**: Relocating UI files to `sharedUi/src/commonMain/` required stripping all desktop/JVM-specific types (`java.util.Locale`, `java.util.concurrent.TimeUnit`, `System.nanoTime`). By replacing these with pure Kotlin constructs and implementing platform-specific gestures (long-press haptic context click) and pixel extraction via CoreGraphics on iOS, the UI layer becomes 100% platform-agnostic and ready for iPadOS rendering.
4. **Build & Test Validation**: All 9 compilation targets across common metadata, iOS Simulator Arm64, iOS Device Arm64, and JVM targets compiled cleanly with zero errors. The Python E2E verification suite confirmed zero regressions across 279 test cases.

---

## 3. Caveats

- **No Caveats**: All requirements from the dispatch prompt, master porting plan, and interface contracts for WP-02 have been completely fulfilled, verified, and documented.

---

## 4. Conclusion

Milestone M2 (WP-02: JVM API Decoupling & Source Relocation) is **100% complete**. All 75 shared files and 49 UI files have been relocated to `commonMain`, all platform primitives and UI actuals have been implemented for iOS and JVM, all compilation targets across `:shared`, `:sharedUi`, and `:desktopApp` compile cleanly, all 279 E2E tests pass, and `PORTING_PROGRESS.md` has been updated to 17%. The codebase is fully ready for Milestone M3 (WP-04 C++17 DSP Analyzer iOS Bridge or WP-03 Stream Resolution).

---

## 5. Verification Method

To independently verify the implementation:

1. **Verify `:shared` Compilation**:
   ```pwsh
   .\gradlew.bat :shared:compileCommonMainKotlinMetadata
   .\gradlew.bat :shared:compileKotlinIosSimulatorArm64
   .\gradlew.bat :shared:compileKotlinIosArm64
   .\gradlew.bat :shared:compileKotlinJvm
   ```
2. **Verify `:sharedUi` Compilation**:
   ```pwsh
   .\gradlew.bat :sharedUi:compileCommonMainKotlinMetadata
   .\gradlew.bat :sharedUi:compileKotlinIosSimulatorArm64
   .\gradlew.bat :sharedUi:compileKotlinIosArm64
   .\gradlew.bat :sharedUi:compileKotlinJvm
   ```
3. **Verify `:desktopApp` Compilation**:
   ```pwsh
   .\gradlew.bat :desktopApp:compileKotlin
   ```
4. **Verify E2E Test Suite**:
   ```pwsh
   python tests\e2e\run_e2e_tests.py
   ```
5. **Inspect Progress Documentation**:
   Check `D:\Bitchord\PORTING_PROGRESS.md` for WP-02 completion and 17% overall progress.
