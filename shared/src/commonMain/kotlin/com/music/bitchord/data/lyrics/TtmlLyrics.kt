package com.music.bitchord.data.lyrics

expect object TtmlLyrics {
    fun parse(ttml: String): List<LyricLine>
}
