"""Tier 2 Feature 11: Boundary & Limits Tests for Authentication & Keychain."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in
from tests.e2e.harness.mocks import MockIOSKeychain, MockPlatformCrypto


def test_f11_boundary_duplicate_keychain_item():
    """Validates errSecDuplicateItem (-25299) when inserting existing account key."""
    keychain = MockIOSKeychain()
    status1 = keychain.sec_item_add("account_a", "secret_1")
    assert_equal(status1, 0)
    status2 = keychain.sec_item_add("account_a", "secret_2")
    assert_equal(status2, -25299, "Must return errSecDuplicateItem")


def test_f11_boundary_missing_keychain_item():
    """Validates sec_item_copy_matching returns None on non-existent account."""
    keychain = MockIOSKeychain()
    res = keychain.sec_item_copy_matching("non_existent_user")
    assert_equal(res, None)


def test_f11_boundary_update_non_existent_key():
    """Validates errSecItemNotFound (-25300) when updating non-existent key."""
    keychain = MockIOSKeychain()
    status = keychain.sec_item_update("unknown_key", "secret")
    assert_equal(status, -25300)


def test_f11_boundary_delete_non_existent_key():
    """Validates errSecItemNotFound (-25300) when deleting non-existent key."""
    keychain = MockIOSKeychain()
    status = keychain.sec_item_delete("unknown_key")
    assert_equal(status, -25300)


def test_f11_boundary_empty_origin_in_sapisidhash():
    """Validates SAPISIDHASH calculation when origin is empty string."""
    hash_val = MockPlatformCrypto.generate_sapisid_hash(1729000000000, "COOKIE", origin="")
    parts = hash_val.split("_")
    assert_equal(len(parts), 2)
    assert_equal(len(parts[1]), 40)


def test_f11_boundary_oversized_cookie_payload():
    """Validates storing large multi-cookie string (e.g. 16 KB) in Keychain."""
    keychain = MockIOSKeychain()
    big_cookie = "SID=" + ("X" * 16384)
    status = keychain.sec_item_add("large_cookie_jar", big_cookie)
    assert_equal(status, 0)
    retrieved = keychain.sec_item_copy_matching("large_cookie_jar")
    assert_equal(len(retrieved), len(big_cookie))
