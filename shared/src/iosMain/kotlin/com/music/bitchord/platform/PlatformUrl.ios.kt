@file:Suppress("CAST_NEVER_SUCCEEDS")

package com.music.bitchord.platform

import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSString
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding

actual object PlatformUrl {

    // RFC 3986 unreserved character set: ALPHA, DIGIT, "-", "_", ".", "~"
    private val rfc3986AllowedCharacters: NSCharacterSet = run {
        val set = NSMutableCharacterSet()
        set.formUnionWithCharacterSet(NSCharacterSet.alphanumericCharacterSet())
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

    fun decodeComponent(s: String): String = urlDecode(s)

    fun extractScheme(url: String): String? {
        val idx = url.indexOf("://")
        return if (idx > 0) url.substring(0, idx) else null
    }

    fun extractHost(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    }

    fun extractPath(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val slashIdx = afterScheme.indexOf('/')
        if (slashIdx < 0) return "/"
        return afterScheme.substring(slashIdx).substringBefore('?').substringBefore('#')
    }
}
