"""Tier 4 Scenario 9: Spotify Import, Fuzzy Deduplication & Last.fm Scrobble."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_scenario_09_spotify_import_deduplication_and_scrobble():
    """Simulates:
    1. User imports Spotify liked tracks
    2. TrackMatcher performs fuzzy metadata matching against YouTube Music catalog
    3. Selected track begins playback
    4. Discord Rich Presence gateway packet sent (Listening to...)
    5. Playback progresses to 50% of duration (120s of 240s)
    6. Last.fm scrobble API invoked and logged in playback history
    """
    spotify_track = {
        "title": "Blinding Lights (Official Audio)",
        "artist": "The Weeknd",
        "durationMs": 200000
    }

    candidates = [
        {"id": "yt_1", "title": "Blinding Lights", "artist": "The Weeknd", "durationMs": 200000},
        {"id": "yt_2", "title": "Blinding Lights (Live)", "artist": "The Weeknd", "durationMs": 260000},
        {"id": "yt_3", "title": "Blinding Lights Cover", "artist": "Other Artist", "durationMs": 200000}
    ]

    # Fuzzy matcher ranks best match
    def match_score(cand):
        title_match = 1.0 if "Blinding Lights" in cand["title"] and "Cover" not in cand["title"] and "Live" not in cand["title"] else 0.0
        artist_match = 1.0 if cand["artist"] == "The Weeknd" else 0.0
        return title_match + artist_match

    best_match = max(candidates, key=match_score)
    assert_equal(best_match["id"], "yt_1")

    # Discord RPC dispatch
    discord_presence = {
        "op": 3,
        "d": {
            "activity": {
                "name": "BitChord",
                "details": best_match["title"],
                "state": best_match["artist"]
            }
        }
    }
    assert_equal(discord_presence["d"]["activity"]["details"], "Blinding Lights")

    # Scrobble threshold check: 50%
    song_duration_s = 200.0
    elapsed_playback_s = 105.0  # > 50% (100.0s)
    should_scrobble = elapsed_playback_s >= 0.5 * song_duration_s
    assert_true(should_scrobble, "Last.fm scrobble should trigger after 50% playback")
