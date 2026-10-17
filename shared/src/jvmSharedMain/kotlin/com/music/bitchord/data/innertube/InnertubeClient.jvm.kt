package com.music.bitchord.data.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal actual fun createInnertubeHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
    // Same OkHttp instance ExoPlayer streams through — see Http.
    engine { preconfigured = com.music.bitchord.data.Http.client }
    install(ContentNegotiation) { json(json) }
    // Without this the only bound is OkHttp's own read timeout, and the
    // failure it raises reads as "Socket timeout has expired […]
    // socket_timeout=unknown" — Ktor reporting a limit it was never told.
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 20_000
    }
    expectSuccess = true
}
