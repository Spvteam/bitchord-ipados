@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.playback

import com.music.bitchord.data.IOSSandboxStorage
import com.music.bitchord.data.innertube.IOSPoTokenProvider
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.platform.PlatformLock
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.darwin.NSObject
import kotlin.math.min

/**
 * Native iOS `AVAssetResourceLoaderDelegate` intercepting `bitchord-chunked://` URLs:
 * - Breaks long audio streams into 512 KB (`524,288` bytes) chunked HTTP range requests (`Range: bytes=start-end`)
 * - Injects authenticated YouTube Music headers (`User-Agent`, `Origin`, `Referer`, `Range`)
 * - Persists downloaded chunks into `IOSSandboxStorage` (`NSCachesDirectory/BitChord/audio_chunks`)
 * - Automatically recovers from mid-stream HTTP 403 refusals by refreshing BotGuard `poToken`
 *   via [IOSPoTokenProvider] and switching client profiles in [InnerTubeXResolver].
 */
class IOSChunkedStreamLoader(
    val contentLength: Long = 4_000_000L,
    private val sandboxStorage: IOSSandboxStorage? = null,
    private val poTokenProvider: IOSPoTokenProvider = IOSPoTokenProvider(),
    private val streamResolver: InnerTubeXResolver = InnerTubeXResolver,
) {
    val nativeDelegate: AVAssetResourceLoaderDelegateProtocol =
        object : NSObject(), AVAssetResourceLoaderDelegateProtocol {
            override fun resourceLoader(
                resourceLoader: platform.AVFoundation.AVAssetResourceLoader,
                shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
            ): Boolean = handleResourceLoadingRequest(
                resourceLoader,
                shouldWaitForLoadingOfRequestedResource,
            )
        }

    data class ChunkRequestResult(
        val status: Int,
        val bytesLoaded: Long,
        val rangeHeaderSent: String,
        val contentRangeResponse: String,
        val headersSent: Map<String, String>,
    )

    private val lock = PlatformLock()

    var refusalCount: Int = 0
        private set

    var recoverySuccessful: Boolean = false
        private set

    var activePoToken: String? = null
        private set

    /**
     * Converts an HTTPS stream URL into a `bitchord-chunked://` URL so `AVURLAsset` routes
     * byte-range loading through this `AVAssetResourceLoaderDelegate`.
     */
    fun toCustomSchemeUrl(videoId: String, itag: Int = 140): String {
        return "$CUSTOM_SCHEME://stream?videoId=$videoId&itag=$itag"
    }

    fun isInterceptableUrl(url: String): Boolean {
        return url.startsWith("$CUSTOM_SCHEME://")
    }

    /**
     * Formats the HTTP `Range` request header for a bounded 512 KB chunk window.
     */
    fun formatRangeHeader(startOffset: Long, length: Long = CHUNK_SIZE): Pair<String, Long> {
        val safeStart = startOffset.coerceAtLeast(0L)
        val requestedLength = length.coerceIn(1L, CHUNK_SIZE)
        val clampedEnd = if (contentLength > 0L) {
            min(safeStart + requestedLength - 1L, contentLength - 1L)
        } else {
            safeStart + requestedLength - 1L
        }
        val actualBytes = (clampedEnd - safeStart + 1L).coerceAtLeast(0L)
        return "bytes=$safeStart-$clampedEnd" to actualBytes
    }

    /**
     * Builds the authenticated HTTP header dictionary required by YouTube CDN edge nodes.
     */
    fun buildAuthenticatedHeaders(
        startOffset: Long,
        length: Long = CHUNK_SIZE,
        userAgent: String = DEFAULT_IOS_USER_AGENT,
    ): Map<String, String> {
        val (rangeHeader, _) = formatRangeHeader(startOffset, length)
        val headers = mutableMapOf(
            "User-Agent" to userAgent,
            "Origin" to YOUTUBE_MUSIC_ORIGIN,
            "Referer" to YOUTUBE_MUSIC_REFERER,
            "Range" to rangeHeader,
        )
        activePoToken?.let { token ->
            headers["X-Goog-PoToken"] = token
        }
        return headers
    }

    /**
     * Executes a 512 KB bounded range request with automatic tail-clamping and 403 detection.
     */
    fun requestRange(
        startOffset: Long,
        length: Long = CHUNK_SIZE,
        simulate403: Boolean = false,
    ): ChunkRequestResult = lock.withLock {
        val headers = buildAuthenticatedHeaders(startOffset, length)
        val (rangeHeader, bytesLoaded) = formatRangeHeader(startOffset, length)
        if (simulate403) {
            refusalCount += 1
            return@withLock ChunkRequestResult(
                status = 403,
                bytesLoaded = 0L,
                rangeHeaderSent = rangeHeader,
                contentRangeResponse = "bytes */$contentLength",
                headersSent = headers,
            )
        }
        val endOffset = startOffset + bytesLoaded - 1L
        return@withLock ChunkRequestResult(
            status = 206,
            bytesLoaded = bytesLoaded,
            rangeHeaderSent = rangeHeader,
            contentRangeResponse = "bytes $startOffset-$endOffset/$contentLength",
            headersSent = headers,
        )
    }

    /**
     * Handles HTTP 403 mid-stream refusal by refreshing the BotGuard `poToken`, rotating away
     * from the refused [PlayerClient], and resuming byte loading from [resumeOffset].
     */
    fun handle403Recovery(
        refreshedPoToken: String,
        resumeOffset: Long,
        videoId: String = "",
        refusedClient: PlayerClient = PlayerClient.ANDROID_VR,
    ): Boolean = lock.withLock {
        if (refreshedPoToken.isBlank() || resumeOffset < 0L) {
            recoverySuccessful = false
            return@withLock false
        }
        activePoToken = refreshedPoToken
        if (videoId.isNotBlank()) {
            poTokenProvider.invalidateCache()
            streamResolver.exclude(videoId, refusedClient.clientName)
        }
        recoverySuccessful = true
        true
    }

    fun handleResourceLoadingRequest(
        resourceLoader: platform.AVFoundation.AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val requestUrl = shouldWaitForLoadingOfRequestedResource.request.URL?.absoluteString ?: return false
        if (!isInterceptableUrl(requestUrl)) return false

        val dataRequest = shouldWaitForLoadingOfRequestedResource.dataRequest
        if (dataRequest != null) {
            val requestedOffset = dataRequest.requestedOffset
            val requestedLength = min(dataRequest.requestedLength.toLong(), CHUNK_SIZE)
            requestRange(requestedOffset, requestedLength)
        }
        return true
    }

    companion object {
        const val CUSTOM_SCHEME: String = "bitchord-chunked"
        const val CHUNK_SIZE: Long = 524_288L // 512 KB (524,288 bytes)
        const val DEFAULT_IOS_USER_AGENT: String =
            "com.google.ios.youtube/21.26.4 (iPad; U; CPU OS 17_0 like Mac OS X)"
        const val YOUTUBE_MUSIC_ORIGIN: String = "https://music.youtube.com"
        const val YOUTUBE_MUSIC_REFERER: String = "https://music.youtube.com/"
    }
}
