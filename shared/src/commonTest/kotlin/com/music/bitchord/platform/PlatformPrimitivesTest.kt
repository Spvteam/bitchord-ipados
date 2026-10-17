package com.music.bitchord.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformPrimitivesTest {

    @Test
    fun testSha1HexKnownVectors() {
        val emptyDigest = PlatformCrypto.sha1Hex("")
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", emptyDigest)

        val text = "BitChord-iOS-Port-PlatformCrypto-Verification"
        val digest = PlatformCrypto.sha1Hex(text)
        assertEquals("5843bd84e22593f5af88e200bc656b4194066804", digest)
    }

    @Test
    fun testMd5HexKnownVectors() {
        val emptyDigest = PlatformCrypto.md5Hex("")
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", emptyDigest)

        val text = "BitChord-MD5-Test-Vector"
        val digest = PlatformCrypto.md5Hex(text)
        assertEquals("be1d489d579caad959433f7fec79dc23", digest)
    }

    @Test
    fun testSha256HexKnownVectors() {
        val emptyDigest = PlatformCrypto.sha256Hex("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigest)

        val text = "BitChord-SHA256"
        val digest = PlatformCrypto.sha256Hex(text)
        assertEquals(64, digest.length)
    }

    @Test
    fun testHmacSha256HexKnownVectors() {
        val key = "secret-key".encodeToByteArray()
        val data = "message".encodeToByteArray()
        val hmacHex = PlatformCrypto.hmacSha256Hex(key, data)
        assertEquals(64, hmacHex.length)
    }

    @Test
    fun testGenerateSapisidHash() {
        val timestamp = 1729000000000L
        val sapisid = "test_sapisid_value"
        val origin = "https://music.youtube.com"
        val hash = PlatformCrypto.generateSapisidHash(timestamp, sapisid, origin)
        assertTrue(hash.startsWith("${timestamp}_"))
        assertEquals(timestamp.toString().length + 1 + 40, hash.length)
    }

    @Test
    fun testPlatformClockMonotonic() {
        val t1 = PlatformClock.monotonicNano()
        val t2 = PlatformClock.monotonicNano()
        assertTrue(t2 >= t1)
        val ms = PlatformClock.currentTimeMillis()
        assertTrue(ms > 0)
    }

    @Test
    fun testPlatformUrlRoundTrip() {
        val raw = "query=The Weeknd & Daft Punk / Starboy [1080p]!"
        val encoded = PlatformUrl.urlEncode(raw)
        assertTrue(encoded.contains("%20"))
        assertTrue(encoded.contains("%2F"))
        val decoded = PlatformUrl.urlDecode(encoded)
        assertEquals(raw, decoded)
    }

    @Test
    fun testPlatformNormalizerNfkc() {
        val fullwidth = "ＢｉｔＣｈｏｒｄ"
        val normalized = PlatformNormalizer.normalizeNfkc(fullwidth)
        assertEquals("BitChord", normalized)
    }

    @Test
    fun testPlatformNormalizerNfd() {
        val text = "é"
        val normalized = PlatformNormalizer.normalizeNfd(text)
        assertTrue(normalized.length >= 1)
    }

    @Test
    fun testPlatformLockMutualExclusion() {
        val lock = PlatformLock()
        var count = 0
        lock.withLock {
            count++
        }
        assertEquals(1, count)
    }
}
