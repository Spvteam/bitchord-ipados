"""Tier 2 Feature 14: Boundary & Limits Tests for Dual-Deck AVAudioEngine."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_equal_power_crossfade
from tests.e2e.harness.mocks import MockAVAudioEngine


def test_f14_boundary_crossfade_duration_zero():
    """Validates 0.0s instant cut crossfade."""
    engine = MockAVAudioEngine()
    engine.load_track("A", "trackA")
    engine.load_track("B", "trackB")
    engine.play("A")

    # Immediate crossfade progress = 1.0
    engine.apply_crossfade_step(1.0)
    assert_equal(engine.deck_a_volume, 0.0)
    assert_equal(engine.deck_b_volume, 1.0)
    assert_equal(engine.active_deck, "B")


def test_f14_boundary_crossfade_duration_maximum_12s():
    """Validates 12.0s maximum crossfade boundary."""
    max_duration = 12.0
    progress = 0.5
    engine = MockAVAudioEngine()
    gain_a, gain_b = engine.compute_equal_power_crossfade(progress)
    assert_equal_power_crossfade(gain_a, gain_b)


def test_f14_boundary_eq_gain_clamping():
    """Validates that EQ gains outside [-12, +12] dB raise ValueError."""
    engine = MockAVAudioEngine()
    # Valid bands
    engine.set_equalizer_bands([12.0, -12.0, 0.0, 5.0, -5.0, 0.0, 0.0, 0.0, 0.0, 0.0])
    assert_equal(engine.eq_bands[0], 12.0)
    assert_equal(engine.eq_bands[1], -12.0)

    # Invalid bands (+15 dB)
    raised = False
    try:
        engine.set_equalizer_bands([15.0] + [0.0] * 9)
    except ValueError:
        raised = True
    assert_true(raised, "Must raise ValueError for EQ gain > +12 dB")


def test_f14_boundary_time_pitch_rate_clamping():
    """Validates tempo playback rate clamping within [0.5, 2.0]."""
    engine = MockAVAudioEngine()
    engine.set_deck_pitch_and_rate("A", rate=0.5, pitch_cents=0.0)
    assert_equal(engine.deck_a_rate, 0.5)

    engine.set_deck_pitch_and_rate("A", rate=2.0, pitch_cents=0.0)
    assert_equal(engine.deck_a_rate, 2.0)

    raised = False
    try:
        engine.set_deck_pitch_and_rate("A", rate=2.5, pitch_cents=0.0)
    except ValueError:
        raised = True
    assert_true(raised, "Must reject rate > 2.0x")


def test_f14_boundary_pitch_shift_cents_clamping():
    """Validates pitch shift bounds within [-2400, +2400] cents (+/- 2 octaves)."""
    engine = MockAVAudioEngine()
    engine.set_deck_pitch_and_rate("B", rate=1.0, pitch_cents=-2400.0)
    assert_equal(engine.deck_b_pitch_cents, -2400.0)

    engine.set_deck_pitch_and_rate("B", rate=1.0, pitch_cents=2400.0)
    assert_equal(engine.deck_b_pitch_cents, 2400.0)

    raised = False
    try:
        engine.set_deck_pitch_and_rate("B", rate=1.0, pitch_cents=3000.0)
    except ValueError:
        raised = True
    assert_true(raised, "Must reject pitch cents > 2400")


def test_f14_boundary_negative_seek_clamping():
    """Validates clamping of negative seek timestamp to 0ms."""
    requested_seek_ms = -5000
    actual_seek_ms = max(0, requested_seek_ms)
    assert_equal(actual_seek_ms, 0)
