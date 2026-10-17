package com.music.bitchord.data

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.monotonicMillis
import kotlin.concurrent.Volatile

/**
 * Milliseconds on a monotonic clock.
 *
 * Backed by [PlatformClock.monotonicMillis] across Android, Desktop JVM, and Apple iOS.
 */
object MonotonicClock {
    @Volatile
    var nowMs: () -> Long = { PlatformClock.monotonicMillis() }
}
