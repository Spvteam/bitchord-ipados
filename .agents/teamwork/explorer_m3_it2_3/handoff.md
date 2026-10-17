# Hard Handoff Report: E2E Test Suite Remediation for Static Archive Binary Validation and Multi-Sample-Rate Buffer Capacity

**Agent**: `explorer_m3_it2_3`  
**Milestone**: M3 Iteration 2 (WP-04: C++17 DSP Analyzer iOS Bridge Remediation)  
**Parent Agent**: `8ceaa9c4-ff79-4a51-ab9e-9824605a03b5`  
**Working Directory**: `D:\Bitchord\.agents\teamwork\explorer_m3_it2_3`  
**Date**: 2026-10-16  

---

## 1. Observation

1. **Defects in Static Archive Binary Structure (`native/libs/iosArm64/libbitchord_analysis.a` and `iosSimulatorArm64`)**:
   - `C:\MinGW\bin\ar.exe -t native/libs/iosArm64/libbitchord_analysis.a`
     - Return code: `1`
     - Verbatim stderr: `C:\MinGW\bin\ar.exe: D:\Bitchord\native\libs\iosArm64\libbitchord_analysis.a: Malformed archive`
   - In `scripts/generate_static_archives.py:188-230`:
     - `m2_name_field = f"{obj_name:<16}"` where `obj_name = "bitchord_ios_bridge.o"`.
     - `len("bitchord_ios_bridge.o") == 21` bytes, overflowing the 16-character field and creating a 65-byte `struct ar_hdr` (instead of 60 bytes).
     - `member2_offset` calculated at line 188 as `8 + 60 + symdef_data_len = 880`, omitting `m1_name_data` (`b"__.SYMDEF SORTED\x00\x00\x00\x00"`, 20 bytes). The actual starting offset of Member 2 is `900`.
     - Every ranlib index pointer is misaligned by 20 bytes.
2. **Defects in Kotlin iOS Spectrogram Buffer Capacity Calculations**:
   - In `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/MelSpectrogram.ios.kt:18-24`:
     ```kotlin
     val pad = 512
     val paddedLength = samples.size + 2 * pad
     val frames = (paddedLength - 1024) / hop + 1
     val totalFloats = frames * mels
     val outBuffer = FloatArray(totalFloats)
     ```
   - In `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/VocalSpectrogram.ios.kt:21-26`:
     ```kotlin
     val pad = fftSize / 2
     val paddedLength = left.size + 2 * pad
     val frames = (paddedLength - fftSize) / hop + 1
     val totalFloats = CHANNELS * bins * frames
     val outBuffer = FloatArray(totalFloats)
     ```
   - Both calculate `frames` exclusively using `samples.size` / `left.size`, disregarding `sampleRate` / `rate`.
   - In `native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp:320-366`:
     - C++ resamples input audio to target rates (22,050 Hz for Mel, 44,100 Hz for Vocal) via `bitchord::smart::Resample`.
     - When `sampleRate` = 11,025 Hz (2.0s audio = 22,050 samples), C++ upsamples to 44,100 samples, generating 101 frames (12,928 floats). Kotlin only allocates 51 frames (6,528 floats). C++ writes 6,400 floats (25,600 bytes) past buffer bounds.
     - When `rate` = 22,050 Hz (2.0s audio = 44,100 samples), C++ upsamples to 88,200 samples, generating 87 frames (258,738 floats). Kotlin only allocates 44 frames (130,856 floats). C++ writes 127,882 floats (499.5 KB) past buffer bounds.
3. **E2E Test Masking**:
   - In `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py:80-92`:
     ```python
     arm64_lib = os.path.join(repo_root, "native", "libs", "iosArm64", "libbitchord_analysis.a")
     sim_lib = os.path.join(repo_root, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")
     assert_true(os.path.exists(arm64_lib), f"Archive must exist at {arm64_lib}")
     assert_true(os.path.exists(sim_lib), f"Archive must exist at {sim_lib}")
     ```
     Only checked `os.path.exists()`.
   - `test_f07_mel_spectrogram_dimensions_contract` and `test_f07_vocal_spectrogram_dimensions_contract` only tested native sample rates (22050 Hz, 44100 Hz) with hardcoded dimensions.
   - `test_f07_limits.py` only checked sample rate ratio arithmetic (`96000 * (22050/96000) == 22050`), without testing buffer capacity vs resampled frames.
   - Running `python tests/e2e/run_e2e_tests.py` yielded:
     `Total: 280 | Passed: 280 | Failed: 0 | Errors: 0 | Result: ALL TESTS PASSED - 100% SUCCESS`
     masking both fatal defects.

---

## 2. Logic Chain

1. **Archive Corruption Masking**:
   - Observation 1 demonstrates that both committed static archive files are rejected by binutils `ar -t` as `Malformed archive` due to a 65-byte `struct ar_hdr` and 20-byte offset misalignment.
   - Observation 3 demonstrates that `test_f07_bridge_header_and_def_disk_artifacts` merely checks `os.path.exists()`. Since the files physically exist on disk, the test passed unconditionally.
   - Therefore, the test suite must perform in-process binary parsing (verifying magic `!<arch>\n`, exact 60-byte header length, matching trailer `` `\n ``, ranlib symbol offset parity `ran_off == actual_member2_offset`, Mach-O 64-bit ARM64 headers) as well as invoking `ar -t` and `nm -s` whenever available.
2. **Buffer Overflow Masking**:
   - Observation 2 demonstrates that non-native sample rates trigger upsampling in C++, expanding the frame count and output buffer requirement by $2\times$ to $4\times$.
   - Observation 3 demonstrates that existing E2E tests only evaluated single 1.0-second native sample rate cases (22050 Hz for Mel, 44100 Hz for Vocal).
   - Therefore, non-native rates were completely unexercised in the test suite, allowing the 25.6 KB and 499.5 KB heap memory corruptions to slip through undetected.
3. **Integration Strategy**:
   - By embedding multi-sample-rate checks across standard rates ($[8000, 11025, 16000, 22050, 44100, 48000, 96000]$ Hz) and durations ($[0.5, 1.0, 2.0, 3.5]$ s) into `test_f07_cpp_analyzer_bridge.py` and boundary tests into `test_f07_limits.py`, the test suite directly enforces the invariant that allocated capacity $\ge$ required resampled capacity.
   - Any future regression in static archive packaging or buffer calculation will immediately fail the E2E suite at build/test time.

---

## 3. Caveats

1. **Tool Availability**:
   - Standard `ar` / `nm` tools may not be pre-installed on every developer machine (e.g. clean Windows installations without MinGW). The proposed test suite employs a dual-layer approach: Layer 1 (pure Python binary inspection) is completely environment-independent and runs unconditionally in 100% of test runs. Layer 2 executes `ar -t` and `nm -s` conditionally when present.
2. **Kotlin/Native Execution on Windows**:
   - Because Windows hosts lack Xcode SDKs, Kotlin/Native `cinterop` linking is not natively invoked during Gradle builds on Windows. The E2E Python test harness serves as the primary barrier ensuring binary validity of the static archives and mathematical consistency of the bindings.

---

## 4. Conclusion & Proposed Test Suite Additions

The E2E test suite must be updated across `test_f07_cpp_analyzer_bridge.py` and `test_f07_limits.py` to add:
1. **`test_f07_static_archive_binary_integrity`**: Pure Python byte-level inspection of `!<arch>\n`, 60-byte headers, ranlib offsets, and Mach-O ARM64 structures for both `iosArm64` and `iosSimulatorArm64`.
2. **`test_f07_static_archive_tool_validation`**: Execution of `ar -t` and `nm -s` asserting returncode 0 and no `Malformed archive` errors.
3. **`test_f07_c_bridge_header_signatures`**: Comprehensive check of all 10 exported C ABI signatures, including frame query and resampling functions.
4. **`test_f07_multi_sample_rate_mel_spectrogram_dimensions`**: Parameterized validation of Mel spectrogram buffer capacity across 7 sample rates and 4 durations.
5. **`test_f07_multi_sample_rate_vocal_spectrogram_dimensions`**: Parameterized validation of Vocal spectrogram buffer capacity across 6 sample rates and 4 durations.
6. **`test_f07_boundary_static_archive_header_struct_sizes`**: Byte-for-byte struct sizing and boundary checks.
7. **`test_f07_boundary_spectrogram_extreme_sample_rates_buffer_overflow_prevention`**: Extreme upsampling/downsampling stress test proving zero buffer overflow and zero memory corruption.
8. **`test_f07_boundary_spectrogram_short_buffer_thresholds`**: Verifying safe fallback on short/empty buffers.
9. **`test_f07_boundary_resampler_buffer_capacity_guard`**: Resampler capacity bound verification.

Full code implementations and architectural details are documented in `D:\Bitchord\.agents\teamwork\explorer_m3_it2_3\analysis.md`.

---

## 5. Verification Method

1. **Verify Static Archive Malformation on Current Code**:
   ```pwsh
   C:\MinGW\bin\ar.exe -t native/libs/iosArm64/libbitchord_analysis.a
   C:\MinGW\bin\ar.exe -t native/libs/iosSimulatorArm64/libbitchord_analysis.a
   ```
   *Expected*: `Malformed archive` on current archives.
2. **Verify Bug Reproduction with Challenger Harness**:
   ```pwsh
   python tests/verify_m3_challenger.py
   ```
   *Expected*: Confirms 20-byte offset discrepancy, 65-byte header, and buffer overflows.
3. **Run E2E Test Suite on Remediated Code**:
   ```pwsh
   python tests/e2e/run_e2e_tests.py --feature 7 -v
   python tests/e2e/run_e2e_tests.py
   ```
   *Expected*: All tests pass with 100% success once worker agents apply the remediation.
