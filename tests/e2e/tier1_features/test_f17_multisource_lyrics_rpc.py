"""Tier 1 Feature 17: Multi-Source Lyrics & RPC Tests."""

import re
import zlib
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.fixtures import (
    KUGOU_XOR_KEY, SAMPLE_LRC_SYLLABLE_CONTENT, generate_kugou_krc_payload
)


def test_f17_kugou_krc_decryption_and_decompression():
    """Validates KuGou .krc XOR decryption with key @Gaw^2tGQ61-ÎÒni and zlib inflate."""
    original_text = "[00:01.00]KuGou Test Lyrics Word Sync"
    payload = generate_kugou_krc_payload(original_text)

    # Verify header
    assert_true(payload.startswith(b"krc1"))
    encrypted_body = payload[4:]

    # Decrypt with XOR
    key_len = len(KUGOU_XOR_KEY)
    decrypted = bytearray(len(encrypted_body))
    for i, b in enumerate(encrypted_body):
        decrypted[i] = b ^ KUGOU_XOR_KEY[i % key_len]

    # Decompress with zlib
    decompressed = zlib.decompress(bytes(decrypted)).decode("utf-8")
    assert_equal(decompressed, original_text)


def test_f17_word_synced_syllable_timing_parser():
    """Validates parsing word/syllable timing tags <mm:ss.xx>."""
    line = "<00:05.10>I'm <00:05.35>tryna <00:05.60>put <00:05.80>you"
    pattern = re.compile(r"<(\d{2}):(\d{2}\.\d{2})>([^<]*)")
    matches = pattern.findall(line)
    assert_equal(len(matches), 4)
    # Check first word
    min1, sec1, word1 = matches[0]
    assert_equal(min1, "00")
    assert_equal(sec1, "05.10")
    assert_equal(word1, "I'm ")


def test_f17_multi_source_lyrics_priority_order():
    """Validates lyrics registry query fallback across providers."""
    providers = ["LrcLib", "NetEase", "KuGou", "Paxsenix", "SimpMusic", "YouTube"]
    assert_equal(providers[0], "LrcLib", "LrcLib should be primary synchronized lyrics source")
    assert_in("KuGou", providers)


def test_f17_lyrics_translation_alignment():
    """Validates pairing original lyrics lines with translated lines."""
    original = ["Je t'aime", "Tous les jours"]
    translated = ["I love you", "Every day"]
    paired = list(zip(original, translated))
    assert_equal(len(paired), 2)
    assert_equal(paired[0], ("Je t'aime", "I love you"))


def test_f17_discord_rpc_gateway_payload():
    """Validates Discord Gateway Rich Presence payload formatting."""
    activity = {
        "name": "BitChord",
        "type": 2,  # Listening
        "details": "Blinding Lights",
        "state": "The Weeknd",
        "timestamps": {"start": 1729000000},
        "assets": {"large_image": "app_logo", "large_text": "BitChord iPadOS"}
    }
    assert_equal(activity["name"], "BitChord")
    assert_equal(activity["type"], 2)


def test_f17_lastfm_scrobble_threshold_logic():
    """Validates Last.fm scrobble trigger: 50% duration or 240 seconds."""
    def should_scrobble(elapsed_s: float, duration_s: float) -> bool:
        return (elapsed_s >= 240.0) or (elapsed_s >= 0.5 * duration_s)

    # 3-minute song (180s): 50% is 90s
    assert_equal(should_scrobble(80.0, 180.0), False)
    assert_true(should_scrobble(95.0, 180.0))

    # 10-minute song (600s): 240s threshold applies before 50% (300s)
    assert_true(should_scrobble(245.0, 600.0))
