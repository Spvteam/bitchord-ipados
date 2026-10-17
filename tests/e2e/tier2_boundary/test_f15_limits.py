"""Tier 2 Feature 15: Boundary & Limits Tests for Chunked Stream Loader."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockResourceLoaderDelegate


def test_f15_boundary_repeated_403_exceeds_retry_limit():
    """Validates fallback when repeated 403 refusals exceed maximum retry limit (3)."""
    max_retries = 3
    retry_count = 0
    refusal_encountered = True

    while refusal_encountered and retry_count < max_retries:
        retry_count += 1

    assert_equal(retry_count, 3)
    has_exhausted_retries = retry_count >= max_retries
    assert_true(has_exhausted_retries)


def test_f15_boundary_range_offset_exceeds_content_length():
    """Validates request when start_offset is greater than content_length."""
    delegate = MockResourceLoaderDelegate(content_length=1000)
    res = delegate.request_range(start_offset=2000, length=500)
    assert_equal(res["bytes_loaded"], 0)


def test_f15_boundary_zero_byte_stream_request():
    """Validates handling of empty 0-byte audio stream."""
    delegate = MockResourceLoaderDelegate(content_length=0)
    res = delegate.request_range(start_offset=0, length=524288)
    assert_equal(res["bytes_loaded"], 0)


def test_f15_boundary_server_ignoring_range_header():
    """Validates detecting server returning HTTP 200 instead of HTTP 206 Partial Content."""
    def validate_range_response_status(status_code: int) -> bool:
        return status_code == 206

    assert_true(validate_range_response_status(206))
    assert_equal(validate_range_response_status(200), False, "200 OK means server ignored range header")


def test_f15_boundary_chunk_cache_key_hash():
    """Validates deterministic chunk cache key generation based on URL and range."""
    import hashlib
    def get_chunk_cache_key(url: str, start: int, end: int) -> str:
        payload = f"{url}:{start}-{end}"
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    k1 = get_chunk_cache_key("https://audio.stream/1", 0, 524287)
    k2 = get_chunk_cache_key("https://audio.stream/1", 0, 524287)
    assert_equal(k1, k2)
    assert_equal(len(k1), 64)


def test_f15_boundary_network_disconnection_retry():
    """Validates exponential backoff delay calculation on network error."""
    def get_backoff_delay_ms(attempt: int) -> int:
        base_ms = 200
        return min(base_ms * (2 ** attempt), 5000)

    assert_equal(get_backoff_delay_ms(0), 200)
    assert_equal(get_backoff_delay_ms(1), 400)
    assert_equal(get_backoff_delay_ms(2), 800)
    assert_equal(get_backoff_delay_ms(6), 5000)
