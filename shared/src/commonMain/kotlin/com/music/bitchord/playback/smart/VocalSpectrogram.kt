package com.music.bitchord.playback.smart

expect object VocalSpectrogram {
    val available: Boolean
    val bins: Int
    val sampleRate: Double
    val hop: Int
    val fftSize: Int
    val frameRate: Double
    val CHANNELS: Int

    fun compute(left: FloatArray, right: FloatArray, rate: Double = VocalSpectrogram.sampleRate): Spectrogram?

    class Spectrogram(
        values: FloatArray,
        frames: Int,
        bins: Int
    ) {
        val values: FloatArray
        val frames: Int
        val bins: Int
    }
}
