package com.music.bitchord.data.presence

import com.music.bitchord.data.DebugLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

enum class PresencePlatform(val wire: String) {
    Android("android"),
    Pc("pc"),
    Ios("ios"),
}

/**
 * Last.fm scrobbling threshold evaluator and Discord RPC Gateway payload builder for iOS (`WP-09`).
 */
object LastFmAndDiscordIntegration {
    const val SCROBBLE_MAX_THRESHOLD_SECONDS: Double = 240.0
    const val SCROBBLE_FRACTION_THRESHOLD: Double = 0.5
    const val DISCORD_ACTIVITY_TYPE_LISTENING: Int = 2

    /**
     * Last.fm scrobbling rule: trigger when elapsed playback reaches 50% of track duration
     * OR 240 seconds (4 minutes), whichever comes first.
     */
    fun shouldScrobble(elapsedSeconds: Double, durationSeconds: Double): Boolean {
        if (durationSeconds <= 0.0 || elapsedSeconds < 0.0) return false
        return (elapsedSeconds >= SCROBBLE_MAX_THRESHOLD_SECONDS) ||
            (elapsedSeconds >= SCROBBLE_FRACTION_THRESHOLD * durationSeconds)
    }

    /**
     * Builds a Discord Gateway Rich Presence `Activity` JSON payload (`type = 2` Listening).
     */
    fun buildDiscordRpcPayload(
        title: String,
        artist: String,
        startEpochSeconds: Long,
        largeImage: String = "app_logo",
        largeText: String = "BitChord iPadOS",
    ): JsonObject = buildJsonObject {
        put("name", "BitChord")
        put("type", DISCORD_ACTIVITY_TYPE_LISTENING)
        put("details", title)
        put("state", artist)
        putJsonObject("timestamps") {
            put("start", startEpochSeconds)
        }
        putJsonObject("assets") {
            put("large_image", largeImage)
            put("large_text", largeText)
        }
    }
}

/**
 * iOS implementation of [Presence] reporting active listener telemetry via Ktor `Darwin`.
 */
class Presence(
    private val platform: PresencePlatform,
    private val installId: String,
    private val endpoint: String = ENDPOINT,
) {
    private val open = MutableStateFlow(false)
    private var announced = false
    private var intervalSec = DEFAULT_INTERVAL_SEC
    private val client by lazy { HttpClient(Darwin) }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            open.collectLatest { isOpen ->
                if (isOpen) {
                    while (true) {
                        send(open = true)
                        delay(nextDelayMs())
                    }
                } else if (announced) {
                    delay(CLOSE_DEBOUNCE_MS)
                    send(open = false)
                }
            }
        }
    }

    fun setOpen(isOpen: Boolean) {
        open.value = isOpen
    }

    fun closeBlocking() {
        open.value = false
        if (announced) {
            runBlocking { send(open = false) }
        }
    }

    private fun nextDelayMs(): Long {
        val base = intervalSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC) * 1000L
        return base + Random.nextLong(-base / 10, base / 10 + 1)
    }

    private suspend fun send(open: Boolean) {
        val body = buildJsonObject {
            put("id", installId)
            put("platform", platform.wire)
            put("open", open)
        }.toString()
        if (open) announced = true
        try {
            val response = client.post(endpoint) {
                header("Content-Type", "application/json")
                setBody(body)
            }
            if (!open) announced = false
            if (response.status.value !in 200..299) {
                DebugLog.w(TAG, "presence ping refused: HTTP ${response.status.value}")
                return
            }
            val text = response.bodyAsText()
            runCatching { Json.parseToJsonElement(text).jsonObject["intervalSec"]?.jsonPrimitive?.intOrNull }
                .getOrNull()
                ?.let { intervalSec = it }
        } catch (e: Exception) {
            DebugLog.d(TAG, "presence ping failed: ${e.message}")
        }
    }

    companion object {
        const val ENDPOINT = "https://api.bitchord.kushagrasingh.in/api/presence"
        private const val TAG = "Presence"
        private const val DEFAULT_INTERVAL_SEC = 300
        private const val MIN_INTERVAL_SEC = 60
        private const val MAX_INTERVAL_SEC = 3600
        private const val CLOSE_DEBOUNCE_MS = 2_000L
    }
}
