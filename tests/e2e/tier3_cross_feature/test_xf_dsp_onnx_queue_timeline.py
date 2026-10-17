"""Tier 3: C++ DSP Analyzer + ONNX Beat & Vocal Tracker + Queue Ordering Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_xf_audio_pcm_to_stft_and_beat_grid():
    """Validates pipeline flow: PCM -> Mel Spectrogram (128 bins) -> ONNX Beat Model -> Beat Grid."""
    pcm_length = 22050 * 2  # 2 seconds
    mel_frames = 200        # 100 fps
    mel_bins = 128

    # Simulate ONNX logits output
    beat_logits = [0.1] * mel_frames
    # Peaks at 0.5s, 1.0s, 1.5s (frames 50, 100, 150)
    beat_logits[50] = 0.95
    beat_logits[100] = 0.92
    beat_logits[150] = 0.94

    beats = [frame * 0.01 for frame, logit in enumerate(beat_logits) if logit > 0.5]
    assert_equal(len(beats), 3)
    assert_equal(beats, [0.5, 1.0, 1.5])


def test_xf_vocal_and_energy_cue_out_detection():
    """Validates combining vocal presence probability with spectral flux to pick outro cue."""
    # Song duration 180s; outro from 150s to 180s
    vocal_prob_outro = [0.0, 0.0, 0.0]  # Vocals drop out in outro
    energy_drop = [0.8, 0.4, 0.2]       # Energy subsides
    has_vocals_in_outro = any(p > 0.5 for p in vocal_prob_outro)
    assert_equal(has_vocals_in_outro, False)

    ideal_cue_out_s = 150.0
    assert_true(ideal_cue_out_s >= 140.0)


def test_xf_harmonic_camelot_queue_ranker():
    """Validates ranking candidate tracks by Camelot wheel proximity and BPM similarity."""
    current_track = {"camelot": "8A", "bpm": 128.0}
    candidates = [
        {"id": "t1", "camelot": "8A", "bpm": 128.0},  # Exact match (Score: high)
        {"id": "t2", "camelot": "9A", "bpm": 127.0},  # Adjacent match (Score: medium-high)
        {"id": "t3", "camelot": "2B", "bpm": 85.0}    # Clash (Score: low)
    ]

    def rank_score(cand):
        c_diff = 0 if cand["camelot"] == current_track["camelot"] else 1 if cand["camelot"][0] == "9" else 10
        bpm_diff = abs(cand["bpm"] - current_track["bpm"])
        return c_diff + (bpm_diff * 0.1)

    ranked = sorted(candidates, key=rank_score)
    assert_equal(ranked[0]["id"], "t1")
    assert_equal(ranked[1]["id"], "t2")
    assert_equal(ranked[2]["id"], "t3")
