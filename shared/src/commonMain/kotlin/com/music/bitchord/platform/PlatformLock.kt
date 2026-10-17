package com.music.bitchord.platform

/**
 * Multiplatform mutual exclusion lock.
 *
 * Implemented using JVM monitors on Android/Desktop and Apple Foundation [NSLock] on iOS.
 */
expect class PlatformLock() {
    inline fun <T> withLock(block: () -> T): T
}
