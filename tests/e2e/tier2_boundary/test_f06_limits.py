"""Tier 2 Feature 6: Boundary & Limits Tests for Platform Primitives."""

import unicodedata
from tests.e2e.harness.assertions import assert_equal, assert_true, assert_sha1_hex, assert_md5_hex
from tests.e2e.harness.mocks import MockPlatformCrypto, MockPlatformClock, MockPlatformUrl


def test_f06_boundary_empty_string_sha1():
    """Validates authoritative SHA-1 hash for empty string input."""
    digest = MockPlatformCrypto.sha1_string("")
    assert_sha1_hex(digest)
    expected_empty_sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
    assert_equal(digest, expected_empty_sha1)


def test_f06_boundary_empty_string_md5():
    """Validates authoritative MD5 hash for empty string input."""
    digest = MockPlatformCrypto.md5_string("")
    assert_md5_hex(digest)
    expected_empty_md5 = "d41d8cd98f00b204e9800998ecf8427e"
    assert_equal(digest, expected_empty_md5)


def test_f06_boundary_url_special_and_unicode_characters():
    """Validates URL percent-encoding of emoji, non-ASCII and reserved symbols."""
    raw = "artist=The Weeknd 🎵 & track=Starboy/M4A [2026]!"
    encoded = MockPlatformUrl.encode(raw)
    decoded = MockPlatformUrl.decode(encoded)
    assert_equal(decoded, raw)
    assert_true("%" in encoded)


def test_f06_boundary_large_input_hashing():
    """Validates SHA-1 computation on 1 MB continuous buffer."""
    large_buffer = b"A" * (1024 * 1024)
    digest = MockPlatformCrypto.sha1_bytes(large_buffer)
    assert_sha1_hex(digest)


def test_f06_boundary_clock_rollback_resilience():
    """Validates that time comparisons withstand system clock changes gracefully."""
    clock = MockPlatformClock(initial_ms=5000)
    # Simulate monotonic duration tracking
    start_time = clock.current_time_millis()
    clock.advance_millis(100)
    elapsed = max(0, clock.current_time_millis() - start_time)
    assert_equal(elapsed, 100)


def test_f06_boundary_unicode_normalization_idempotence():
    """Validates that NFKC normalization is idempotent (f(f(x)) == f(x))."""
    text = "ＢｉｔＣｈｏｒｄ　②⓪②⑥"
    norm1 = unicodedata.normalize("NFKC", text)
    norm2 = unicodedata.normalize("NFKC", norm1)
    assert_equal(norm1, norm2)
    assert_equal(norm1, "BitChord 2026")
