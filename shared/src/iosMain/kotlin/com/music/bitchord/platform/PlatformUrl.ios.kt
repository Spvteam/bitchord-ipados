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
}
