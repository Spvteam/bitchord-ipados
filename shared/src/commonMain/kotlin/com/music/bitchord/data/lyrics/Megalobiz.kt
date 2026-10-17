package com.music.bitchord.data.lyrics

import com.music.bitchord.platform.PlatformUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Line-synced community LRC scraped from Megalobiz. */
object Megalobiz {
    private const val BASE = "https://www.megalobiz.com"

    suspend fun lyrics(title: String, artist: String): List<LyricLine>? = withContext(Dispatchers.Default) {
        val search = "$BASE/searchall?qry=${PlatformUrl.urlEncode("$artist $title".trim())}"
        val results = lyricsGet(search) ?: return@withContext null
        val path = LRC_LINK.find(results)?.groupValues?.get(1) ?: return@withContext null
        val page = lyricsGet(BASE + path.replace("&amp;", "&")) ?: return@withContext null
        val raw = LRC_BODY.find(page)?.groupValues?.get(1) ?: return@withContext null
        val lrc = raw.replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .let(EnhancedLrc::decodeEntities)
        LrcLib.parseLrc(lrc).takeIf { lines -> lines.any { it.text.isNotBlank() } }
    }

    private val LRC_LINK = Regex("""href=[\"'](/lrc/maker/download/[^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val LRC_BODY = Regex(
        """(?s)id=[\"']lrc_[^\"']*_details[\"'][^>]*>(.*?)</span>""",
        RegexOption.IGNORE_CASE,
    )
}
