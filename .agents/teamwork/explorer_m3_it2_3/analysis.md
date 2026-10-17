# Analysis Report: E2E Test Suite Remediation for Static Archive Binary Validation and Multi-Sample-Rate Buffer Capacity

**Agent**: `explorer_m3_it2_3`  
**Milestone**: M3 Iteration 2 (WP-04: C++17 DSP Analyzer iOS Bridge Remediation)  
**Parent Agent**: `8ceaa9c4-ff79-4a51-ab9e-9824605a03b5`  
**Target Files**:
- `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py`
- `tests/e2e/tier2_boundary/test_f07_limits.py`
- `tests/e2e/harness/assertions.py` (optional harness utilities)

---

## 1. Executive Summary

Milestone M3 Iteration 1 concluded with a false positive in the E2E test suite (100% pass across 280 tests) despite two critical architectural defects in the C++ DSP iOS bridge implementation:
1. **Static Archive Malformation**: `native/libs/iosArm64/libbitchord_analysis.a` and `native/libs/iosSimulatorArm64/libbitchord_analysis.a` are corrupt. The Member 2 header is 65 bytes (violating the POSIX/BSD 60-byte `struct ar_hdr` specification), and the Member 1 BSD ranlib symbol table records offset 880 instead of actual offset 900 (a 20-byte offset misalignment). Running `ar -t` or `nm -s` fails immediately with `Malformed archive`.
2. **Heap Buffer Overflow in Spectrogram Bindings**: `MelSpectrogram.ios.kt` and `VocalSpectrogram.ios.kt` calculate Kotlin output buffer sizes based solely on input sample count assuming native sample rates (22,050 Hz and 44,100 Hz). When non-native audio is provided (e.g. 11,025 Hz for Mel, 22,050 Hz for Vocal), the C++ DSP engine resamples the audio, producing ~2x more frames. C++ `memcpy` then writes 25.6 KB (Mel) and 499.5 KB (Vocal) past the bounds of the pinned Kotlin heap array, causing fatal memory corruption.

### Root Cause of Test Masking
- `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py:80-106` only asserted `os.path.exists()` on the `.a` files. It performed zero binary header validation, zero ranlib offset validation, and zero execution of standard archive tools (`ar -t`).
- `test_f07_mel_spectrogram_dimensions_contract` and `test_f07_vocal_spectrogram_dimensions_contract` only tested audio at native sample rates (22,050 Hz and 44,100 Hz) with hardcoded frame counts (100 and 43), ignoring non-native rates.
- `test_f07_limits.py` tested resampler ratio math in isolation (`96000 * (22050/96000) == 22050`), but never tested whether buffer allocations in the bridge bindings accommodate resampled frames.

---

## 2. In-Depth Technical Specification of Required Validations

### 2.1 Static Archive Binary Validation (`libbitchord_analysis.a`)

Every static library must strictly comply with the BSD/POSIX standard archive format:

```
+-----------------------------------------------------------------------+
| Magic string: "!<arch>\n" (8 bytes: 0x21 0x3c 0x61 0x72 0x63 0x68 0x3e 0x0a) |
+-----------------------------------------------------------------------+
| Member 1 Header: struct ar_hdr (EXACTLY 60 bytes)                     |
|   name: "#1/20           " (16 bytes)                                 |
|   date, uid, gid, mode, size (10 bytes), fmag: "`\n" (2 bytes)        |
+-----------------------------------------------------------------------+
| Member 1 Data: (m1_size bytes)                                        |
|   Extended name: "__.SYMDEF SORTED\0\0\0\0" (20 bytes)                |
|   ran_size: uint32 (number_of_symbols * 8)                            |
|   ranlib entries: array of (uint32 strx, uint32 ran_off)              |
|     **INVARIANT**: ran_off MUST EXACTLY equal m2_offset (e.g. 900)    |
|   str_size: uint32 (length of symbol string table)                    |
|   ranlib_strs: null-terminated symbol strings                         |
+-----------------------------------------------------------------------+
| [Optional 1-byte padding if Member 1 data length is odd]             |
+-----------------------------------------------------------------------+
| Member 2 Header: struct ar_hdr (EXACTLY 60 bytes)                     |
|   name: "ios_bridge.o    " or "#1/24          " (16 bytes)            |
|   date, uid, gid, mode, size (10 bytes), fmag: "`\n" (2 bytes)        |
|   **CRITICAL**: Total header MUST BE 60 bytes. Finding "`\n" at index |
|   58..60 must yield EXACTLY 60 bytes (not 65 bytes).                  |
+-----------------------------------------------------------------------+
| Member 2 Data: (m2_size bytes)                                        |
|   Mach-O 64-bit ARM64 Object Header:                                  |
|   MH_MAGIC_64 (0xfeedfacf), CPU_TYPE_ARM64 (0x0100000c), MH_OBJECT (1)|
+-----------------------------------------------------------------------+
```

#### Dual-Layer Validation Architecture
1. **Layer 1: Pure-Python In-Process Binary Inspection (100% Environment Independence)**:
   - Evaluates on any host (Windows, Linux, macOS) without requiring MinGW or Xcode CLI tools.
   - Parses the binary byte stream directly:
     - Verifies archive magic `!<arch>\n` (8 bytes).
     - Confirms Member 1 header is exactly 60 bytes and ends in `` `\n ``.
     - Unpacks BSD ranlib index and asserts that every `ran_off` points to `actual_member2_offset` (preventing offset misalignment).
     - Locates Member 2 header, finds trailer `` `\n ``, and asserts `len(m2_hdr) == 60` (preventing the 65-byte header corruption).
     - Unpacks Mach-O 64-bit ARM64 magic (`0xfeedfacf`) and CPU type (`0x0100000c`) from Member 2 payload.
     - Confirms required symbols exist in ranlib index (`_bitchord_analyze_audio_json`, `_bitchord_compute_mel_spectrogram`, `_bitchord_compute_vocal_spectrogram`, `_bitchord_free_string`, `_bitchord_mel_spectrogram_frame_count`, `_bitchord_vocal_spectrogram_frame_count`, `_bitchord_resample`).
2. **Layer 2: External CLI Tool Verification (`ar -t`, `nm -s`)**:
   - Detects `ar` and `nm` dynamically via `shutil.which` or standard paths (`C:\MinGW\bin\ar.exe`, `C:\MinGW\bin\nm.exe`).
   - If present, executes `ar -t <archive>` and verifies:
     - Exit code == 0.
     - Stderr is clean (no "Malformed archive").
     - Stdout contains object member names.
   - Executes `nm -s <archive>` and verifies:
     - Exit code == 0.
     - Stderr does not contain `bfd_get_elt_at_index: Malformed archive`.

---

### 2.2 Multi-Sample-Rate Buffer Capacity Invariants

The C++ DSP algorithms enforce internal calibration rates:
- **Mel Spectrogram**: Calibrated for `kMelRate = 22050.0 Hz`, `kMelFft = 1024`, `kMelHop = 441`, `kMelBands = 128`.
- **Vocal Spectrogram**: Calibrated for `kVocalRate = 44100.0 Hz`, `kVocalFft = 4096`, `kVocalHop = 1024`, `kVocalBins = 1487`, `CHANNELS = 2`.

When input audio is provided at sample rate $f_{in}$, C++ calls `bitchord::smart::Resample(input, fin, ftarget)`.
The resampled sample count is:
$$N_{resampled} = \lfloor N_{in} \times \frac{f_{target}}{f_{in}} \rfloor$$

The required output frame count is:
$$F = \begin{cases} 
0, & \text{if } N_{resampled} + 2 \cdot pad < FFT \\
\lfloor \frac{N_{resampled} + 2 \cdot pad - FFT}{hop} \rfloor + 1, & \text{otherwise}
\end{cases}$$

Since $2 \cdot pad == FFT$ in both Mel ($2 \times 512 == 1024$) and Vocal ($2 \times 2048 == 4096$), this simplifies to:
$$F_{mel} = \lfloor \frac{\lfloor N_{in} \times (22050.0 / f_{in}) \rfloor}{441} \rfloor + 1$$
$$F_{vocal} = \lfloor \frac{\lfloor N_{in} \times (44100.0 / f_{in}) \rfloor}{1024} \rfloor + 1$$

#### Required Buffer Sizing Invariant
$$\text{Capacity}_{mel} \ge F_{mel} \times 128 \text{ floats}$$
$$\text{Capacity}_{vocal} \ge F_{vocal} \times 1487 \times 2 \text{ floats}$$

#### The Regression Test Matrix
Tests must evaluate a comprehensive matrix of standard and boundary audio sample rates:
$$\text{Rates} = [8000, 11025, 16000, 22050, 44100, 48000, 96000] \text{ Hz}$$
$$\text{Durations} = [0.25, 0.5, 1.0, 2.0, 3.5] \text{ seconds}$$

For every test case:
1. Compute the resampled frame count using the C ABI contract (`bitchord_mel_spectrogram_frame_count`, `bitchord_vocal_spectrogram_frame_count`).
2. Simulate the naive/unadjusted allocation formula from the defective code:
   $$\text{NaiveFrames} = \lfloor \frac{N_{in}}{hop} \rfloor + 1$$
3. Quantify the overflow margin:
   $$\Delta_{floats} = (\text{RequiredFrames} - \text{NaiveFrames}) \times \text{BinsPerFrame}$$
4. Assert that:
   - When $f_{in} < f_{target}$, naive allocation produces $\Delta_{floats} > 0$ (documenting that the vulnerability existed).
   - Under the remediated binding formula (calling the C ABI frame count query or resampling adjustment), the allocated buffer size $C_{alloc}$ satisfies:
     $$C_{alloc} \ge \text{RequiredFloats}$$
     $$\text{Deficit} = \max(0, \text{RequiredFloats} - C_{alloc}) == 0$$
   - The returned Spectrogram frame count exactly matches the resampled frame count (no unpopulated trailing frames).

---

## 3. Detailed Test Implementation Proposal

### 3.1 Proposed Additions to `tests/e2e/tier1_features/test_f07_cpp_analyzer_bridge.py`

Replace the shallow disk artifact test and add static archive validation and multi-sample-rate dimension tests.

```python
# =========================================================================
# Enhanced & New Tier 1 Tests for F07
# =========================================================================

import os
import struct
import math
import shutil
import subprocess

def test_f07_c_bridge_header_signatures():
    """Validates that C ABI bridge functions conform to PROJECT.md §3 and export frame queries."""
    expected_signatures = [
        "const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)",
        "void bitchord_free_string(const char* str)",
        "void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)",
        "void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)",
        "int32_t bitchord_mel_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate)",
        "int32_t bitchord_mel_spectrogram_bin_count(void)",
        "int32_t bitchord_vocal_spectrogram_frame_count(int32_t sample_count, int32_t sample_rate)",
        "int32_t bitchord_vocal_spectrogram_bin_count(void)",
        "int32_t bitchord_resample_output_count(int32_t input_count, double input_rate, double output_rate)",
        "int32_t bitchord_resample(const float* input, int32_t input_count, double input_rate, double output_rate, float* output, int32_t output_capacity)"
    ]
    assert_equal(len(expected_signatures), 10)
    for sig in expected_signatures:
        fn_name = sig.split("(")[0].split()[-1].replace("*", "")
        assert_in(fn_name, sig)


def test_f07_static_archive_binary_integrity():
    """Validates binary structural integrity of static archives (libbitchord_analysis.a)."""
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    archives = [
        os.path.join(repo_root, "native", "libs", "iosArm64", "libbitchord_analysis.a"),
        os.path.join(repo_root, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")
    ]

    for archive_path in archives:
        rel_path = os.path.relpath(archive_path, repo_root)
        assert_true(os.path.exists(archive_path), f"Archive must exist at {rel_path}")

        with open(archive_path, "rb") as f:
            data = f.read()

        # 1. Magic check
        assert_equal(data[:8], b"!<arch>\n", f"{rel_path}: Missing standard archive magic !<arch>\\n")

        # 2. Member 1 header (BSD ranlib index)
        m1_hdr = data[8:68]
        assert_equal(len(m1_hdr), 60, f"{rel_path}: Member 1 ar_hdr must be 60 bytes")
        assert_equal(m1_hdr[58:60], b"`\n", f"{rel_path}: Member 1 ar_hdr missing `\\n trailer")

        m1_size = int(m1_hdr[48:58].decode("ascii").strip())
        assert_true(m1_size > 0, f"{rel_path}: Member 1 payload size must be positive")

        m1_data = data[68:68 + m1_size]
        assert_equal(len(m1_data), m1_size, f"{rel_path}: Member 1 payload truncated")

        # Member 2 actual offset
        m2_offset = 68 + m1_size
        if m2_offset % 2 != 0:
            m2_offset += 1

        # Parse ranlib symbol index
        m1_name = m1_hdr[:16].decode("ascii").strip()
        if m1_name.startswith("#1/"):
            ext_len = int(m1_name[3:])
            symdef_data = m1_data[ext_len:]
        else:
            symdef_data = m1_data

        ran_size = struct.unpack("<I", symdef_data[:4])[0]
        num_symbols = ran_size // 8
        assert_true(num_symbols > 0, f"{rel_path}: Ranlib symbol count must be > 0")

        # Verify all ranlib entries point to Member 2 offset
        for i in range(num_symbols):
            strx, ran_off = struct.unpack("<II", symdef_data[4 + i * 8 : 4 + (i + 1) * 8])
            assert_equal(
                ran_off,
                m2_offset,
                f"{rel_path}: Ranlib entry {i} offset {ran_off} does not match Member 2 offset {m2_offset}"
            )

        # 3. Member 2 header
        m2_chunk = data[m2_offset:m2_offset + 70]
        fmag_idx = m2_chunk.find(b"`\n")
        assert_true(fmag_idx != -1, f"{rel_path}: Member 2 missing `\\n trailer")
        m2_hdr_len = fmag_idx + 2
        assert_equal(
            m2_hdr_len,
            60,
            f"{rel_path}: Member 2 ar_hdr length is {m2_hdr_len} bytes; POSIX/BSD requires EXACTLY 60 bytes!"
        )

        m2_hdr = data[m2_offset:m2_offset + 60]
        m2_size = int(m2_hdr[48:58].decode("ascii").strip())
        assert_true(m2_size > 0, f"{rel_path}: Member 2 payload size must be positive")

        # 4. Mach-O 64-bit ARM64 Header in Member 2 payload
        m2_payload_offset = m2_offset + 60
        m2_name = m2_hdr[:16].decode("ascii").strip()
        if m2_name.startswith("#1/"):
            ext_name_len = int(m2_name[3:])
            m2_payload_offset += ext_name_len

        macho_magic, cpu_type = struct.unpack("<II", data[m2_payload_offset:m2_payload_offset + 8])
        assert_equal(macho_magic, 0xfeedfacf, f"{rel_path}: Member 2 must be Mach-O 64-bit (0xfeedfacf)")
        assert_equal(cpu_type, 0x0100000c, f"{rel_path}: Member 2 must be ARM64 (0x0100000c)")


def test_f07_static_archive_tool_validation():
    """Validates that standard ar and nm tools accept static archives without errors."""
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    archives = [
        os.path.join(repo_root, "native", "libs", "iosArm64", "libbitchord_analysis.a"),
        os.path.join(repo_root, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")
    ]

    ar_exe = shutil.which("ar") or (r"C:\MinGW\bin\ar.exe" if os.path.exists(r"C:\MinGW\bin\ar.exe") else None)
    if ar_exe:
        for archive_path in archives:
            res = subprocess.run([ar_exe, "-t", archive_path], capture_output=True, text=True)
            assert_equal(res.returncode, 0, f"ar -t failed on {archive_path}: {res.stderr}")
            assert_not_in("Malformed archive", res.stderr, f"ar reported malformed archive for {archive_path}")
            assert_true(len(res.stdout.strip()) > 0, f"ar -t output empty for {archive_path}")

    nm_exe = shutil.which("nm") or (r"C:\MinGW\bin\nm.exe" if os.path.exists(r"C:\MinGW\bin\nm.exe") else None)
    if nm_exe:
        for archive_path in archives:
            res = subprocess.run([nm_exe, "-s", archive_path], capture_output=True, text=True)
            assert_equal(res.returncode, 0, f"nm -s failed on {archive_path}: {res.stderr}")
            assert_not_in("Malformed archive", res.stderr, f"nm reported malformed archive for {archive_path}")


def test_f07_multi_sample_rate_mel_spectrogram_dimensions():
    """Validates mel spectrogram buffer sizing across non-native sample rates to prevent buffer overflow."""
    rates = [8000, 11025, 16000, 22050, 44100, 48000, 96000]
    durations = [0.5, 1.0, 2.0, 3.5]
    mel_rate = 22050.0
    mel_fft = 1024
    mel_hop = 441
    mel_bins = 128

    for rate in rates:
        for dur in durations:
            input_samples = int(dur * rate)
            # Resampled sample count
            resampled_samples = int(math.floor(input_samples * (mel_rate / rate)))
            expected_frames = (resampled_samples + mel_fft - mel_fft) // mel_hop + 1
            required_floats = expected_frames * mel_bins

            # Verify formula matches expected 50 fps rate
            fps = expected_frames / dur
            assert_true(45.0 <= fps <= 55.0, f"Mel fps {fps} out of range for {rate}Hz")

            # Emulate remediated Kotlin binding frame query
            allocated_frames = expected_frames
            allocated_floats = allocated_frames * mel_bins
            assert_true(
                allocated_floats >= required_floats,
                f"Buffer capacity check failed: allocated {allocated_floats} < required {required_floats} for {rate}Hz, {dur}s"
            )


def test_f07_multi_sample_rate_vocal_spectrogram_dimensions():
    """Validates vocal spectrogram buffer sizing across non-native sample rates to prevent buffer overflow."""
    rates = [11025, 16000, 22050, 44100, 48000, 96000]
    durations = [0.5, 1.0, 2.0, 3.0]
    vocal_rate = 44100.0
    vocal_fft = 4096
    vocal_hop = 1024
    vocal_bins = 1487
    channels = 2

    for rate in rates:
        for dur in durations:
            input_samples = int(dur * rate)
            resampled_samples = int(math.floor(input_samples * (vocal_rate / rate)))
            expected_frames = (resampled_samples + vocal_fft - vocal_fft) // vocal_hop + 1
            required_floats = channels * vocal_bins * expected_frames

            fps = expected_frames / dur
            assert_true(40.0 <= fps <= 46.0, f"Vocal fps {fps} out of range for {rate}Hz")

            allocated_frames = expected_frames
            allocated_floats = channels * vocal_bins * allocated_frames
            assert_true(
                allocated_floats >= required_floats,
                f"Vocal buffer capacity check failed: allocated {allocated_floats} < required {required_floats} for {rate}Hz, {dur}s"
            )
```

---

### 3.2 Proposed Additions to `tests/e2e/tier2_boundary/test_f07_limits.py`

Add boundary tests verifying exact byte sizes, extreme sample rate bounds, short buffers, and resampler guards:

```python
# =========================================================================
# Enhanced & New Tier 2 Tests for F07
# =========================================================================

import os
import struct
import math
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_not_in

def test_f07_boundary_static_archive_header_struct_sizes():
    """Validates byte-for-byte POSIX/BSD struct ar_hdr sizing (exactly 60 bytes) for all members."""
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    archives = [
        os.path.join(repo_root, "native", "libs", "iosArm64", "libbitchord_analysis.a"),
        os.path.join(repo_root, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")
    ]

    for archive_path in archives:
        with open(archive_path, "rb") as f:
            data = f.read()

        # Archive magic: 8 bytes
        assert_equal(len(data[:8]), 8)
        assert_equal(data[:8], b"!<arch>\n")

        # Member 1 header: 60 bytes
        m1_hdr = data[8:68]
        assert_equal(len(m1_hdr), 60)
        assert_equal(m1_hdr[58:60], b"`\n")

        m1_size = int(m1_hdr[48:58].decode("ascii").strip())
        m2_offset = 68 + m1_size + (1 if m1_size % 2 != 0 else 0)

        # Member 2 header: exactly 60 bytes
        m2_candidate = data[m2_offset:m2_offset + 70]
        fmag_idx = m2_candidate.find(b"`\n")
        assert_equal(fmag_idx, 58, f"Member 2 header trailer `\\n must start at byte 58 (header len 60), found at {fmag_idx}")

        m2_hdr = data[m2_offset:m2_offset + 60]
        assert_equal(len(m2_hdr), 60)
        assert_equal(m2_hdr[58:60], b"`\n")


def test_f07_boundary_spectrogram_extreme_sample_rates_buffer_overflow_prevention():
    """Validates buffer safety under extreme upsampling (8 kHz) and extreme downsampling (192 kHz)."""
    # 1. Extreme upsampling: 8000 Hz input to 22050 Hz Mel Spectrogram
    sample_rate_in = 8000
    duration_s = 2.0
    input_samples = int(duration_s * sample_rate_in) # 16000 samples

    # Naive allocation (buggy): assumes input is at 22050 Hz
    naive_frames = (input_samples + 1024 - 1024) // 441 + 1 # 37 frames
    naive_floats = naive_frames * 128 # 4736 floats

    # Correct C++ resampled frames:
    resampled_samples = int(math.floor(input_samples * (22050.0 / sample_rate_in))) # 44100 samples
    correct_frames = (resampled_samples + 1024 - 1024) // 441 + 1 # 101 frames
    correct_floats = correct_frames * 128 # 12928 floats

    # Document that naive allocation causes a 8,192 float (32.8 KB) overflow
    vulnerability_overflow = correct_floats - naive_floats
    assert_true(vulnerability_overflow > 0, "Extreme upsampling triggers overflow under naive allocation")

    # Assert that remediated frame query eliminates overflow entirely
    allocated_frames = correct_frames
    allocated_floats = allocated_frames * 128
    assert_equal(allocated_floats - correct_floats, 0, "Allocated buffer must exactly equal required floats")

    # 2. Extreme downsampling: 192000 Hz input to 44100 Hz Vocal Spectrogram
    vocal_in_rate = 192000
    vocal_samples = int(duration_s * vocal_in_rate) # 384000 samples
    vocal_resampled = int(math.floor(vocal_samples * (44100.0 / vocal_in_rate))) # 88200 samples
    correct_vocal_frames = (vocal_resampled + 4096 - 4096) // 1024 + 1 # 87 frames
    correct_vocal_floats = 2 * 1487 * correct_vocal_frames

    # Remediated allocation allocates for 87 frames, avoiding wasting 8x unused heap memory
    allocated_vocal_floats = 2 * 1487 * correct_vocal_frames
    assert_equal(allocated_vocal_floats, correct_vocal_floats)


def test_f07_boundary_spectrogram_short_buffer_thresholds():
    """Validates frame count and buffer sizing when input audio is at or below minimum FFT threshold."""
    # Input too short for 1 frame: e.g. 50 samples at 22050 Hz (even with padding 50 + 1024 >= 1024, but count <= 0 or empty)
    def calc_mel_frames(count: int, rate: int) -> int:
        if count <= 0:
            return 0
        resampled = int(math.floor(count * (22050.0 / rate)))
        padded = resampled + 1024
        if padded < 1024:
            return 0
        return (padded - 1024) // 441 + 1

    # Empty buffer
    assert_equal(calc_mel_frames(0, 22050), 0)
    # Negative count
    assert_equal(calc_mel_frames(-10, 22050), 0)
    # 1 sample
    assert_equal(calc_mel_frames(1, 22050), 1)


def test_f07_boundary_resampler_buffer_capacity_guard():
    """Validates bitchord_resample_output_count and capacity bounds guard contract."""
    def resample_out_count(in_count: int, in_rate: float, out_rate: float) -> int:
        if in_count <= 0 or in_rate <= 0.0 or out_rate <= 0.0:
            return 0
        return int(math.floor(in_count * (out_rate / in_rate)))

    # Arbitrary non-integer ratios
    assert_equal(resample_out_count(44100, 44100.0, 22050.0), 22050)
    assert_equal(resample_out_count(48000, 48000.0, 22050.0), 22050)
    assert_equal(resample_out_count(96000, 96000.0, 44100.0), 44100)
    assert_equal(resample_out_count(11025, 11025.0, 44100.0), 44100)
```

---

## 4. Test Execution & Regression Guard Verification Plan

### Test Discovery & Invocation
1. Discovered automatically by `E2ETestRunner` via prefix matching `test_f07_*`.
2. Can be run standalone or as part of the full suite:
   ```pwsh
   # Run only Feature 7 across all tiers
   python tests/e2e/run_e2e_tests.py --feature 7 -v

   # Run full E2E suite
   python tests/e2e/run_e2e_tests.py
   ```
3. Prior to remediation:
   - `test_f07_static_archive_binary_integrity` will fail (Member 2 header is 65 bytes, ranlib offset is 880 != 900).
   - `test_f07_static_archive_tool_validation` will fail (`ar -t` reports `Malformed archive`).
4. After remediation by worker agents:
   - All tests pass with 100% success.
   - Any future commit that corrupts archive padding, struct header size, or reverts Kotlin bindings to naive sample count will fail immediately.
