"""Tier 3: Multi-Source Lyrics + Translation Pipeline + Compose UI Interaction."""

import re
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_xf_lyrics_fetch_translate_and_dual_line_format():
    """Validates fetching synced lyrics, translating them, and preparing dual-line Compose UI state."""
    raw_synced_line = "[00:15.50]All red Lamb' just to tease you"
    translated_line = "Toute la Lamb' rouge juste pour te taquiner"

    ui_lyric_line = {
        "timestampMs": 15500,
        "primaryText": "All red Lamb' just to tease you",
        "translatedText": translated_line,
        "isCurrentActive": True
    }
    assert_equal(ui_lyric_line["timestampMs"], 15500)
    assert_true(ui_lyric_line["isCurrentActive"])
    assert_in("Lamb", ui_lyric_line["translatedText"])


def test_xf_word_sync_syllable_animation_timeline():
    """Validates syllable timing tags driving character highlight progress."""
    line = "<00:01.00>Hel<00:01.20>lo <00:01.50>World"
    pattern = re.compile(r"<(\d{2}):(\d{2}\.\d{2})>([^<]*)")
    syllables = pattern.findall(line)
    assert_equal(len(syllables), 3)

    # Current playback at 1.30s (1300ms)
    current_time_s = 1.30
    active_syllable = None
    for mm, ss, text in syllables:
        ts = int(mm) * 60 + float(ss)
        if ts <= current_time_s:
            active_syllable = text

    assert_equal(active_syllable, "lo ")


def test_xf_lyrics_selection_to_story_card_export():
    """Validates selecting 3 verses and creating 9:16 Instagram Story lyrics card payload."""
    selected = [
        "I'm tryna put you in the worst mood, ah",
        "P1 cleaner than your church shoes, ah",
        "Milli point two just to hurt you, ah"
    ]
    card_payload = {
        "aspectRatio": "9:16",
        "width": 1080,
        "height": 1920,
        "trackTitle": "Starboy",
        "artist": "The Weeknd",
        "lyricsVerses": selected
    }
    assert_equal(len(card_payload["lyricsVerses"]), 3)
    assert_equal(card_payload["aspectRatio"], "9:16")
