package com.music.bitchord.playback.smart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

expect object TrackFeatures {
    val available: Boolean
    val sampleRate: Double

    fun analyze(samples: FloatArray, durationSeconds: Double): Features?
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = sampleRate): FloatArray?

    class Features(
        duration: Double,
        bpm: Double,
        beatInterval: Double,
        firstBeat: Double,
        beatConfidence: Double,
        key: String,
        keyConfidence: Double,
        audibleStartTime: Double,
        pickupTime: Double,
        introEndTime: Double,
        outroStartTime: Double,
        contentEndTime: Double,
        mixInTime: Double,
        mixOutTime: Double,
        vocalProbability: Double,
        downbeats: List<Double>,
        phraseBoundaries: List<Double>,
        vocalActivityMask: List<Double>,
        energyCurve: List<EnergySample>,
        lowEnergyCurve: List<EnergySample>,
        mixInCandidates: List<MixCandidate>,
        mixOutCandidates: List<MixCandidate>,
        camelot: String = "",
        durationMs: Long = 0L,
        cueInMs: Long = 0L,
        cueOutMs: Long = 0L,
    ) {
        val duration: Double
        val bpm: Double
        val beatInterval: Double
        val firstBeat: Double
        val beatConfidence: Double
        val key: String
        val keyConfidence: Double
        val audibleStartTime: Double
        val pickupTime: Double
        val introEndTime: Double
        val outroStartTime: Double
        val contentEndTime: Double
        val mixInTime: Double
        val mixOutTime: Double
        val vocalProbability: Double
        val downbeats: List<Double>
        val phraseBoundaries: List<Double>
        val vocalActivityMask: List<Double>
        val energyCurve: List<EnergySample>
        val lowEnergyCurve: List<EnergySample>
        val mixInCandidates: List<MixCandidate>
        val mixOutCandidates: List<MixCandidate>
        val camelot: String
        val durationMs: Long
        val cueInMs: Long
        val cueOutMs: Long
    }
}

/** Pure KMP JSON parser shared across platforms. */
fun parseTrackFeaturesJson(root: JsonObject): TrackFeatures.Features {
    fun JsonObject.number(name: String): Double =
        (this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0).orZero()

    fun JsonObject.text(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: ""

    fun JsonObject.doubles(name: String): List<Double> {
        val array = runCatching { this[name]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.doubleOrNull?.takeIf(Double::isFinite) }
    }

    fun JsonObject.energyCurve(name: String): List<EnergySample> {
        val array = runCatching { this[name]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val point = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val time = point["t"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val energy = point["e"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (time.isFinite() && energy.isFinite()) EnergySample(time, energy) else null
        }
    }

    fun JsonObject.cuePoints(name: String): List<MixCandidate> {
        val array = runCatching { this[name]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val point = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val time = point["t"]?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite)
                ?: return@mapNotNull null
            MixCandidate(time = time, score = point.number("s"), type = point.text("y"))
        }
    }

    val dur = root.number("duration").let { if (it > 0) it else root.number("duration_ms") / 1000.0 }
    val mixIn = root.number("mixInTime").let { if (it > 0) it else root.number("cue_in_ms") / 1000.0 }
    val mixOut = root.number("mixOutTime").let { if (it > 0) it else root.number("cue_out_ms") / 1000.0 }

    return TrackFeatures.Features(
        duration = dur,
        bpm = root.number("bpm"),
        beatInterval = root.number("beatInterval"),
        firstBeat = root.number("firstBeat"),
        beatConfidence = root.number("beatConfidence").let { if (it > 0) it else root.number("bpm_confidence") },
        key = root.text("key"),
        keyConfidence = root.number("keyConfidence"),
        audibleStartTime = root.number("audibleStartTime"),
        pickupTime = root.number("pickupTime"),
        introEndTime = root.number("introEndTime"),
        outroStartTime = root.number("outroStartTime"),
        contentEndTime = root.number("contentEndTime"),
        mixInTime = mixIn,
        mixOutTime = mixOut,
        vocalProbability = root.number("vocalProbability"),
        downbeats = root.doubles("downbeats"),
        phraseBoundaries = root.doubles("phraseBoundaries").ifEmpty { root.doubles("phrase_boundaries") },
        vocalActivityMask = root.doubles("vocalActivityMask").ifEmpty { root.doubles("vocal_presence_prob") },
        energyCurve = root.energyCurve("energyCurve"),
        lowEnergyCurve = root.energyCurve("lowEnergyCurve"),
        mixInCandidates = root.cuePoints("mixInCandidates"),
        mixOutCandidates = root.cuePoints("mixOutCandidates"),
        camelot = root.text("camelot"),
        durationMs = (dur * 1000.0).toLong(),
        cueInMs = (mixIn * 1000.0).toLong(),
        cueOutMs = (mixOut * 1000.0).toLong(),
    )
}
