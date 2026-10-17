# BRIEFING — 2026-10-16T10:37:00Z

## Mission
Analyze the relocation of business logic from shared/src/jvmSharedMain to shared/src/commonMain/kotlin for iOS/iPadOS portability, inventorying all files, categorizing clean relocations vs platform abstraction requirements, and producing structured analysis and handoff reports.

## 🔒 My Identity
- Archetype: explorer
- Roles: Shared Logic Relocation Specialist
- Working directory: D:\Bitchord\.agents\teamwork\explorer_m2_2
- Original parent: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Milestone: M2

## 🔒 Key Constraints
- Read-only investigation — do NOT implement / modify source code directly
- Write only to working directory D:\Bitchord\.agents\teamwork\explorer_m2_2\
- Produce analysis.md and handoff.md
- Report back to parent agent b9d6a6c6-0231-4a6f-a397-e747c73d7ef9 via send_message

## Current Parent
- Conversation ID: b9d6a6c6-0231-4a6f-a397-e747c73d7ef9
- Updated: 2026-10-16T10:37:00Z

## Investigation State
- **Explored paths**:
  - `shared/src/jvmSharedMain/kotlin/` (all 90 files examined)
  - `shared/src/commonMain/kotlin/` (16 existing files audited)
  - `shared/build.gradle.kts` (source sets, dependencies, linker opts)
  - `BitChord_iPadOS_Master_Porting_Plan.md` (WP-02, WP-03, WP-05, WP-08)
  - `PROJECT.md` (M2 milestones, interface contracts)
- **Key findings**:
  - Exactly 90 files in `jvmSharedMain`: 75 relocate cleanly to `commonMain`, 12 require platform abstractions (`YtMusicRepository`, `Innertube`, `BeatTracker`, `VocalTracker`, `TransitionFilter`, `AnalysisStore`, `MelSpectrogram`, `TrackFeatures`, `NativeAnalysisLibrary`, `TtmlLyrics`, `Genius`, `LyricsTranslation`), and 3 must stay in `jvmSharedMain` (`StreamResolver.kt`, `Http.kt`, `PlayerClient.kt` NewPipe binding).
  - All 19 Discord Kizzy RPC files, 28 lyrics sources/models, and queue builders are portable with minimal stdlib adaptations (`kotlin.uuid.Uuid`, standard lowercase, Ktor client).
  - Neural models (`BeatTracker`, `VocalTracker`) and C++ DSP (`MelSpectrogram`, `TrackFeatures`) need `expect`/`actual` bridging to `cinterop` and CocoaPods `onnxruntime-c` on iOS.
- **Unexplored areas**: None within M2 scope.

## Key Decisions Made
- Fully cataloged all 90 files in master inventory table with exact migration dispositions.
- Identified necessity of retaining `StreamResolver.kt` and `Http.kt` in `jvmSharedMain` for backward compatibility with JVM/Android.
- Formulated step-by-step implementation roadmap for implementer agents.

## Artifact Index
- DISPATCH.md — incoming instructions record
- progress.md — liveness and heartbeat tracking
- analysis.md — authoritative 90-file inventory and platform abstraction architecture
- handoff.md — self-contained 5-component handoff report
