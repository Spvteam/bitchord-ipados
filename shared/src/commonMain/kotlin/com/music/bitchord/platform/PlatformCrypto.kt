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
