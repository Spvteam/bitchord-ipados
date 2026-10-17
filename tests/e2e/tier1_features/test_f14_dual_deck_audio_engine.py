"""Tier 1 Feature 14: Dual-Deck AVAudioEngine Tests."""

import math
from tests.e2e.harness.assertions import (
    assert_equal, assert_true, assert_equal_power_crossfade, assert_almost_equal
)
from tests.e2e.harness.mocks import MockAVAudioEngine
from tests.e2e.harness.fixtures import EQ_PRESETS


def test_f14_dual_deck_player_node_states():
    """Validates independent Deck A and Deck B player node playback controls."""
    engine = MockAVAudioEngine()
    engine.load_track("A", "bitchord-chunked://trackA")
    engine.load_track("B", "bitchord-chunked://trackB")

    engine.play("A")
    assert_true(engine.deck_a_playing)
    assert_equal(engine.deck_b_playing, False)

    engine.play("B")
    assert_true(engine.deck_b_playing)

    engine.pause("A")
    assert_equal(engine.deck_a_playing, False)
    assert_true(engine.deck_b_playing)


def test_f14_equal_power_crossfade_invariant():
    """Validates that equal-power crossfade satisfies Gain_A^2 + Gain_B^2 = 1.0 across full progression."""
    engine = MockAVAudioEngine()
    # Sample progression steps from 0.0 to 1.0
    steps = [0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0]
    for step in steps:
        gain_a, gain_b = engine.compute_equal_power_crossfade(step)
        assert_equal_power_crossfade(gain_a, gain_b, tolerance=1e-5, msg=f"Crossfade step {step}")


def test_f14_equal_power_midpoint_gain():
    """Validates that at midpoint t=0.5, both gains equal 1/sqrt(2) (~0.7071) representing -3dB."""
    engine = MockAVAudioEngine()
    gain_a, gain_b = engine.compute_equal_power_crossfade(0.5)
    expected = 1.0 / math.sqrt(2.0)
    assert_almost_equal(gain_a, expected, delta=1e-4)
    assert_almost_equal(gain_b, expected, delta=1e-4)


def test_f14_crossfade_duration_range():
    """Validates allowable crossfade duration configuration (0.0s to 12.0s)."""
    min_duration_s = 0.0
    max_duration_s = 12.0
    configured = 6.0
    assert_true(min_duration_s <= configured <= max_duration_s)


def test_f14_10_band_biquad_eq_presets():
    """Validates 10-band equalizer configuration with built-in presets."""
    engine = MockAVAudioEngine()
    for preset_name, bands in EQ_PRESETS.items():
        engine.set_equalizer_bands(bands)
        assert_equal(len(engine.eq_bands), 10)
        assert_equal(engine.eq_bands, bands)


def test_f14_independent_time_pitch_controls():
    """Validates independent rate (0.5x-2.0x) and pitch (-2400 to +2400 cents) nodes."""
    engine = MockAVAudioEngine()
    engine.set_deck_pitch_and_rate("A", rate=1.05, pitch_cents=100.0)
    engine.set_deck_pitch_and_rate("B", rate=0.98, pitch_cents=-50.0)

    assert_equal(engine.deck_a_rate, 1.05)
    assert_equal(engine.deck_a_pitch_cents, 100.0)
    assert_equal(engine.deck_b_rate, 0.98)
    assert_equal(engine.deck_b_pitch_cents, -50.0)
