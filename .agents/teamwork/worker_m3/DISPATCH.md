## 2026-10-16T14:00:40Z
You are worker_m3 for Milestone M3 (WP-04: C++17 DSP Analyzer iOS Bridge).
Your working directory: D:\Bitchord\.agents\teamwork\worker_m3
Your dispatch file: D:\Bitchord\.agents\teamwork\worker_m3\DISPATCH.md

MANDATORY FIRST STEP: Read D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md before starting work.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Key Inputs:
- D:\Bitchord\.agents\teamwork\ORIGINAL_REQUEST.md
- D:\Bitchord\PROJECT.md
- D:\Bitchord\PORTING_PROGRESS.md
- D:\Bitchord\BitChord_iPadOS_Master_Porting_Plan.md
- D:\Bitchord\.agents\teamwork\explorer_m3_1\handoff.md & analysis.md (C ABI Bridge Specification)
- D:\Bitchord\.agents\teamwork\explorer_m3_2\handoff.md & analysis.md (Clang Static Lib Packaging)
- D:\Bitchord\.agents\teamwork\explorer_m3_3\handoff.md & analysis.md (cinterop & KMP Bindings)

Your mission:
1. Implement the C ABI bridge:
   - Create header: native/analyzer/ios_c_bridge/bitchord_ios_bridge.h
   - Create implementation: native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp
   - Conforming to the 4 signatures:
     * const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)
     * void bitchord_free_string(const char* str)
     * void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)
     * void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)
   - Zero-copy buffer output writing:
     * Mel: 128 mel bins, 22,050 Hz, hop = 441
     * Vocal: 1487 STFT bins (Open-Unmix 16 kHz cutoff), 44,100 Hz, hop = 1024, stereo [1, 2, 1487, frames]
     * JSON: Serializes all fields required by TrackFeatures and E2E assertions (bpm, key, camelot, duration_ms, cue_in_ms, cue_out_ms, phrase_boundaries, rms_envelope, spectral_flux).
     * bitchord_free_string: safely frees heap-allocated JSON string and gracefully handles NULL pointers.
   - Exact IEEE behavior: DO NOT compile with -ffast-math.
2. Build Automation & Static Library Packaging:
   - Provide build scripts: scripts/compile_dsp_ios.sh and native/analyzer/ios_c_bridge/build_ios_static.sh
   - Structure archive directories:
     * native/libs/iosArm64/libbitchord_analysis.a
     * native/libs/iosSimulatorArm64/libbitchord_analysis.a
   - Provide static archives with valid Mach-O ARM64 ar headers so Kotlin/Native compiler and cinterop linkers succeed on both Windows and Apple targets.
3. Kotlin/Native cinterop Configuration:
   - Create definition file: shared/src/nativeInterop/cinterop/bitchord_analysis.def
     * headers = bitchord_ios_bridge.h
     * headerFilter = bitchord_ios_bridge.h
     * package = com.music.bitchord.native.analysis
     * compilerOpts = -std=c++17
     * staticLibraries = libbitchord_analysis.a
     * linkerOpts = -lc++
   - Update shared/build.gradle.kts:
     * Configure cinterops block for iOS targets (iosArm64, iosSimulatorArm64)
     * Set includeDirs to native/analyzer/ios_c_bridge and native/analyzer
     * Configure framework linkerOpts: -lc++, -lbitchord_analysis
4. Multiplatform Expect/Actual Implementation:
   - Refactor TrackFeatures.kt, MelSpectrogram.kt, VocalSpectrogram.kt:
     * Place shared models and expect objects in shared/src/commonMain/kotlin/com/music/bitchord/playback/smart/
     * Implement actual objects in shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/ using kotlinx.cinterop zero-copy usePinned memory contracts
     * Retain JNI actual objects in shared/src/jvmSharedMain/kotlin/com/music/bitchord/playback/smart/ ensuring 100% backward compatibility with desktopApp and Android
5. Verification & Testing:
   - Run E2E tests:
     * python -m pytest tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py -v
     * python -m pytest tests/e2e/tier2_boundary/test_f07_limits.py -v
     * python tests/e2e/run_e2e_tests.py
   - Compile targets:
     * .\gradlew.bat :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileCommonMainKotlinMetadata :desktopApp:compileKotlin --no-daemon
6. Documentation & Progress:
   - Update D:\Bitchord\PORTING_PROGRESS.md to 25% (WP-04 marked completed).
   - Write handoff report in D:\Bitchord\.agents\teamwork\worker_m3\handoff.md with all build and test command outputs.
   - Send completion message back to parent.
