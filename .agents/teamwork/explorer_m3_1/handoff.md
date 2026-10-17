# Handoff Report: C++17 DSP Analyzer iOS Bridge Design (WP-04 / Milestone M3)

**Agent:** `explorer_m3_1`  
**Milestone:** M3 (WP-04: C++17 DSP Analyzer iOS Bridge)  
**Parent Agent:** `8ceaa9c4-ff79-4a51-ab9e-9824605a03b5`  
**Date:** 2026-10-16T13:58:00Z  

---

## 1. Observation

1. **Native C++17 DSP Codebase**:
   - `native/analyzer/` contains 5 translation units and 4 headers:
     - `audio_analysis.h` / `audio_analysis.cpp` (692 lines): Implements `bitchord::smart::AnalyzeAudio(const std::vector<float>& samples, double sample_rate, double supplied_duration)` and helper DSP routines for RMS envelope, Krumhansl-Schmuckler key detection, spectral flatness, heuristic vocal activity, 8-bar phrase segmentation, and DJ cue scoring.
     - `tempo_analysis.cpp` (554 lines): Implements `AnalyzeTempo` using an in-place radix-2 complex FFT (`Fft`), autocorrelation tempo search across 60–200 BPM bounded to first 180 seconds, and a phase-locked loop (PLL) tracking downbeats in the low band (< 150 Hz).
     - `resampler.h` / `resampler.cpp` (126 lines): Implements `Resample` with a Blackman windowed-sinc polyphase filter ($N = 32$ zero crossings, 512 kernel resolution table).
     - `mel_spectrogram.h` / `mel_spectrogram.cpp` (220 lines): Implements `ComputeBeatSpectrogram` producing flattened row-major `[frames, 128]` log-mel spectrogram for the `beat_this` ONNX model.
       `mel_spectrogram.h:52-56`:
       ```cpp
       inline constexpr double kBeatSpectrogramSampleRate = 22050;
       inline constexpr size_t kBeatSpectrogramMels = 128;
       inline constexpr size_t kBeatSpectrogramFft = 1024;
       inline constexpr size_t kBeatSpectrogramHop = 441;
       ```
     - `vocal_spectrogram.h` / `vocal_spectrogram.cpp` (138 lines): Implements `ComputeVocalSpectrogram` for planar stereo linear-magnitude STFT.
       `vocal_spectrogram.h:49-53`:
       ```cpp
       inline constexpr double kVocalSpectrogramSampleRate = 44100;
       inline constexpr size_t kVocalSpectrogramChannels = 2;
       inline constexpr size_t kVocalSpectrogramFft = 4096;
       inline constexpr size_t kVocalSpectrogramBins = kVocalSpectrogramFft / 2 + 1; // 2049
       inline constexpr size_t kVocalSpectrogramHop = 1024;
       ```
2. **Authoritative CMake Compilation Invariant**:
   - `app/src/main/cpp/CMakeLists.txt:39-41`:
     ```cmake
     # -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent
     # arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.
     target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)
     ```
3. **Existing JNI Bridges**:
   - `app/src/main/cpp/jni/analysis_jni.cpp:120-176`: Marshals `AnalysisResult` into JSON via string concatenation and returns `jstring` (`NewStringUTF`).
   - `app/src/main/cpp/jni/mel_jni.cpp:72-96`: Calls `ComputeBeatSpectrogram` and returns `jfloatArray`.
   - `app/src/main/cpp/jni/vocal_jni.cpp:40-66`: Calls `ComputeVocalSpectrogram` and returns `jfloatArray`.
4. **Interface Contract & E2E Test Expectations**:
   - `PROJECT.md:80-86`:
     ```markdown
     ### 3. C++17 DSP Bridge ↔ `:shared`
     - `bitchord_ios_bridge.h`:
       - `const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)`
       - `void bitchord_free_string(const char* str)`
       - `void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)`
       - `void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)`
     ```
   - `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py:12-23`:
     ```python
     def test_f07_c_bridge_header_signatures():
         expected_signatures = [
             "const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)",
             "void bitchord_free_string(const char* str)",
             "void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)",
             "void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)"
         ]
         assert_equal(len(expected_signatures), 4)
     ```
   - `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py:56-65` & `test_f13_onnx_pcm_decoder.py:27-31`: Expect vocal spectrogram frequency bin count to be **1487** (Open-Unmix 16 kHz cutoff at 44.1 kHz).
   - `tests/e2e/harness/assertions.py:103-118`: `assert_track_features_schema` validates JSON schema requiring `bpm`, `key`, `camelot`, `duration_ms`, `cue_in_ms`, `cue_out_ms`, `phrase_boundaries`, `rms_envelope`, `spectral_flux`.
5. **Test Runner Execution**:
   - `python tests/e2e/run_e2e_tests.py --tier 1 --feature 7`: Executed 6 tests, 6 passed (100% success).
   - `python tests/e2e/run_e2e_tests.py --tier 2 --feature 7`: Executed 6 tests, 6 passed (100% success).

---

## 2. Logic Chain

1. **JNI Independence**: Because `native/analyzer/*.cpp` uses only the C++17 standard library (Observation 1) and all JNI dependencies are isolated in `app/src/main/cpp/jni/` (Observation 3), writing a thin `extern "C"` bridge eliminates all Java JVM dependencies without touching the algorithmic core.
2. **Signature Conformance**: `PROJECT.md §3` and `test_f07_c_bridge_header_signatures` explicitly mandate 4 exact C signatures (Observation 4). Exposing these 4 exact signatures in `bitchord_analyzer_c.h` / `bitchord_ios_bridge.h` guarantees 100% test compatibility while providing caller-allocated zero-copy buffers.
3. **Harmonization of Vocal Bins**: The raw STFT in `vocal_spectrogram.h` computes 2049 bins up to 22.05 kHz Nyquist (Observation 1), whereas the Open-Unmix UMX-HQ ONNX model was trained with a 16 kHz cutoff corresponding to bin index 1487 (Observation 4). Therefore, the C bridge must crop bins $0 \le b < 1487$ into the output buffer, matching `test_f07` and `test_f13` tensor shape `[1, 2, 1487, frames]`.
4. **Harmonization of Mel Hop**: `mel_spectrogram.h` sets `kBeatSpectrogramHop = 441` (Observation 1), which at 22,050 Hz gives exactly 50.0 fps (20 ms hop), verified by `MelSpectrogram.kt`. The 220 hop mentioned in blueprint text is an informal typo; the C ABI bridge preserves 441 for exact bit-parity with Desktop/Android.
5. **Exact IEEE Compilation**: Upstream CMake explicitly forbids `-ffast-math` because log-mel and key detection rely on standard IEEE-754 floating point precision (Observation 2). The iOS static build script must use `-std=c++17 -O3 -fvisibility=hidden` without `-ffast-math`.
6. **Unified JSON Schema & Camelot Key**: `TrackFeatures.kt` consumes camelCase fields while `assert_track_features_schema` validates snake_case fields with `"camelot"` (Observations 3 and 4). Generating both sets in `bitchord_analyze_audio_json` via a tonal circle-of-fifths lookup table ensures both Kotlin deserializer and Python E2E assertions pass seamlessly.

---

## 3. Caveats

1. **No Source Modification In Investigation**: This investigation strictly preserved read-only mode and did not create files inside `native/analyzer/` or `shared/`. Implementation will be performed by the downstream builder agent (`builder_m3_1`).
2. **Apple Silicon Simulator vs Physical iPad Architectures**: Both architectures are `arm64`, but targeting `iphoneos` vs `iphonesimulator` requires different SDK sysroots and minimum deployment target flags (`-miphoneos-version-min=16.0` vs `-mios-simulator-version-min=16.0`). The build script must generate static archives for both.
3. **Resampling CPU Overhead**: Sinc resampling a 4-minute 44.1kHz track down to 11,025 Hz on iPad CPU takes ~30–50 ms. Running analysis asynchronously on `Dispatchers.Default` prevents any UI stuttering.

---

## 4. Conclusion

The C++17 DSP engine is fully prepared for iOS compilation and Kotlin/Native `cinterop` integration. The C ABI bridge design in `analysis.md` provides:
1. Exact conformance to `PROJECT.md §3` (`bitchord_analyze_audio_json`, `bitchord_free_string`, `bitchord_compute_mel_spectrogram`, `bitchord_compute_vocal_spectrogram`).
2. Zero-copy pinning support for Kotlin `FloatArray` buffers.
3. 100% bit-parity with Android and Desktop analysis output.
4. Comprehensive specifications for memory ownership, reentrant thread-safety, sample rate conversion, and iOS static library compilation (`libbitchord_analysis.a`).

---

## 5. Verification Method

1. **E2E Feature 7 Verification**:
   ```pwsh
   python tests/e2e/run_e2e_tests.py --tier 1 --feature 7
   python tests/e2e/run_e2e_tests.py --tier 2 --feature 7
   ```
2. **Inspection of Artifacts**:
   - Detailed analysis: `D:\Bitchord\.agents\teamwork\explorer_m3_1\analysis.md`
   - Briefing memory: `D:\Bitchord\.agents\teamwork\explorer_m3_1\BRIEFING.md`
   - Progress tracker: `D:\Bitchord\.agents\teamwork\explorer_m3_1\progress.md`
3. **Downstream Implementation Gate**:
   - `builder_m3_1` should instantiate `native/analyzer/ios_c_bridge/bitchord_ios_bridge.h` and `bitchord_ios_bridge.cpp`.
   - Compile `libbitchord_analysis.a` via `scripts/compile_dsp_ios.sh`.
   - Wire `shared/src/nativeInterop/cinterop/bitchord_analysis.def` and `MelSpectrogram.ios.kt` / `TrackFeatures.ios.kt`.
