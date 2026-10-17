package com.music.bitchord.stress

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.KaraokeLrc
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.ProviderLyrics
import com.music.bitchord.data.lyrics.TtmlLyrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LyricsParserStressTest {

    @Test
    fun testEnhancedLrcEmptyAndWhitespace() {
        assertEquals(emptyList(), EnhancedLrc.parse(""))
        assertEquals(emptyList(), EnhancedLrc.parse("   \n\n\t  \r\n"))
    }

    @Test
    fun testEnhancedLrcFallsBackOnPlainLrc() {
        val plainLrc = """
            [00:10.00]First line of song
            [00:15.50]Second line of song
            [00:20.00]Third line of song
        """.trimIndent()
        // EnhancedLrc requires word stamps; if none found, returns emptyList()
        val result = EnhancedLrc.parse(plainLrc)
        assertEquals(emptyList(), result)
    }

    @Test
    fun testEnhancedLrcWordSyncParsing() {
        val a2Lrc = """
            [00:05.00]<00:05.00>Never <00:05.40>gonna <00:05.80>give <00:06.20>you <00:06.50>up
            [00:07.00]<00:07.00>Never <00:07.30>gonna <00:07.60>let <00:07.90>you <00:08.20>down
        """.trimIndent()
        val lines = EnhancedLrc.parse(a2Lrc)
        // Note: withInstrumentalGaps prepends an intro gap (timeMs=0, text="") when first stamp >= 4000ms
        val sungLines = lines.filter { it.text.isNotEmpty() }
        assertEquals(2, sungLines.size)
        val firstSung = sungLines[0]
        assertEquals("Never gonna give you up", firstSung.text)
        assertEquals(5, firstSung.words.size)
        assertEquals("Never", firstSung.words[0].text)
        assertEquals(5000L, firstSung.words[0].startMs)
        assertEquals(5400L, firstSung.words[0].endMs)
    }

    @Test
    fun testEnhancedLrcEntityDecoding() {
        val text = "Don&apos;t &quot;stop&quot; &amp; believe &lt;3&gt; &#x27;cause &#39;love&#39; &nbsp;"
        val decoded = EnhancedLrc.decodeEntities(text)
        assertEquals("Don't \"stop\" & believe <3> 'cause 'love'  ", decoded)
    }

    @Test
    fun testEnhancedLrcCorruptedAndAdversarialInputs() {
        val malformed = listOf(
            "[99:99.99]<99:99.99>Word with huge stamp",
            "[00:00.00]<00:00.00>", // empty word
            "Not an LRC line at all",
            "[[[[[[[00:10.00]]]]]]]]",
            "<00:10.00>Missing line header",
            "[00:10.00]<00:10.00>First<00:10.50>Second<invalid_stamp>Third",
            "[00:10.00]<00:10.00>Syllable<00:10.20>without<00:10.40>spaces",
            "[00:10.00]<00:10.00>Special emoji 🎵 🔥 💥 <00:10.50>and kanji 歌詞",
        )
        for (input in malformed) {
            // Must not throw or crash
            val res = runCatching { EnhancedLrc.parse(input) }
            assertTrue(res.isSuccess, "EnhancedLrc.parse crashed on: $input with ${res.exceptionOrNull()}")
        }
    }

    @Test
    fun testLrcLibParserResilience() {
        val input = """
            [00:01.00]Intro line
            [00:10.00]Song lyrics start here
            [00:12.00]
            [00:25.00]After a long instrumental break
            [00:30.00]Outro line
            [00:35.00]
        """.trimIndent()
        val lines = LrcLib.parseLrc(input)
        assertTrue(lines.isNotEmpty())
        assertEquals(1000L, lines[0].timeMs)
    }

    @Test
    fun testLrcLibAlignmentMarker() {
        val rtlLrc = """
            [00:05.00]<R>مرحبا بكم في العالم
        """.trimIndent()
        val lines = LrcLib.parseLrc(rtlLrc)
        // With intro gap, last line is the RTL sung line
        val rtlLine = lines.last()
        assertEquals("مرحبا بكم في العالم", rtlLine.text)
        assertEquals(LyricAlignment.End, rtlLine.alignment)
    }

    @Test
    fun testProviderLyricsUnwrapAndDispatch() {
        // Plain text
        val plain = ProviderLyrics.parse("Line one\nLine two")
        assertNotNull(plain)
        assertEquals(2, plain.size)

        // JSON wrapper
        val jsonWrapped = """{"plainLyrics": "Hello world\nFrom JSON"}"""
        val fromJson = ProviderLyrics.parse(jsonWrapped)
        assertNotNull(fromJson)
        assertEquals(2, fromJson.size)

        // Markdown code fence wrapper
        val fenced = "```json\n{\"lyrics\": \"[00:01.00]Fenced song\"}\n```"
        val fromFenced = ProviderLyrics.parse(fenced)
        assertNotNull(fromFenced)

        // Error JSON
        val errorJson = """{"isError": true, "message": "Song not found"}"""
        val fromError = ProviderLyrics.parse(errorJson)
        assertEquals(null, fromError)

        // False boolean ok
        val falseOk = """{"ok": false}"""
        assertEquals(null, ProviderLyrics.parse(falseOk))
    }

    @Test
    fun testKaraokeLrcParsing() {
        val qrc = """
            [1000,3000](1000,1000)Hel(2000,1000)lo (3000,1000)world
            [5000,2000](5000,1000)Good(6000,1000)bye
        """.trimIndent()
        assertTrue(KaraokeLrc.looksLike(qrc))
        val lines = KaraokeLrc.parse(qrc)
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
    }

    @Test
    fun testTtmlLyricsParsing() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.500" end="00:03.000">
                    <span begin="00:01.500" end="00:02.000">Hello </span>
                    <span begin="00:02.000" end="00:03.000">World</span>
                  </p>
                  <p begin="00:04.000" end="00:06.000">Line-synced plain TTML line</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val lines = TtmlLyrics.parse(ttml)
        assertEquals(2, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello World", lines[0].text)
        assertEquals(4000L, lines[1].timeMs)
        assertEquals("Line-synced plain TTML line", lines[1].text)
    }

    @Test
    fun testTtmlLyricsXxeAndDisallowDoctypeSecurity() {
        // Attempt XML External Entity (XXE) injection
        val maliciousTtml = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE test [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.000">&xxe;</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        // Must safely reject or parse without expanding external entity
        val lines = TtmlLyrics.parse(maliciousTtml)
        // Secure XML parser disallows doctype or returns empty list safely
        assertTrue(lines.isEmpty() || lines.none { it.text.contains("root:") })
    }
}
