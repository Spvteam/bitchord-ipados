package com.music.bitchord.platform

import platform.Foundation.NSLock

actual class PlatformLock {
    @PublishedApi
    internal val nsLock = NSLock()

    actual inline fun <T> withLock(block: () -> T): T {
        nsLock.lock()
        try {
            return block()
        } finally {
            nsLock.unlock()
        }
    }
}
