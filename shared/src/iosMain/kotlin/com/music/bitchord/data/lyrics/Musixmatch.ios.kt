package com.music.bitchord.data.lyrics

actual object Musixmatch {
    actual suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? = null
}
