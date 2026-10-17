package com.music.bitchord.data.lyrics

expect object Musixmatch {
    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>?
}
