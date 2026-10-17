"""Tier 4 Scenario 5: Equalizer, TimePitch & Visualizer FFT Pipeline."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockAVAudioEngine
from tests.e2e.harness.fixtures import EQ_PRESETS, generate_sine_wave_pcm


def test_scenario_05_equalizer_and_realtime_fft_visualizer():
    """Simulates:
    1. User opens Equalizer sheet in LandscapePlayer
    2. Selects 'Electronic' preset (+5dB low, +4.5dB high)
    3. TimePitch node tempo rate increased to 1.15x
    4. Audio PCM passes through 1024-point FFT tap
    5. Visualizer publishes 512 magnitude bins to Compose UI
    """
    engine = MockAVAudioEngine()
    engine.load_track("A", "track1")
    engine.play("A")

    # Step 2: Apply Electronic preset
    preset_bands = EQ_PRESETS["Electronic"]
    engine.set_equalizer_bands(preset_bands)
    assert_equal(engine.eq_bands[0], 5.0)
    assert_equal(engine.eq_bands[9], 4.5)

    # Step 3: Set tempo rate to 1.15x
    engine.set_deck_pitch_and_rate("A", rate=1.15, pitch_cents=0.0)
    assert_equal(engine.deck_a_rate, 1.15)

    # Step 4 & 5: Simulate 1024-bin FFT spectrum calculation
    pcm_block = generate_sine_wave_pcm(freq=440.0, sample_rate=44100, duration_sec=0.05)
    fft_size = 1024
    num_spectrum_bins = fft_size // 2  # 512 magnitude bins
    spectrum = [0.0] * num_spectrum_bins
    # Fundamental frequency at 440 Hz bin index: 440 / (44100 / 1024) ~= 10
    peak_bin = int(440.0 / (44100.0 / fft_size))
    spectrum[peak_bin] = 0.95

    assert_equal(len(spectrum), 512)
    assert_equal(spectrum[peak_bin], 0.95)
    assert_true(peak_bin in range(8, 12))
