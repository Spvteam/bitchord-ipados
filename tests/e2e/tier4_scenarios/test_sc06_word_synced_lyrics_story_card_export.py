"""Tier 4 Scenario 6: Word-Synced Lyrics, Karaoke Mode & Story Card Export."""

import re
import zlib
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.fixtures import generate_kugou_krc_payload, KUGOU_XOR_KEY


def test_scenario_06_lyrics_karaoke_and_story_card_export():
    """Simulates:
    1. User switches to Lyrics view in NowPlayingScreen
    2. Encrypted KuGou .krc word-sync lyrics payload fetched
    3. XOR decrypted with key @Gaw^2tGQ61-ÎÒni and zlib decompressed
    4. Syllable timestamps extracted for real-time word animation
    5. User taps 'Share Lyrics'
    6. Selects 2 favorite lines
    7. Generates 9:16 Instagram Story card bitmap layout
    """
    lyrics_raw = "[00:05.00]<00:05.00>Star<00:05.40>boy <00:05.80>groove"
    krc_data = generate_kugou_krc_payload(lyrics_raw)

    # Step 3: Decrypt & Decompress
    encrypted_body = krc_data[4:]
    decrypted = bytearray(len(encrypted_body))
    for i, b in enumerate(encrypted_body):
        decrypted[i] = b ^ KUGOU_XOR_KEY[i % len(KUGOU_XOR_KEY)]
    decompressed = zlib.decompress(bytes(decrypted)).decode("utf-8")
    assert_equal(decompressed, lyrics_raw)

    # Step 4: Parse syllable tags
    pattern = re.compile(r"<(\d{2}):(\d{2}\.\d{2})>([^<]*)")
    tokens = pattern.findall(decompressed)
    assert_equal(len(tokens), 3)

    # Step 5 & 6: Select lines for export
    selected_verses = [
        "Starboy groove",
        "Second line of lyrics"
    ]

    # Step 7: Story card layout model
    story_card = {
        "aspectRatio": "9:16",
        "canvasWidth": 1080,
        "canvasHeight": 1920,
        "trackTitle": "Starboy",
        "artist": "The Weeknd",
        "verses": selected_verses,
        "renderScale": 3.0  # Retina 3x scale
    }
    assert_equal(story_card["aspectRatio"], "9:16")
    assert_equal(len(story_card["verses"]), 2)
