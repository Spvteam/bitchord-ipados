package com.music.bitchord.data.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking

private val client by lazy {
    HttpClient(Darwin)
}

internal actual fun lyricsGet(url: String): String? = runCatching {
    runBlocking {
        val response = client.get(url) {
            header("User-Agent", LYRICS_AGENT)
            header("Accept", "application/json")
        }
        if (response.status.value in 200..299) response.bodyAsText() else null
    }
}.getOrNull()

internal actual fun lyricsGetBearer(url: String, bearer: String): String? = runCatching {
    if (bearer.isBlank()) return null
    runBlocking {
        val response = client.get(url) {
            header("User-Agent", LYRICS_AGENT)
            header("Accept", "application/json, text/plain, */*")
            header("Authorization", "Bearer $bearer")
        }
        if (response.status.value in 200..299) response.bodyAsText() else null
    }
}.getOrNull()

internal actual fun lyricsGetAuthorized(url: String, bearer: String): String? = runCatching {
    if (bearer.isBlank()) return null
    runBlocking {
        val response = client.get(url) {
            header("User-Agent", LYRICS_AGENT)
            header("Accept", "application/json")
            header("Authorization", "Bearer $bearer")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
        }
        if (response.status.value in 200..299) response.bodyAsText() else null
    }
}.getOrNull()
