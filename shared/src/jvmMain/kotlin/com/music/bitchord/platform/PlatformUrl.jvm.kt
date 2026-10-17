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
