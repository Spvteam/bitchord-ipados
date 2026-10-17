package com.music.bitchord.platform

/**
 * Multiplatform wall and monotonic clocks.
 */
expect object PlatformClock {
    /**
     * Milliseconds since Unix Epoch (1970-01-01T00:00:00Z).
     *
     * Subject to system clock steps (NTP, manual adjustment).
     * Corresponds to [System.currentTimeMillis] on JVM and [gettimeofday] on iOS.
     */
    fun currentTimeMillis(): Long

    /**
     * Nanoseconds from an arbitrary monotonic timebase.
     *
     * Guaranteed never to decrease. Suitable for measuring intervals and timeouts.
     * Corresponds to [System.nanoTime] on JVM and [mach_absolute_time] on iOS.
     */
    fun monotonicNano(): Long
}

/** Convenience monotonic millisecond timestamp. */
fun PlatformClock.monotonicMillis(): Long = monotonicNano() / 1_000_000L

/** Top-level monotonic nanosecond timer for UI and shared components. */
fun nanoTime(): Long = PlatformClock.monotonicNano()
