package com.music.bitchord.data.lyrics

actual object TtmlLyrics {
    private val P_REGEX = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val SPAN_REGEX = Regex("""<span\b([^>]*)>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val ATTR_REGEX = Regex("""(\w+(?::\w+)?)=["']([^"']*)["']""")

    actual fun parse(ttml: String): List<LyricLine> = runCatching {
        val lines = mutableListOf<LyricLine>()
        for (pMatch in P_REGEX.findAll(ttml)) {
            val pAttrs = parseAttributes(pMatch.groupValues[1])
            val pBody = pMatch.groupValues[2]
            val beginStr = pAttrs["begin"] ?: continue
            val beginMs = parseTimeToMs(beginStr) ?: continue
            val words = mutableListOf<LyricWord>()
            val spans = SPAN_REGEX.findAll(pBody).toList()
            val textBuilder = StringBuilder()
            if (spans.isNotEmpty()) {
                for (spanMatch in spans) {
                    val spanAttrs = parseAttributes(spanMatch.groupValues[1])
                    val spanText = spanMatch.groupValues[2].replace(Regex("<[^>]*>"), "")
                    val sBegin = spanAttrs["begin"]?.let(::parseTimeToMs) ?: beginMs
                    val sEnd = spanAttrs["end"]?.let(::parseTimeToMs) ?: (sBegin + 300)
                    textBuilder.append(spanText)
                    words.add(LyricWord(startMs = sBegin, endMs = sEnd, text = spanText))
                }
            } else {
                val clean = pBody.replace(Regex("<[^>]*>"), "").trim()
                textBuilder.append(clean)
            }
            val lineText = textBuilder.toString().trim()
            if (lineText.isNotEmpty()) {
                lines.add(LyricLine(timeMs = beginMs, text = lineText, words = words))
            }
        }
        lines.sortedBy { it.timeMs }
    }.getOrDefault(emptyList())

    private fun parseAttributes(attrString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (match in ATTR_REGEX.findAll(attrString)) {
            map[match.groupValues[1]] = match.groupValues[2]
        }
        return map
    }

    private fun parseTimeToMs(time: String): Long? {
        val parts = time.trim().split(':')
        return when (parts.size) {
            1 -> {
                val sec = parts[0].toDoubleOrNull() ?: return null
                (sec * 1000).toLong()
            }
            2 -> {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toDoubleOrNull() ?: return null
                min * 60_000 + (sec * 1000).toLong()
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toDoubleOrNull() ?: return null
                hr * 3600_000 + min * 60_000 + (sec * 1000).toLong()
            }
            else -> null
        }
    }
}
