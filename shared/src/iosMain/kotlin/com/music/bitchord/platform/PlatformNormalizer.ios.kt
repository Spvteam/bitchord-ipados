package com.music.bitchord.platform

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping

actual object PlatformNormalizer {

    actual fun normalizeNfkc(s: String): String {
        if (s.isEmpty()) return s
        return NSString.create(string = s).precomposedStringWithCompatibilityMapping
    }

    actual fun normalizeNfd(s: String): String {
        if (s.isEmpty()) return s
        return NSString.create(string = s).decomposedStringWithCanonicalMapping
    }
}
