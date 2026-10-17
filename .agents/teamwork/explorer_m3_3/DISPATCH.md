## 2026-10-16T13:48:02Z
You are explorer_m3_3 for Milestone M3 (WP-04: C++17 DSP Analyzer iOS Bridge).
Your working directory: D:\Bitchord\.agents\teamwork\explorer_m3_3
Your dispatch file: D:\Bitchord\.agents\teamwork\explorer_m3_3\DISPATCH.md

MANDATORY FIRST STEP: Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.

Scope: Kotlin/Native cinterop Configuration & KMP Expect/Actual Binding
1. Investigate how Kotlin/Native cinterop should be configured in shared/build.gradle.kts (bitchord_analysis.def definition, headers, staticLibraries, linkerOpts).
2. Examine existing JVM DSP code in shared/src/jvmSharedMain/kotlin/com/music/bitchord/ (MelSpectrogram.kt, BeatTracker.kt, TrackFeatures.kt, etc.) and tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py.
3. Design the commonMain expect interface and iosMain actual implementation using the generated cinterop Kotlin package (kotlinx.cinterop, native heap pinning/allocation).
4. Ensure the JVM actual continues to function via JNI without breaking existing desktop functionality.

Write your detailed findings in D:\Bitchord\.agents\teamwork\explorer_m3_3\analysis.md and your handoff in D:\Bitchord\.agents\teamwork\explorer_m3_3\handoff.md, then send a completion message back.
