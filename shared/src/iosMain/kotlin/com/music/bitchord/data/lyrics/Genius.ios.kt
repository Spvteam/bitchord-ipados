package com.music.bitchord.data.lyrics

actual object Genius {
    actual suspend fun lyrics(title: String, artist: String): List<LyricLine>? = null
}
