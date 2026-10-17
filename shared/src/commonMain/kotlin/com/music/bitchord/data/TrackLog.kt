package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

internal expect fun trackLogAbout(id: String?): CoroutineContext
internal expect fun currentWorkingTrack(): String?

/**
 * The app's own record of how each track came to be playing, as text you can
 * paste somewhere.
 */
object TrackLog {
    /**
     * Where each line is echoed as it is logged — logcat on the phone's debug
     * builds, the desktop's own log. The held lines are recorded regardless.
     */
    @Volatile
    var echo: ((level: Char, tag: String, message: String, error: Throwable?) -> Unit)? = null

    /** The exported header's line on which sources may stand in for YouTube, where there is one. */
    @Volatile
    var sourcesLine: () -> String? = { null }

    /** The platform's own closing lines for an exported log's header — the build, the device. */
    @Volatile
    var headerLines: () -> List<String> = { emptyList() }

    // ── Writing ─────────────────────────────────────────────────────────────

    fun d(tag: String, message: String, about: String? = currentWorkingTrack()) {
        echo?.invoke('D', tag, message, null)
        record('D', message, about)
    }

    fun i(tag: String, message: String, about: String? = currentWorkingTrack()) {
        echo?.invoke('I', tag, message, null)
        record('I', message, about)
    }

    fun w(tag: String, message: String, about: String? = currentWorkingTrack()) {
        echo?.invoke('W', tag, message, null)
        record('W', message, about)
    }

    fun w(tag: String, message: String, error: Throwable, about: String? = currentWorkingTrack()) {
        echo?.invoke('W', tag, message, error)
        record('W', "$message\n${error.stackTraceToString()}", about)
    }

    fun e(tag: String, message: String, about: String? = currentWorkingTrack()) {
        echo?.invoke('E', tag, message, null)
        record('E', message, about)
    }

    fun e(tag: String, message: String, error: Throwable, about: String? = currentWorkingTrack()) {
        echo?.invoke('E', tag, message, error)
        record('E', "$message\n${error.stackTraceToString()}", about)
    }

    // ── Whose line is it ────────────────────────────────────────────────────

    fun about(id: String?): CoroutineContext = trackLogAbout(id)

    private class Line(val at: Long, val level: Char, val text: String, val track: String?)

    private val linesLock = PlatformLock()
    private val lines = ArrayDeque<Line>()

    /** Total characters held, so the buffer is bounded by size rather than by count. */
    private var held = 0

    private fun record(level: Char, message: String, about: String?) {
        val text = if (message.length > MAX_LINE_CHARS) {
            message.take(MAX_LINE_CHARS) + "…(${message.length - MAX_LINE_CHARS} more)"
        } else {
            message
        }
        linesLock.withLock {
            lines.addLast(Line(PlatformClock.currentTimeMillis(), level, text, about))
            held += text.length
            while (held > MAX_HELD_CHARS && lines.isNotEmpty()) {
                held -= lines.removeFirst().text.length
            }
        }
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    private val startedAtLock = PlatformLock()
    private val startedAt = mutableMapOf<String, Long>()

    fun onTrackStarted(videoId: String) = startedAtLock.withLock {
        if (startedAt.size >= MAX_REMEMBERED) startedAt.clear()
        startedAt[videoId] = PlatformClock.currentTimeMillis()
    }

    suspend fun forTrack(song: Song, stats: NerdStats.Snapshot?): String = withContext(Dispatchers.Default) {
        val held = linesLock.withLock { lines.toList() }
        val started = startedAtLock.withLock { startedAt[song.videoId] }
        val from = listOfNotNull(
            held.firstOrNull { it.track == song.videoId }?.at,
            started?.minus(LEAD_IN_MS),
        ).minOrNull()
        val since = held.filter { from == null || it.at >= from }
        val window = since.filter { it.track == null || it.track == song.videoId }
        header(song, stats, from, window.size, since.size - window.size) + "\n" +
            window.joinToString("\n") { "${formatTimestamp(it.at)} ${it.level} ${it.text}" } +
            "\n"
    }

    // ── The part that isn't the log ─────────────────────────────────────────

    private fun header(
        song: Song,
        stats: NerdStats.Snapshot?,
        from: Long?,
        count: Int,
        elsewhere: Int,
    ) = buildString {
        appendLine("BitChord log — ${song.title} — ${song.artist}")
        appendLine("id=${song.videoId} duration=${song.durationText ?: "?"} album=${song.albumName ?: "?"}")
        appendLine("playing: ${stats.describe()}")
        sourcesLine()?.let(::appendLine)
        val now = PlatformClock.currentTimeMillis()
        appendLine(
            "window: ${from?.let { formatTimestamp(it) } ?: "everything held"} → " +
                "${formatTimestamp(now)} ($count lines" +
                (if (elsewhere > 0) ", $elsewhere for other tracks left out)" else ")"),
        )
        headerLines().forEach(::appendLine)
    }

    private fun NerdStats.Snapshot?.describe(): String {
        if (this == null) return "nothing reported"
        val measured = listOfNotNull(
            mimeType,
            bitDepth?.let { "$it-bit" },
            bitrateKbps?.let { "$it kbps" },
            sampleRateHz?.let { "$it Hz" },
            channels?.let { "${it}ch" },
        ).joinToString(" · ").ifEmpty { "nothing reported" }
        val promised = claimed?.summary?.let { " (source said: $it)" }.orEmpty()
        val tier = when {
            isHiRes -> " [Hi-Res Lossless]"
            isLossless -> " [Lossless]"
            isHiQuality -> " [Hi-Quality]"
            else -> ""
        }
        return measured + promised + tier
    }

    private fun formatTimestamp(epochMs: Long): String {
        val totalSeconds = (epochMs / 1000).coerceAtLeast(0)
        val ms = (epochMs % 1000).coerceAtLeast(0)
        val s = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val m = totalMinutes % 60
        val h = (totalMinutes / 60) % 24
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${ms.toString().padStart(3, '0')}"
    }

    private const val LEAD_IN_MS = 20_000L
    private const val MAX_HELD_CHARS = 512_000
    private const val MAX_LINE_CHARS = 2_000
    private const val MAX_REMEMBERED = 32
}
