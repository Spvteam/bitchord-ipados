package com.music.bitchord.data.lyrics

import kotlinx.serialization.json.Json

internal const val LYRICS_AGENT = "BitChord (https://github.com/bitchord)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Body of a successful GET, or null for any failure at all. */
internal expect fun lyricsGet(url: String): String?

/** Body of an authenticated provider GET without service-specific browser headers. */
internal expect fun lyricsGetBearer(url: String, bearer: String): String?

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one — `amp-api.music.apple.com` answers a token with no
 * `Origin` at all the same way it answers a wrong one, with a 403.
 */
internal expect fun lyricsGetAuthorized(url: String, bearer: String): String?
