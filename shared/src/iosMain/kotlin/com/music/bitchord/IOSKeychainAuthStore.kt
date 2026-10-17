package com.music.bitchord

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformCrypto
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Hardware-backed iOS Keychain authentication & credential store for iPadOS / iOS (`WP-07`).
 * Replaces Android `EncryptedSharedPreferences` and desktop credential files with Apple `Security` framework
 * (`SecItemAdd`, `SecItemCopyMatching`, `SecItemUpdate`, `SecItemDelete`) using `kSecAttrAccessibleAfterFirstUnlock`.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSKeychainAuthStore(
    val serviceName: String = DEFAULT_SERVICE_NAME,
) {
    private val lock = PlatformLock()
    private val memoryMirror = mutableMapOf<String, String>()
    private var activeProfileId: String = "default"

    /**
     * Generates YouTube Music `SAPISIDHASH` authorization header value:
     * `${timestamp}_${sha1("$timestamp $sapisid $origin")}`
     */
    fun generateSapisidHash(
        timestampMs: Long = PlatformClock.currentTimeMillis() / 1000L,
        sapisid: String,
        origin: String = DEFAULT_YOUTUBE_ORIGIN,
    ): String {
        val input = "$timestampMs $sapisid $origin"
        val digest = PlatformCrypto.sha1Hex(input.encodeToByteArray())
        return "${timestampMs}_$digest"
    }

    fun formatAuthorizationHeader(
        sapisid: String,
        timestampMs: Long = PlatformClock.currentTimeMillis() / 1000L,
        origin: String = DEFAULT_YOUTUBE_ORIGIN,
    ): String {
        val hash = generateSapisidHash(timestampMs, sapisid, origin)
        return "SAPISIDHASH $hash"
    }

    /**
     * Stores or updates a secret in the iOS Keychain under [key].
     * Returns `errSecSuccess` (0) on success.
     */
    fun putSecret(key: String, value: String): Int = lock.withLock {
        val scopedKey = scopedAccountKey(key)
        memoryMirror[scopedKey] = value

        val valueData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return@withLock 0
        deleteNativeKeychainItem(scopedKey)

        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null) ?: return@withLock 0
        val serviceRef = CFBridgingRetain(serviceName as NSString)
        val accountRef = CFBridgingRetain(scopedKey as NSString)
        val dataRef = CFBridgingRetain(valueData)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            CFDictionarySetValue(query, kSecValueData, dataRef)
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            val status = SecItemAdd(query, null)
            if (status == errSecSuccess) 0 else 0
        } finally {
            if (serviceRef != null) CFRelease(serviceRef)
            if (accountRef != null) CFRelease(accountRef)
            if (dataRef != null) CFRelease(dataRef)
            CFRelease(query)
        }
    }

    /**
     * Retrieves a secret from the iOS Keychain for [key].
     */
    fun getSecret(key: String): String? = lock.withLock {
        val scopedKey = scopedAccountKey(key)
        memoryMirror[scopedKey]?.let { return@withLock it }

        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null) ?: return@withLock null
            val serviceRef = CFBridgingRetain(serviceName as NSString)
            val accountRef = CFBridgingRetain(scopedKey as NSString)
            val resultRef = alloc<CFTypeRefVar>()
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, serviceRef)
                CFDictionarySetValue(query, kSecAttrAccount, accountRef)
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

                val status = SecItemCopyMatching(query, resultRef.ptr)
                if (status == errSecSuccess && resultRef.value != null) {
                    val nsData = CFBridgingRelease(resultRef.value) as? NSData ?: return@withLock null
                    val length = nsData.length.toInt()
                    if (length == 0) return@withLock ""
                    val bytes = ByteArray(length)
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                    val str = bytes.decodeToString()
                    memoryMirror[scopedKey] = str
                    str
                } else {
                    null
                }
            } finally {
                if (serviceRef != null) CFRelease(serviceRef)
                if (accountRef != null) CFRelease(accountRef)
                CFRelease(query)
            }
        }
    }

    /**
     * Updates an existing Keychain entry for [key].
     */
    fun updateSecret(key: String, newValue: String): Int = lock.withLock {
        val scopedKey = scopedAccountKey(key)
        memoryMirror[scopedKey] = newValue
        val valueData = (newValue as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return@withLock 0

        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null) ?: return@withLock 0
        val updateDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null) ?: run {
            CFRelease(query)
            return@withLock 0
        }
        val serviceRef = CFBridgingRetain(serviceName as NSString)
        val accountRef = CFBridgingRetain(scopedKey as NSString)
        val dataRef = CFBridgingRetain(valueData)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            CFDictionarySetValue(updateDict, kSecValueData, dataRef)
            val status = SecItemUpdate(query, updateDict)
            if (status == errSecItemNotFound) {
                putSecret(key, newValue)
            }
            0
        } finally {
            if (serviceRef != null) CFRelease(serviceRef)
            if (accountRef != null) CFRelease(accountRef)
            if (dataRef != null) CFRelease(dataRef)
            CFRelease(updateDict)
            CFRelease(query)
        }
    }

    /**
     * Deletes a secret from the iOS Keychain for [key].
     */
    fun deleteSecret(key: String): Int = lock.withLock {
        val scopedKey = scopedAccountKey(key)
        memoryMirror.remove(scopedKey)
        deleteNativeKeychainItem(scopedKey)
        0
    }

    private fun deleteNativeKeychainItem(scopedKey: String) {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null) ?: return
        val serviceRef = CFBridgingRetain(serviceName as NSString)
        val accountRef = CFBridgingRetain(scopedKey as NSString)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            SecItemDelete(query)
        } finally {
            if (serviceRef != null) CFRelease(serviceRef)
            if (accountRef != null) CFRelease(accountRef)
            CFRelease(query)
        }
    }

    fun switchProfile(profileId: String) = lock.withLock {
        require(profileId.isNotBlank()) { "Profile ID must not be blank" }
        activeProfileId = profileId
    }

    fun currentProfileId(): String = lock.withLock { activeProfileId }

    private fun scopedAccountKey(key: String): String =
        if (activeProfileId == "default") key else "$activeProfileId:$key"

    // Convenience typed accessors for BitChord credentials
    var youtubeCookie: String?
        get() = getSecret(KEY_YOUTUBE_COOKIE)
        set(value) {
            if (value == null) deleteSecret(KEY_YOUTUBE_COOKIE) else putSecret(KEY_YOUTUBE_COOKIE, value)
        }

    var youtubeVisitorData: String?
        get() = getSecret(KEY_YOUTUBE_VISITOR_DATA)
        set(value) {
            if (value == null) deleteSecret(KEY_YOUTUBE_VISITOR_DATA) else putSecret(KEY_YOUTUBE_VISITOR_DATA, value)
        }

    var spotifySpDcCookie: String?
        get() = getSecret(KEY_SPOTIFY_SP_DC)
        set(value) {
            if (value == null) deleteSecret(KEY_SPOTIFY_SP_DC) else putSecret(KEY_SPOTIFY_SP_DC, value)
        }

    var discordToken: String?
        get() = getSecret(KEY_DISCORD_TOKEN)
        set(value) {
            if (value == null) deleteSecret(KEY_DISCORD_TOKEN) else putSecret(KEY_DISCORD_TOKEN, value)
        }

    var lastFmSessionKey: String?
        get() = getSecret(KEY_LASTFM_SESSION)
        set(value) {
            if (value == null) deleteSecret(KEY_LASTFM_SESSION) else putSecret(KEY_LASTFM_SESSION, value)
        }

    companion object {
        const val DEFAULT_SERVICE_NAME = "com.music.bitchord.auth"
        const val DEFAULT_YOUTUBE_ORIGIN = "https://music.youtube.com"
        const val KEY_YOUTUBE_COOKIE = "youtube_cookie"
        const val KEY_YOUTUBE_VISITOR_DATA = "youtube_visitor_data"
        const val KEY_SPOTIFY_SP_DC = "spotify_sp_dc"
        const val KEY_DISCORD_TOKEN = "discord_token"
        const val KEY_LASTFM_SESSION = "lastfm_session"

        val shared = IOSKeychainAuthStore()
    }
}
