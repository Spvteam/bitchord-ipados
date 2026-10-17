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
