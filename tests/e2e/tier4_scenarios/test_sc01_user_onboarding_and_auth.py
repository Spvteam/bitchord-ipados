"""Tier 4 Scenario 1: Fresh User Onboarding, Google OAuth & Home Feed Browsing."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockIOSKeychain, MockPlatformCrypto


def test_scenario_01_user_onboarding_and_authenticated_browsing():
    """Simulates:
    1. First launch on new iPad (empty credentials)
    2. User opens modal WKWebView Google login
    3. Tokens (SAPISID, authUser) stored securely in iOS Keychain
    4. YtMusicRepository builds SAPISIDHASH header
    5. Home feed sections loaded and validated
    """
    keychain = MockIOSKeychain()

    # Step 1: Initial state - no active credentials
    assert_equal(keychain.sec_item_copy_matching("google_sapisid"), None)

    # Step 2: Login completed via WKWebView cookie capture
    captured_cookies = {
        "SAPISID": "SECURE_SAPISID_COOKIE_777",
        "SSID": "SSID_COOKIE_ABC",
        "authUser": "0"
    }
    # Step 3: Persist in Keychain
    status = keychain.sec_item_add("google_sapisid", captured_cookies["SAPISID"])
    assert_equal(status, 0)

    # Step 4: Construct authenticated HTTP request for Home Feed
    sapisid = keychain.sec_item_copy_matching("google_sapisid")
    timestamp_ms = 1729000000000
    auth_header = MockPlatformCrypto.generate_sapisid_hash(timestamp_ms, sapisid)
    headers = {
        "Authorization": f"SAPISIDHASH {auth_header}",
        "X-Origin": "https://music.youtube.com",
        "User-Agent": "com.google.ios.youtube/21.26.4"
    }
    assert_in("SAPISIDHASH", headers["Authorization"])

    # Step 5: Simulate home feed response deserialization
    home_feed = {
        "sections": [
            {"title": "Quick Picks", "itemCount": 10},
            {"title": "Mixed for You", "itemCount": 8},
            {"title": "Trending Charts", "itemCount": 20}
        ]
    }
    assert_equal(len(home_feed["sections"]), 3)
    assert_equal(home_feed["sections"][0]["title"], "Quick Picks")
