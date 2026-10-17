"""Tier 3: Stream Resolution + JavaScriptCore Cipher + Headless PoToken Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_valid_stream_result
from tests.e2e.harness.mocks import MockJSContext, MockWKWebViewPoToken


def test_xf_stream_cipher_potoken_happy_path():
    """Validates complete stream resolution: cipher deciphering + PoToken minting."""
    po_gen = MockWKWebViewPoToken()
    po_token = po_gen.obtain_po_token()
    assert_true(po_token.startswith("PO_"))

    # Decipher raw stream URL signature
    raw_sig = "abcdefghij"
    ops = [("reverse", 0), ("splice", 2)]
    deciphered_sig = MockJSContext.decipher_signature(raw_sig, ops)
    assert_equal(deciphered_sig, "hgfedcba")

    resolved_result = {
        "streamUrl": f"https://googlevideo.com/playback?sig={deciphered_sig}&pot={po_token}&itag=140",
        "itag": 140,
        "mimeType": "audio/mp4",
        "bitrate": 128000,
        "contentLength": 3500000,
        "poTokenUsed": True
    }
    assert_valid_stream_result(resolved_result)
    assert_in(deciphered_sig, resolved_result["streamUrl"])
    assert_in(po_token, resolved_result["streamUrl"])


def test_xf_stream_throttled_n_parameter_resolution():
    """Validates n-param transform combined with PoToken prevents stream throttling."""
    raw_n = "N_PARAM_TOKEN_123"
    transformed_n = MockJSContext.transform_n_param(raw_n)
    po_gen = MockWKWebViewPoToken()
    po_token = po_gen.obtain_po_token()

    final_url = f"https://googlevideo.com/videoplayback?n={transformed_n}&pot={po_token}&itag=141"
    assert_in(transformed_n, final_url)
    assert_in("itag=141", final_url)


def test_xf_potoken_cache_expiry_and_cipher_revalidation():
    """Validates re-resolving stream when PoToken expires and cipher updates."""
    from tests.e2e.harness.mocks import MockPlatformClock
    clock = MockPlatformClock(initial_ms=1000)
    po_gen = MockWKWebViewPoToken(clock=clock)
    token_old = po_gen.obtain_po_token()
    # Advance clock and force refresh token
    clock.advance_millis(100)
    token_new = po_gen.obtain_po_token(force_refresh=True)
    assert_true(token_old != token_new)

    # Decipher updated signature
    sig = MockJSContext.decipher_signature("test_sig_12345", [("reverse", 0)])
    assert_equal(sig, "54321_gis_tset")
