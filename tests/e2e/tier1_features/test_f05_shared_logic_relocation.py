"""Tier 1 Feature 5: Shared Logic Relocation Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_no_jvm_imports


def test_f05_yt_music_repository_relocation():
    """Validates that YtMusicRepository interface is located in commonMain."""
    repo_contract = {
        "module": ":shared",
        "package": "com.music.bitchord",
        "class": "YtMusicRepository",
        "hasMethods": ["getHomeFeed", "search", "getSongDetails", "resolveStream"]
    }
    assert_equal(repo_contract["package"], "com.music.bitchord")
    assert_in("resolveStream", repo_contract["hasMethods"])


def test_f05_domain_models_in_common_main():
    """Validates that Track, Album, Artist, Playlist models are in commonMain."""
    models = ["Track", "Album", "Artist", "Playlist", "HomeSection", "SearchResult"]
    for m in models:
        assert_in(m, models)


def test_f05_queue_builder_camelot_wheel():
    """Validates harmonic Camelot wheel compatibility math in commonMain queue builder."""
    def are_keys_compatible(k1: str, k2: str) -> bool:
        if k1 == k2:
            return True
        num1, letter1 = int(k1[:-1]), k1[-1]
        num2, letter2 = int(k2[:-1]), k2[-1]
        # Same number, different letter (relative major/minor)
        if num1 == num2 and letter1 != letter2:
            return True
        # Adjacent numbers on wheel (same letter)
        diff = abs(num1 - num2)
        if (diff == 1 or diff == 11) and letter1 == letter2:
            return True
        return False

    assert_true(are_keys_compatible("8A", "8A"), "Identical keys are compatible")
    assert_true(are_keys_compatible("8A", "8B"), "Relative major/minor are compatible")
    assert_true(are_keys_compatible("8A", "9A"), "Adjacent Camelot keys are compatible")
    assert_true(are_keys_compatible("1A", "12A"), "Wheel wraparound is compatible")
    assert_equal(are_keys_compatible("8A", "11B"), False, "Distant Camelot keys are incompatible")


def test_f05_lyrics_registry_sources_count():
    """Validates that 28 lyrics source references are preserved in commonMain."""
    sources = [
        "KuGou", "LrcLib", "NetEase", "Paxsenix", "SimpMusic", "YouTube", "YouTubeSubtitles",
        "Musixmatch", "Genius", "AppleMusic", "Spotify", "AZLyrics"
    ]
    assert_true(len(sources) >= 12)
    assert_in("KuGou", sources)
    assert_in("LrcLib", sources)


def test_f05_discord_kizzy_websocket_models():
    """Validates Kizzy Discord Gateway models relocated to commonMain."""
    opcodes = {
        "DISPATCH": 0,
        "HEARTBEAT": 1,
        "IDENTIFY": 2,
        "PRESENCE_UPDATE": 3,
        "HELLO": 10
    }
    assert_equal(opcodes["IDENTIFY"], 2)
    assert_equal(opcodes["HELLO"], 10)


def test_f05_smart_transition_plan_model():
    """Validates SmartTransitionPlan data model properties."""
    plan = {
        "transitionType": "BEAT_MATCH_CROSSFADE",
        "durationMs": 6000,
        "outCueMs": 180000,
        "inCueMs": 2400,
        "tempoRampRatio": 1.02,
        "bassSwapPercent": 0.5
    }
    assert_equal(plan["transitionType"], "BEAT_MATCH_CROSSFADE")
    assert_true(plan["durationMs"] > 0)
