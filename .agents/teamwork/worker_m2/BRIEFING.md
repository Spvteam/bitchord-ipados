# BRIEFING — 2026-10-16T10:39:00Z

## Mission
Implement Milestone M2 (WP-02: JVM API Decoupling & Source Relocation) for Bitchord iOS/macOS KMP port.

## 🔒 My Identity
- Archetype: implementer, qa, specialist
- Roles: implementer, qa, specialist
- Working directory: D:\Bitchord\.agents\teamwork\worker_m2
- Original parent: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Milestone: M2 (WP-02: JVM API Decoupling & Source Relocation)

## 🔒 Key Constraints
- Genuine implementation only; no cheating, facade, or dummy implementations.
- Minimal change principle.
- Relocate UI (49 files) from sharedUi/src/jvmSharedMain to sharedUi/src/commonMain.
- Relocate Shared logic (75 pure KMP files) from shared/src/jvmSharedMain to shared/src/commonMain.
- Multiplatform primitives in shared/src/commonMain/kotlin/com/music/bitchord/platform/ (PlatformCrypto, PlatformClock, PlatformUrl, PlatformNormalizer, PlatformLock).
- iOS actuals in shared/src/iosMain/kotlin/com/music/bitchord/platform/ using Apple frameworks (CommonCrypto, mach_absolute_time, NSString RFC 3986, CFStringNormalize, NSLock).
- JVM actuals in shared/src/jvmMain/kotlin/com/music/bitchord/platform/.
- iOS actuals in sharedUi/src/iosMain/kotlin/com/music/bitchord/ (PlatformHaptics, ContextClick, ArtworkPixels, PlayerPlatformUi, StatusBarIcons).
- Refactor ServerClock.kt and MonotonicClock.kt to use PlatformClock and PlatformLock.
- Validate with compile commands & python tests/e2e/run_e2e_tests.py.
- Update PORTING_PROGRESS.md to 17% complete (WP-02 complete).

## Current Parent
- Conversation ID: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Updated: not yet

## Task Summary
- **What to build**: Relocate UI and shared logic to commonMain, implement platform primitives & iOS actuals, decouple JVM dependencies.
- **Success criteria**: JVM and iOS compilation pass, E2E tests pass, PORTING_PROGRESS.md updated, handoff complete.
- **Interface contracts**: D:\Bitchord\PROJECT.md
- **Code layout**: D:\Bitchord\PROJECT.md

## Key Decisions Made
- [TBD]

## Change Tracker
- **Files modified**: [TBD]
- **Build status**: pending
- **Pending issues**: none

## Quality Status
- **Build/test result**: pending
- **Lint status**: pending
- **Tests added/modified**: pending

## Loaded Skills
- None specified in dispatch prompt.

## Artifact Index
- D:\Bitchord\.agents\teamwork\worker_m2\DISPATCH.md — Assignment from orchestrator
- D:\Bitchord\.agents\teamwork\worker_m2\progress.md — Liveness & step tracking
- D:\Bitchord\.agents\teamwork\worker_m2\handoff.md — Final handoff report
