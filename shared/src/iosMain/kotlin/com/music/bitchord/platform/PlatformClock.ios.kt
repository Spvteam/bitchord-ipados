package com.music.bitchord.platform

import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

actual object PlatformClock {
    actual fun currentTimeMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    actual fun monotonicNano(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()
}
