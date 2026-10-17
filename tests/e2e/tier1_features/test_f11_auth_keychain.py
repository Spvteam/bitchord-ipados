"""Tier 1 Feature 11: Authentication & Keychain Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockPlatformCrypto, MockIOSKeychain


def test_f11_sapisidhash_generation():
    """Validates SAPISIDHASH computation: ${timestamp}_${sha1("$timestamp $sapisid $origin")}."""
    timestamp_ms = 1729000000000
    sapisid = "SAMPLE_SAPISID_COOKIE_VALUE"
    origin = "https://music.youtube.com"

    auth_hash = MockPlatformCrypto.generate_sapisid_hash(timestamp_ms, sapisid, origin)
    parts = auth_hash.split("_")
    assert_equal(len(parts), 2)
    assert_equal(parts[0], str(timestamp_ms))
    assert_equal(len(parts[1]), 40, "Hash part must be 40-char SHA1")


def test_f11_keychain_sec_item_add():
    """Validates storing auth credentials via SecItemAdd in hardware keychain."""
    keychain = MockIOSKeychain()
    status = keychain.sec_item_add("google_oauth_token", "ya29.sample_oauth_token_val")
    assert_equal(status, 0, "SecItemAdd must return errSecSuccess (0)")


def test_f11_keychain_sec_item_copy_matching():
    """Validates retrieving stored secrets via SecItemCopyMatching."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("spotify_sp_dc", "AQD_sample_spotify_cookie")
    retrieved = keychain.sec_item_copy_matching("spotify_sp_dc")
    assert_equal(retrieved, "AQD_sample_spotify_cookie")


def test_f11_keychain_sec_item_update():
    """Validates updating existing keychain credentials."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("discord_token", "old_token_123")
    status = keychain.sec_item_update("discord_token", "new_token_456")
    assert_equal(status, 0)
    assert_equal(keychain.sec_item_copy_matching("discord_token"), "new_token_456")


def test_f11_keychain_sec_item_delete():
    """Validates deleting credentials on account logout."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("session_cookie", "cookie_val")
    status = keychain.sec_item_delete("session_cookie")
    assert_equal(status, 0)
    assert_equal(keychain.sec_item_copy_matching("session_cookie"), None)


def test_f11_keychain_accessibility_attribute():
    """Validates kSecAttrAccessibleAfterFirstUnlock security attribute."""
    keychain = MockIOSKeychain()
    keychain.sec_item_add("user_secret", "secret_payload", accessibility="kSecAttrAccessibleAfterFirstUnlock")
    stored_entry = keychain._store["user_secret"]
    assert_equal(stored_entry["accessibility"], "kSecAttrAccessibleAfterFirstUnlock")
    assert_equal(stored_entry["service"], "com.music.bitchord.auth")
