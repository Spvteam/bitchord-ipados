package com.music.bitchord.data.lyrics

actual object Genius {
    actual suspend fun lyrics(title: String, artist: String): List<LyricLine>? = null

    fun isSectionHeader(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2
    }
}
