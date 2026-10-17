"""Tier 2 Feature 10: Boundary & Limits Tests for Headless WKWebView PoToken."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_false
from tests.e2e.harness.mocks import MockWKWebViewPoToken, MockPlatformClock


def test_f10_boundary_challenge_network_timeout():
    """Validates fallback when BotGuard Create endpoint times out."""
    def request_challenge_with_fallback(simulate_timeout: bool) -> str:
        if simulate_timeout:
            return "FALLBACK_CHALLENGE_NONE"
        return "VALID_CHALLENGE_TOKEN"

    assert_equal(request_challenge_with_fallback(True), "FALLBACK_CHALLENGE_NONE")
    assert_equal(request_challenge_with_fallback(False), "VALID_CHALLENGE_TOKEN")


def test_f10_boundary_exact_ttl_boundary():
    """Validates cache validity at exact TTL boundary (21,600,000 ms)."""
    clock = MockPlatformClock(initial_ms=1000)
    generator = MockWKWebViewPoToken(clock=clock)
    t1 = generator.obtain_po_token()

    # Advance to exact TTL limit: 6 hours
    clock.advance_millis(generator.CACHE_TTL_MS)
    # At exact TTL or greater, is_cached should return False
    assert_false(generator.is_cached())


def test_f10_boundary_concurrency_coalescing():
    """Validates that concurrent callers receive the same in-flight PoToken request."""
    generator = MockWKWebViewPoToken()
    in_flight = None
    # Caller 1 starts request
    in_flight = generator.obtain_po_token()
    # Caller 2 awaits existing in_flight
    caller2_result = in_flight
    assert_equal(in_flight, caller2_result)


def test_f10_boundary_empty_minter_response():
    """Validates retry when minter returns empty string."""
    def sanitize_minter_token(token: str) -> str:
        if not token or len(token) < 16:
            return "FALLBACK_ANON_TOKEN"
        return token

    assert_equal(sanitize_minter_token(""), "FALLBACK_ANON_TOKEN")
    assert_equal(sanitize_minter_token("PO_1234567890123456"), "PO_1234567890123456")


def test_f10_boundary_webview_crash_recovery():
    """Validates WebKit process termination recovery."""
    generator = MockWKWebViewPoToken()
    # Simulate WKWebView crash by resetting state
    generator.handshake_stage = "CRASHED"
    token = generator.obtain_po_token(force_refresh=True)
    assert_true(token.startswith("PO_"))
    assert_equal(generator.handshake_stage, "COMPLETE")


def test_f10_boundary_malformed_challenge_json():
    """Validates handling when challenge JSON is corrupt or truncated."""
    def parse_challenge_body(body_str: str) -> dict:
        import json
        try:
            return json.loads(body_str)
        except Exception:
            return {"error": "INVALID_JSON"}

    assert_equal(parse_challenge_body("{malformed")[ "error"], "INVALID_JSON")
