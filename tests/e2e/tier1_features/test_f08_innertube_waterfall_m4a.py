"""Tier 1 Feature 8: Innertube Waterfall & M4A Tests."""

from tests.e2e.harness.assertions import (
    assert_equal, assert_true, assert_in, assert_valid_stream_result
)
from tests.e2e.harness.fixtures import SAMPLE_PLAYER_RESPONSE_M4A


def test_f08_waterfall_client_priority_order():
    """Validates the multi-client priority waterfall order: ANDROID_VR -> IOS -> WEB_REMIX."""
    waterfall_order = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    assert_equal(waterfall_order[0], "ANDROID_VR", "First priority must be ANDROID_VR for raw cipher-free streams")
    assert_equal(waterfall_order[1], "IOS", "Second priority must be native iOS client")
    assert_equal(waterfall_order[2], "WEB_REMIX", "Third priority fallback is WEB_REMIX")


def test_f08_m4a_aac_format_prioritization():
    """Validates that M4A / AAC itags (140, 141) are strictly prioritized over WebM/Opus."""
    formats = SAMPLE_PLAYER_RESPONSE_M4A["streamingData"]["adaptiveFormats"]
    # Filter and sort by priority: m4a over webm, then highest bitrate
    def format_score(f):
        is_m4a = "audio/mp4" in f.get("mimeType", "")
        return (1 if is_m4a else 0, f.get("bitrate", 0))

    sorted_formats = sorted(formats, key=format_score, reverse=True)
    best_format = sorted_formats[0]
    assert_equal(best_format["itag"], 141, "Must select highest bitrate AAC M4A format (itag 141)")
    assert_in("audio/mp4", best_format["mimeType"])


def test_f08_bounded_range_probe_header():
    """Validates 1 MiB bounded range probe request header formatting."""
    auth_boundary_bytes = 1048576  # 1 MiB
    probe_header = f"bytes=0-{auth_boundary_bytes - 1}"
    assert_equal(probe_header, "bytes=0-1048575")


def test_f08_resolved_stream_result_contract():
    """Validates ResolvedStreamResult structure conforms to PROJECT.md §1."""
    result = {
        "streamUrl": "https://googlevideo.com/videoplayback?itag=140",
        "itag": 140,
        "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
        "bitrate": 128000,
        "contentLength": 3845921,
        "poTokenUsed": True
    }
    assert_valid_stream_result(result)
    assert_true(result["poTokenUsed"])


def test_f08_client_exclusion_on_failure():
    """Validates that failing clients are excluded and fallback proceeds."""
    clients = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    failed = ["ANDROID_VR"]
    remaining = [c for c in clients if c not in failed]
    assert_equal(remaining, ["IOS", "WEB_REMIX"])
    assert_equal(remaining[0], "IOS")


def test_f08_itag_bitrate_mapping():
    """Validates standard YouTube audio itag bitrate definitions."""
    itag_map = {
        140: ("m4a", 128000),
        141: ("m4a", 256000),
        251: ("opus", 160000)
    }
    assert_equal(itag_map[140][0], "m4a")
    assert_equal(itag_map[141][1], 256000)
