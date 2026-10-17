package com.music.bitchord.playback

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Identifies the active or incoming deck in the dual-deck audio engine (`PROJECT.md §4`).
 */
enum class DeckId {
    A,
    B,
}

/**
 * Transition plan parameters for Smart Automix and equal-power crossfade (`PROJECT.md §4`).
 */
data class SmartTransitionPlan(
    val outgoingDeck: DeckId = DeckId.A,
    val incomingDeck: DeckId = DeckId.B,
    val durationMs: Long = 6_000L,
    val outgoingMixOutSec: Double = 0.0,
    val incomingMixInSec: Double = 0.0,
    val targetTempoRateA: Float = 1.0f,
    val targetTempoRateB: Float = 1.0f,
    val pitchShiftCentsA: Float = 0.0f,
    val pitchShiftCentsB: Float = 0.0f,
    val filterSweepEnabled: Boolean = false,
)

/**
 * Multiplatform Dual-Deck Audio Engine contract (`PROJECT.md §4`).
 */
interface AudioEngineController {
    val visualizerFftFlow: StateFlow<FloatArray>

    fun loadTrack(deck: DeckId, streamUrl: String, initialPositionMs: Long = 0L)
    fun play(deck: DeckId)
    fun pause(deck: DeckId)
    fun seek(deck: DeckId, positionMs: Long)
    fun startCrossfade(durationMs: Long, transitionPlan: SmartTransitionPlan = SmartTransitionPlan(durationMs = durationMs))
    fun setEqualizer(gains: FloatArray)
    fun setTempo(deck: DeckId, rate: Float)

    companion object {
        const val FFT_BIN_COUNT: Int = 1024
        const val EQ_BAND_COUNT: Int = 10
        const val MIN_CROSSFADE_SECONDS: Double = 0.0
        const val MAX_CROSSFADE_SECONDS: Double = 12.0
        const val MIN_TEMPO_RATE: Float = 0.5f
        const val MAX_TEMPO_RATE: Float = 2.0f
        const val MIN_PITCH_CENTS: Float = -2400.0f
        const val MAX_PITCH_CENTS: Float = 2400.0f

        val EQ_CENTER_FREQUENCIES_HZ: FloatArray = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        val EQ_PRESETS: Map<String, FloatArray> = mapOf(
            "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Vocal Boost" to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f),
            "Electronic" to floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f),
            "Acoustic" to floatArrayOf(3f, 3f, 2f, 1f, 2f, 2f, 3f, 4f, 3f, 2f),
        )

        /**
         * Computes equal-power crossfade gains $(G_A, G_B)$ at normalized progression $t \in [0, 1]$:
         * - $G_A = \cos(\frac{\pi}{2} t)$
         * - $G_B = \sin(\frac{\pi}{2} t)$
         * Preserves constant power invariant $G_A^2 + G_B^2 = 1.0$.
         */
        fun computeEqualPowerCrossfade(progress: Double): Pair<Double, Double> {
            val clamped = progress.coerceIn(0.0, 1.0)
            val angle = clamped * (PI / 2.0)
            val gainA = cos(angle)
            val gainB = sin(angle)
            return gainA to gainB
        }
    }
}
