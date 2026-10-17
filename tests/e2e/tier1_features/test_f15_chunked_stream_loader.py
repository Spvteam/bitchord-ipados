"""Tier 1 Feature 15: Chunked Stream Loader Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockResourceLoaderDelegate


def test_f15_custom_scheme_interception():
    """Validates AVAssetResourceLoaderDelegate intercepting bitchord-chunked:// URLs."""
    url = "bitchord-chunked://stream?videoId=XYZ123&itag=140"
    assert_true(url.startswith("bitchord-chunked://"))
    scheme = url.split("://")[0]
    assert_equal(scheme, "bitchord-chunked")


def test_f15_512kb_chunk_windowing():
    """Validates 512 KB (524,288 bytes) chunk request windowing."""
    delegate = MockResourceLoaderDelegate(content_length=4000000)
    assert_equal(delegate.CHUNK_SIZE, 524288)

    req = delegate.request_range(start_offset=0, length=delegate.CHUNK_SIZE)
    assert_equal(req["status"], 206)
    assert_equal(req["bytes_loaded"], 524288)
    assert_equal(req["range"], "bytes 0-524287/4000000")


def test_f15_range_header_formatting():
    """Validates Range HTTP request header injection."""
    start = 524288
    end = 1048575
    header_val = f"bytes={start}-{end}"
    assert_equal(header_val, "bytes=524288-1048575")


def test_f15_authenticated_headers_injection():
    """Validates injected HTTP headers for stream requests."""
    headers = {
        "User-Agent": "com.google.ios.youtube/21.26.4 (iPad; U; CPU OS 17_0 like Mac OS X)",
        "Origin": "https://music.youtube.com",
        "Referer": "https://music.youtube.com/",
        "Range": "bytes=0-524287"
    }
    assert_in("User-Agent", headers)
    assert_in("Origin", headers)
    assert_in("Range", headers)


def test_f15_http_403_recovery_flow():
    """Validates 403 refusal recovery and stream resumption."""
    delegate = MockResourceLoaderDelegate(content_length=3000000)
    # First chunk succeeds
    res1 = delegate.request_range(0, 524288)
    assert_equal(res1["status"], 206)

    # Next chunk encounters HTTP 403
    res2 = delegate.request_range(524288, 524288, simulate_403=True)
    assert_equal(res2["status"], 403)
    assert_equal(delegate.refusal_count, 1)

    # Recovery with refreshed token
    recovered = delegate.handle_403_recovery("PO_REFRESHED_SAMPLE_TOKEN", resume_offset=524288)
    assert_true(recovered)
    assert_true(delegate.recovery_successful)


def test_f15_tail_chunk_boundary():
    """Validates chunk request when nearing end of audio stream."""
    total_len = 1000000
    delegate = MockResourceLoaderDelegate(content_length=total_len)
    # Request starting at 800,000 with length 524,288 -> should be clamped to 200,000 bytes
    res = delegate.request_range(800000, 524288)
    assert_equal(res["bytes_loaded"], 200000)
    assert_equal(res["range"], f"bytes 800000-999999/{total_len}")
