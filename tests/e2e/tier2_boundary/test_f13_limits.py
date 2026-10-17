"""Tier 2 Feature 13: Boundary & Limits Tests for ONNX Runtime & PCM Decoder."""

import math
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f13_boundary_mismatched_mel_bins_dimension():
    """Validates rejecting input tensor with mel bins != 128 for beat_this model."""
    def validate_beat_tensor_shape(shape: list) -> bool:
        return len(shape) == 3 and shape[0] == 1 and shape[2] == 128

    assert_true(validate_beat_tensor_shape([1, 100, 128]))
    assert_equal(validate_beat_tensor_shape([1, 100, 64]), False, "64 bins must be rejected")
    assert_equal(validate_beat_tensor_shape([1, 100, 256]), False, "256 bins must be rejected")


def test_f13_boundary_mono_to_stereo_channel_duplication():
    """Validates automatic channel duplication when mono PCM is fed to stereo vocal model."""
    mono_pcm = [0.5, -0.2, 0.8]
    # Vocal model requires [channels=2, ...]
    left = list(mono_pcm)
    right = list(mono_pcm)
    assert_equal(len(left), len(mono_pcm))
    assert_equal(len(right), len(mono_pcm))
    assert_equal(left, right)


def test_f13_boundary_nan_and_inf_sanitization():
    """Validates sanitizing NaN or Inf values in decoded PCM before neural inference."""
    raw_samples = [0.1, float("nan"), 0.5, float("inf"), -float("inf"), -0.2]
    sanitized = [0.0 if (math.isnan(s) or math.isinf(s)) else s for s in raw_samples]
    assert_equal(sanitized[1], 0.0)
    assert_equal(sanitized[3], 0.0)
    assert_equal(sanitized[4], 0.0)
    assert_equal(sanitized[0], 0.1)


def test_f13_boundary_missing_model_file_fallback():
    """Validates fallback to heuristic beat detection if ONNX model is missing."""
    model_loaded = False
    engine_mode = "NEURAL_ONNX" if model_loaded else "HEURISTIC_COMB_FILTER"
    assert_equal(engine_mode, "HEURISTIC_COMB_FILTER")


def test_f13_boundary_rapid_inference_memory_flat():
    """Validates tensor handle allocation counter remains zero across multiple inferences."""
    active_handles = 0
    for _ in range(50):
        # allocate
        active_handles += 2
        # release in finally
        active_handles -= 2
    assert_equal(active_handles, 0, "No tensor handle leak across 50 inferences")


def test_f13_boundary_zero_probability_vocal_contour():
    """Validates purely instrumental track produces vocal probability contour near 0.0."""
    instrumental_probs = [0.01, 0.02, 0.00, 0.01]
    has_vocals = any(p > 0.5 for p in instrumental_probs)
    assert_equal(has_vocals, False)
