# BRIEFING — 2026-10-16T10:32:00Z

## Mission
Analyze UI relocation from jvmSharedMain to commonMain and design iOS actual implementations for sharedUi.

## 🔒 My Identity
- Archetype: explorer
- Roles: UI Relocation & iOS Actuals Specialist
- Working directory: D:\Bitchord\.agents\teamwork\explorer_m2_1
- Original parent: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Milestone: M2 (sharedUi relocation and iOS actuals)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Analyze relocation of files in sharedUi/src/jvmSharedMain/ to sharedUi/src/commonMain/kotlin/com/music/bitchord/
- Identify JVM imports (Locale, TimeUnit, System.nanoTime, etc.) and provide standard multiplatform replacements
- Inspect 5 platform expect/actual declarations (ContextClick, ArtworkPixels, Haptics, PlayerPlatformUi, StatusBarIcons)
- Design and document exact iOS actual implementations required in sharedUi/src/iosMain/kotlin/
- Write findings to analysis.md and handoff.md, notify orchestrator

## Current Parent
- Conversation ID: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Updated: 2026-10-16T10:32:00Z

## Investigation State
- **Explored paths**:
  - `sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/` (all 49 files)
  - `sharedUi/src/androidMain/kotlin/com/music/bitchord/ui/` (5 actual files)
  - `sharedUi/src/jvmMain/kotlin/com/music/bitchord/ui/` (6 actual/support files)
  - `sharedUi/build.gradle.kts`
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/MonotonicClock.kt`
  - `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/TranslationLanguages.kt`
- **Key findings**:
  - Exactly 49 Kotlin files in `sharedUi/src/jvmSharedMain/` mapped to 11 destination packages in `commonMain`.
  - Only 10 files contain JVM imports or calls (`java.util.Locale`, `java.util.concurrent.TimeUnit`, `System.nanoTime()`, `Math.floorMod`, `String.format`); 39 files are already 100% pure Kotlin Compose Multiplatform.
  - Exactly 5 files contain `expect` declarations (14 expect members total).
  - Complete iOS actuals designed using UIKit (`UIImpactFeedbackGenerator`, `UIApplication.setStatusBarStyle`, `UIApplication.idleTimerDisabled`), Foundation (`NSProcessInfo.systemUptime`, `NSLocale`), Skiko Skia bitmap allocation, and Compose pointer gesture handlers.
- **Unexplored areas**: None for M2 sharedUi scope.

## Key Decisions Made
- Replace `System.nanoTime()` with monotonic `nanoTime(): Long` provided by `:shared`.
- Replace `Locale` formatting with pure Kotlin formatting functions.
- Replace `Math.floorMod` with Kotlin stdlib `mod()`.
- Implement `ContextClick.ios.kt` supporting iPadOS Magic Keyboard/Trackpad secondary click.
- Implement `Haptics.ios.kt` with full Taptic Engine mapping via `UIImpactFeedbackGenerator`.
- Implement `ArtworkPixels.ios.kt` using Skiko little-endian BGRA byte buffer installation.
- Implement `PlayerPlatformUi.ios.kt` with `NSProcessInfo.systemUptime` and `UIApplication.idleTimerDisabled`.
- Implement `StatusBarIcons.ios.kt` with `UIApplication.setStatusBarStyle`.

## Artifact Index
- D:\Bitchord\.agents\teamwork\explorer_m2_1\BRIEFING.md
- D:\Bitchord\.agents\teamwork\explorer_m2_1\progress.md
- D:\Bitchord\.agents\teamwork\explorer_m2_1\analysis.md
- D:\Bitchord\.agents\teamwork\explorer_m2_1\handoff.md
