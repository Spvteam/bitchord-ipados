package com.music.bitchord.data.lyrics

expect object Genius {
    suspend fun lyrics(title: String, artist: String): List<LyricLine>?
}
