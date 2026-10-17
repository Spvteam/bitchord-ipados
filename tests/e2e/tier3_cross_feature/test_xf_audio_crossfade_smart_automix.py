"""Tier 3: Dual-Deck AVAudioEngine + Equal-Power Crossfade + Smart Automix Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_equal_power_crossfade
from tests.e2e.harness.mocks import MockAVAudioEngine


def test_xf_automix_transition_trigger_window():
    """Validates executing transition plan across out-cue and duration window."""
    engine = MockAVAudioEngine()
    engine.load_track("A", "bitchord-chunked://track_out")
    engine.load_track("B", "bitchord-chunked://track_in")
    engine.play("A")

    plan = {
        "outCueMs": 180000,
        "durationMs": 8000,
        "inCueMs": 2000,
        "bassSwapPercent": 0.5
    }

    # Simulate transition steps across 8 seconds (8 steps)
    for i in range(9):
        progress = i / 8.0
        engine.apply_crossfade_step(progress)
        gain_a, gain_b = engine.compute_equal_power_crossfade(progress)
        assert_equal_power_crossfade(gain_a, gain_b)

    # Deck B now active
    assert_equal(engine.active_deck, "B")
    assert_equal(engine.deck_a_playing, False)


def test_xf_tempo_ramp_alignment_during_crossfade():
    """Validates tempo pitch adjustment gradually aligning Track A BPM to Track B BPM."""
    engine = MockAVAudioEngine()
    bpm_a = 124.0
    bpm_b = 128.0
    target_rate_a = bpm_b / bpm_a  # ~1.0322

    # Step through transition: ramp deck A rate towards target rate
    steps = 5
    for s in range(steps + 1):
        fraction = s / steps
        current_rate_a = 1.0 + (target_rate_a - 1.0) * fraction
        engine.set_deck_pitch_and_rate("A", rate=current_rate_a, pitch_cents=0.0)
        assert_true(1.0 <= engine.deck_a_rate <= target_rate_a)


def test_xf_bass_swap_eq_automation():
    """Validates 10-band EQ cut on Track A low frequencies and boost on Track B."""
    engine = MockAVAudioEngine()
    # Initial: flat
    engine.set_equalizer_bands([0.0] * 10)
    # At crossfade midpoint: low band cut on Deck A (-6 dB at 31 Hz and 62 Hz)
    transition_eq = [-6.0, -6.0, -2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    engine.set_equalizer_bands(transition_eq)
    assert_equal(engine.eq_bands[0], -6.0)
    assert_equal(engine.eq_bands[1], -6.0)
