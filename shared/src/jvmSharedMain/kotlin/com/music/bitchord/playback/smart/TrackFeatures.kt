/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Whole-track envelope, structure and key analysis, from the native DSP
 * analyzer (`native/analyzer/audio_analysis.cpp`).
 *
 * This answers "where does the music actually end, where can a transition
 * enter and leave, how loud is it there, and is anyone singing" — the
 * transition policy needs a beat grid (also produced here, from
 * autocorrelation) to know how to mix, and these features to know *where*,
 * and through the energy curve, whether an interior mix-out anchor would
 * skip silence or skip a minute of music.
 */
actual object TrackFeatures {

    /** True when the native library loaded. Analysis is optional, so this is a fact, not a fault. */
    actual val available: Boolean get() = NativeAnalysisLibrary.available

    /**
     * The rate the analyzer's window and hop constants assume, deliberately
     * low, because envelope and structure work needs time resolution rather
     * than bandwidth, and an eighth of the samples is an eighth of the work.
     */
    actual val sampleRate: Double by lazy { if (available) nativeSampleRate() else 11_025.0 }

    /**
     * Analyses [samples], which must be mono float PCM at [sampleRate].
     *
     * Returns null when the native library is missing or the analyzer
     * declined the input. Callers treat that as "no evidence", which the
     * policy already degrades on.
     */
    actual fun analyze(samples: FloatArray, durationSeconds: Double): Features? {
        if (!available || samples.isEmpty()) return null
        val json = runCatching { nativeAnalyze(samples, sampleRate, durationSeconds) }
            .onFailure { AnalysisLog.warn("Native analysis failed", it) }
            .getOrNull() ?: return null
        return runCatching { parse(Json.parseToJsonElement(json).jsonObject) }
            .onFailure { AnalysisLog.warn("Could not parse analysis output", it) }
            .getOrNull()
    }

    /**
     * Converts mono float PCM from [inputRate] to [sampleRate] (or any other
     * target), with an anti-aliasing windowed-sinc filter — see
     * `native/analyzer/resampler.cpp`.
     *
     * Returns the input unchanged when the rates already match, and null when
     * the native library is missing or the rates are unusable.
     */
    actual fun resample(samples: FloatArray, inputRate: Double, outputRate: Double): FloatArray? {
        if (!available || samples.isEmpty() || inputRate <= 0 || outputRate <= 0) return null
        return nativeResample(samples, inputRate, outputRate).takeIf { it.isNotEmpty() }
    }

    /** The subset of the analyzer's output the transition policy reads. */
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

    fun parse(root: JsonObject): Features = parseTrackFeaturesJson(root)

    @JvmStatic private external fun nativeAnalyze(
        samples: FloatArray,
        sampleRate: Double,
        duration: Double,
    ): String

    @JvmStatic private external fun nativeSampleRate(): Double

    @JvmStatic private external fun nativeResample(
        samples: FloatArray,
        inputRate: Double,
        outputRate: Double,
    ): FloatArray
}
