package com.music.bitchord.playback.smart

import com.music.bitchord.native.analysis.bitchord_compute_vocal_spectrogram
import com.music.bitchord.native.analysis.bitchord_vocal_spectrogram_frame_count
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
actual object VocalSpectrogram {
    actual val available: Boolean get() = true
    actual val bins: Int get() = 1487
    actual val sampleRate: Double get() = 44100.0
    actual val hop: Int get() = 1024
    actual val fftSize: Int get() = 4096
    actual val frameRate: Double get() = sampleRate / hop
    actual val CHANNELS: Int get() = 2

    actual fun compute(left: FloatArray, right: FloatArray, rate: Double): Spectrogram? {
        if (left.isEmpty() || left.size != right.size || rate <= 0.0) return null
        val targetRate = this.sampleRate
        val effectiveCount = if (rate.toInt() == targetRate.toInt()) {
            left.size
        } else {
            (left.size * (targetRate / rate)).roundToInt()
        }
        if (effectiveCount < fftSize) return null

        val nativeFrames = bitchord_vocal_spectrogram_frame_count(left.size, rate.toInt())
        val pad = fftSize / 2
        val paddedLength = effectiveCount + 2 * pad
        val fallbackFrames = (paddedLength - fftSize) / hop + 1
        val frames = if (nativeFrames > 0) maxOf(nativeFrames, fallbackFrames) else fallbackFrames
        if (frames <= 0) return null

        val totalFloats = CHANNELS * bins * frames
        val outBuffer = FloatArray(totalFloats)

        left.usePinned { pinnedLeft ->
            right.usePinned { pinnedRight ->
                outBuffer.usePinned { pinnedOut ->
                    bitchord_compute_vocal_spectrogram(
                        pinnedLeft.addressOf(0),
                        pinnedRight.addressOf(0),
                        left.size,
                        rate.toInt(),
                        pinnedOut.addressOf(0)
                    )
                }
            }
        }

        return Spectrogram(values = outBuffer, frames = frames, bins = bins)
    }

    actual class Spectrogram actual constructor(
        actual val values: FloatArray,
        actual val frames: Int,
        actual val bins: Int
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }
}
