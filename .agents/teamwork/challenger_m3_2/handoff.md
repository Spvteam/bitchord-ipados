# Hard Handoff Report: Milestone M3 (WP-04: C++17 DSP Analyzer iOS Bridge)

**Agent**: `challenger_m3_2` (Empirical Challenger)  
**Roles**: critic, specialist  
**Milestone**: M3 (WP-04: C++17 DSP Analyzer iOS Bridge)  
**Parent Agent**: `8ceaa9c4-ff79-4a51-ab9e-9824605a03b5`  
**Working Directory**: `D:\Bitchord\.agents\teamwork\challenger_m3_2`  
**Date**: 2026-10-16  
**Empirical Verdict**: **REJECT**

---

## 1. Observation

### 1.1 Multi-Target Compilation & E2E Test Suite Execution
- **Command**:
  ```pwsh
  .\gradlew.bat :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileCommonMainKotlinMetadata :shared:compileKotlinJvm :desktopApp:compileKotlin --no-daemon
  ```
  **Output**:
  ```
  BUILD SUCCESSFUL in 1m 30s
  42 actionable tasks: 3 executed, 39 up-to-date
  ```
- **Command**:
  ```pwsh
  python tests/e2e/run_e2e_tests.py
  ```
  **Output**:
  ```
  Total: 280 | Passed: 280 | Failed: 0 | Errors: 0 | Time: 0.05s
  Result: ALL TESTS PASSED - 100% SUCCESS
  ```

---

### 1.2 Binary Integrity of Static Archives (`libbitchord_analysis.a`)
- **Inspection Command**:
  ```pwsh
  C:\MinGW\bin\ar.exe -t native/libs/iosArm64/libbitchord_analysis.a
  C:\MinGW\bin\ar.exe -t native/libs/iosSimulatorArm64/libbitchord_analysis.a
  ```
  **Verbatim Output**:
  ```
  C:\MinGW\bin\ar.exe: native/libs/iosArm64/libbitchord_analysis.a: Malformed archive
  C:\MinGW\bin\ar.exe: native/libs/iosSimulatorArm64/libbitchord_analysis.a: Malformed archive
  ```
- **Inspection Command (`nm`)**:
  ```pwsh
  C:\MinGW\bin\nm.exe -s native/libs/iosArm64/libbitchord_analysis.a
  ```
  **Verbatim Output**:
  ```
  Archive index:
  C:\MinGW\bin\nm.exe: bfd_get_elt_at_index: Malformed archive
  ```
- **Binary Structure Analysis in `scripts/generate_static_archives.py`**:
  1. In `scripts/generate_static_archives.py:188`:
     ```python
     symdef_data_unpadded = 4 + ran_size + 4 + str_size
     symdef_pad = 1 if (symdef_data_unpadded % 2 != 0) else 0
     symdef_data_len = symdef_data_unpadded + symdef_pad
     member2_offset = 8 + 60 + symdef_data_len
     ```
     However, at lines 199-202:
     ```python
     m1_name = "#1/20".ljust(16)
     m1_name_data = b"__.SYMDEF SORTED\x00\x00\x00\x00"  # 20 bytes!
     m1_total_data = m1_name_data + symdef_data
     ```
     `m1_name_data` prepends 20 bytes to Member 1's payload. The true offset where Member 2 starts is `8 + 60 + 20 + symdef_data_len` = `900`. But `member2_offset` was written into every ranlib index entry as `880`. The pointer is offset by 20 bytes into Member 1's string table.
  2. In `scripts/generate_static_archives.py:219-230`:
     ```python
     m2_name_field = f"{obj_name:<16}"
     ```
     where `obj_name = "bitchord_ios_bridge.o"` (length = 21). In Python, `f"{'bitchord_ios_bridge.o':<16}"` produces 21 characters, NOT 16.
     Consequently, Member 2's `ar_hdr` header length is 65 bytes instead of the required 60-byte POSIX/BSD `struct ar_hdr` specification.

---

### 1.3 Memory Safety & Spectrogram Buffer Sizing Stress Tests
- **In `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/MelSpectrogram.ios.kt:16-34`**:
  ```kotlin
  actual fun compute(samples: FloatArray, sampleRate: Double): Spectrogram? {
      if (samples.isEmpty() || samples.size < 1024) return null
      val pad = 512
      val paddedLength = samples.size + 2 * pad
      if (paddedLength < 1024) return null
      val frames = (paddedLength - 1024) / hop + 1
      val totalFloats = frames * mels
      val outBuffer = FloatArray(totalFloats)

      samples.usePinned { pinnedIn ->
          outBuffer.usePinned { pinnedOut ->
              bitchord_compute_mel_spectrogram(
                  pinnedIn.addressOf(0),
                  samples.size,
                  sampleRate.toInt(),
                  pinnedOut.addressOf(0)
              )
          }
      }
      return Spectrogram(values = outBuffer, frames = frames, mels = mels)
  }
  ```
  - Note: `frames` and `outBuffer` size are calculated strictly from `samples.size` assuming 22050 Hz.
  - However, `sampleRate` can be passed by callers (e.g. 11025.0 Hz).
  - In `native/analyzer/ios_c_bridge/bitchord_ios_bridge.cpp:320-343`:
    When `sample_rate != 22050`, `bitchord_compute_mel_spectrogram` calls `bitchord::smart::Resample(input, sample_rate, 22050.0)`.
    If `sample_rate` = 11025.0 Hz and `samples.size` = 22050 (2 seconds):
    - Kotlin allocates: `(22050 / 441 + 1) * 128 = 51 * 128 = 6,528 floats` (26,112 bytes).
    - C++ resamples 22,050 samples to 44,100 samples at 22,050 Hz.
    - C++ computes 101 frames and writes: `101 * 128 = 12,928 floats` (51,712 bytes) via `memcpy(out_buffer, spec.values.data(), 12928 * 4)`.
    - **Result**: Heap buffer overflow of 6,400 floats (25,600 bytes) past the end of the pinned `outBuffer`.

- **In `shared/src/iosMain/kotlin/com/music/bitchord/playback/smart/VocalSpectrogram.ios.kt:18-37`**:
  ```kotlin
  actual fun compute(left: FloatArray, right: FloatArray, rate: Double): Spectrogram? {
      if (left.isEmpty() || left.size != right.size || left.size < 4096) return null
      val pad = fftSize / 2
      val paddedLength = left.size + 2 * pad
      if (paddedLength < fftSize) return null
      val frames = (paddedLength - fftSize) / hop + 1
      val totalFloats = CHANNELS * bins * frames
      val outBuffer = FloatArray(totalFloats)
      ...
  ```
  - When `rate` = 22050.0 Hz and `left.size` = 44100 (2.0s audio):
    - Kotlin allocates: `2 * 1487 * 44 = 130,856 floats` (523,424 bytes).
    - C++ resamples 44,100 samples from 22,050 Hz to 44,100 Hz, producing 88,200 samples.
    - C++ produces 87 frames and writes up to index `(1 * 1487 + 1486) * 87 + 86 = 258,737` (258,738 floats = 1,034,952 bytes).
    - **Result**: Heap buffer overflow of 127,882 floats (511,528 bytes = 499.5 KB) past the end of the pinned array.

- **In Downsampling Scenario (e.g. `MelSpectrogram.compute` with 44100.0 Hz audio)**:
  - Kotlin allocates 101 frames based on sample count.
  - C++ resamples down to 22050 Hz, producing 51 frames (6,528 floats).
  - C++ copies only 51 frames into `outBuffer`.
  - Returned `Spectrogram` reports `frames = 101` (duration 2.02s), but frames 51..100 are completely unpopulated (zeros).

- **Execution of Challenger Reproduction Harness (`python tests/verify_m3_challenger.py`)**:
  ```
  ============================================================
  TEST 1: Static Archive Binary Integrity (iosArm64 & iosSimulatorArm64)
  ============================================================
  [*] Inspecting native\libs\iosArm64\libbitchord_analysis.a:
    ar -t exit code: 1
    ar -t stderr: C:\MinGW\bin\ar.exe: D:\Bitchord\native\libs\iosArm64\libbitchord_analysis.a: Malformed archive
    [CONFIRMED BUG] Standard 'ar' rejects archive as 'Malformed archive'!
    Member 1 payload size reported in header: 832 bytes
    First ranlib entry points to offset: 880
    Member 2 actual starting offset in file: 900
    Offset discrepancy (actual - pointer): 20 bytes (omitted 20-byte extended name!)
    Member 2 ar_hdr actual length: 65 bytes (POSIX/BSD specification requires EXACTLY 60 bytes!)
    [CONFIRMED BUG] Member 2 ar_hdr is 65 bytes instead of 60 bytes!

  [*] Inspecting native\libs\iosSimulatorArm64\libbitchord_analysis.a:
    ar -t exit code: 1
    ar -t stderr: C:\MinGW\bin\ar.exe: D:\Bitchord\native\libs\iosSimulatorArm64\libbitchord_analysis.a: Malformed archive
    [CONFIRMED BUG] Standard 'ar' rejects archive as 'Malformed archive'!
    Member 1 payload size reported in header: 832 bytes
    First ranlib entry points to offset: 880
    Member 2 actual starting offset in file: 900
    Offset discrepancy (actual - pointer): 20 bytes (omitted 20-byte extended name!)
    Member 2 ar_hdr actual length: 65 bytes (POSIX/BSD specification requires EXACTLY 60 bytes!)
    [CONFIRMED BUG] Member 2 ar_hdr is 65 bytes instead of 60 bytes!

  ============================================================
  TEST 2: Mel & Vocal Spectrogram Buffer Sizing vs Resampling
  ============================================================
  [*] Scenario 1: MelSpectrogram.compute with sampleRate=11025.0 Hz (2.0s audio):
    Kotlin allocates: 51 frames * 128 mels = 6528 floats (26112 bytes)
    C++ resamples to 22050 Hz and writes: 101 frames * 128 mels = 12928 floats (51712 bytes)
    [CONFIRMED BUFFER OVERFLOW] Overwriting 6400 floats (25600 bytes) past end of buffer!

  [*] Scenario 2: VocalSpectrogram.compute with rate=22050.0 Hz (2.0s audio):
    Kotlin allocates: 2 * 1487 * 44 frames = 130856 floats (523424 bytes)
    C++ resamples to 44100 Hz and writes: 2 * 1487 * 87 frames = 258738 floats (1034952 bytes)
    [CONFIRMED BUFFER OVERFLOW] Overwriting 127882 floats (511528 bytes = 499.5 KB) past end of buffer!

  ============================================================
  [VERDICT] ALL CHALLENGE FINDINGS EMPIRICALLY CONFIRMED.
  ============================================================
  ```

---

## 2. Logic Chain

1. **Static Archive Malformation**:
   - POSIX/BSD archive format dictates that every member header is exactly 60 bytes, formatted as `struct ar_hdr { char ar_name[16]; ... };`. Filenames exceeding 16 bytes must use `#1/<len>` extended naming.
   - `scripts/generate_static_archives.py` formatted `m2_name_field` using `f"{'bitchord_ios_bridge.o':<16}"`, which emitted 21 bytes without `#1/21` syntax, breaking the 60-byte struct requirement and producing a 65-byte header (Observation 1.2).
   - In addition, Member 1's symbol table calculation computed `member2_offset` as `8 + 60 + symdef_data_len = 880`, omitting the 20-byte `m1_name_data` header `__.SYMDEF SORTED\0\0\0\0`. Thus, all ranlib symbol pointers point 20 bytes before Member 2.
   - Standard tools `ar` and `nm` verify this fact directly by failing with `Malformed archive` on both committed static archives (`native/libs/iosArm64/libbitchord_analysis.a` and `native/libs/iosSimulatorArm64/libbitchord_analysis.a`).
   - Consequently, when linked on macOS / iOS Xcode toolchains, compilation or linking will fail immediately with archive corruption errors.

2. **Spectrogram Buffer Under-Allocation and Memory Corruption**:
   - The C bridge functions `bitchord_compute_mel_spectrogram` and `bitchord_compute_vocal_spectrogram` accept arbitrary `sample_rate` inputs and internally resample to their target calibration frequencies (22,050 Hz and 44,100 Hz respectively) before computing STFT/mel frames (Observation 1.3).
   - However, `MelSpectrogram.ios.kt` and `VocalSpectrogram.ios.kt` calculate output buffer capacity assuming the input sample count is already at the target sample rate, ignoring the ratio `targetRate / inputRate`.
   - When audio at lower sample rates (e.g., 11,025 Hz audio for Mel, or 22,050 Hz audio for Vocal) is passed to `compute()`, the C++ resampler upsamples the signal, generating roughly 2x more frames than Kotlin allocated.
   - The C++ functions write directly into `pinnedOut.addressOf(0)` without buffer capacity bounds checking, writing 25.6 KB (Mel) and 499.5 KB (Vocal) past the bounds of the pinned Kotlin heap array, causing fatal memory corruption / SIGSEGV (Observation 1.3).

3. **E2E Test Masking**:
   - The E2E test `test_f07_cpp_analyzer_bridge.py` passed because it asserted static hardcoded calculations (`num_frames = 100`, `mel_bins = 128`, `assert_equal(flattened_size, 12800)`) and only checked `os.path.exists()` for the archive files rather than verifying archive binary validity with `ar` / `nm` or testing non-native sample rates.

---

## 3. Caveats

- On Windows hosts without macOS Xcode CLI tools, `cinterop` execution in Gradle (`compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`) is skipped by Kotlin/Native design; Gradle reports `BUILD SUCCESSFUL` because tasks are skipped, which masked the archive malformation during the build step.
- The C++ DSP mathematical core algorithms (`MelFilterbank`, `ComputeBeatSpectrogram`, `Resample`) are mathematically sound and IEEE-754 compliant when invoked with correctly pre-allocated buffers. The flaws are exclusively in archive packaging and Kotlin buffer dimension calculation.

---

## 4. Conclusion & Required Worker Mitigations

**VERDICT: REJECT**

Milestone M3 cannot be approved due to 2 CRITICAL issues that would cause link-time failures on iOS/macOS CI and fatal memory corruption at runtime:
1. **Critical Archive Corruption**: Both `iosArm64` and `iosSimulatorArm64` static archives are malformed.
   - *Mitigation*: In `scripts/generate_static_archives.py`, either truncate Member 2 object name to <= 16 chars (e.g. `bridge.o`) or use BSD `#1/21` extended naming. Correct `member2_offset` to include `len(m1_name_data)` (i.e. `8 + 60 + len(m1_name_data) + symdef_data_len`). Regenerate and commit valid archives verified by `ar -t`.
2. **Critical Heap Buffer Overflow in Kotlin iOS Bindings**:
   - *Mitigation*: In `MelSpectrogram.ios.kt` and `VocalSpectrogram.ios.kt`:
     Either use the exposed C query functions `bitchord_mel_spectrogram_frame_count(samples.size, sampleRate.toInt())` / `bitchord_vocal_spectrogram_frame_count(left.size, rate.toInt())` to size `outBuffer`, or properly adjust `samples.size` by `(MelSpectrogram.sampleRate / sampleRate)` before calculating `frames`.
   - In `bitchord_ios_bridge.cpp`: Ensure `bitchord_compute_mel_spectrogram` and `bitchord_compute_vocal_spectrogram` document or check buffer bounds so un-resampled queries do not overwrite caller buffers.

---

## 5. Verification Method

To independently verify these findings:
1. **Verify Static Archive Malformation**:
   ```pwsh
   C:\MinGW\bin\ar.exe -t native/libs/iosArm64/libbitchord_analysis.a
   C:\MinGW\bin\ar.exe -t native/libs/iosSimulatorArm64/libbitchord_analysis.a
   ```
   *Observed*: `Malformed archive` error.
2. **Execute Challenger Reproduction Harness**:
   ```pwsh
   python tests/verify_m3_challenger.py
   ```
   *Observed*: Outputs exact offset discrepancy (20 bytes), header size discrepancy (65 vs 60 bytes), and reproduces the 25.6 KB and 499.5 KB buffer overflows.
3. **Run Existing E2E & Multi-Target Build**:
   ```pwsh
   python tests/e2e/run_e2e_tests.py
   .\gradlew.bat :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileCommonMainKotlinMetadata :shared:compileKotlinJvm :desktopApp:compileKotlin --no-daemon
   ```
   *Observed*: Passes, confirming the flaws were hidden from upstream tests.
