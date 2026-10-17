@file:Suppress("CAST_NEVER_SUCCEEDS")

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
