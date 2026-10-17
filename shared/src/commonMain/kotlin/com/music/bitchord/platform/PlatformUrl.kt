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
