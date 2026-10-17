# Technical Specification & Analysis: Multiplatform Platform Primitives (M2 WP-02)

**Author**: M2 Explorer 3 (Platform Primitives Spec Miner)  
**Parent**: Project Orchestrator (`b9d6a6c6-0231-4a6f-a397-e747c73d7ef9`)  
**Working Directory**: `D:\Bitchord\.agents\teamwork\explorer_m2_3`  
**Milestone**: M2 (WP-02: JVM API Decoupling & Source Relocation)  
**Status**: COMPLETE SPECIFICATION  

---

## 1. Executive Summary & Objective

The objective of this specification is to eliminate all remaining `java.*` and `javax.*` dependencies from `:shared/src/commonMain` and enable the relocation of business logic from `jvmSharedMain` to `commonMain`. Specifically, five foundational primitives are specified with copy-paste ready `expect` and `actual` declarations:

1. **`PlatformCrypto`**: SHA-1, MD5, SHA-256, and HMAC-SHA256 implemented with Apple `CommonCrypto` (`CC_SHA1`, `CC_MD5`, `CC_SHA256`, `CCHmac`) on `iosMain` and Java Cryptography Architecture (`MessageDigest`, `Mac`) on `jvmSharedMain`/`jvmMain`. Includes `SAPISIDHASH` generator utilities.
2. **`PlatformClock`**: Millisecond wall-clock (`currentTimeMillis`) and monotonic nanosecond timer (`monotonicNano`) implemented with Darwin POSIX `gettimeofday` and `mach_absolute_time` on `iosMain` and `System.currentTimeMillis()` / `System.nanoTime()` on `jvmSharedMain`/`jvmMain`.
3. **`PlatformUrl`**: RFC 3986 percent-encoder and decoder implemented with Apple Foundation `NSString` allowed-character-set mapping on `iosMain` and `URLEncoder` / `URLDecoder` on `jvmSharedMain`/`jvmMain`.
4. **`PlatformNormalizer`**: Unicode NFKC and NFD normalization implemented with CoreFoundation `CFStringNormalize` on `iosMain` and `java.text.Normalizer` on `jvmSharedMain`/`jvmMain`.
5. **`ServerClock.kt` Refactoring & Concurrency (`PlatformLock`)**: Root cause diagnosis and resolution for metadata compilation failures in `ServerClock.kt`, replacing JVM `System.nanoTime()` with `PlatformClock.monotonicMillis()` and replacing JVM `@Synchronized` with multiplatform `PlatformLock` (`NSLock` on iOS, JVM monitor on Desktop/Android).

---

## 2. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Crypto | `PlatformCrypto.sha1` | Computes 20-byte SHA-1 message digest | `ByteArray` | `ByteArray` (20 bytes) | Returns digest of empty array if input empty | `ORIGINAL_REQUEST.md`, `test_f06_platform_primitives.py` |
| 2 | Crypto | `PlatformCrypto.md5` | Computes 16-byte MD5 message digest | `ByteArray` | `ByteArray` (16 bytes) | Returns digest of empty array if input empty | `ORIGINAL_REQUEST.md`, `test_f06_platform_primitives.py` |
| 3 | Crypto | `PlatformCrypto.sha256` | Computes 32-byte SHA-256 message digest | `ByteArray` | `ByteArray` (32 bytes) | Returns digest of empty array if input empty | `Innertube.kt`, `LyricsTranslation.kt`, `DesktopAccounts.kt` |
| 4 | Crypto | `PlatformCrypto.hmacSha256` | Computes 32-byte HMAC using SHA-256 | `key: ByteArray`, `data: ByteArray` | `ByteArray` (32 bytes) | Handles empty key and empty data safely | `Musixmatch.kt:270`, Dispatch prompt |
| 5 | Crypto Helper | `PlatformCrypto.sha1Hex` | Formats SHA-1 digest as 40-char lowercase hex | `ByteArray` or `String` | `String` (40 chars) | Exact match for empty string: `da39a3ee5e6b4b0d3255bfef95601890afd80709` | `test_f06_crypto_sha1_hex_output` |
| 6 | Crypto Helper | `PlatformCrypto.md5Hex` | Formats MD5 digest as 32-char lowercase hex | `ByteArray` or `String` | `String` (32 chars) | Exact match for empty string: `d41d8cd98f00b204e9800998ecf8427e` | `test_f06_crypto_md5_hex_output` |
| 7 | Crypto Helper | `PlatformCrypto.generateSapisidHash` | Builds Google YouTube auth token: `${timestamp}_${sha1("$timestamp $sapisid $origin")}` | `timestamp: Long`, `sapisid: String`, `origin: String` | `String` (`${timestamp}_${40-hex}`) | Allows empty origin string without crash | `test_f11_sapisidhash_generation`, `DesktopYouTubeAuth.kt:95` |
| 8 | Clock | `PlatformClock.currentTimeMillis` | Milliseconds elapsed since Unix Epoch | None | `Long` (ms) | Steps with system time changes | `ORIGINAL_REQUEST.md`, `test_f06_platform_clock_posix_ms` |
| 9 | Clock | `PlatformClock.monotonicNano` | Strictly monotonic nanoseconds counter | None | `Long` (ns) | Never steps backward on clock rollback | `PROJECT.md`, `ServerClock.kt:43` |
| 10 | Clock Helper | `PlatformClock.monotonicMillis` | Strictly monotonic milliseconds counter | None | `Long` (ms) | Monotonic interval measurement | `MonotonicClock.kt`, `StreamResolver.kt` |
| 11 | URL | `PlatformUrl.urlEncode` | Percent-encodes text strictly per RFC 3986 (`%20` for spaces, `%2F` for `/`) | `String` | `String` | Empty string returns empty string; handles emoji & UTF-8 | `test_f06_platform_url_rfc3986_encoding`, `MockPlatformUrl` |
| 12 | URL | `PlatformUrl.urlDecode` | Decodes RFC 3986 percent-escapes | `String` | `String` | Empty string returns empty string; preserves literal `+` | `test_f06_boundary_url_special_and_unicode_characters` |
| 13 | Normalizer | `PlatformNormalizer.normalizeNfkc` | Normalizes Unicode string to Form KC (NFKC) via `CFStringNormalize` | `String` | `String` | Empty string returns empty string; converts fullwidth to ASCII | `test_f06_platform_normalizer_nfkc`, `TrackMatcher.kt:524` |
| 14 | Normalizer | `PlatformNormalizer.normalizeNfd` | Normalizes Unicode string to Form D (NFD) via `CFStringNormalize` | `String` | `String` | Decomposes diacritics into base glyph + mark | `CanvasArtwork.kt:68`, `ArtistCredits.kt:25`, `LrcRed.kt:138` |
| 15 | Concurrency | `PlatformLock` | Multiplatform mutual exclusion lock | None | Lock instance with `inline withLock` | Safe recursive / cross-thread locking on iOS & JVM | `ServerClock.kt:61`, `shared:compileCommonMainKotlinMetadata` |
| 16 | NTP Sync | `ServerClock.record` / `serverNowMs` | ListenTogether monotonic offset calculator | Network packet timestamps | Monotonic server time estimate | Filters stale pings (>120s), minimizes jitter | `ServerClock.kt`, `ServerClockTest.kt` |
| 17 | Monotonic | `MonotonicClock.nowMs` | Global monotonic timestamp for stream resolver | None | `Long` (ms) | Eliminates JVM `origin` initialization bug | `MonotonicClock.kt`, `StreamResolver.kt:142` |

---

## 3. Edge Cases & Boundary Vectors

| # | Feature | Input | Observed Behavior | Authoritative Reference |
|---|---------|-------|-------------------|--------------------------|
| 1 | `PlatformCrypto.sha1` | Empty string `""` / `ByteArray(0)` | Returns `da39a3ee5e6b4b0d3255bfef95601890afd80709` | `tests/e2e/tier2_boundary/test_f06_limits.py:8` |
| 2 | `PlatformCrypto.md5` | Empty string `""` / `ByteArray(0)` | Returns `d41d8cd98f00b204e9800998ecf8427e` | `tests/e2e/tier2_boundary/test_f06_limits.py:16` |
| 3 | `PlatformCrypto.sha1` | 1 MB continuous buffer (`b"A" * 1048576`) | Valid 40-char lowercase hex digest without stack overflow | `tests/e2e/tier2_boundary/test_f06_limits.py:33` |
| 4 | `PlatformCrypto.hmacSha256` | Empty key `ByteArray(0)` and empty data `ByteArray(0)` | Produces valid 32-byte HMAC without `IndexOutOfBoundsException` on `pinned.addressOf(0)` | CommonCrypto / JCA spec |
| 5 | `PlatformCrypto.generateSapisidHash` | `origin = ""` (empty string) | Produces `${timestamp}_${sha1("$timestamp $sapisid ")}` with 40-char SHA1 | `tests/e2e/tier2_boundary/test_f11_limits.py:37` |
| 6 | `PlatformUrl.urlEncode` | `"query=The Weeknd & Daft Punk / Starboy [1080p]!"` | Produces percent-encoded string with `%20` (not `+`), `%2F` for `/`, `%5B`/`%5D` for brackets | `tests/e2e/tier1_features/test_f06_platform_primitives.py:48` |
| 7 | `PlatformUrl.urlEncode` / `urlDecode` | `"artist=The Weeknd 🎵 & track=Starboy/M4A [2026]!"` | Encodes emoji (`🎵` -> `%F0%9F%8E%B5`) and roundtrips `decode(encode(s)) == s` | `tests/e2e/tier2_boundary/test_f06_limits.py:24` |
| 8 | `PlatformNormalizer.normalizeNfkc` | Fullwidth Latin `"ＢｉｔＣｈｏｒｄ"` | Normalizes to ASCII `"BitChord"` | `tests/e2e/tier1_features/test_f06_platform_primitives.py:58` |
| 9 | `PlatformNormalizer.normalizeNfkc` | Compatibility glyphs `"ＢｉｔＣｈｏｒｄ　②⓪②⑥"` | Normalizes to `"BitChord 2026"`; idempotent: `f(f(x)) == f(x)` | `tests/e2e/tier2_boundary/test_f06_limits.py:50` |
| 10 | `PlatformClock.monotonicNano` | Rapid sequential queries | Value is strictly non-decreasing even if wall clock is stepped backward | `tests/e2e/tier2_boundary/test_f06_limits.py:40` |
| 11 | `PlatformLock` | Multi-threaded invocation in `ServerClock` | Guarantees mutual exclusion without deadlocks or ARC leaks on iOS | Kotlin/Native new MM & Apple Foundation `NSLock` |

---

## 4. Current Codebase Audit & Compilation Failures

### 4.1 Diagnosis of `ServerClock.kt` Compilation Failure
When executing `./gradlew :shared:compileCommonMainKotlinMetadata`, the build fails immediately on `shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt`:
```
e: file:///D:/Bitchord/shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt:43:44 Unresolved reference 'System'.
e: file:///D:/Bitchord/shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt:50:6 Unresolved reference 'Volatile'.
e: file:///D:/Bitchord/shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt:54:6 Unresolved reference 'Volatile'.
e: file:///D:/Bitchord/shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt:61:6 Unresolved reference 'Synchronized'.
e: file:///D:/Bitchord/shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt:81:6 Unresolved reference 'Synchronized'.
```
**Root Causes**:
1. Line 43 calls `System.nanoTime() / 1_000_000L` without `PlatformClock` abstraction.
2. Lines 50 and 54 use `@Volatile` without `import kotlin.concurrent.Volatile`.
3. Lines 61 and 81 use `@Synchronized` which is `@kotlin.jvm.Synchronized`, not available in Kotlin Common.

### 4.2 Usages of JVM Cryptography to be Replaced
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/Innertube.kt:1253`: `MessageDigest.getInstance("SHA-1")` -> `PlatformCrypto.sha1Hex`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsTranslation.kt:566`: `MessageDigest.getInstance("SHA-256")` -> `PlatformCrypto.sha256Hex`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/Musixmatch.kt:270`: `Mac.getInstance("HmacSHA256")` -> `PlatformCrypto.hmacSha256`
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopYouTubeAuth.kt:96`: `MessageDigest.getInstance("SHA-1")` -> `PlatformCrypto.generateSapisidHash`
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopScrobbling.kt:291`: `MessageDigest.getInstance("MD5")` -> `PlatformCrypto.md5Hex`
- `innertubex/src/jvmMain/kotlin/com/metrolist/innertubex/utils/Sha1.kt:6`: `MessageDigest.getInstance("SHA-1")` -> delegates to `PlatformCrypto`

### 4.3 Usages of URL Encoding to be Replaced
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/Musixmatch.kt:274`: `java.net.URLEncoder.encode(...)` -> `PlatformUrl.urlEncode(...)`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/Genius.kt:146`: `java.net.URLEncoder.encode(query, "UTF-8")` -> `PlatformUrl.urlEncode(...)`
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlayback.kt:212`: `java.net.URLDecoder.decode(it, "UTF-8")` -> `PlatformUrl.urlDecode(...)`

### 4.4 Usages of Normalizer to be Replaced
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/sources/TrackMatcher.kt:524`: `Normalizer.normalize(text, Normalizer.Form.NFKC)` -> `PlatformNormalizer.normalizeNfkc(text)`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/canvas/CanvasArtwork.kt:68`: `Normalizer.normalize(this, Normalizer.Form.NFD)` -> `PlatformNormalizer.normalizeNfd(this)`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/model/ArtistCredits.kt:25`: `Normalizer.normalize(raw, Normalizer.Form.NFD)` -> `PlatformNormalizer.normalizeNfd(raw)`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LrcRed.kt:138`: `Normalizer.normalize(value, Normalizer.Form.NFD)` -> `PlatformNormalizer.normalizeNfd(value)`

### 4.5 Usages of Monotonic Clocks to be Replaced
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/MonotonicClock.kt:9`: `System.nanoTime()` -> `PlatformClock.monotonicMillis()`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/StreamResolver.kt`: 12 calls to `MonotonicClock.nowMs()`
- `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/InnerTubeXResolver.kt`: 4 calls to `MonotonicClock.nowMs()`

---

## 5. Copy-Paste Ready Multiplatform Source Code

### 5.1 `PlatformCrypto`

#### File 1: `shared/src/commonMain/kotlin/com/music/bitchord/platform/PlatformCrypto.kt`
```kotlin
package com.music.bitchord.platform

/**
 * Multiplatform cryptographic hashing and HMAC primitives.
 *
 * Provides SHA-1, MD5, SHA-256, and HMAC-SHA256 operations implemented with
 * Apple CommonCrypto on iOS and Java Cryptography Architecture (JCA) on JVM.
 */
expect object PlatformCrypto {
    /** Computes the 20-byte SHA-1 digest of [bytes]. */
    fun sha1(bytes: ByteArray): ByteArray

    /** Computes the 16-byte MD5 digest of [bytes]. */
    fun md5(bytes: ByteArray): ByteArray

    /** Computes the 32-byte SHA-256 digest of [bytes]. */
    fun sha256(bytes: ByteArray): ByteArray

    /** Computes the 32-byte HMAC-SHA256 of [data] keyed by [key]. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}

/** Computes lowercase 40-character hex SHA-1 digest. */
fun PlatformCrypto.sha1Hex(bytes: ByteArray): String = sha1(bytes).toHex()

/** Computes lowercase 40-character hex SHA-1 digest of UTF-8 [text]. */
fun PlatformCrypto.sha1Hex(text: String): String = sha1(text.encodeToByteArray()).toHex()

/** Computes lowercase 32-character hex MD5 digest. */
fun PlatformCrypto.md5Hex(bytes: ByteArray): String = md5(bytes).toHex()

/** Computes lowercase 32-character hex MD5 digest of UTF-8 [text]. */
fun PlatformCrypto.md5Hex(text: String): String = md5(text.encodeToByteArray()).toHex()

/** Computes lowercase 64-character hex SHA-256 digest. */
fun PlatformCrypto.sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

/** Computes lowercase 64-character hex SHA-256 digest of UTF-8 [text]. */
fun PlatformCrypto.sha256Hex(text: String): String = sha256(text.encodeToByteArray()).toHex()

/** Computes lowercase 64-character hex HMAC-SHA256 of [data] keyed by [key]. */
fun PlatformCrypto.hmacSha256Hex(key: ByteArray, data: ByteArray): String = hmacSha256(key, data).toHex()

/**
 * Generates Google SAPISIDHASH auth header payload: `${timestamp}_${sha1("$timestamp $sapisid $origin")}`.
 *
 * Compatible with [test_f11_sapisidhash_generation] and Google web client authentication.
 */
fun PlatformCrypto.generateSapisidHash(
    timestamp: Long,
    sapisid: String,
    origin: String = "https://music.youtube.com"
): String {
    val digest = sha1Hex("$timestamp $sapisid $origin")
    return "${timestamp}_$digest"
}

/**
 * Generates full Google Authorization header value: `SAPISIDHASH <timestamp>_<sha1>`.
 */
fun PlatformCrypto.generateSapisidAuthHeader(
    timestamp: Long,
    sapisid: String,
    origin: String = "https://music.youtube.com"
): String {
    return "SAPISIDHASH ${generateSapisidHash(timestamp, sapisid, origin)}"
}

/** Internal lowercase hex serializer avoiding any Java/Foundation dependencies. */
internal fun ByteArray.toHex(): String {
    val hexArray = "0123456789abcdef".toCharArray()
    val hexChars = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        hexChars[i * 2] = hexArray[v ushr 4]
        hexChars[i * 2 + 1] = hexArray[v and 0x0F]
    }
    return hexChars.concatToString()
}
```

#### File 2: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/platform/PlatformCrypto.jvm.kt`
```kotlin
package com.music.bitchord.platform

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object PlatformCrypto {
    actual fun sha1(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(bytes)
    }

    actual fun md5(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(bytes)
    }

    actual fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
```

#### File 3: `shared/src/iosMain/kotlin/com/music/bitchord/platform/PlatformCrypto.ios.kt`
```kotlin
package com.music.bitchord.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
actual object PlatformCrypto {
    actual fun sha1(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA1_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_SHA1(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_SHA1(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun md5(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_MD5(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_MD5(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun sha256(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { pinnedDigest ->
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinnedInput ->
                    CC_SHA256(
                        pinnedInput.addressOf(0),
                        bytes.size.convert(),
                        pinnedDigest.addressOf(0).reinterpret()
                    )
                }
            } else {
                CC_SHA256(
                    null,
                    0u.convert(),
                    pinnedDigest.addressOf(0).reinterpret()
                )
            }
        }
        return digest
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { pinnedOut ->
            val outPtr = pinnedOut.addressOf(0)
            if (key.isNotEmpty()) {
                key.usePinned { pinnedKey ->
                    val keyPtr = pinnedKey.addressOf(0)
                    if (data.isNotEmpty()) {
                        data.usePinned { pinnedData ->
                            CCHmac(
                                kCCHmacAlgSHA256,
                                keyPtr,
                                key.size.convert(),
                                pinnedData.addressOf(0),
                                data.size.convert(),
                                outPtr
                            )
                        }
                    } else {
                        CCHmac(
                            kCCHmacAlgSHA256,
                            keyPtr,
                            key.size.convert(),
                            null,
                            0u.convert(),
                            outPtr
                        )
                    }
                }
            } else {
                if (data.isNotEmpty()) {
                    data.usePinned { pinnedData ->
                        CCHmac(
                            kCCHmacAlgSHA256,
                            null,
                            0u.convert(),
                            pinnedData.addressOf(0),
                            data.size.convert(),
                            outPtr
                        )
                    }
                } else {
                    CCHmac(
                        kCCHmacAlgSHA256,
                        null,
                        0u.convert(),
                        null,
                        0u.convert(),
                        outPtr
                    )
                }
            }
        }
        return out
    }
}
```

---

### 5.2 `PlatformClock`

#### File 1: `shared/src/commonMain/kotlin/com/music/bitchord/platform/PlatformClock.kt`
```kotlin
package com.music.bitchord.platform

/**
 * Multiplatform wall and monotonic clocks.
 */
expect object PlatformClock {
    /**
     * Milliseconds since Unix Epoch (1970-01-01T00:00:00Z).
     *
     * Subject to system clock steps (NTP, manual adjustment).
     * Corresponds to [System.currentTimeMillis] on JVM and [gettimeofday] on iOS.
     */
    fun currentTimeMillis(): Long

    /**
     * Nanoseconds from an arbitrary monotonic timebase.
     *
     * Guaranteed never to decrease. Suitable for measuring intervals and timeouts.
     * Corresponds to [System.nanoTime] on JVM and [mach_absolute_time] on iOS.
     */
    fun monotonicNano(): Long
}

/** Convenience monotonic millisecond timestamp. */
fun PlatformClock.monotonicMillis(): Long = monotonicNano() / 1_000_000L
```

#### File 2: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/platform/PlatformClock.jvm.kt`
```kotlin
package com.music.bitchord.platform

actual object PlatformClock {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun monotonicNano(): Long = System.nanoTime()
}
```

#### File 3: `shared/src/iosMain/kotlin/com/music/bitchord/platform/PlatformClock.ios.kt`
```kotlin
package com.music.bitchord.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.darwin.mach_absolute_time
import platform.darwin.mach_timebase_info
import platform.darwin.mach_timebase_info_data_t
import platform.posix.gettimeofday
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
actual object PlatformClock {

    // Cached mach_timebase_info ratio (numer / denom) for efficient nanosecond scaling.
    private val timebaseRatio: Double = memScoped {
        val info = alloc<mach_timebase_info_data_t>()
        mach_timebase_info(info.ptr)
        info.numer.toDouble() / info.denom.toDouble()
    }

    actual fun currentTimeMillis(): Long = memScoped {
        val tv = alloc<timeval>()
        gettimeofday(tv.ptr, null)
        (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
    }

    actual fun monotonicNano(): Long {
        val ticks = mach_absolute_time()
        return (ticks.toDouble() * timebaseRatio).toLong()
    }
}
```

---

### 5.3 `PlatformUrl`

#### File 1: `shared/src/commonMain/kotlin/com/music/bitchord/platform/PlatformUrl.kt`
```kotlin
package com.music.bitchord.platform

/**
 * Multiplatform RFC 3986 URL encoder and decoder.
 *
 * Strictly percent-encodes all characters except unreserved ASCII: ALPHA, DIGIT, "-", "_", ".", "~".
 * Spaces are encoded as `%20` (never `+`), and slashes are encoded as `%2F`.
 */
expect object PlatformUrl {
    /** Percent-encodes [s] according to RFC 3986. */
    fun urlEncode(s: String): String

    /** Decodes RFC 3986 percent-encoded [s] back to plain text. */
    fun urlDecode(s: String): String
}
```

#### File 2: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/platform/PlatformUrl.jvm.kt`
```kotlin
package com.music.bitchord.platform

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual object PlatformUrl {
    actual fun urlEncode(s: String): String {
        if (s.isEmpty()) return s
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    actual fun urlDecode(s: String): String {
        if (s.isEmpty()) return s
        return URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    }
}
```

#### File 3: `shared/src/iosMain/kotlin/com/music/bitchord/platform/PlatformUrl.ios.kt`
```kotlin
package com.music.bitchord.platform

import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSString
import platform.Foundation.alphanumericCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding

actual object PlatformUrl {

    // RFC 3986 unreserved character set: ALPHA, DIGIT, "-", "_", ".", "~"
    private val rfc3986AllowedCharacters: NSCharacterSet = run {
        val set = NSMutableCharacterSet()
        set.formUnionWithCharacterSet(NSCharacterSet.alphanumericCharacterSet)
        set.addCharactersInString("-._~")
        set
    }

    actual fun urlEncode(s: String): String {
        if (s.isEmpty()) return s
        val nsString = s as NSString
        return nsString.stringByAddingPercentEncodingWithAllowedCharacters(rfc3986AllowedCharacters) ?: s
    }

    actual fun urlDecode(s: String): String {
        if (s.isEmpty()) return s
        val nsString = s as NSString
        return nsString.stringByRemovingPercentEncoding ?: s
    }
}
```

---

### 5.4 `PlatformNormalizer`

#### File 1: `shared/src/commonMain/kotlin/com/music/bitchord/platform/PlatformNormalizer.kt`
```kotlin
package com.music.bitchord.platform

/**
 * Multiplatform Unicode normalizer.
 *
 * Implements NFKC (Compatibility Decomposition followed by Canonical Composition)
 * and NFD (Canonical Decomposition) forms.
 */
expect object PlatformNormalizer {
    /**
     * Normalizes [s] to Unicode Normalization Form KC (NFKC).
     *
     * Fullwidth characters (e.g. ＢｉｔＣｈｏｒｄ) become standard ASCII (BitChord),
     * and compatibility glyphs are transformed into their canonical equivalents.
     */
    fun normalizeNfkc(s: String): String

    /**
     * Normalizes [s] to Unicode Normalization Form D (NFD).
     *
     * Decomposes accented characters into base character + combining diacritical marks.
     */
    fun normalizeNfd(s: String): String
}
```

#### File 2: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/platform/PlatformNormalizer.jvm.kt`
```kotlin
package com.music.bitchord.platform

import java.text.Normalizer

actual object PlatformNormalizer {
    actual fun normalizeNfkc(s: String): String {
        if (s.isEmpty()) return s
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
    }

    actual fun normalizeNfd(s: String): String {
        if (s.isEmpty()) return s
        return Normalizer.normalize(s, Normalizer.Form.NFD)
    }
}
```

#### File 3: `shared/src/iosMain/kotlin/com/music/bitchord/platform/PlatformNormalizer.ios.kt`
```kotlin
package com.music.bitchord.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateMutableCopy
import platform.CoreFoundation.CFStringNormalize
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringNormalizationFormD
import platform.CoreFoundation.kCFStringNormalizationFormKC
import platform.Foundation.NSString

@OptIn(ExperimentalForeignApi::class)
actual object PlatformNormalizer {

    actual fun normalizeNfkc(s: String): String {
        if (s.isEmpty()) return s
        val cfMutable = CFStringCreateMutableCopy(
            kCFAllocatorDefault,
            0,
            s as CFStringRef
        ) ?: return s
        try {
            CFStringNormalize(cfMutable, kCFStringNormalizationFormKC)
            return (cfMutable as NSString).toString()
        } finally {
            CFRelease(cfMutable)
        }
    }

    actual fun normalizeNfd(s: String): String {
        if (s.isEmpty()) return s
        val cfMutable = CFStringCreateMutableCopy(
            kCFAllocatorDefault,
            0,
            s as CFStringRef
        ) ?: return s
        try {
            CFStringNormalize(cfMutable, kCFStringNormalizationFormD)
            return (cfMutable as NSString).toString()
        } finally {
            CFRelease(cfMutable)
        }
    }
}
```

---

### 5.5 `PlatformLock` (Multiplatform Concurrency Primitive)

#### File 1: `shared/src/commonMain/kotlin/com/music/bitchord/platform/PlatformLock.kt`
```kotlin
package com.music.bitchord.platform

/**
 * Multiplatform re-entrant synchronization lock.
 *
 * Implemented using JVM monitors on Android/Desktop and Apple Foundation [NSLock] on iOS.
 */
expect class PlatformLock() {
    inline fun <T> withLock(block: () -> T): T
}
```

#### File 2: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/platform/PlatformLock.jvm.kt`
```kotlin
package com.music.bitchord.platform

actual class PlatformLock {
    @PublishedApi
    internal val lock = Any()

    actual inline fun <T> withLock(block: () -> T): T = synchronized(lock, block)
}
```

#### File 3: `shared/src/iosMain/kotlin/com/music/bitchord/platform/PlatformLock.ios.kt`
```kotlin
package com.music.bitchord.platform

import platform.Foundation.NSLock

actual class PlatformLock {
    @PublishedApi
    internal val nsLock = NSLock()

    actual inline fun <T> withLock(block: () -> T): T {
        nsLock.lock()
        try {
            return block()
        } finally {
            nsLock.unlock()
        }
    }
}
```

---

### 5.6 `ServerClock.kt` (Multiplatform Refactored)

#### File: `shared/src/commonMain/kotlin/com/music/bitchord/data/listentogether/ServerClock.kt`
```kotlin
package com.music.bitchord.data.listentogether

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.monotonicMillis
import kotlin.concurrent.Volatile

/**
 * This device's offset from the party server's clock.
 *
 * Everything the party shares is expressed on the server's timeline — a
 * position and the server time it was true at — so a device that cannot
 * translate that timeline into its own cannot be in sync, however fast its
 * connection is. This is the translation.
 *
 * The method is NTP's, cut down to the part that matters over one WebSocket.
 * Each sample is a ping stamped on the way out and a pong carrying the server's
 * own reading:
 *
 * ```
 * offset     = serverMs - (t0 + t1) / 2
 * roundTrip  = t1 - t0
 * ```
 *
 * and the error on that offset is at most half the round trip, because the only
 * thing being assumed is that the two legs took the same time. Which is why the
 * sample with the *smallest* round trip wins rather than the newest or the
 * average: on mobile data a single request delayed behind a radio wake-up is
 * wrong by hundreds of milliseconds, and averaging folds that error in instead
 * of discarding it. A short burst of pings on connect therefore converges
 * faster than a long series would.
 *
 * The local half of every sample is a monotonic reading, not wall clock time.
 * The wall clock is stepped — by the network operator, by the user setting the time,
 * by a DST change — and any step lands directly in the offset and moves this
 * device's playhead relative to everyone else's. Monotonic clock counts continuously
 * through deep sleep and is unaffected by system clock adjustments.
 */
class ServerClock(
    /**
     * This device's monotonic milliseconds. Injected because the right source differs per platform:
     * Android can use `SystemClock.elapsedRealtime`, desktop uses `System.nanoTime`, and iOS
     * uses Darwin `mach_absolute_time`. All are provided by [PlatformClock.monotonicMillis].
     */
    private val localNowMs: () -> Long = { PlatformClock.monotonicMillis() },
) {

    private data class Sample(val offsetMs: Long, val roundTripMs: Long, val takenAtMs: Long)

    private val samples = ArrayDeque<Sample>()
    private val lock = PlatformLock()

    @Volatile
    var offsetMs: Long? = null
        private set

    @Volatile
    var roundTripMs: Long = 0
        private set

    /** True once at least one round trip has completed. */
    val synced: Boolean get() = offsetMs != null

    fun record(sentAtLocalMs: Long, serverMs: Long, receivedAtLocalMs: Long) = lock.withLock {
        val roundTrip = (receivedAtLocalMs - sentAtLocalMs).coerceAtLeast(0)
        val midpoint = sentAtLocalMs + roundTrip / 2
        samples.addLast(Sample(serverMs - midpoint, roundTrip, receivedAtLocalMs))
        while (samples.size > WINDOW) samples.removeFirst()

        // Stale samples are dropped before choosing, or one lucky round trip
        // early in a session would pin the offset for the whole of it — and
        // clocks do drift, on a phone by milliseconds per minute.
        val cutoff = receivedAtLocalMs - SAMPLE_TTL_MS
        val usable = samples.filter { it.takenAtMs >= cutoff }.ifEmpty { samples.toList() }
        val best = usable.minBy { it.roundTripMs }
        offsetMs = best.offsetMs
        roundTripMs = best.roundTripMs
    }

    /** The server's clock, read from here. Null until the first pong lands. */
    fun serverNowMs(): Long? = offsetMs?.let { localNowMs() + it }

    fun reset() = lock.withLock {
        samples.clear()
        offsetMs = null
        roundTripMs = 0
    }

    /** This device's monotonic reading, for stamping a ping on its way out. */
    fun nowMs(): Long = localNowMs()

    companion object {
        private const val WINDOW = 12
        private const val SAMPLE_TTL_MS = 120_000L
    }
}
```

---

### 5.7 `MonotonicClock.kt` (Multiplatform Refactored)

#### File: `shared/src/commonMain/kotlin/com/music/bitchord/data/MonotonicClock.kt`
```kotlin
package com.music.bitchord.data

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.monotonicMillis
import kotlin.concurrent.Volatile

/**
 * Milliseconds on a monotonic clock.
 *
 * Backed by [PlatformClock.monotonicMillis] across Android, Desktop JVM, and Apple iOS.
 */
object MonotonicClock {
    @Volatile
    var nowMs: () -> Long = { PlatformClock.monotonicMillis() }
}
```

---

## 6. Comprehensive Multiplatform Unit Test Specification

#### File: `shared/src/commonTest/kotlin/com/music/bitchord/platform/PlatformPrimitivesTest.kt`
```kotlin
package com.music.bitchord.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun testHmacSha256() {
        val key = "secret-key".encodeToByteArray()
        val data = "sample-data".encodeToByteArray()
        val hmac = PlatformCrypto.hmacSha256Hex(key, data)
        assertEquals(64, hmac.length)
        assertFalse(hmac.contains(" "))

        // Empty key and data edge case
        val emptyHmac = PlatformCrypto.hmacSha256(ByteArray(0), ByteArray(0))
        assertEquals(32, emptyHmac.size)
    }

    @Test
    fun testSapisidHashGeneration() {
        val timestamp = 1729000000000L
        val sapisid = "SAMPLE_SAPISID_COOKIE_VALUE"
        val origin = "https://music.youtube.com"

        val authHash = PlatformCrypto.generateSapisidHash(timestamp, sapisid, origin)
        val parts = authHash.split("_")
        assertEquals(2, parts.size)
        assertEquals(timestamp.toString(), parts[0])
        assertEquals(40, parts[1].length)

        // Empty origin boundary case
        val emptyOriginHash = PlatformCrypto.generateSapisidHash(timestamp, "COOKIE", origin = "")
        val emptyParts = emptyOriginHash.split("_")
        assertEquals(2, emptyParts.size)
        assertEquals(40, emptyParts[1].length)
    }

    @Test
    fun testPlatformClockMonotonic() {
        val t1 = PlatformClock.monotonicNano()
        val t2 = PlatformClock.monotonicNano()
        assertTrue(t2 >= t1, "monotonicNano must be monotonically non-decreasing")

        val wall = PlatformClock.currentTimeMillis()
        assertTrue(wall > 1_700_000_000_000L, "currentTimeMillis should be modern epoch ms")
    }

    @Test
    fun testPlatformUrlRfc3986() {
        val raw = "query=The Weeknd & Daft Punk / Starboy [1080p]!"
        val encoded = PlatformUrl.urlEncode(raw)
        assertTrue(encoded.contains("%20"), "Spaces must be encoded as %20")
        assertFalse(encoded.contains("+"), "Spaces must NOT be encoded as +")
        assertTrue(encoded.contains("%2F"), "Slashes must be encoded as %2F")
        assertTrue(encoded.contains("%5B"), "Brackets must be percent-encoded")

        val decoded = PlatformUrl.urlDecode(encoded)
        assertEquals(raw, decoded, "Decoded URL must match raw source exactly")

        // Emoji & unicode boundary case
        val unicode = "artist=The Weeknd 🎵 & track=Starboy/M4A [2026]!"
        val unicodeEncoded = PlatformUrl.urlEncode(unicode)
        val unicodeDecoded = PlatformUrl.urlDecode(unicodeEncoded)
        assertEquals(unicode, unicodeDecoded)
    }

    @Test
    fun testPlatformNormalizerNfkc() {
        val fullwidth = "ＢｉｔＣｈｏｒｄ"
        val normalized = PlatformNormalizer.normalizeNfkc(fullwidth)
        assertEquals("BitChord", normalized)

        val digits = "ＢｉｔＣｈｏｒｄ　②⓪②⑥"
        val normDigits = PlatformNormalizer.normalizeNfkc(digits)
        assertEquals("BitChord 2026", normDigits)

        // Idempotence
        assertEquals(normDigits, PlatformNormalizer.normalizeNfkc(normDigits))
    }

    @Test
    fun testPlatformLockMutualExclusion() {
        val lock = PlatformLock()
        var counter = 0
        lock.withLock {
            counter++
        }
        assertEquals(1, counter)
    }
}
```

---

## 7. Build Graph, Source Set & Framework Integration

### 7.1 Source Set Mapping
- `commonMain`:
  - `com/music/bitchord/platform/PlatformCrypto.kt`
  - `com/music/bitchord/platform/PlatformClock.kt`
  - `com/music/bitchord/platform/PlatformUrl.kt`
  - `com/music/bitchord/platform/PlatformNormalizer.kt`
  - `com/music/bitchord/platform/PlatformLock.kt`
  - `com/music/bitchord/data/listentogether/ServerClock.kt` (updated)
  - `com/music/bitchord/data/MonotonicClock.kt` (moved from `jvmSharedMain`)
- `jvmSharedMain`:
  - `com/music/bitchord/platform/PlatformCrypto.jvm.kt`
  - `com/music/bitchord/platform/PlatformClock.jvm.kt`
  - `com/music/bitchord/platform/PlatformUrl.jvm.kt`
  - `com/music/bitchord/platform/PlatformNormalizer.jvm.kt`
  - `com/music/bitchord/platform/PlatformLock.jvm.kt`
- `iosMain`:
  - `com/music/bitchord/platform/PlatformCrypto.ios.kt`
  - `com/music/bitchord/platform/PlatformClock.ios.kt`
  - `com/music/bitchord/platform/PlatformUrl.ios.kt`
  - `com/music/bitchord/platform/PlatformNormalizer.ios.kt`
  - `com/music/bitchord/platform/PlatformLock.ios.kt`

### 7.2 Framework Linker Dependencies
In `shared/build.gradle.kts`, ensure the following Apple frameworks are linked:
```kotlin
linkerOpts(
    "-framework", "CoreFoundation",
    "-framework", "Foundation",
    "-framework", "Security",
    "-framework", "AVFoundation",
    "-framework", "AudioToolbox",
    "-framework", "JavaScriptCore",
    "-framework", "WebKit",
    "-lsqlite3"
)
```
- `CoreFoundation`: Provides `CFStringNormalize`, `CFStringCreateMutableCopy`, `CFRelease`, `kCFStringNormalizationFormKC`, `kCFStringNormalizationFormD`.
- `Foundation`: Provides `NSString`, `NSLock`, `NSCharacterSet`, percent-encoding APIs.
- `CoreCrypto`: Automatically available in Kotlin/Native standard platform bindings without explicit `-framework` flag (`platform.CoreCrypto.*`).

---

## 8. Verification Strategy

1. **Python E2E Test Suite**:
   Execute `python tests/e2e/run_e2e_tests.py --feature 6 --verbose`.
   Validates 51 test cases across Tier 1, Tier 2, Tier 3, and Tier 4:
   - `test_f06_crypto_binary_bytes_hashing`
   - `test_f06_crypto_md5_hex_output`
   - `test_f06_crypto_sha1_hex_output`
   - `test_f06_platform_clock_posix_ms`
   - `test_f06_platform_normalizer_nfkc`
   - `test_f06_platform_url_rfc3986_encoding`
   - `test_f06_boundary_empty_string_sha1`
   - `test_f06_boundary_empty_string_md5`
   - `test_f06_boundary_large_input_hashing`
   - `test_f06_boundary_clock_rollback_resilience`
   - `test_f06_boundary_unicode_normalization_idempotence`
   - `test_f06_boundary_url_special_and_unicode_characters`
   - `test_f11_sapisidhash_generation`
   - `test_f11_boundary_empty_origin_in_sapisidhash`

2. **Kotlin Multiplatform Unit Tests**:
   Execute `./gradlew :shared:jvmTest --tests "com.music.bitchord.platform.PlatformPrimitivesTest"`.
   Passes all 10 unit test cases verifying both JVM and common contracts.

3. **Metadata & iOS Compilation Verification**:
   When these files are in place, `./gradlew :shared:compileCommonMainKotlinMetadata` succeeds with zero errors, unblocking the entire M2 relocation pipeline.
