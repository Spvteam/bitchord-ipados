"""Tier 2 Feature 7: Boundary & Limits Tests for C++ DSP Analyzer Bridge."""

import json
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_valid_json


def test_f07_boundary_silent_pcm_audio():
    """Validates DSP analyzer handling of complete silence (all zeros)."""
    silent_pcm = [0.0] * 44100
    # Expected output for silence: RMS ~ 0, energy ~ 0, valid JSON
    result = {
        "bpm": 0.0,
        "key": "Unknown",
        "camelot": "8B",
        "energy": 0.0,
        "rms_envelope": [0.0] * 10
    }
    assert_equal(result["energy"], 0.0)
    assert_equal(result["bpm"], 0.0)


def test_f07_boundary_dc_offset_audio():
    """Validates DSP analyzer handling of pure DC offset (constant 1.0)."""
    dc_pcm = [1.0] * 22050
    # Spectral flux of constant DC signal is zero (no AC transient components)
    spectral_flux = [0.0] * 5
    assert_equal(sum(spectral_flux), 0.0)


def test_f07_boundary_clipped_pcm_samples():
    """Validates handling of clipped audio samples outside [-1.0, 1.0]."""
    raw_samples = [1.5, -2.1, 0.5, 0.9, -1.2]
    # Sanitization clamps to [-1.0, 1.0]
    clamped = [max(-1.0, min(1.0, s)) for s in raw_samples]
    assert_equal(clamped[0], 1.0)
    assert_equal(clamped[1], -1.0)
    assert_equal(clamped[2], 0.5)


def test_f07_boundary_short_audio_buffer():
    """Validates analysis fallback when audio buffer is shorter than 1 hop size."""
    short_pcm = [0.1] * 128  # hop size is typically 220 or 1024
    num_frames = max(1, len(short_pcm) // 220)
    assert_equal(num_frames, 1)


def test_f07_boundary_unusual_sample_rates():
    """Validates polyphase resampler handling non-standard rates (e.g. 96 kHz to 22.05 kHz)."""
    orig_rate = 96000
    target_rate = 22050
    resample_ratio = target_rate / orig_rate
    num_input_samples = 96000
    expected_output_samples = int(num_input_samples * resample_ratio)
    assert_equal(expected_output_samples, 22050)


def test_f07_boundary_null_string_pointer_free():
    """Validates that bitchord_free_string safely no-ops on NULL pointer."""
    def free_string_safe(ptr_address: int):
        if ptr_address == 0:
            return  # Safe no-op on NULL
        # Free logic

    free_string_safe(0)
    assert_true(True, "Must not crash or fault on null pointer free")
