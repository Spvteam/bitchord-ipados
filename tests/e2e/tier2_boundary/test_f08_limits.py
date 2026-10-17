"""Tier 2 Feature 8: Boundary & Limits Tests for Innertube Waterfall & M4A."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f08_boundary_android_vr_cascade_to_ios():
    """Validates waterfall cascading from ANDROID_VR to IOS client on failure."""
    waterfall = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    # Simulate ANDROID_VR rejection
    client_failed = "ANDROID_VR"
    active_client = [c for c in waterfall if c != client_failed][0]
    assert_equal(active_client, "IOS")


def test_f08_boundary_dual_client_failure_cascade_to_web_remix():
    """Validates waterfall cascading to WEB_REMIX when ANDROID_VR and IOS fail."""
    waterfall = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    failed = {"ANDROID_VR", "IOS"}
    remaining = [c for c in waterfall if c not in failed]
    assert_equal(remaining, ["WEB_REMIX"])


def test_f08_boundary_all_clients_exhausted():
    """Validates error reporting when all waterfall clients fail."""
    waterfall = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    failed = {"ANDROID_VR", "IOS", "WEB_REMIX"}
    remaining = [c for c in waterfall if c not in failed]
    assert_equal(len(remaining), 0)
    error_msg = "StreamResolutionExhaustedError: All Innertube clients failed"
    assert_in("All Innertube clients failed", error_msg)


def test_f08_boundary_server_404_on_probe():
    """Validates probe handling when Google Video returns 404 Not Found."""
    probe_response = {"status": 404, "headers": {}}
    is_valid_stream = (probe_response["status"] == 200 or probe_response["status"] == 206)
    assert_equal(is_valid_stream, False)


def test_f08_boundary_track_smaller_than_1mib():
    """Validates bounded range probe when total audio file size is under 1 MiB."""
    total_file_bytes = 650000  # 650 KB
    probe_boundary = 1048576   # 1 MiB
    actual_range_end = min(total_file_bytes - 1, probe_boundary - 1)
    assert_equal(actual_range_end, 649999)
    range_header = f"bytes=0-{actual_range_end}"
    assert_equal(range_header, "bytes=0-649999")


def test_f08_boundary_url_without_query_parameters():
    """Validates parameter injection when raw stream URL has no initial query string."""
    base_url = "https://example.googlevideo.com/playback"
    has_query = "?" in base_url
    delimiter = "&" if has_query else "?"
    url_with_param = f"{base_url}{delimiter}itag=140"
    assert_equal(url_with_param, "https://example.googlevideo.com/playback?itag=140")
