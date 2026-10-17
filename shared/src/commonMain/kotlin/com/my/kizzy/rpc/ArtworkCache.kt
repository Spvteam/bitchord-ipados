package com.my.kizzy.rpc

import com.music.bitchord.platform.PlatformLock

internal object ArtworkCache {
    private val cache = mutableMapOf<String, String>()
    private val lock = PlatformLock()

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        lock.withLock { cache[key] }?.let { return it }
        val value = fetch() ?: return null
        lock.withLock { cache[key] = value }
        return value
    }
}
