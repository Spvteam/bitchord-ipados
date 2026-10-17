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
