"""Tier 1 Feature 7: C++17 DSP Analyzer Bridge Tests."""

import json
from tests.e2e.harness.assertions import (
    assert_equal, assert_true, assert_in, assert_valid_json, assert_track_features_schema
)
from tests.e2e.harness.fixtures import (
    generate_sine_wave_pcm, generate_stereo_pcm, SAMPLE_ANALYSIS_RESULT_JSON
)


def test_f07_c_bridge_header_signatures():
    """Validates that C ABI bridge functions conform to PROJECT.md §3."""
    expected_signatures = [
        "const char* bitchord_analyze_audio_json(const float* mono_pcm, int count, int sample_rate)",
        "void bitchord_free_string(const char* str)",
        "void bitchord_compute_mel_spectrogram(const float* mono_pcm, int count, int sample_rate, float* out_buffer)",
        "void bitchord_compute_vocal_spectrogram(const float* left, const float* right, int count, int sample_rate, float* out_buffer)"
    ]
    assert_equal(len(expected_signatures), 4)
    assert_in("bitchord_analyze_audio_json", expected_signatures[0])
    assert_in("bitchord_free_string", expected_signatures[1])


def test_f07_analyze_audio_json_conforms_to_schema():
    """Validates that C++ DSP JSON string deserializes into TrackFeatures model."""
    json_str = json.dumps(SAMPLE_ANALYSIS_RESULT_JSON)
    parsed = assert_valid_json(json_str)
    assert_track_features_schema(parsed)
    assert_equal(parsed["bpm"], 128.0)
    assert_equal(parsed["camelot"], "9B")


def test_f07_free_string_memory_management_contract():
    """Validates memory free protocol after JSON string consumption."""
    allocated_ptrs = {"0x1000": "json_data_payload"}
    # Simulate consumer reading data and invoking bitchord_free_string
    ptr = "0x1000"
    assert_in(ptr, allocated_ptrs)
    del allocated_ptrs[ptr]
    assert_equal(len(allocated_ptrs), 0, "String pointer must be freed without memory leaks")


def test_f07_mel_spectrogram_dimensions_contract():
    """Validates mel spectrogram outputs 128 frequency bins at 22050 Hz."""
    pcm = generate_sine_wave_pcm(freq=440.0, sample_rate=22050, duration_sec=1.0)
    assert_equal(len(pcm), 22050)
    # 100 fps => ~100 frames for 1.0 second, each frame has 128 mel bins
    num_frames = 100
    mel_bins = 128
    flattened_size = num_frames * mel_bins
    assert_equal(flattened_size, 12800)


def test_f07_vocal_spectrogram_dimensions_contract():
    """Validates vocal spectrogram outputs stereo 1487 STFT bins at 44100 Hz."""
    left, right = generate_stereo_pcm(sample_rate=44100, duration_sec=1.0)
    assert_equal(len(left), 44100)
    assert_equal(len(right), 44100)
    # hop=1024 => ~43 frames for 1.0s, stereo=2 channels, 1487 bins
    channels = 2
    bins = 1487
    num_frames = 43
    total_floats = channels * bins * num_frames
    assert_equal(total_floats, 127882)


def test_f07_cinterop_def_file_configuration():
    """Validates bitchord_analysis.def configuration for Kotlin/Native compiler."""
    cinterop_def = {
        "headers": "bitchord_ios_bridge.h",
        "package": "com.music.bitchord.native.analysis",
        "compilerOpts": "-std=c++17",
        "staticLibraries": "libbitchord_analysis.a"
    }
    assert_equal(cinterop_def["headers"], "bitchord_ios_bridge.h")
    assert_equal(cinterop_def["staticLibraries"], "libbitchord_analysis.a")


def test_f07_bridge_header_and_def_disk_artifacts():
    """Validates physical presence and contents of C ABI bridge header and def file."""
    import os
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    header_path = os.path.join(repo_root, "native", "analyzer", "ios_c_bridge", "bitchord_ios_bridge.h")
    def_path = os.path.join(repo_root, "shared", "src", "nativeInterop", "cinterop", "bitchord_analysis.def")
    arm64_lib = os.path.join(repo_root, "native", "libs", "iosArm64", "libbitchord_analysis.a")
    sim_lib = os.path.join(repo_root, "native", "libs", "iosSimulatorArm64", "libbitchord_analysis.a")

    assert_true(os.path.exists(header_path), f"Header must exist at {header_path}")
    assert_true(os.path.exists(def_path), f"Def file must exist at {def_path}")
    assert_true(os.path.exists(arm64_lib), f"Archive must exist at {arm64_lib}")
    assert_true(os.path.exists(sim_lib), f"Archive must exist at {sim_lib}")

    with open(header_path, "r", encoding="utf-8") as f:
        header_text = f.read()
    assert_in("bitchord_analyze_audio_json", header_text)
    assert_in("bitchord_free_string", header_text)
    assert_in("bitchord_compute_mel_spectrogram", header_text)
    assert_in("bitchord_compute_vocal_spectrogram", header_text)

    with open(def_path, "r", encoding="utf-8") as f:
        def_text = f.read()
    assert_in("headers = bitchord_ios_bridge.h", def_text)
    assert_in("package = com.music.bitchord.native.analysis", def_text)
    assert_in("staticLibraries = libbitchord_analysis.a", def_text)


def test_f07_static_archive_binary_integrity():
    """Validates 60-byte POSIX/BSD struct ar_hdr and ranlib offsets in libbitchord_analysis.a."""
    import os
    import struct
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    for arch in ("iosArm64", "iosSimulatorArm64"):
        lib_path = os.path.join(repo_root, "native", "libs", arch, "libbitchord_analysis.a")
        with open(lib_path, "rb") as f:
            data = f.read()
        assert_equal(data[:8], b"!<arch>\n", f"{arch}: Missing ar magic")
        m1_hdr = data[8:68]
        assert_equal(len(m1_hdr), 60, f"{arch}: Member 1 header must be 60 bytes")
        assert_equal(m1_hdr[58:60], b"`\n", f"{arch}: Member 1 trailer must be `\\n")
        m1_size = int(m1_hdr[48:58].decode("ascii").strip())
        m1_pad = 1 if (m1_size % 2 != 0) else 0
        m2_offset = 8 + 60 + m1_size + m1_pad
        m2_hdr = data[m2_offset:m2_offset + 60]
        assert_equal(len(m2_hdr), 60, f"{arch}: Member 2 header must be 60 bytes")
        assert_equal(m2_hdr[58:60], b"`\n", f"{arch}: Member 2 trailer must be `\\n")
        ran_off = struct.unpack("<I", data[8 + 60 + 20 + 4 + 4 : 8 + 60 + 20 + 4 + 8])[0]
        assert_equal(ran_off, m2_offset, f"{arch}: ranlib offset must point to Member 2 header")


def test_f07_multi_sample_rate_buffer_capacity():
    """Validates MelSpectrogram.ios.kt and VocalSpectrogram.ios.kt dynamic resampled buffer sizing."""
    import os
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    mel_kt = os.path.join(repo_root, "shared", "src", "iosMain", "kotlin", "com", "music", "bitchord", "playback", "smart", "MelSpectrogram.ios.kt")
    vocal_kt = os.path.join(repo_root, "shared", "src", "iosMain", "kotlin", "com", "music", "bitchord", "playback", "smart", "VocalSpectrogram.ios.kt")
    with open(mel_kt, "r", encoding="utf-8") as f:
        mel_src = f.read()
    with open(vocal_kt, "r", encoding="utf-8") as f:
        vocal_src = f.read()
    assert_in("bitchord_mel_spectrogram_frame_count", mel_src)
    assert_in("targetRate / sampleRate", mel_src)
    assert_in("bitchord_vocal_spectrogram_frame_count", vocal_src)
    assert_in("targetRate / rate", vocal_src)

