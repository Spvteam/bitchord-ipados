package com.music.bitchord.platform

actual class PlatformLock {
    @PublishedApi
    internal val lock = Any()

    actual inline fun <T> withLock(block: () -> T): T = synchronized(lock, block)
}
