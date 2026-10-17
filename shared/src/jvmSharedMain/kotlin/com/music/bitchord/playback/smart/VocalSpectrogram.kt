/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 */

package com.music.bitchord.playback.smart

actual object VocalSpectrogram {
    actual val available: Boolean get() = MelSpectrogram.available
    actual val bins: Int by lazy { if (available) nativeBins() else 2049 }
    actual val sampleRate: Double by lazy { if (available) nativeSampleRate() else 44_100.0 }
    actual val hop: Int by lazy { if (available) nativeHop() else 1024 }
    actual val fftSize: Int by lazy { if (available) nativeFftSize() else 4096 }
    actual val frameRate: Double get() = sampleRate / hop
    actual val CHANNELS: Int get() = 2

    actual fun compute(left: FloatArray, right: FloatArray, rate: Double): Spectrogram? {
        if (!available || left.isEmpty() || left.size != right.size) return null
        val values = nativeCompute(left, right, rate)
        if (values.isEmpty()) return null
        return Spectrogram(values, frames = values.size / (CHANNELS * bins), bins = bins)
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

    @JvmStatic private external fun nativeCompute(left: FloatArray, right: FloatArray, rate: Double): FloatArray
    @JvmStatic private external fun nativeBins(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
    @JvmStatic private external fun nativeFftSize(): Int
}
