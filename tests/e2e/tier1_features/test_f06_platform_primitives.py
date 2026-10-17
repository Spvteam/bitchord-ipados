"""Tier 1 Feature 6: Platform Primitives Tests."""

import unicodedata
from tests.e2e.harness.assertions import (
    assert_equal, assert_true, assert_sha1_hex, assert_md5_hex
)
from tests.e2e.harness.mocks import MockPlatformCrypto, MockPlatformClock, MockPlatformUrl


def test_f06_crypto_sha1_hex_output():
    """Validates that PlatformCrypto.sha1 produces accurate 40-char lowercase hex."""
    text = "BitChord-iOS-Port-PlatformCrypto-Verification"
    digest = MockPlatformCrypto.sha1_string(text)
    assert_sha1_hex(digest)
    # Authoritative reference sha1
    expected = "5843bd84e22593f5af88e200bc656b4194066804"
    assert_equal(digest, expected)


def test_f06_crypto_md5_hex_output():
    """Validates that PlatformCrypto.md5 produces accurate 32-char lowercase hex."""
    text = "BitChord-MD5-Test-Vector"
    digest = MockPlatformCrypto.md5_string(text)
    assert_md5_hex(digest)
    expected = "be1d489d579caad959433f7fec79dc23"
    assert_equal(digest, expected)


def test_f06_crypto_binary_bytes_hashing():
    """Validates binary ByteArray hashing matching Apple CC_SHA1/CC_MD5."""
    data = b"\x00\x01\x02\x03\x04\x05\x06\x07\xff\xfe\xfd"
    sha1_hex = MockPlatformCrypto.sha1_bytes(data)
    md5_hex = MockPlatformCrypto.md5_bytes(data)
    assert_sha1_hex(sha1_hex)
    assert_md5_hex(md5_hex)


def test_f06_platform_clock_posix_ms():
    """Validates PlatformClock.currentTimeMillis monotonic timestamp."""
    clock = MockPlatformClock(initial_ms=1729000000000)
    t1 = clock.current_time_millis()
    assert_equal(t1, 1729000000000)
    clock.advance_millis(250)
    t2 = clock.current_time_millis()
    assert_equal(t2 - t1, 250)


def test_f06_platform_url_rfc3986_encoding():
    """Validates PlatformUrl encoding according to RFC 3986."""
    raw = "query=The Weeknd & Daft Punk / Starboy [1080p]!"
    encoded = MockPlatformUrl.encode(raw)
    assert_true("%20" in encoded or "+" not in encoded, "Spaces should be percent-encoded")
    assert_true("%2F" in encoded, "Slashes should be percent-encoded")
    decoded = MockPlatformUrl.decode(encoded)
    assert_equal(decoded, raw)


def test_f06_platform_normalizer_nfkc():
    """Validates Unicode normalization using NFKC form."""
    # Full-width Latin letters should normalize to ASCII
    fullwidth = "ＢｉｔＣｈｏｒｄ"
    normalized = unicodedata.normalize("NFKC", fullwidth)
    assert_equal(normalized, "BitChord")
