"""Authoritative fixtures and reference vectors for BitChord E2E tests."""

import math
import struct
import zlib

# 1. Audio PCM Reference Generators
def generate_sine_wave_pcm(freq: float = 440.0, sample_rate: int = 44100, duration_sec: float = 1.0, amplitude: float = 0.8) -> list:
    """Generates mono float PCM samples for a pure sine wave."""
    total_samples = int(sample_rate * duration_sec)
    samples = []
    omega = 2.0 * math.pi * freq / sample_rate
    for i in range(total_samples):
        samples.append(amplitude * math.sin(omega * i))
    return samples


def generate_stereo_pcm(sample_rate: int = 44100, duration_sec: float = 1.0) -> tuple:
    """Generates left and right stereo PCM channels."""
    left = generate_sine_wave_pcm(440.0, sample_rate, duration_sec, amplitude=0.7)
    right = generate_sine_wave_pcm(880.0, sample_rate, duration_sec, amplitude=0.5)
    return left, right


def generate_beat_pulse_pcm(bpm: float = 120.0, sample_rate: int = 22050, duration_sec: float = 4.0) -> list:
    """Generates synthetic audio PCM with clear periodic transient peaks at specified BPM."""
    total_samples = int(sample_rate * duration_sec)
    samples = [0.0] * total_samples
    samples_per_beat = int(sample_rate * (60.0 / bpm))
    for beat_idx in range(0, total_samples, samples_per_beat):
        # Insert a 50ms transient impulse
        for j in range(min(int(0.05 * sample_rate), total_samples - beat_idx)):
            samples[beat_idx + j] = (1.0 - (j / (0.05 * sample_rate))) * 0.9
    return samples


# 2. YouTube Innertube API Formats & Clients
SAMPLE_PLAYER_RESPONSE_M4A = {
    "playabilityStatus": {"status": "OK"},
    "streamingData": {
        "expiresInSeconds": "21600",
        "adaptiveFormats": [
            {
                "itag": 140,
                "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                "bitrate": 129528,
                "contentLength": "3845921",
                "approxDurationMs": "237512",
                "audioSampleRate": "44100",
                "audioChannels": 2,
                "url": "https://rr1---sn-oxunx-g5fe.googlevideo.com/videoplayback?expire=1730000000&ei=sample&ip=0.0.0.0&id=o-SAMPLE&itag=140&source=youtube&requiressl=yes&mime=audio/mp4"
            },
            {
                "itag": 141,
                "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                "bitrate": 256000,
                "contentLength": "7691842",
                "approxDurationMs": "237512",
                "audioSampleRate": "44100",
                "audioChannels": 2,
                "url": "https://rr1---sn-oxunx-g5fe.googlevideo.com/videoplayback?expire=1730000000&ei=sample&ip=0.0.0.0&id=o-SAMPLE&itag=141&source=youtube&requiressl=yes&mime=audio/mp4"
            },
            {
                "itag": 251,
                "mimeType": "audio/webm; codecs=\"opus\"",
                "bitrate": 160000,
                "contentLength": "4125890",
                "approxDurationMs": "237512",
                "audioSampleRate": "48000",
                "audioChannels": 2,
                "url": "https://rr1---sn-oxunx-g5fe.googlevideo.com/videoplayback?expire=1730000000&ei=sample&ip=0.0.0.0&id=o-SAMPLE&itag=251&source=youtube&requiressl=yes&mime=audio/webm"
            }
        ]
    },
    "videoDetails": {
        "videoId": "SAMPLE_VID_01",
        "title": "Starboy (feat. Daft Punk)",
        "author": "The Weeknd",
        "lengthSeconds": "238"
    }
}

# 3. Synchronized Lyrics Fixtures
SAMPLE_LRC_SYLLABLE_CONTENT = """[00:00.00]Starboy - The Weeknd
[00:05.10]<00:05.10>I'm <00:05.35>tryna <00:05.60>put <00:05.80>you <00:06.00>in <00:06.20>the <00:06.50>worst <00:06.85>mood, <00:07.20>ah
[00:08.50]<00:08.50>P1 <00:08.90>cleaner <00:09.30>than <00:09.60>your <00:09.90>church <00:10.20>shoes, <00:10.60>ah
[00:12.00]<00:12.00>Milli <00:12.40>point <00:12.70>two <00:13.00>just <00:13.20>to <00:13.50>hurt <00:13.90>you, <00:14.20>ah
[00:15.80]<00:15.80>All <00:16.10>red <00:16.40>Lamb' <00:16.70>just <00:17.00>to <00:17.20>tease <00:17.60>you, <00:18.00>ah
"""

KUGOU_XOR_KEY = b'@Gaw^2tGQ61-\xc3\x8e\xc3\x92ni'

def generate_kugou_krc_payload(text: str) -> bytes:
    """Generates an authoritative encrypted KuGou .krc binary payload."""
    # Step 1: Compress via zlib
    compressed = zlib.compress(text.encode("utf-8"))
    # Step 2: XOR encrypt with KuGou key
    key_len = len(KUGOU_XOR_KEY)
    encrypted = bytearray(len(compressed))
    for i, b in enumerate(compressed):
        encrypted[i] = b ^ KUGOU_XOR_KEY[i % key_len]
    return b"krc1" + bytes(encrypted)


# 4. C++ DSP Analyzer JSON Schema Reference
SAMPLE_ANALYSIS_RESULT_JSON = {
    "version": 1,
    "duration_ms": 238000,
    "sample_rate": 44100,
    "bpm": 128.0,
    "bpm_confidence": 0.94,
    "key": "G Major",
    "camelot": "9B",
    "energy": 0.82,
    "cue_in_ms": 1250,
    "cue_out_ms": 231400,
    "phrase_boundaries": [0, 15000, 30000, 60000, 90000, 120000, 150000, 180000, 210000, 238000],
    "rms_envelope": [0.05, 0.12, 0.45, 0.82, 0.85, 0.84, 0.81, 0.40, 0.08],
    "spectral_flux": [0.02, 0.08, 0.35, 0.65, 0.70, 0.68, 0.62, 0.20, 0.01],
    "downbeats": [0.0, 1.875, 3.75, 5.625, 7.5, 9.375],
    "beats": [0.0, 0.468, 0.937, 1.406, 1.875, 2.343, 2.812, 3.281, 3.75],
    "vocal_presence_prob": [0.0, 0.1, 0.85, 0.92, 0.95, 0.90, 0.15, 0.0]
}

# 5. Equalizer 10-Band Center Frequencies
EQ_CENTER_FREQUENCIES_HZ = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]

EQ_PRESETS = {
    "Flat": [0.0] * 10,
    "Bass Boost": [6.0, 5.5, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
    "Vocal Boost": [-1.0, -1.0, 0.0, 2.0, 4.5, 4.0, 3.0, 1.0, 0.0, -1.0],
    "Electronic": [5.0, 4.5, 2.0, 0.0, -1.5, 2.0, 0.5, 2.5, 4.0, 4.5],
    "Acoustic": [4.0, 3.0, 2.0, 1.0, 1.5, 1.5, 2.0, 3.0, 3.5, 4.0]
}
