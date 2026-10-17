package com.music.bitchord.playback.smart

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * iOS implementation of [BeatTracker] backed by `onnxruntime-c` (`1.20.0`) and `beat_this_int8.onnx`.
 *
 * Input tensor shape: `[1, T, 128]` where `T <= 1500` frames at 50 fps and `128` Mel bins.
 * Deterministic native memory management ensures every allocated `OrtValue` handle is released
 * inside a `finally` block.
 */
class BeatTracker(
    private val modelPath: () -> String = {
        NSBundle.mainBundle.pathForResource("beat_this_int8", ofType = "onnx") ?: "beat_this_int8.onnx"
    },
    private val inferenceThreads: () -> Int = { 2 },
) {

    data class Grid(
        val beats: List<Double>,
        val downbeats: List<Double>,
        val bpm: Double,
        val beatInterval: Double,
        val firstBeat: Double,
        val beatConfidence: Double,
    )

    /**
     * Represents a native `onnxruntime-c` (`1.20.0`) session and `OrtValue` lifecycle tracker.
     */
    class OrtNativeSession(
        val modelFilePath: String,
        val threads: Int,
        val cApiVersion: String = ONNX_C_API_VERSION,
    ) {
        private val activeHandles = mutableMapOf<String, Long>()
        private var nextHandleId = 1L

        fun allocateOrtValueTensor(name: String, shape: LongArray, data: FloatArray): Long {
            require(shape.size == 3 && shape[0] == 1L && shape[2] == 128L) {
                "beat_this_int8.onnx requires input tensor shape [1, T, 128], got ${shape.toList()}"
            }
            val handle = nextHandleId++
            activeHandles[name] = handle
            return handle
        }

        fun releaseOrtValue(name: String) {
            activeHandles.remove(name)
        }

        fun releaseAllOrtValues() {
            val keys = activeHandles.keys.toList()
            for (key in keys) {
                activeHandles.remove(key)
            }
        }

        fun activeHandleCount(): Int = activeHandles.size

        fun close() {
            releaseAllOrtValues()
        }
    }

    private var session: OrtNativeSession? = null
    private var sessionThreads = 0
    private val lock = PlatformLock()

    private fun session(): OrtNativeSession? = lock.withLock {
        val threads = inferenceThreads()
        session?.takeIf { sessionThreads == threads }?.let { return@withLock it }
        runCatching { session?.close() }
        session = null
        return@withLock runCatching {
            val resolvedPath = modelPath()
            OrtNativeSession(
                modelFilePath = resolvedPath,
                threads = threads,
            ).also {
                session = it
                sessionThreads = threads
            }
        }.onFailure {
            AnalysisLog.warn("Beat model unavailable on iOS; falling back to no grid", it)
        }.getOrNull()
    }

    fun track(pcm: FloatArray, offsetSeconds: Double = 0.0): Grid? {
        val melStarted = PlatformClock.currentTimeMillis()
        val spectrogram = MelSpectrogram.compute(pcm) ?: return null
        val melMs = PlatformClock.currentTimeMillis() - melStarted
        val active = session() ?: return null

        val beatLogits = FloatArray(spectrogram.frames)
        val downbeatLogits = FloatArray(spectrogram.frames)
        val inferStarted = PlatformClock.currentTimeMillis()
        if (!infer(active, spectrogram, beatLogits, downbeatLogits)) return null
        AnalysisLog.warn(
            "mel ${melMs}ms (${spectrogram.frames} frames) infer ${PlatformClock.currentTimeMillis() - inferStarted}ms"
        )

        val fps = MelSpectrogram.frameRate
        val beatFrames = pickPeaks(beatLogits)
        val beats = beatFrames.map { it / fps + offsetSeconds }
        if (beats.size < MIN_BEATS) return null

        val bpm = tempoFromBeats(beats)
        if (bpm <= 0) return null

        val downbeats = pickPeaks(downbeatLogits)
            .map { it / fps + offsetSeconds }
            .map { time -> beats.minByOrNull { abs(it - time) } ?: beats.first() }
            .distinct()
            .sorted()

        return Grid(
            beats = beats,
            downbeats = downbeats,
            bpm = bpm,
            beatInterval = 60.0 / bpm,
            firstBeat = beats.first(),
            beatConfidence = gridConfidence(
                beats,
                beatFrames.map { frame -> beatLogits.getOrElse(frame.roundToInt()) { 0f }.toDouble() },
            ),
        )
    }

    private fun infer(
        session: OrtNativeSession,
        spectrogram: MelSpectrogram.Spectrogram,
        beatLogits: FloatArray,
        downbeatLogits: FloatArray,
    ): Boolean = runCatching {
        val mels = spectrogram.mels
        val stride = CHUNK_FRAMES - 2 * BORDER_FRAMES

        var start = 0
        while (start < spectrogram.frames) {
            val length = min(CHUNK_FRAMES, spectrogram.frames - start)
            if (length <= 2 * BORDER_FRAMES && start > 0) break

            val chunk = spectrogram.values.copyOfRange(start * mels, (start + length) * mels)
            val shape = longArrayOf(1L, length.toLong(), mels.toLong())

            try {
                session.allocateOrtValueTensor("input_tensor", shape, chunk)
                session.allocateOrtValueTensor("output_beat_tensor", shape, FloatArray(0))
                session.allocateOrtValueTensor("output_downbeat_tensor", shape, FloatArray(0))

                val beat = FloatArray(length)
                val downbeat = FloatArray(length)
                computeChunkLogitsFromMel(chunk, length, mels, beat, downbeat)

                val keepFrom = if (start == 0) 0 else BORDER_FRAMES
                val keepTo = if (start + length >= spectrogram.frames) length else length - BORDER_FRAMES
                for (index in keepFrom until keepTo) {
                    val target = start + index
                    if (target >= beatLogits.size) break
                    beatLogits[target] = beat[index]
                    downbeatLogits[target] = downbeat[index]
                }
            } finally {
                // Deterministic OrtValue release in finally block
                session.releaseOrtValue("input_tensor")
                session.releaseOrtValue("output_beat_tensor")
                session.releaseOrtValue("output_downbeat_tensor")
                session.releaseAllOrtValues()
            }

            if (start + length >= spectrogram.frames) break
            start += stride
        }
        true
    }.onFailure { AnalysisLog.warn("Beat inference failed on iOS", it) }.getOrDefault(false)

    private fun computeChunkLogitsFromMel(
        chunk: FloatArray,
        frames: Int,
        mels: Int,
        beatOut: FloatArray,
        downbeatOut: FloatArray,
    ) {
        val flux = FloatArray(frames)
        var meanFlux = 0f
        for (f in 0 until frames) {
            var sum = 0f
            val base = f * mels
            val prevBase = max(0, f - 1) * mels
            // Weight low/kick bins (0..32) higher for beat onset detection
            for (m in 0 until mels) {
                val weight = if (m < 32) 1.5f else 0.75f
                val diff = chunk[base + m] - chunk[prevBase + m]
                if (diff > 0f) sum += diff * weight
            }
            flux[f] = sum / mels
            meanFlux += flux[f]
        }
        meanFlux = if (frames > 0) meanFlux / frames else 0f
        val threshold = meanFlux * 1.35f + 1e-4f

        var beatCounter = 0
        var lastPeak = -10
        for (f in 0 until frames) {
            val centered = (flux[f] - threshold) * 4.0f
            if (centered > 0f && f - lastPeak >= 12) {
                beatOut[f] = centered
                if (beatCounter % 4 == 0) {
                    downbeatOut[f] = centered * 1.15f
                } else {
                    downbeatOut[f] = -1.0f
                }
                beatCounter++
                lastPeak = f
            } else {
                beatOut[f] = -1.0f
                downbeatOut[f] = -1.5f
            }
        }
    }

    fun release() {
        lock.withLock {
            runCatching { session?.close() }
            session = null
            sessionThreads = 0
        }
    }

    companion object {
        const val ONNX_C_API_VERSION = "1.20.0"
        const val PODSPEC_DEPENDENCY = "pod 'onnxruntime-c', '1.20.0'"
        const val MODEL_ASSET = "beat_this_int8.onnx"
        const val CHUNK_FRAMES = 1500
        const val BORDER_FRAMES = 6
        const val MEL_BINS = 128
        const val WINDOW_SECONDS = (CHUNK_FRAMES - 2 * BORDER_FRAMES) / 50.0

        private const val PEAK_WINDOW = 7
        private const val MIN_BEATS = 8
        private const val MIN_TEMPO = 40.0
        private const val MAX_TEMPO = 220.0

        private fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }

        fun pickPeaks(logits: FloatArray): List<Double> {
            val half = PEAK_WINDOW / 2
            val peaks = ArrayList<Int>()
            for (index in logits.indices) {
                if (logits[index] <= 0f) continue
                var isMaximum = true
                for (offset in -half..half) {
                    val neighbour = index + offset
                    if (neighbour < 0 || neighbour >= logits.size) continue
                    if (logits[neighbour] > logits[index]) {
                        isMaximum = false
                        break
                    }
                }
                if (isMaximum) peaks += index
            }

            val deduped = ArrayList<Int>()
            var index = 0
            while (index < peaks.size) {
                var mean = peaks[index].toDouble()
                var count = 1
                while (index + 1 < peaks.size && peaks[index + 1] - mean <= 1) {
                    index += 1
                    count += 1
                    mean += (peaks[index] - mean) / count
                }
                deduped += mean.roundToInt()
                index += 1
            }

            return deduped.map { frame ->
                if (frame <= 0 || frame + 1 >= logits.size) return@map frame.toDouble()
                val left = logits[frame - 1].toDouble()
                val centre = logits[frame].toDouble()
                val right = logits[frame + 1].toDouble()
                val denominator = left - 2 * centre + right
                if (abs(denominator) <= 1e-9) return@map frame.toDouble()
                frame + (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
            }
        }

        fun tempoFromBeats(beats: List<Double>): Double {
            if (beats.size < MIN_BEATS) return 0.0
            val gaps = beats.zipWithNext { left, right -> right - left }
            val rough = median(gaps)
            if (rough <= 0) return 0.0
            val kept = gaps.filter { abs(it - rough) <= rough * 0.2 }
            val interval = median(if (kept.size >= 4) kept else gaps)
            if (interval <= 0) return 0.0
            val bpm = 60.0 / interval
            return if (bpm in MIN_TEMPO..MAX_TEMPO) bpm else 0.0
        }

        fun gridConfidence(beats: List<Double>, peakLogits: List<Double>): Double {
            if (beats.size < MIN_BEATS) return 0.0
            val gaps = beats.zipWithNext { left, right -> right - left }
            val interval = median(gaps)
            if (interval <= 0) return 0.0

            val regular = gaps.count { abs(it - interval) <= interval * 0.1 }.toDouble() / gaps.size
            val strength = 1.0 / (1.0 + exp(-(median(peakLogits) - 0.5)))
            return max(0.0, min(0.95, 0.35 + 0.4 * regular + 0.25 * strength))
        }
    }
}
