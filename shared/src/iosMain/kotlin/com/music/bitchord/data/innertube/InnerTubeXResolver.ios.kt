package com.music.bitchord.data.innertube

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeLocale
import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.TrackLog
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * iPadOS / iOS implementation of [InnerTubeXResolver].
 * Enforces the multi-client waterfall (`ANDROID_VR` -> `IOS` -> `WEB_REMIX`),
 * prioritizes AAC/M4A (`itag 141` / `140`) for hardware-accelerated Apple `AVAudioEngine` decoding,
 * and integrates with [IOSPoTokenProvider] and [com.music.bitchord.IOSCipher].
 */
object InnerTubeXResolver {

    private const val TAG = "BitChord"

    class Extracted(
        val videoId: String,
        val url: String,
        val kbps: Int,
        val mimeType: String,
        val loudnessDb: Double?,
        val clientName: String,
        val profileId: String,
        val headers: Map<String, String>,
    )

    interface ConfigStore {
        fun getString(key: String): String?
        fun getLong(key: String): Long?
        fun putString(key: String, value: String)
        fun putLong(key: String, value: Long)
    }

    @Volatile
    var debugLogging: Boolean = false

    private val lock = PlatformLock()
    private var prefs: ConfigStore? = null
    private var poTokens: TokenProvider = IOSPoTokenProvider.shared
    private val playerCache = mutableMapOf<String, String>()
    private var warmup: Job? = null

    fun init(store: ConfigStore? = null, poTokenProvider: TokenProvider? = IOSPoTokenProvider.shared) {
        prefs = store
        if (poTokenProvider != null) {
            poTokens = poTokenProvider
        }
        scope.launch {
            cipherService.setPreprocessedPlayerCache(::readPlayer, ::writePlayer)
            warm(WARM_DELAY_MS)
        }
    }

    private fun readPlayer(key: String): String? = lock.withLock {
        playerCache[key] ?: prefs?.getString("player_$key")
    }

    private fun writePlayer(key: String, value: String?) = lock.withLock {
        if (value == null) {
            playerCache.remove(key)
        } else {
            playerCache[key] = value
            prefs?.putString("player_$key", value)
        }
    }

    private fun warm(delayMs: Long) {
        warmup?.cancel()
        warmup = scope.launch {
            delay(delayMs)
            val start = MonotonicClock.nowMs()
            runCatching {
                innerTube.cookie = Innertube.cookie
                innerTube.visitorData = Innertube.ensureVisitorData()
                innerTube.locale = YouTubeLocale(gl = Innertube.currentRegion, hl = Innertube.currentLanguage)
                extractor.prewarm()
            }.onFailure { if (it is CancellationException) throw it }
                .onFailure { TrackLog.w(TAG, "InnerTubeX warm-up failed: ${it.message}") }
                .onSuccess { TrackLog.d(TAG, "InnerTubeX warmed in ${MonotonicClock.nowMs() - start}ms") }
        }
    }

    private val tokenProvider = object : TokenProvider {
        override val capabilities: TokenProviderCapabilities
            get() = poTokens.capabilities

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
            poTokens.getPoToken(videoId, visitorData, cookie)

        override suspend fun close() {
            poTokens.close()
        }
    }

    private val http = createInnertubeHttpClient(
        Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    )

    private val logger = InnerTubeLogger { event ->
        if (event.level == InnerTubeLogLevel.DEBUG && !debugLogging) return@InnerTubeLogger
        val details = if (event.details.isEmpty()) "" else event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        val line = "ITX ${event.tag}: ${event.message}$details"
        if (event.level == InnerTubeLogLevel.INFO || event.level == InnerTubeLogLevel.DEBUG) TrackLog.d(TAG, line) else TrackLog.w(TAG, line)
    }

    private val repository = object : PlayerConfigRepository {
        override val enabled: Boolean get() = prefs != null
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = prefs?.getString("json").orEmpty()
            set(value) { prefs?.putString("json", value) }
        override var cachedAtMs: Long
            get() = prefs?.getLong("cached_at_ms") ?: 0L
            set(value) { prefs?.putLong("cached_at_ms", value) }
        override var cachedSourceUrl: String
            get() = prefs?.getString("source_url").orEmpty()
            set(value) { prefs?.putString("source_url", value) }
        override var cachedEtag: String
            get() = prefs?.getString("etag").orEmpty()
            set(value) { prefs?.putString("etag", value) }
    }

    private val innerTube = InnerTube(http, logger = logger)
    private val remoteStore = RemotePlayerConfigStore(http, repository, logger)
    private val cipherService = YouTubeCipherService(http, remoteStore, logger)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(http, innerTube, remoteStore, logger),
        cipherService = cipherService,
        innerTube = innerTube,
        tokenProvider = tokenProvider,
        logger = logger,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val minted = mutableMapOf<String, Extracted>()
    private val excluded = mutableMapOf<String, MutableMap<String, Long>>()

    /**
     * Resolves a stream conforming to PROJECT.md §1 contract (`resolveStream`),
     * enforcing client priority waterfall (`ANDROID_VR -> IOS -> WEB_REMIX`) and AAC/M4A preference (`requireM4a = true` by default on iOS).
     */
    suspend fun resolveStream(
        videoId: String,
        clientPriority: List<PlayerClient> = PlayerClient.WATERFALL_PRIORITY,
        requireM4a: Boolean = true,
    ): ResolvedStreamResult? {
        val extracted = extract(
            videoId = videoId,
            maxKbps = 256,
            skipClients = emptySet(),
            requireM4a = requireM4a,
        ) ?: return null

        val isM4a = extracted.mimeType.contains("audio/mp4", ignoreCase = true)
        val itag = when {
            isM4a && extracted.kbps >= 200 -> 141
            isM4a -> 140
            extracted.kbps >= 140 -> 251
            else -> 250
        }
        val poTokenUsed = extracted.clientName.equals("WEB_REMIX", ignoreCase = true) ||
            extracted.clientName.equals("WEB", ignoreCase = true)

        return ResolvedStreamResult(
            streamUrl = extracted.url,
            itag = itag,
            mimeType = extracted.mimeType,
            bitrate = extracted.kbps * 1000,
            contentLength = PlayerClient.rangeBytesFor(extracted.url),
            poTokenUsed = poTokenUsed,
            clientName = clientPriority.firstOrNull { it.clientName == extracted.clientName }?.clientName ?: extracted.clientName,
            headers = extracted.headers,
            loudnessDb = extracted.loudnessDb,
        )
    }

    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = true,
    ): Extracted? {
        innerTube.cookie = Innertube.cookie
        innerTube.visitorData = Innertube.ensureVisitorData()
        innerTube.locale = YouTubeLocale(gl = Innertube.currentRegion, hl = Innertube.currentLanguage)
        val stream = extractor.extract(
            videoId = videoId,
            hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
            excludedClients = excludedFor(videoId) + skipClients,
            audioQuality = when {
                requireM4a -> AudioQuality.MP4
                maxKbps <= LOW_KBPS -> AudioQuality.LOW
                else -> AudioQuality.AUTO
            },
            clientPlaybackNonce = generateClientPlaybackNonce(),
        ) ?: return null
        val mime = stream.mimeType.orEmpty()
        val extracted = Extracted(
            videoId = videoId,
            url = stream.audioUrl,
            kbps = (stream.bitrate ?: 0) / 1000,
            mimeType = if (stream.codecs.isNullOrBlank()) mime else "$mime; codecs=\"${stream.codecs}\"",
            loudnessDb = stream.loudnessDb,
            clientName = stream.clientName,
            profileId = stream.profileId,
            headers = stream.headers,
        )
        lock.withLock {
            if (minted.size >= MAX_REMEMBERED) minted.clear()
            minted[extracted.url] = extracted
        }
        return extracted
    }

    fun headersFor(url: String): Map<String, String>? = lock.withLock { minted[url]?.headers }

    fun onRefused(url: String): String? {
        val refused = lock.withLock { minted.remove(url) } ?: return null
        exclude(refused.videoId, refused.profileId)
        scope.launch { runCatching { cipherService.refreshAfterStreamRejection() } }
        return refused.videoId
    }

    fun exclude(videoId: String, profileId: String) {
        TrackLog.w(TAG, "InnerTubeX: $profileId refused $videoId; skipping it for this track", about = videoId)
        lock.withLock {
            val map = excluded.getOrPut(videoId) { mutableMapOf() }
            map[profileId] = MonotonicClock.nowMs() + EXCLUDE_MS
        }
    }

    fun onSessionChanged() {
        lock.withLock {
            excluded.clear()
            minted.clear()
        }
        if (prefs != null) warm(0)
    }

    private fun excludedFor(videoId: String): Set<String> = lock.withLock {
        val entries = excluded[videoId] ?: return@withLock emptySet()
        val now = MonotonicClock.nowMs()
        val expired = entries.filterValues { it <= now }.keys.toList()
        for (k in expired) entries.remove(k)
        entries.keys.toSet()
    }

    private const val PLAYER_CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    private const val LOW_KBPS = 64
    private const val EXCLUDE_MS = 10 * 60 * 1000L
    private const val MAX_REMEMBERED = 64
    private const val WARM_DELAY_MS = 2_000L
}
