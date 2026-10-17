package com.music.bitchord.playback.smart

import com.music.bitchord.native.analysis.bitchord_compute_mel_spectrogram
import com.music.bitchord.native.analysis.bitchord_mel_spectrogram_frame_count
import com.music.bitchord.native.analysis.bitchord_resample
import com.music.bitchord.native.analysis.bitchord_resample_output_count
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
actual object MelSpectrogram {
    actual val available: Boolean get() = true
    actual val mels: Int get() = 128
    actual val sampleRate: Double get() = 22050.0
    actual val hop: Int get() = 441
    actual val frameRate: Double get() = sampleRate / hop

    actual fun compute(samples: FloatArray, sampleRate: Double): Spectrogram? {
        if (samples.isEmpty() || sampleRate <= 0.0) return null
        val targetRate = this.sampleRate
        val effectiveCount = if (sampleRate.toInt() == targetRate.toInt()) {
            samples.size
        } else {
            (samples.size * (targetRate / sampleRate)).roundToInt()
        }
        if (effectiveCount < 1024) return null

        val nativeFrames = bitchord_mel_spectrogram_frame_count(samples.size, sampleRate.toInt())
        val pad = 512
        val paddedLength = effectiveCount + 2 * pad
        val fallbackFrames = (paddedLength - 1024) / hop + 1
        val frames = if (nativeFrames > 0) maxOf(nativeFrames, fallbackFrames) else fallbackFrames
        if (frames <= 0) return null

        val totalFloats = frames * mels
        val outBuffer = FloatArray(totalFloats)

        samples.usePinned { pinnedIn ->
            outBuffer.usePinned { pinnedOut ->
                bitchord_compute_mel_spectrogram(
                    pinnedIn.addressOf(0),
                    samples.size,
                    sampleRate.toInt(),
                    pinnedOut.addressOf(0)
                )
            }
        }

        return Spectrogram(values = outBuffer, frames = frames, mels = mels)
    }

    actual fun resample(samples: FloatArray, inputRate: Double, outputRate: Double): FloatArray? {
        if (samples.isEmpty() || inputRate <= 0.0 || outputRate <= 0.0) return null
        if (inputRate == outputRate) return samples.copyOf()
        val nativeCount = bitchord_resample_output_count(samples.size, inputRate.toInt(), outputRate.toInt())
        val ratio = outputRate / inputRate
        val fallbackCount = (samples.size * ratio).roundToInt()
        val outLength = if (nativeCount > 0) nativeCount else fallbackCount
        if (outLength <= 0) return null

        val out = FloatArray(outLength)
        val written = samples.usePinned { pinnedIn ->
            out.usePinned { pinnedOut ->
                bitchord_resample(
                    pinnedIn.addressOf(0),
                    samples.size,
                    inputRate.toInt(),
                    outputRate.toInt(),
                    pinnedOut.addressOf(0),
                    outLength
                )
            }
        }
        if (written <= 0) {
            for (i in 0 until outLength) {
                val pos = i / ratio
                val idx0 = pos.toInt().coerceIn(0, samples.size - 1)
                val idx1 = (idx0 + 1).coerceIn(0, samples.size - 1)
                val frac = (pos - idx0).toFloat()
                out[i] = samples[idx0] * (1f - frac) + samples[idx1] * frac
            }
        }
        return out
    }

    actual class Spectrogram actual constructor(
        actual val values: FloatArray,
        actual val frames: Int,
        actual val mels: Int
    ) {
        actual val durationSeconds: Double get() = frames / (sampleRate / hop)

        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames && mels == other.mels &&
                values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + mels
    }
}
