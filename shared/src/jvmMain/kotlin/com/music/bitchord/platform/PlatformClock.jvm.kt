package com.music.bitchord.platform

actual object PlatformClock {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun monotonicNano(): Long = System.nanoTime()
}
