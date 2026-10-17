@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.data.lyrics

import com.music.bitchord.platform.PlatformCrypto
import com.music.bitchord.platform.PlatformLock
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import platform.zlib.Z_OK
import platform.zlib.uncompress

/**
 * KuGou `.krc` binary lyrics decryptor and syllable timing parser for iOS (`WP-09`).
 *
 * - Strips the 4-byte `"krc1"` magic header
 * - Applies bytewise XOR with the 16-byte key `@Gaw^2tGQ61-ÎÒni`
 *   (`[0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2D, 0xCE, 0xD2, 0x6E, 0x69]`)
 * - Decompresses the resulting stream via native `platform.zlib.uncompress`
 * - Parses word/syllable timing tags `<mm:ss.xx>word`
 */
object KuGouKrcDecoder {
    val KUGOU_XOR_KEY: ByteArray = byteArrayOf(
        0x40.toByte(), 0x47.toByte(), 0x61.toByte(), 0x77.toByte(),
        0x5E.toByte(), 0x32.toByte(), 0x74.toByte(), 0x47.toByte(),
        0x51.toByte(), 0x36.toByte(), 0x31.toByte(), 0x2D.toByte(),
        0xCE.toByte(), 0xD2.toByte(), 0x6E.toByte(), 0x69.toByte()
    )

    val PROVIDER_PRIORITY_ORDER: List<String> = listOf(
        "LrcLib",
        "NetEase",
        "KuGou",
        "Paxsenix",
        "SimpMusic",
        "YouTube",
    )

    private val SYLLABLE_TAG_REGEX = Regex("""<(\d{2}):(\d{2}\.\d{2})>([^<]*)""")

    data class SyllableToken(
        val minutes: String,
        val seconds: String,
        val startMs: Long,
        val text: String,
    )

    fun decryptAndDecompressKrc(payload: ByteArray): String? {
        if (payload.size <= 4) return null
        val magic = payload.copyOfRange(0, 4).decodeToString()
        if (magic != "krc1") return null

        val encryptedBody = payload.copyOfRange(4, payload.size)
        val keyLen = KUGOU_XOR_KEY.size
        val decrypted = ByteArray(encryptedBody.size)
        for (i in encryptedBody.indices) {
            decrypted[i] = (encryptedBody[i].toInt() xor KUGOU_XOR_KEY[i % keyLen].toInt()).toByte()
        }

        return zlibInflate(decrypted)
    }

    private fun zlibInflate(compressed: ByteArray): String? = memScoped {
        if (compressed.isEmpty()) return null
        val maxOutputSize = (compressed.size * 12).coerceAtLeast(16_384)
        val outputBuffer = ByteArray(maxOutputSize)
        val destLenVar = alloc<ULongVar>()
        destLenVar.value = maxOutputSize.toULong()

        val status = compressed.usePinned { srcPinned ->
            outputBuffer.usePinned { dstPinned ->
                uncompress(
                    dest = dstPinned.addressOf(0).reinterpret<UByteVar>(),
                    destLen = destLenVar.ptr,
                    source = srcPinned.addressOf(0).reinterpret<UByteVar>(),
                    sourceLen = compressed.size.toULong(),
                )
            }
        }
        if (status != Z_OK) return null
        val actualLen = destLenVar.value.toInt().coerceIn(0, maxOutputSize)
        outputBuffer.copyOfRange(0, actualLen).decodeToString()
    }

    fun parseSyllableTimingLine(line: String): List<SyllableToken> {
        return SYLLABLE_TAG_REGEX.findAll(line).map { match ->
            val minStr = match.groupValues[1]
            val secStr = match.groupValues[2]
            val wordText = match.groupValues[3]
            val totalMs = (minStr.toLongOrNull() ?: 0L) * 60_000L +
                ((secStr.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
            SyllableToken(
                minutes = minStr,
                seconds = secStr,
                startMs = totalMs,
                text = wordText,
            )
        }.toList()
    }
}

/**
 * iOS implementation of [LyricsTranslation] using Ktor `Darwin` and `PlatformCrypto`.
 */
object LyricsTranslation {
    sealed interface Result {
        data class Translated(
            val lines: List<LyricLine>,
            val sourceLanguage: String,
            val fromCache: Boolean,
        ) : Result

        data class SameLanguage(val language: String) : Result
        data object Unavailable : Result
    }

    sealed interface RomanizationResult {
        data class Romanized(
            val lines: List<LyricLine>,
            val sourceLanguage: String,
            val fromCache: Boolean,
        ) : RomanizationResult

        data object AlreadyRomanized : RomanizationResult
        data object Unavailable : RomanizationResult
    }

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val INPUT_TOOLS_ENDPOINT = "https://inputtools.google.com/request"
    private const val CACHE_VERSION = 3
    private const val MAX_BATCH_CHARS = 3_500
    private const val MAX_PARALLEL_REQUESTS = 2
    private val markerRegex = Regex("\\uE000[^\\uE001]*\\uE001")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = PlatformLock()
    private val memoryEntries = LinkedHashMap<String, CachedTranslation>()

    private val client by lazy { HttpClient(Darwin) }

    @Serializable
    private data class CachedTranslation(
        val version: Int = CACHE_VERSION,
        val sourceLanguage: String,
        val targetLanguage: String,
        val texts: List<String>,
    )

    private data class TextSlot(
        val lineIndex: Int,
        val background: Boolean,
        val text: String,
        val sectionHeader: Boolean,
    )

    private data class Batch(
        val slots: List<TextSlot>,
        val payload: String,
    )

    private data class BatchAnswer(
        val translations: List<String>,
        val sourceLanguage: String,
        val sourceWeight: Int,
    )

    /**
     * Pairs original lyric lines with translated lines (`WP-09`).
     */
    fun alignOriginalWithTranslated(
        original: List<String>,
        translated: List<String>,
    ): List<Pair<String, String>> = original.zip(translated)

    suspend fun translate(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): Result {
        val target = targetLanguageTag.trim()
        if (target.isBlank() || lines.isEmpty()) return Result.Unavailable

        val slots = flatten(lines)
        if (slots.isEmpty()) return Result.Unavailable
        val cacheKey = cacheKey(trackId, target, slots)
        val cached = lock.withLock { memoryEntries[cacheKey] }
        if (cached != null && cached.version == CACHE_VERSION && cached.texts.size == slots.size) {
            return if (sameLanguage(cached.sourceLanguage, target)) {
                Result.SameLanguage(cached.sourceLanguage)
            } else {
                Result.Translated(
                    lines = rebuild(lines, slots, cached.texts),
                    sourceLanguage = cached.sourceLanguage,
                    fromCache = true,
                )
            }
        }

        val batches = batches(slots)
        val answers = coroutineScope {
            batches.chunked(MAX_PARALLEL_REQUESTS).flatMap { group ->
                group.map { batch -> async { requestBatch(batch, target) } }.awaitAll()
            }
        }
        if (answers.any { it == null }) return Result.Unavailable
        val complete = answers.filterNotNull()
        val source = complete
            .groupBy { canonicalLanguage(it.sourceLanguage) }
            .maxByOrNull { (_, values) -> values.sumOf { it.sourceWeight } }
            ?.key
            .orEmpty()
        if (source.isBlank()) return Result.Unavailable

        val translated = complete.flatMap { it.translations }
        if (translated.size != slots.size) return Result.Unavailable

        val entry = CachedTranslation(
            sourceLanguage = source,
            targetLanguage = target,
            texts = translated,
        )
        lock.withLock {
            if (memoryEntries.size >= 12) {
                memoryEntries.keys.firstOrNull()?.let { memoryEntries.remove(it) }
            }
            memoryEntries[cacheKey] = entry
        }

        return if (sameLanguage(source, target)) {
            Result.SameLanguage(source)
        } else {
            Result.Translated(
                lines = rebuild(lines, slots, translated),
                sourceLanguage = source,
                fromCache = false,
            )
        }
    }

    suspend fun romanize(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): RomanizationResult {
        if (lines.isEmpty()) return RomanizationResult.Unavailable
        val slots = flatten(lines)
        if (slots.isEmpty()) return RomanizationResult.Unavailable
        if (!hasNonLatinLetters(slots)) return RomanizationResult.AlreadyRomanized

        val cacheKey = cacheKey(trackId, "romanize", slots)
        val cached = lock.withLock { memoryEntries[cacheKey] }
        if (cached != null && cached.version == CACHE_VERSION && cached.texts.size == slots.size) {
            return RomanizationResult.Romanized(
                lines = rebuild(lines, slots, cached.texts),
                sourceLanguage = cached.sourceLanguage,
                fromCache = true,
            )
        }

        val target = targetLanguageTag.trim().ifBlank { "en" }
        val batches = batches(slots)
        val answers = coroutineScope {
            batches.chunked(MAX_PARALLEL_REQUESTS).flatMap { group ->
                group.map { batch -> async { requestRomanizationBatch(batch, target) } }.awaitAll()
            }
        }
        if (answers.any { it == null }) return RomanizationResult.Unavailable
        val complete = answers.filterNotNull()
        val source = complete
            .groupBy { canonicalLanguage(it.sourceLanguage) }
            .maxByOrNull { (_, values) -> values.sumOf { it.sourceWeight } }
            ?.key
            .orEmpty()
        val romanized = complete.flatMap { it.translations }
        if (source.isBlank() || romanized.size != slots.size) return RomanizationResult.Unavailable

        val entry = CachedTranslation(
            sourceLanguage = source,
            targetLanguage = "Latn",
            texts = romanized,
        )
        lock.withLock { memoryEntries[cacheKey] = entry }
        return RomanizationResult.Romanized(
            lines = rebuild(lines, slots, romanized),
            sourceLanguage = source,
            fromCache = false,
        )
    }

    private fun flatten(lines: List<LyricLine>): List<TextSlot> = buildList {
        lines.forEachIndexed { index, line ->
            if (line.text.isNotBlank()) {
                val header = Genius.isSectionHeader(line.text)
                add(
                    TextSlot(
                        lineIndex = index,
                        background = false,
                        text = if (header) line.text.removePrefix("[").removeSuffix("]").trim() else line.text,
                        sectionHeader = header,
                    ),
                )
            }
            line.background?.takeIf { it.text.isNotBlank() }?.let { background ->
                add(TextSlot(index, background = true, background.text, sectionHeader = false))
            }
        }
    }

    private fun batches(slots: List<TextSlot>): List<Batch> {
        val result = mutableListOf<Batch>()
        var current = mutableListOf<TextSlot>()
        var length = 0

        fun flush() {
            if (current.isEmpty()) return
            result += Batch(current.toList(), payload(current))
            current = mutableListOf()
            length = 0
        }

        slots.forEach { slot ->
            val added = slot.text.length + if (current.isEmpty()) 0 else 8
            if (current.isNotEmpty() && length + added > MAX_BATCH_CHARS) flush()
            current += slot
            length += added
        }
        flush()
        return result
    }

    private fun payload(slots: List<TextSlot>): String = buildString {
        slots.forEachIndexed { index, slot ->
            if (index > 0) append('\n').append(marker(index)).append('\n')
            append(slot.text)
        }
    }

    private fun marker(index: Int): String = "\uE000${index.toString().padStart(4, '0')}\uE001"

    private suspend fun requestBatch(
        batch: Batch,
        target: String,
        sourceLanguage: String = "auto",
    ): BatchAnswer? {
        val response = try {
            val httpResp = client.submitForm(
                url = ENDPOINT,
                formParameters = Parameters.build {
                    append("client", "dict-chrome-ex")
                    append("sl", sourceLanguage)
                    append("tl", target)
                    append("dt", "t")
                    append("q", batch.payload)
                }
            ) {
                header("User-Agent", "BitChord/1.8-beta1")
                header("Accept", "application/json")
            }
            if (httpResp.status.value !in 200..299) return null
            httpResp.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }

        return runCatching {
            val root = json.parseToJsonElement(response).jsonArray
            val translatedBody = root[0].jsonArray.joinToString(separator = "") { segment ->
                segment.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val source = root.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty()
            val parts = translatedBody.split(markerRegex).map(String::trim)
            if (parts.size != batch.slots.size || parts.any { it.isBlank() }) return@runCatching null
            BatchAnswer(parts, source, batch.payload.length)
        }.getOrNull()
    }

    private suspend fun requestRomanizationBatch(batch: Batch, target: String): BatchAnswer? {
        val response = try {
            val httpResp = client.submitForm(
                url = ENDPOINT,
                formParameters = Parameters.build {
                    append("client", "dict-chrome-ex")
                    append("sl", "auto")
                    append("tl", target)
                    append("dt", "rm")
                    append("q", batch.payload)
                }
            ) {
                header("User-Agent", "BitChord/1.8-beta1")
                header("Accept", "application/json")
            }
            if (httpResp.status.value !in 200..299) return null
            httpResp.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }

        return runCatching {
            val root = json.parseToJsonElement(response).jsonArray
            val romanizedBody = root[0].jsonArray.joinToString(separator = "") { segment ->
                segment.jsonArray.getOrNull(3)?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val source = root.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty()
            val parts = romanizedBody.split(markerRegex).map(String::trim)
            if (source.isBlank() || parts.size != batch.slots.size || parts.any { it.isBlank() }) {
                return@runCatching null
            }
            BatchAnswer(parts, source, batch.payload.length)
        }.getOrNull()
    }

    private fun hasNonLatinLetters(slots: List<TextSlot>): Boolean = slots.any { slot ->
        slot.text.any { ch -> ch.isLetter() && ch.code > 0x024F }
    }

    private fun rebuild(
        original: List<LyricLine>,
        slots: List<TextSlot>,
        translated: List<String>,
    ): List<LyricLine> {
        val byLine = slots.zip(translated).groupBy { it.first.lineIndex }
        return original.mapIndexed { index, line ->
            val entries = byLine[index].orEmpty()
            val lead = entries.firstOrNull { !it.first.background }
            val backing = entries.firstOrNull { it.first.background }
            val leadText = lead?.let { (slot, text) -> if (slot.sectionHeader) "[$text]" else text }
                ?: line.text
            line.copy(
                text = leadText,
                words = retimeWords(line, leadText),
                timingSource = line.takeIf { it.isWordSynced },
                background = line.background?.let { source ->
                    val text = backing?.second ?: source.text
                    source.copy(
                        text = text,
                        words = retimeWords(source, text),
                        timingSource = source.takeIf { it.isWordSynced },
                    )
                },
            )
        }
    }

    private fun retimeWords(source: LyricLine, translated: String): List<LyricWord> {
        if (source.words.isEmpty() || translated.isEmpty()) return emptyList()
        val start = source.words.first().startMs
        return listOf(LyricWord(start, source.words.last().endMs, translated))
    }

    private fun canonicalLanguage(tag: String): String {
        val base = tag.replace('_', '-').substringBefore('-').lowercase()
        return when (base) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> base
        }
    }

    private fun sameLanguage(first: String, second: String): Boolean =
        canonicalLanguage(first) == canonicalLanguage(second)

    private fun cacheKey(trackId: String, target: String, slots: List<TextSlot>): String {
        val source = buildString {
            append(trackId).append('\u0000').append(target)
            slots.forEach { append('\u0000').append(it.text) }
        }
        return PlatformCrypto.sha256Hex(source.encodeToByteArray())
    }
}
