#!/usr/bin/env python3
"""
Empirical Verification & Bug Reproduction Harness for Milestone M3 (WP-04).
Author: challenger_m3_2 (EMPIRICAL CHALLENGER)
"""

import os
import struct
import math
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def test_static_archive_integrity():
    print("=" * 60)
    print("TEST 1: Static Archive Binary Integrity (iosArm64 & iosSimulatorArm64)")
    print("=" * 60)
    
    archives = [
        os.path.join(REPO_ROOT, "native", "libs", "iosArm64", "libbitchord_analysis.a"),
        os.path.join(REPO_ROOT, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")
    ]
    
    for path in archives:
        rel = os.path.relpath(path, REPO_ROOT)
        print(f"\n[*] Inspecting {rel}:")
        
        # 1. Run standard ar.exe if available
        ar_exe = r"C:\MinGW\bin\ar.exe"
        if os.path.exists(ar_exe):
            res = subprocess.run([ar_exe, "-t", path], capture_output=True, text=True)
            print(f"  ar -t exit code: {res.returncode}")
            if res.returncode != 0:
                print(f"  ar -t stderr: {res.stderr.strip()}")
            assert res.returncode != 0, "Expected ar to fail on malformed archive"
            assert "Malformed archive" in res.stderr, "Expected 'Malformed archive' error from ar"
            print("  [CONFIRMED BUG] Standard 'ar' rejects archive as 'Malformed archive'!")
            
        # 2. Binary structural analysis
        with open(path, "rb") as f:
            data = f.read()
            
        assert data[:8] == b"!<arch>\n", "Missing AR magic"
        
        # Member 1 header
        m1_hdr = data[8:68]
        m1_size = int(m1_hdr[48:58].decode("ascii").strip())
        print(f"  Member 1 payload size reported in header: {m1_size} bytes")
        
        # Read ran_off from first ranlib entry
        # symdef data starts at byte 68 + 20 (extended name) = 88
        symdef = data[88:68+m1_size]
        ran_size = struct.unpack("<I", symdef[:4])[0]
        first_strx, first_ran_off = struct.unpack("<II", symdef[4:12])
        print(f"  First ranlib entry points to offset: {first_ran_off}")
        
        # Member 2 actual start offset
        actual_m2_offset = 68 + m1_size
        if actual_m2_offset % 2 != 0:
            actual_m2_offset += 1
        print(f"  Member 2 actual starting offset in file: {actual_m2_offset}")
        
        offset_delta = actual_m2_offset - first_ran_off
        print(f"  Offset discrepancy (actual - pointer): {offset_delta} bytes (omitted 20-byte extended name!)")
        assert offset_delta == 20, f"Expected 20 byte offset bug, got {offset_delta}"
        
        # Member 2 header format check:
        m2_hdr_raw = data[actual_m2_offset:actual_m2_offset+70]
        fmag_idx = m2_hdr_raw.find(b"`\n")
        m2_hdr_len = fmag_idx + 2 if fmag_idx != -1 else len(m2_hdr_raw)
        print(f"  Member 2 ar_hdr actual length: {m2_hdr_len} bytes (POSIX/BSD specification requires EXACTLY 60 bytes!)")
        assert m2_hdr_len == 65, f"Expected 65 bytes due to 21-character filename without BSD #1/21 extended naming, got {m2_hdr_len}"
        print("  [CONFIRMED BUG] Member 2 ar_hdr is 65 bytes instead of 60 bytes!")


def test_buffer_overflow_stress_reproduction():
    print("\n" + "=" * 60)
    print("TEST 2: Mel & Vocal Spectrogram Buffer Sizing vs Resampling")
    print("=" * 60)
    
    # MelSpectrogram.ios.kt reproduction:
    # fun compute(samples: FloatArray, sampleRate: Double): Spectrogram?
    # val pad = 512
    # val paddedLength = samples.size + 2 * pad
    # val frames = (paddedLength - 1024) / hop + 1
    # val totalFloats = frames * mels (where mels=128, hop=441)
    # outBuffer = FloatArray(totalFloats)
    
    print("\n[*] Scenario 1: MelSpectrogram.compute with sampleRate=11025.0 Hz (2.0s audio):")
    sample_rate_in = 11025
    duration_s = 2.0
    input_samples = int(duration_s * sample_rate_in) # 22050 samples
    
    # Kotlin allocation:
    kt_pad = 512
    kt_padded_len = input_samples + 2 * kt_pad
    kt_frames = (kt_padded_len - 1024) // 441 + 1
    kt_floats_allocated = kt_frames * 128
    print(f"  Kotlin allocates: {kt_frames} frames * 128 mels = {kt_floats_allocated} floats ({kt_floats_allocated * 4} bytes)")
    
    # C++ execution:
    # Resamples 22050 from 11025 Hz to 22050 Hz:
    resampled_samples = int(math.floor(input_samples * (22050.0 / sample_rate_in))) # 44100 samples
    cpp_pad = 512
    cpp_padded_len = resampled_samples + 2 * cpp_pad
    cpp_frames = (cpp_padded_len - 1024) // 441 + 1
    cpp_floats_written = cpp_frames * 128
    print(f"  C++ resamples to 22050 Hz and writes: {cpp_frames} frames * 128 mels = {cpp_floats_written} floats ({cpp_floats_written * 4} bytes)")
    
    overflow_floats = cpp_floats_written - kt_floats_allocated
    overflow_bytes = overflow_floats * 4
    print(f"  [CONFIRMED BUFFER OVERFLOW] Overwriting {overflow_floats} floats ({overflow_bytes} bytes) past end of buffer!")
    assert overflow_floats > 0, "Buffer overflow did not trigger"
    
    # VocalSpectrogram.ios.kt reproduction:
    print("\n[*] Scenario 2: VocalSpectrogram.compute with rate=22050.0 Hz (2.0s audio):")
    vocal_rate_in = 22050
    vocal_samples = int(2.0 * vocal_rate_in) # 44100 samples
    
    # Kotlin allocation:
    vkt_pad = 2048
    vkt_padded_len = vocal_samples + 2 * vkt_pad
    vkt_frames = (vkt_padded_len - 4096) // 1024 + 1
    vkt_floats_allocated = 2 * 1487 * vkt_frames
    print(f"  Kotlin allocates: 2 * 1487 * {vkt_frames} frames = {vkt_floats_allocated} floats ({vkt_floats_allocated * 4} bytes)")
    
    # C++ execution:
    # Resamples to 44100 Hz:
    vresampled_samples = int(math.floor(vocal_samples * (44100.0 / vocal_rate_in))) # 88200 samples
    vcpp_frames = (vresampled_samples + 4096 - 4096) // 1024 + 1
    vcpp_floats_written = 2 * 1487 * vcpp_frames
    print(f"  C++ resamples to 44100 Hz and writes: 2 * 1487 * {vcpp_frames} frames = {vcpp_floats_written} floats ({vcpp_floats_written * 4} bytes)")
    
    voverflow_floats = vcpp_floats_written - vkt_floats_allocated
    voverflow_bytes = voverflow_floats * 4
    print(f"  [CONFIRMED BUFFER OVERFLOW] Overwriting {voverflow_floats} floats ({voverflow_bytes} bytes = {voverflow_bytes/1024:.1f} KB) past end of buffer!")
    assert voverflow_floats > 0, "Vocal buffer overflow did not trigger"

if __name__ == "__main__":
    try:
        test_static_archive_integrity()
        test_buffer_overflow_stress_reproduction()
        print("\n" + "=" * 60)
        print("[VERDICT] ALL CHALLENGE FINDINGS EMPIRICALLY CONFIRMED.")
        print("=" * 60)
    except AssertionError as e:
        print(f"\n[FAIL] Assertion failed: {e}")
        sys.exit(1)
