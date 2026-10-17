package com.music.bitchord.data.innertube

import com.music.bitchord.platform.PlatformUrl

/**
 * Multiplatform iOS representation of the Innertube PlayerClient identity and media headers.
 * Prioritizes waterfall order: ANDROID_VR -> IOS -> WEB_REMIX.
 */
data class PlayerClient(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val origin: String? = null,
) {
    val referer: String? get() = origin?.let { "$it/" }

    fun mediaHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        origin?.let { put("Origin", it) }
        referer?.let { put("Referer", it) }
    }

    companion object {
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/18.3 Safari/605.1.15"

        val ANDROID_VR = PlayerClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        )

        val IOS = PlayerClient(
            clientName = "IOS",
            clientVersion = "21.29.1",
            userAgent = "com.google.ios.youtube/21.29.1 (iPad16,3; U; CPU iPadOS 18_3 like Mac OS X;)",
        )

        val WEB_REMIX = PlayerClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260707.12.00",
            userAgent = WEB_USER_AGENT,
            origin = MUSIC_ORIGIN,
        )

        val WEB = PlayerClient(
            clientName = "WEB",
            clientVersion = "2.20260708.00.00",
            userAgent = WEB_USER_AGENT,
            origin = YOUTUBE_ORIGIN,
        )

        /** Authoritative iOS multi-client waterfall priority: ANDROID_VR -> IOS -> WEB_REMIX. */
        val WATERFALL_PRIORITY: List<PlayerClient> = listOf(ANDROID_VR, IOS, WEB_REMIX)

        fun forStreamUrl(url: String): PlayerClient {
            val query = url.substringAfter('?', "")
            val params = query.split('&').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) part.substring(0, idx) to PlatformUrl.decodeComponent(part.substring(idx + 1))
                else null
            }.toMap()
            val name = params["c"]?.uppercase() ?: return IOS
            return when {
                name == "ANDROID_VR" -> ANDROID_VR
                name.startsWith("IOS") -> IOS
                name == "WEB_REMIX" -> WEB_REMIX
                name.startsWith("WEB") || name == "MWEB" -> WEB
                else -> IOS
            }
        }

        fun rangeBytesFor(url: String): Long {
            val client = forStreamUrl(url)
            return if (client.clientName == "ANDROID_VR") NARROW_RANGE_BYTES else RANGE_BYTES
        }

        fun boundedRangeProbeHeader(bytes: Long = RANGE_BYTES): String =
            "bytes=0-${(bytes - 1).coerceAtLeast(0)}"

        const val RANGE_BYTES = 1024L * 1024L
        const val NARROW_RANGE_BYTES = 512L * 1024L
    }
}
