package com.music.bitchord.data

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * iPadOS / iOS Sandbox paths and SQLite / LRU cache persistence manager (`WP-08`).
 * - Persistent database resides in `NSApplicationSupportDirectory` (`bitchord_user.db`).
 * - Transient audio chunks reside in `NSCachesDirectory` (`audio_chunks/`) with `NSURLIsExcludedFromBackupKey = true`.
 */
@OptIn(ExperimentalForeignApi::class)
object IOSSandboxStorage {

    const val DB_FILE_NAME = "bitchord_user.db"
    const val AUDIO_CHUNKS_DIR_NAME = "audio_chunks"
    const val DEFAULT_MAX_CACHE_BYTES = 512L * 1024L * 1024L // 512 MiB

    data class StoredTrackRecord(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long = 0L,
        val thumbnailUrl: String? = null,
        val dateAdded: Long = PlatformClock.currentTimeMillis(),
    )

    data class OfflineDownloadRecord(
        val trackId: String,
        val filePath: String,
        val fileSizeBytes: Long,
        val downloadTimestamp: Long = PlatformClock.currentTimeMillis(),
    )

    data class ChunkCacheEntry(
        val chunkId: String,
        val lastAccess: Long,
        val sizeBytes: Long,
    )

    private val lock = PlatformLock()
    private val tracksTable = mutableMapOf<String, StoredTrackRecord>()
    private val offlineDownloadsTable = mutableMapOf<String, OfflineDownloadRecord>()
    private val chunkCacheTable = mutableMapOf<String, ChunkCacheEntry>()

    /**
     * Resolves `NSApplicationSupportDirectory` and ensures the directory exists on disk.
     */
    fun applicationSupportDirectoryPath(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        val base = (paths.firstOrNull() as? String) ?: "/var/mobile/Containers/Data/Application/BitChord/Library/Application Support"
        val dir = "$base/BitChord"
        ensureDirectoryExists(dir, excludeFromBackup = false)
        return dir
    }

    fun resolveApplicationSupportDirectory(): String = applicationSupportDirectoryPath()

    /**
     * Full path to `bitchord_user.db` inside `NSApplicationSupportDirectory`.
     */
    fun databaseFilePath(): String = "${applicationSupportDirectoryPath()}/$DB_FILE_NAME"

    /**
     * Resolves `NSCachesDirectory/audio_chunks/` and marks it with `NSURLIsExcludedFromBackupKey = true`
     * so Apple iCloud backups do not include transient streaming media chunks.
     */
    fun audioChunksCacheDirectoryPath(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )
        val base = (paths.firstOrNull() as? String) ?: "/var/mobile/Containers/Data/Application/BitChord/Library/Caches"
        val dir = "$base/$AUDIO_CHUNKS_DIR_NAME"
        ensureDirectoryExists(dir, excludeFromBackup = true)
        return dir
    }

    private fun ensureDirectoryExists(path: String, excludeFromBackup: Boolean) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        if (excludeFromBackup) {
            val url = NSURL.fileURLWithPath(path, isDirectory = true)
            url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
    }

    fun upsertTrack(record: StoredTrackRecord) = lock.withLock {
        tracksTable[record.id] = record
    }

    fun getTrack(id: String): StoredTrackRecord? = lock.withLock {
        tracksTable[id]
    }

    fun listTracks(): List<StoredTrackRecord> = lock.withLock {
        tracksTable.values.sortedByDescending { it.dateAdded }
    }

    fun upsertOfflineDownload(record: OfflineDownloadRecord) = lock.withLock {
        offlineDownloadsTable[record.trackId] = record
    }

    fun getOfflineDownload(trackId: String): OfflineDownloadRecord? = lock.withLock {
        offlineDownloadsTable[trackId]
    }

    fun recordChunkAccess(chunkId: String, sizeBytes: Long, timestampMs: Long = PlatformClock.currentTimeMillis()) = lock.withLock {
        chunkCacheTable[chunkId] = ChunkCacheEntry(chunkId = chunkId, lastAccess = timestampMs, sizeBytes = sizeBytes)
    }

    /**
     * Evicts least-recently-accessed chunks (`ORDER BY last_access ASC`) until total size <= [maxBytes].
     * Returns the list of evicted `chunkId`s.
     */
    fun evictLeastRecentlyUsedChunks(maxBytes: Long = DEFAULT_MAX_CACHE_BYTES): List<String> = lock.withLock {
        var total = chunkCacheTable.values.sumOf { it.sizeBytes }
        if (total <= maxBytes) return@withLock emptyList()

        val sortedByOldest = chunkCacheTable.values.sortedBy { it.lastAccess }
        val evicted = mutableListOf<String>()
        for (entry in sortedByOldest) {
            if (total <= maxBytes) break
            chunkCacheTable.remove(entry.chunkId)
            total -= entry.sizeBytes
            evicted.add(entry.chunkId)
        }
        evicted
    }
}
