package com.music.bitchord.playback.smart

import com.music.bitchord.native.analysis.bitchord_analyze_audio_json
import com.music.bitchord.native.analysis.bitchord_free_string
import com.music.bitchord.native.analysis.bitchord_resample
import com.music.bitchord.native.analysis.bitchord_resample_output_count
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
actual object TrackFeatures {
    actual val available: Boolean get() = true
    actual val sampleRate: Double get() = 11025.0

    actual fun analyze(samples: FloatArray, durationSeconds: Double): Features? {
        if (samples.isEmpty()) return null
        val jsonString: String = samples.usePinned { pinned ->
            val cStr: CPointer<ByteVar>? = bitchord_analyze_audio_json(
                pinned.addressOf(0),
                samples.size,
                sampleRate.toInt()
            )
            if (cStr == null) return null
            try {
                cStr.toKString()
            } finally {
                bitchord_free_string(cStr)
            }
        }

        return runCatching {
            parseTrackFeaturesJson(Json.parseToJsonElement(jsonString).jsonObject)
        }.onFailure { AnalysisLog.warn("Could not parse analysis output", it) }.getOrNull()
    }

    actual fun resample(samples: FloatArray, inputRate: Double, outputRate: Double): FloatArray? {
        if (samples.isEmpty() || inputRate <= 0.0 || outputRate <= 0.0) return null
        if (inputRate == outputRate) return samples.copyOf()
        val nativeCount = bitchord_resample_output_count(samples.size, inputRate, outputRate)
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
                    inputRate,
                    outputRate,
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

    actual class Features actual constructor(
        actual val duration: Double,
        actual val bpm: Double,
        actual val beatInterval: Double,
        actual val firstBeat: Double,
        actual val beatConfidence: Double,
        actual val key: String,
        actual val keyConfidence: Double,
        actual val audibleStartTime: Double,
        actual val pickupTime: Double,
        actual val introEndTime: Double,
        actual val outroStartTime: Double,
        actual val contentEndTime: Double,
        actual val mixInTime: Double,
        actual val mixOutTime: Double,
        actual val vocalProbability: Double,
        actual val downbeats: List<Double>,
        actual val phraseBoundaries: List<Double>,
        actual val vocalActivityMask: List<Double>,
        actual val energyCurve: List<EnergySample>,
        actual val lowEnergyCurve: List<EnergySample>,
        actual val mixInCandidates: List<MixCandidate>,
        actual val mixOutCandidates: List<MixCandidate>,
        actual val camelot: String,
        actual val durationMs: Long,
        actual val cueInMs: Long,
        actual val cueOutMs: Long,
    )
}
