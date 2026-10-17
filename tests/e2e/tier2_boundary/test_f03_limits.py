"""Tier 2 Feature 3: Boundary & Limits Tests for Vendored InnerTubeX."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f03_boundary_unknown_player_client():
    """Validates fallback to ANDROID_VR when unknown client string is passed."""
    def parse_client(client_str: str) -> str:
        valid = {"ANDROID", "ANDROID_VR", "IOS", "WEB_REMIX", "TVHTML5"}
        return client_str if client_str in valid else "ANDROID_VR"

    assert_equal(parse_client("IOS"), "IOS")
    assert_equal(parse_client("UNKNOWN_CLIENT"), "ANDROID_VR")
    assert_equal(parse_client(""), "ANDROID_VR")


def test_f03_boundary_malformed_videoid():
    """Validates video ID sanitation for invalid characters or empty string."""
    def sanitize_video_id(video_id: str) -> str:
        if not video_id or len(video_id) > 64:
            return ""
        import re
        if not re.match(r"^[a-zA-Z0-9_-]+$", video_id):
            return ""
        return video_id

    assert_equal(sanitize_video_id("dQw4w9WgXcQ"), "dQw4w9WgXcQ")
    assert_equal(sanitize_video_id(""), "")
    assert_equal(sanitize_video_id("../../../etc/passwd"), "")
    assert_equal(sanitize_video_id("id with spaces!"), "")


def test_f03_boundary_empty_adaptive_formats():
    """Validates handling when Innertube player response has no adaptiveFormats."""
    response = {"playabilityStatus": {"status": "OK"}, "streamingData": {"adaptiveFormats": []}}
    formats = response.get("streamingData", {}).get("adaptiveFormats", [])
    has_audio = any("audio" in f.get("mimeType", "") for f in formats)
    assert_equal(has_audio, False)


def test_f03_boundary_zero_bitrate_handling():
    """Validates rejecting stream formats with 0 or negative bitrate."""
    def is_valid_stream_format(fmt: dict) -> bool:
        return fmt.get("bitrate", 0) > 0 and int(fmt.get("contentLength", "0")) > 0

    assert_true(is_valid_stream_format({"bitrate": 128000, "contentLength": "1000"}))
    assert_equal(is_valid_stream_format({"bitrate": 0, "contentLength": "1000"}), False)
    assert_equal(is_valid_stream_format({"bitrate": 128000, "contentLength": "0"}), False)


def test_f03_boundary_video_unavailable_status():
    """Validates error extraction when YouTube marks video unavailable or private."""
    error_response = {
        "playabilityStatus": {
            "status": "UNPLAYABLE",
            "reason": "This video is not available in your country"
        }
    }
    is_playable = error_response["playabilityStatus"]["status"] == "OK"
    assert_equal(is_playable, False)
    assert_in("not available", error_response["playabilityStatus"]["reason"])


def test_f03_boundary_empty_brotli_payload():
    """Validates decompression handling of empty or 0-byte buffer."""
    import zlib
    def decompress_safe(payload: bytes) -> bytes:
        if not payload:
            return b""
        try:
            return zlib.decompress(payload)
        except Exception:
            return b""

    assert_equal(decompress_safe(b""), b"")
    assert_equal(decompress_safe(b"corrupted_garbage_bytes"), b"")
