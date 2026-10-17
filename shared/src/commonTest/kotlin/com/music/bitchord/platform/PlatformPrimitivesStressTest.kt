package com.music.bitchord.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Empirical stress test suite for multiplatform primitives:
 * - PlatformCrypto (RFC standard vectors, hex formats, large payloads, empty inputs)
 * - PlatformClock (Monotonicity under rapid loops, wall clock epoch sanity)
 * - PlatformUrl (RFC 3986 reserved vs unreserved, round-trips, percent-encoding)
 * - PlatformNormalizer (NFKC compatibility decompositions, NFD canonical decompositions)
 * - PlatformLock (Concurrent multithreaded mutual exclusion, exception safety)
 */
class PlatformPrimitivesStressTest {

    // ── PlatformCrypto Stress Tests ──────────────────────────────────────────

    @Test
    fun testMd5Rfc1321StandardVectors() {
        // RFC 1321 Test Suite
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", PlatformCrypto.md5Hex(""))
        assertEquals("0cc175b9c0f1b6a831c399e269772661", PlatformCrypto.md5Hex("a"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", PlatformCrypto.md5Hex("abc"))
        assertEquals("f96b697d7cb7938d525a2f31aaf161d0", PlatformCrypto.md5Hex("message digest"))
        assertEquals("c3fcd3d76192e4007dfb496cca67e13b", PlatformCrypto.md5Hex("abcdefghijklmnopqrstuvwxyz"))
        assertEquals(
            "d174ab98d277d9f5a5611c2c9f419d9f",
            PlatformCrypto.md5Hex("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        )
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            PlatformCrypto.md5Hex("12345678901234567890123456789012345678901234567890123456789012345678901234567890")
        )
    }

    @Test
    fun testSha1Rfc3174StandardVectors() {
        // RFC 3174 Test Vectors
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", PlatformCrypto.sha1Hex(""))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", PlatformCrypto.sha1Hex("abc"))
        assertEquals(
            "84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            PlatformCrypto.sha1Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        )
    }

    @Test
    fun testSha256Fips180StandardVectors() {
        // NIST FIPS 180-2 / 180-4 Standard Vectors
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            PlatformCrypto.sha256Hex("")
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PlatformCrypto.sha256Hex("abc")
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            PlatformCrypto.sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        )
    }

    @Test
    fun testHmacSha256Rfc4231StandardVectors() {
        // RFC 4231 Test Case 1
        // Key: 20 bytes of 0x0b
        val key1 = ByteArray(20) { 0x0b }
        val data1 = "Hi There".encodeToByteArray()
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            PlatformCrypto.hmacSha256Hex(key1, data1)
        )

        // RFC 4231 Test Case 2
        // Key: "Jefe"
        val key2 = "Jefe".encodeToByteArray()
        val data2 = "what do ya want for nothing?".encodeToByteArray()
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            PlatformCrypto.hmacSha256Hex(key2, data2)
        )

        // RFC 4231 Test Case 3
        // Key: 20 bytes of 0xaa
        val key3 = ByteArray(20) { 0xaa.toByte() }
        val data3 = ByteArray(50) { 0xdd.toByte() }
        assertEquals(
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
            PlatformCrypto.hmacSha256Hex(key3, data3)
        )

        // RFC 4231 Test Case 6: Key larger than SHA-256 block size (131 bytes of 0xaa)
        val key6 = ByteArray(131) { 0xaa.toByte() }
        val data6 = "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray()
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            PlatformCrypto.hmacSha256Hex(key6, data6)
        )
    }

    @Test
    fun testHmacSha256EmptyInputs() {
        val validKey = "secret-key".encodeToByteArray()
        val emptyData = ByteArray(0)
        // Empty data with valid key
        val emptyDataHmac = PlatformCrypto.hmacSha256(validKey, emptyData)
        assertEquals(32, emptyDataHmac.size)

        // Empty key test - checking platform behavior
        val emptyKey = ByteArray(0)
        val validData = "test-data".encodeToByteArray()
        try {
            val emptyKeyHmac = PlatformCrypto.hmacSha256(emptyKey, validData)
            assertEquals(32, emptyKeyHmac.size)
        } catch (e: IllegalArgumentException) {
            // Documenting behavior on JVM where SecretKeySpec rejects 0-length keys
            assertTrue(e.message?.contains("Empty key") == true || e.message?.contains("key") == true)
        }
    }

    @Test
    fun testPlatformCryptoLargePayloadDigest() {
        // 512 KB payload of alternating bytes
        val large = ByteArray(512 * 1024) { (it % 251).toByte() }
        val md5 = PlatformCrypto.md5(large)
        assertEquals(16, md5.size)

        val sha1 = PlatformCrypto.sha1(large)
        assertEquals(20, sha1.size)

        val sha256 = PlatformCrypto.sha256(large)
        assertEquals(32, sha256.size)

        val hmac = PlatformCrypto.hmacSha256("test-large-key".encodeToByteArray(), large)
        assertEquals(32, hmac.size)
    }

    @Test
    fun testSapisidHashFormatAndValues() {
        val ts = 1729000000000L
        val sapisid = "SAPISID_TEST_COOKIE_12345"
        val origin = "https://music.youtube.com"

        val hash = PlatformCrypto.generateSapisidHash(ts, sapisid, origin)
        val header = PlatformCrypto.generateSapisidAuthHeader(ts, sapisid, origin)

        assertEquals("SAPISIDHASH $hash", header)
        assertTrue(hash.startsWith("${ts}_"))

        val expectedDigest = PlatformCrypto.sha1Hex("$ts $sapisid $origin")
        assertEquals("${ts}_$expectedDigest", hash)
    }

    // ── PlatformClock Stress Tests ───────────────────────────────────────────

    @Test
    fun testPlatformClockRapidMonotonicSampling() {
        var prev = PlatformClock.monotonicNano()
        for (i in 0 until 50_000) {
            val curr = PlatformClock.monotonicNano()
            assertTrue(curr >= prev, "Monotonic nano inverted at sample $i: curr=$curr, prev=$prev")
            prev = curr
        }
    }

    @Test
    fun testPlatformClockEpochSensibility() {
        val now = PlatformClock.currentTimeMillis()
        // Epoch time sanity: must be after 2024-01-01 (1704067200000) and before 2038-01-01 (2145916800000)
        assertTrue(now > 1704067200000L, "Current time before 2024: $now")
        assertTrue(now < 2145916800000L, "Current time beyond 2038: $now")
    }

    // ── PlatformUrl RFC 3986 Stress Tests ────────────────────────────────────

    @Test
    fun testPlatformUrlRfc3986UnreservedCharacters() {
        // RFC 3986 Section 2.3: Unreserved characters MUST NOT be percent-encoded:
        // ALPHA (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen ("-"), underscore ("_"), period ("."), tilde ("~")
        val unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        val encoded = PlatformUrl.urlEncode(unreserved)
        assertEquals(unreserved, encoded, "RFC 3986 unreserved characters were altered")
    }

    @Test
    fun testPlatformUrlReservedCharactersPercentEncoded() {
        // Space must be encoded as %20, never '+'
        assertEquals("Hello%20World", PlatformUrl.urlEncode("Hello World"))

        // Forward slash must be %2F
        assertEquals("a%2Fb%2Fc", PlatformUrl.urlEncode("a/b/c"))

        // RFC 3986 reserved punctuation:
        val punctuation = ":/?#[]@!$&'()*+,;="
        val encoded = PlatformUrl.urlEncode(punctuation)
        assertFalse(encoded.contains("/"), "Slash should be encoded")
        assertFalse(encoded.contains("?"), "Question mark should be encoded")
        assertFalse(encoded.contains("#"), "Hash should be encoded")
        assertFalse(encoded.contains("&"), "Ampersand should be encoded")
        assertFalse(encoded.contains("="), "Equals should be encoded")
        assertFalse(encoded.contains("+"), "Plus should be encoded")

        // Round trip test
        val decoded = PlatformUrl.urlDecode(encoded)
        assertEquals(punctuation, decoded)
    }

    @Test
    fun testPlatformUrlMultilingualRoundTrip() {
        val testStrings = listOf(
            "BitChord: The Next-Gen YouTube Music Player!",
            "เพลงไทย: สบายดีไหม (Official Audio) [HD]",
            "日本語: 真夜中のドア〜stay with me",
            "한국어: 방탄소년단 (BTS) - 봄날 (Spring Day)",
            "Special chars: & < > \" ' % ^ { } ` | \\"
        )
        for (s in testStrings) {
            val encoded = PlatformUrl.urlEncode(s)
            val decoded = PlatformUrl.urlDecode(encoded)
            assertEquals(s, decoded, "Failed round-trip for: $s")
        }
    }

    @Test
    fun testPlatformUrlEmptyString() {
        assertEquals("", PlatformUrl.urlEncode(""))
        assertEquals("", PlatformUrl.urlDecode(""))
    }

    // ── PlatformNormalizer Stress Tests ──────────────────────────────────────

    @Test
    fun testPlatformNormalizerNfkcFullWidthAsciiFolding() {
        // Fullwidth ASCII characters
        val fullwidth = "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ０１２３４５６７８９"
        val expected = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        assertEquals(expected, PlatformNormalizer.normalizeNfkc(fullwidth))
    }

    @Test
    fun testPlatformNormalizerNfkcLigatureDecomposition() {
        // Standard typography ligatures: ﬀ, ﬁ, ﬂ
        val ligatures = "ﬁnding ﬂowers in oﬃce"
        val expected = "finding flowers in office"
        assertEquals(expected, PlatformNormalizer.normalizeNfkc(ligatures))
    }

    @Test
    fun testPlatformNormalizerNfdCanonicalDecomposition() {
        // In NFD, 'é' (U+00E9) is decomposed to 'e' (U+0065) + combining acute accent (U+0301)
        val composed = "café"
        val decomposed = PlatformNormalizer.normalizeNfd(composed)
        assertEquals(5, decomposed.length) // 'c', 'a', 'f', 'e', '\u0301'
        assertEquals('e', decomposed[3])
        assertEquals('\u0301', decomposed[4])
    }

    @Test
    fun testPlatformNormalizerIdempotence() {
        val input = "ＢｉｔＣｈｏｒｄ ﬁ ２０２６"
        val nfkcOnce = PlatformNormalizer.normalizeNfkc(input)
        val nfkcTwice = PlatformNormalizer.normalizeNfkc(nfkcOnce)
        assertEquals(nfkcOnce, nfkcTwice)

        val nfdOnce = PlatformNormalizer.normalizeNfd(input)
        val nfdTwice = PlatformNormalizer.normalizeNfd(nfdOnce)
        assertEquals(nfdOnce, nfdTwice)
    }

    // ── PlatformLock Stress Tests ────────────────────────────────────────────

    @Test
    fun testPlatformLockThreadSafety() {
        val lock = PlatformLock()
        var counter = 0
        val threads = mutableListOf<Thread>()
        val iterationsPerThread = 500
        val threadCount = 20

        for (t in 0 until threadCount) {
            val thread = Thread {
                for (i in 0 until iterationsPerThread) {
                    lock.withLock {
                        counter++
                    }
                }
            }
            threads += thread
            thread.start()
        }

        for (thread in threads) {
            thread.join()
        }

        assertEquals(threadCount * iterationsPerThread, counter, "PlatformLock failed race condition check")
    }

    @Test
    fun testPlatformLockExceptionSafety() {
        val lock = PlatformLock()
        var exceptionThrown = false
        try {
            lock.withLock {
                throw IllegalStateException("Forced error inside lock")
            }
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)

        // Lock must be released and immediately acquirable again without deadlocking
        var recovered = false
        lock.withLock {
            recovered = true
        }
        assertTrue(recovered, "Lock was not released after exception in withLock")
    }
}
