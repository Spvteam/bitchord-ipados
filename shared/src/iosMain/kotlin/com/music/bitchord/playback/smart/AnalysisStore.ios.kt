@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.playback.smart

import com.music.bitchord.data.IOSSandboxStorage
import com.music.bitchord.platform.PlatformLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.math.roundToLong

/**
 * iOS implementation of [AnalysisStore] persisting [TrackAnalysis] JSON records in the
 * sandbox `NSApplicationSupportDirectory/BitChord/smart_analysis` directory.
 */
class AnalysisStore(
    private val directoryOf: () -> String = {
        IOSSandboxStorage.resolveApplicationSupportDirectory()
    },
) {
    private val directory: String by lazy {
        val base = "${directoryOf().trimEnd('/')}/$DIRECTORY"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = base,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        base
    }

    private val lock = PlatformLock()
    private val known = mutableMapOf<String, Boolean>()
    private val memoryStore = mutableMapOf<String, String>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(trackId: String): TrackAnalysis? {
        if (trackId.isBlank()) return null
        lock.withLock {
            if (known[trackId] == false) return null
        }
        val filePath = "$directory/${fileNameFor(trackId)}"
        val rawText = readFileText(filePath) ?: lock.withLock { memoryStore[trackId] }
        if (rawText == null) {
            lock.withLock { known[trackId] = false }
            return null
        }
        return runCatching {
            val stored = json.decodeFromString(Stored.serializer(), rawText)
            require(stored.version == SCHEMA_VERSION) { "schema ${stored.version}" }
            stored.toAnalysis(trackId)
        }.onFailure {
            AnalysisLog.warn("Discarding unreadable analysis for $trackId on iOS", it)
            NSFileManager.defaultManager.removeItemAtPath(filePath, error = null)
            lock.withLock {
                memoryStore.remove(trackId)
                known[trackId] = false
            }
        }.getOrNull()
    }

    fun save(trackId: String, analysis: TrackAnalysis) {
        if (trackId.isBlank() || !analysis.isUsable) return
        runCatching {
            val encoded = json.encodeToString(Stored.serializer(), Stored.of(analysis))
            val filePath = "$directory/${fileNameFor(trackId)}"
            val tmpPath = "$filePath.tmp"
            try {
                writeFileText(tmpPath, encoded)
                val fm = NSFileManager.defaultManager
                if (fm.fileExistsAtPath(filePath)) {
                    fm.removeItemAtPath(filePath, error = null)
                }
                fm.moveItemAtPath(tmpPath, toPath = filePath, error = null)
            } finally {
                NSFileManager.defaultManager.removeItemAtPath(tmpPath, error = null)
            }
            lock.withLock {
                memoryStore[trackId] = encoded
                known[trackId] = true
            }
        }.onFailure {
            AnalysisLog.warn("Could not store analysis for $trackId on iOS", it)
        }
        prune()
    }

    private fun readFileText(path: String): String? {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun writeFileText(path: String, content: String) {
        val nsStr = NSString.create(string = content)
        nsStr.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun prune() {
        lock.withLock {
            if (memoryStore.size > MAX_ENTRIES) {
                val excess = memoryStore.keys.take(memoryStore.size - MAX_ENTRIES)
                for (k in excess) {
                    memoryStore.remove(k)
                }
            }
        }
    }

    private fun fileNameFor(trackId: String): String = "${trackId.hashCode().toUInt()}_${trackId.length}.json"

    @Serializable
    private data class Stored(
        val version: Int = SCHEMA_VERSION,
        val duration: Double = 0.0,
        val bpm: Double = 0.0,
        val beatInterval: Double = 0.0,
        val beatConfidence: Double = 0.0,
        val firstBeat: Double = 0.0,
        val downbeats: List<Double> = emptyList(),
        val phraseBoundaries: List<Double> = emptyList(),
        val key: String = "",
        val keyConfidence: Double = 0.0,
        val audibleStartTime: Double? = null,
        val pickupTime: Double? = null,
        val introEndTime: Double = 0.0,
        val outroStartTime: Double = 0.0,
        val contentEndTime: Double = 0.0,
        val mixInTime: Double = 0.0,
        val mixOutTime: Double = 0.0,
        val mixInCandidates: List<StoredCue> = emptyList(),
        val mixOutCandidates: List<StoredCue> = emptyList(),
        val energyCurve: List<StoredEnergy> = emptyList(),
        val lowEnergyCurve: List<StoredEnergy> = emptyList(),
        val vocalActivityMask: List<Double> = emptyList(),
        val vocalProbability: Double = 0.0,
    ) {
        fun toAnalysis(trackId: String) = TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = duration,
            bpm = bpm,
            beatInterval = beatInterval,
            beatConfidence = beatConfidence,
            firstBeat = firstBeat,
            downbeats = downbeats,
            phraseBoundaries = phraseBoundaries,
            key = key,
            keyConfidence = keyConfidence,
            audibleStartTime = audibleStartTime,
            pickupTime = pickupTime,
            introEndTime = introEndTime,
            outroStartTime = outroStartTime,
            contentEndTime = contentEndTime,
            mixInTime = mixInTime,
            mixOutTime = mixOutTime,
            mixInCandidates = mixInCandidates.map { it.toCue() },
            mixOutCandidates = mixOutCandidates.map { it.toCue() },
            energyCurve = energyCurve.map { it.toSample() },
            lowEnergyCurve = lowEnergyCurve.map { it.toSample() },
            vocalActivityMask = vocalActivityMask,
            vocalProbability = vocalProbability,
        )

        companion object {
            fun of(analysis: TrackAnalysis) = Stored(
                duration = analysis.duration,
                bpm = analysis.bpm,
                beatInterval = analysis.beatInterval,
                beatConfidence = analysis.beatConfidence,
                firstBeat = analysis.firstBeat,
                downbeats = analysis.downbeats.map(::round),
                phraseBoundaries = analysis.phraseBoundaries.map(::round),
                key = analysis.key,
                keyConfidence = analysis.keyConfidence,
                audibleStartTime = analysis.audibleStartTime,
                pickupTime = analysis.pickupTime,
                introEndTime = analysis.introEndTime,
                outroStartTime = analysis.outroStartTime,
                contentEndTime = analysis.contentEndTime,
                mixInTime = analysis.mixInTime,
                mixOutTime = analysis.mixOutTime,
                mixInCandidates = analysis.mixInCandidates.map(StoredCue::of),
                mixOutCandidates = analysis.mixOutCandidates.map(StoredCue::of),
                energyCurve = analysis.energyCurve.map(StoredEnergy::of),
                lowEnergyCurve = analysis.lowEnergyCurve.map(StoredEnergy::of),
                vocalActivityMask = analysis.vocalActivityMask.map(::round),
                vocalProbability = analysis.vocalProbability,
            )
        }
    }

    @Serializable
    private data class StoredCue(val time: Double, val score: Double, val type: String) {
        fun toCue() = MixCandidate(time = time, score = score, type = type)

        companion object {
            fun of(cue: MixCandidate) = StoredCue(round(cue.time), round(cue.score), cue.type)
        }
    }

    @Serializable
    private data class StoredEnergy(val time: Double, val energy: Double) {
        fun toSample() = EnergySample(time = time, energy = energy)

        companion object {
            fun of(sample: EnergySample) = StoredEnergy(round(sample.time), round(sample.energy))
        }
    }

    private companion object {
        const val DIRECTORY = "smart_analysis"
        const val SCHEMA_VERSION = 1
        const val MAX_ENTRIES = 2_000

        fun round(value: Double): Double =
            if (value.isFinite()) (value * 1000.0).roundToLong() / 1000.0 else 0.0
    }
}
