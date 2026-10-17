"""Tier 4 Scenario 3: Smart Automix Dual-Deck DJ Transition."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_equal_power_crossfade
from tests.e2e.harness.mocks import MockAVAudioEngine


def test_scenario_03_smart_automix_seamless_transition():
    """Simulates:
    1. Track A playing on Deck A approaching outro cue (t = 180s)
    2. C++ DSP analysis indicates Camelot 8A (BPM 124.0)
    3. Next Track B selected: Camelot 8B (BPM 126.0)
    4. Smart Automix generates 6-second crossfade plan with bass swap
    5. Deck B pre-buffered and started at t = 180s
    6. Equal-power volume curve executed across 6 seconds
    7. Equalizer automates low-end bass swap
    8. Transition completes; Deck B becomes master deck
    """
    engine = MockAVAudioEngine()
    engine.load_track("A", "trackA")
    engine.load_track("B", "trackB")
    engine.play("A")

    # Automix Transition Plan
    plan = {
        "outCueMs": 180000,
        "durationMs": 6000,
        "inCueMs": 1500,
        "tempoRatio": 126.0 / 124.0,  # ~1.016
        "bassSwap": True
    }

    # Start Deck B at outCueMs
    engine.play("B")
    assert_true(engine.deck_a_playing)
    assert_true(engine.deck_b_playing)

    # Execute 6-second crossfade progression
    steps = 6
    for s in range(steps + 1):
        progress = s / float(steps)
        engine.apply_crossfade_step(progress)
        gain_a, gain_b = engine.compute_equal_power_crossfade(progress)
        assert_equal_power_crossfade(gain_a, gain_b)

        # Equalizer bass swap automation: gradually cut Track A low-end
        eq_low_gain = -6.0 * progress
        engine.set_equalizer_bands([eq_low_gain, eq_low_gain] + [0.0] * 8)

    # After crossfade completion (progress = 1.0)
    assert_equal(engine.active_deck, "B")
    assert_equal(engine.deck_a_playing, False)
    assert_true(engine.deck_b_playing)
    assert_equal(engine.deck_b_volume, 1.0)
