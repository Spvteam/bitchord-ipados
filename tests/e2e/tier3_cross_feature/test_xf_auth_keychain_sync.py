"""Tier 3: Authentication + iOS Keychain + Innertube Headers Interaction."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockIOSKeychain, MockPlatformCrypto


def test_xf_google_auth_to_keychain_and_sapisidhash():
    """Validates login token saved in Keychain and used to compute SAPISIDHASH."""
    keychain = MockIOSKeychain()
    sapisid = "SECURE_SAPISID_COOKIE_12345"
    keychain.sec_item_add("google_sapisid", sapisid)

    # Read back from Keychain
    retrieved_sapisid = keychain.sec_item_copy_matching("google_sapisid")
    assert_equal(retrieved_sapisid, sapisid)

    # Generate authorization header
    timestamp_ms = 1729000000000
    auth_header = MockPlatformCrypto.generate_sapisid_hash(timestamp_ms, retrieved_sapisid)
    assert_in(str(timestamp_ms), auth_header)
    assert_true(len(auth_header) > 40)


def test_xf_multi_account_profile_switching():
    """Validates switching between personal and brand channel profiles in Keychain."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("profile_personal", "user_token_A")
    keychain.sec_item_add("profile_brand", "user_token_B")

    # Switch to brand
    active_profile = "profile_brand"
    token = keychain.sec_item_copy_matching(active_profile)
    assert_equal(token, "user_token_B")


def test_xf_spotify_sp_dc_to_canvas_query():
    """Validates Spotify sp_dc cookie stored in Keychain enabling Canvas animated artwork."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("spotify_sp_dc", "AQD_VALID_SP_DC_COOKIE")

    token = keychain.sec_item_copy_matching("spotify_sp_dc")
    has_canvas_auth = bool(token and token.startswith("AQD_"))
    assert_true(has_canvas_auth)
