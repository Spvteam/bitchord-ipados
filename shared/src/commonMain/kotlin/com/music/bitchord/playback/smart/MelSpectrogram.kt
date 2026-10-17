package com.music.bitchord.playback.smart

expect object MelSpectrogram {
    val available: Boolean
    val mels: Int
    val sampleRate: Double
    val hop: Int
    val frameRate: Double

    fun compute(samples: FloatArray, sampleRate: Double = MelSpectrogram.sampleRate): Spectrogram?
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = MelSpectrogram.sampleRate): FloatArray?

    class Spectrogram(
        values: FloatArray,
        frames: Int,
        mels: Int
    ) {
        val values: FloatArray
        val frames: Int
        val mels: Int
        val durationSeconds: Double
    }
}
