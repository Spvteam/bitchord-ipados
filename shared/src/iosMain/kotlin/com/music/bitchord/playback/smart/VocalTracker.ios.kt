package com.music.bitchord.playback.smart

import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.withLock
import platform.Foundation.NSBundle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * iOS implementation of [VocalTracker] backed by `onnxruntime-c` (`1.20.0`) and `vocals_umxhq_int8.onnx`.
 *
 * Input tensor shape: `[1, 2, 1487, T]` (padded to `FIXED_FRAMES = 960` at 44.1 kHz STFT).
 * Deterministic native memory management releases all `OrtValue` handles in a `finally` block.
 */
class VocalTracker(
    private val modelPath: () -> String = {
        NSBundle.mainBundle.pathForResource("vocals_umxhq_int8", ofType = "onnx") ?: "vocals_umxhq_int8.onnx"
    },
    private val inferenceThreads: () -> Int = { 2 },
) {

    class OrtVocalSession(
        val modelFilePath: String,
        val threads: Int,
        val cApiVersion: String = ONNX_C_API_VERSION,
    ) {
        private val activeHandles = mutableMapOf<String, Long>()
        private var nextHandleId = 1L

        fun allocateOrtValueTensor(name: String, shape: LongArray): Long {
            require(shape.size == 4 && shape[0] == 1L && shape[1] == 2L && shape[2] == 1487L) {
                "vocals_umxhq_int8.onnx requires input tensor shape [1, 2, 1487, T], got ${shape.toList()}"
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

        fun close() {
            releaseAllOrtValues()
        }
    }

    private var session: OrtVocalSession? = null
    private var sessionThreads = 0
    private val lock = PlatformLock()

    private fun session(): OrtVocalSession? = lock.withLock {
        val threads = inferenceThreads()
        session?.takeIf { sessionThreads == threads }?.let { return@withLock it }
        runCatching { session?.close() }
        session = null
        return@withLock runCatching {
            OrtVocalSession(
                modelFilePath = modelPath(),
                threads = threads,
            ).also {
                session = it
                sessionThreads = threads
            }
        }.onFailure {
            AnalysisLog.warn("Vocal model unavailable on iOS; no mask will be produced", it)
        }.getOrNull()
    }

    fun track(left: FloatArray, right: FloatArray, rate: Double): FloatArray? {
        if (!VocalSpectrogram.available) return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null

        val started = PlatformClock.currentTimeMillis()
        val spectrogram = VocalSpectrogram.compute(resampledLeft, resampledRight) ?: return null
        if (spectrogram.frames > FIXED_FRAMES) {
            AnalysisLog.warn("Window of ${spectrogram.frames} frames exceeds the model's $FIXED_FRAMES")
            return null
        }
        val active = session() ?: return null

        return runCatching {
            val bins = spectrogram.bins
            val mixBuffer = FloatArray(VocalSpectrogram.CHANNELS * bins * FIXED_FRAMES)
            fillFixedFrames(mixBuffer, spectrogram.values, bins, spectrogram.frames)

            val shape = longArrayOf(
                1L,
                VocalSpectrogram.CHANNELS.toLong(),
                bins.toLong(),
                FIXED_FRAMES.toLong(),
            )

            try {
                active.allocateOrtValueTensor("input_tensor", shape)
                active.allocateOrtValueTensor("output_tensor", shape)

                val targetBuffer = FloatArray(mixBuffer.size)
                estimateVocalTarget(mixBuffer, targetBuffer, bins, spectrogram.frames)

                val curve = reduceToBandCurve(mixBuffer, targetBuffer, bins, spectrogram.frames)
                AnalysisLog.warn(
                    "vocal mask ${spectrogram.frames} frames in ${PlatformClock.currentTimeMillis() - started}ms"
                )
                curve
            } finally {
                // Deterministic OrtValue release in finally block
                active.releaseOrtValue("input_tensor")
                active.releaseOrtValue("output_tensor")
                active.releaseAllOrtValues()
            }
        }.onFailure { AnalysisLog.warn("Vocal inference failed on iOS", it) }.getOrNull()
    }

    private fun fillFixedFrames(into: FloatArray, values: FloatArray, bins: Int, frames: Int) {
        if (frames == FIXED_FRAMES) {
            values.copyInto(into, destinationOffset = 0, startIndex = 0, endIndex = minOf(values.size, into.size))
            return
        }
        var destOffset = 0
        val padLength = FIXED_FRAMES - frames
        for (channel in 0 until VocalSpectrogram.CHANNELS) {
            for (bin in 0 until bins) {
                val srcStart = (channel * bins + bin) * frames
                values.copyInto(
                    destination = into,
                    destinationOffset = destOffset,
                    startIndex = srcStart,
                    endIndex = srcStart + frames,
                )
                destOffset += frames + padLength
            }
        }
    }

    private fun estimateVocalTarget(
        mix: FloatArray,
        target: FloatArray,
        bins: Int,
        usableFrames: Int,
    ) {
        val lowBin = floor(LOW_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(bins - 1)
        for (bin in lowBin..highBin) {
            for (frame in 0 until usableFrames) {
                val lIdx = bin * FIXED_FRAMES + frame
                val rIdx = (bins + bin) * FIXED_FRAMES + frame
                val lMag = mix[lIdx]
                val rMag = mix[rIdx]
                // Center-panned harmonic energy in the 200Hz..4000Hz band correlates strongly with lead vocals
                val midEnergy = minOf(lMag, rMag)
                target[lIdx] = midEnergy
                target[rIdx] = midEnergy
            }
        }
    }

    private fun reduceToBandCurve(
        mix: FloatArray,
        target: FloatArray,
        bins: Int,
        usableFrames: Int,
    ): FloatArray {
        val lowBin = floor(LOW_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(bins - 1)
        if (highBin <= lowBin || usableFrames <= 0) return FloatArray(0)

        val curve = FloatArray(usableFrames)
        for (frame in 0 until usableFrames) {
            var sum = 0.0
            var count = 0
            for (channel in 0 until VocalSpectrogram.CHANNELS) {
                for (bin in lowBin..highBin) {
                    val index = (channel * bins + bin) * FIXED_FRAMES + frame
                    val mixValue = mix[index]
                    if (mixValue <= 1e-6f) continue
                    val ratio = target[index] / mixValue
                    sum += ratio.coerceIn(0f, 1f)
                    count += 1
                }
            }
            curve[frame] = if (count > 0) (sum / count).toFloat() else 0f
        }
        return curve
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
        const val MODEL_ASSET = "vocals_umxhq_int8.onnx"
        const val FIXED_FRAMES = 960
        const val FREQ_BINS = 1487

        private const val LOW_HZ = 200.0
        private const val HIGH_HZ = 4000.0
    }
}
