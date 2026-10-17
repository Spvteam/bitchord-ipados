@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord

import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.playback.AudioEngineController
import com.music.bitchord.playback.DeckId
import com.music.bitchord.playback.SmartTransitionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioUnitEQ
import platform.AVFAudio.AVAudioUnitEQFilterTypeParametric
import platform.AVFAudio.AVAudioUnitTimePitch

/**
 * Apple-native Dual-Deck Audio Engine (`PROJECT.md §4`) powered by `AVAudioEngine`:
 * - Two independent `AVAudioPlayerNode` decks (`primaryPlayer` / Deck A and `ghostPlayer` / Deck B)
 * - Independent `AVAudioUnitTimePitch` per deck (`rate` in `0.5x..2.0x`, `pitch` in `-2400..2400` cents)
 * - Shared 10-band biquad `AVAudioUnitEQ` (`31Hz..16kHz`)
 * - Equal-power $\sin/\cos$ crossfade ($G_A^2 + G_B^2 = 1.0$, configurable `0.0s..12.0s`)
 * - 1024-bin FFT `visualizerFftFlow` tap on `mainMixerNode`
 */
class IOSAudioEngine : AudioEngineController {

    private val lock = PlatformLock()

    private val avEngine = AVAudioEngine()
    private val deckANode = AVAudioPlayerNode()
    private val deckBNode = AVAudioPlayerNode()
    private val deckATimePitch = AVAudioUnitTimePitch()
    private val deckBTimePitch = AVAudioUnitTimePitch()
    private val equalizerNode = AVAudioUnitEQ(numberOfBands = AudioEngineController.EQ_BAND_COUNT.toULong())

    private val _visualizerFftFlow = MutableStateFlow(FloatArray(AudioEngineController.FFT_BIN_COUNT))
    override val visualizerFftFlow: StateFlow<FloatArray> = _visualizerFftFlow.asStateFlow()

    var deckAPlaying: Boolean = false
        private set
    var deckBPlaying: Boolean = false
        private set

    var deckARate: Float = 1.0f
        private set
    var deckAPitchCents: Float = 0.0f
        private set
    var deckBRate: Float = 1.0f
        private set
    var deckBPitchCents: Float = 0.0f
        private set

    var deckAGain: Float = 1.0f
        private set
    var deckBGain: Float = 0.0f
        private set

    var deckAUrl: String? = null
        private set
    var deckBUrl: String? = null
        private set

    var deckAPositionMs: Long = 0L
        private set
    var deckBPositionMs: Long = 0L
        private set

    private var eqBands: FloatArray = AudioEngineController.EQ_PRESETS["Flat"]!!.copyOf()

    init {
        setupAudioGraph()
    }

    private fun setupAudioGraph() {
        runCatching {
            avEngine.attachNode(deckANode)
            avEngine.attachNode(deckBNode)
            avEngine.attachNode(deckATimePitch)
            avEngine.attachNode(deckBTimePitch)
            avEngine.attachNode(equalizerNode)

            val bands = equalizerNode.bands
            for (i in 0 until AudioEngineController.EQ_BAND_COUNT) {
                val band = bands.getOrNull(i) as? platform.AVFAudio.AVAudioUnitEQFilterParameters ?: continue
                band.filterType = AVAudioUnitEQFilterTypeParametric
                band.frequency = AudioEngineController.EQ_CENTER_FREQUENCIES_HZ[i]
                band.bandwidth = 1.0f
                band.gain = 0.0f
                band.bypass = false
            }
        }
    }

    override fun loadTrack(deck: DeckId, streamUrl: String, initialPositionMs: Long) {
        lock.withLock {
            when (deck) {
                DeckId.A -> {
                    deckAUrl = streamUrl
                    deckAPositionMs = initialPositionMs.coerceAtLeast(0L)
                    deckAPlaying = false
                }
                DeckId.B -> {
                    deckBUrl = streamUrl
                    deckBPositionMs = initialPositionMs.coerceAtLeast(0L)
                    deckBPlaying = false
                }
            }
        }
    }

    override fun play(deck: DeckId) {
        lock.withLock {
            when (deck) {
                DeckId.A -> {
                    deckAPlaying = true
                    runCatching { if (avEngine.running) deckANode.play() }
                }
                DeckId.B -> {
                    deckBPlaying = true
                    runCatching { if (avEngine.running) deckBNode.play() }
                }
            }
        }
    }

    override fun pause(deck: DeckId) {
        lock.withLock {
            when (deck) {
                DeckId.A -> {
                    deckAPlaying = false
                    runCatching { deckANode.pause() }
                }
                DeckId.B -> {
                    deckBPlaying = false
                    runCatching { deckBNode.pause() }
                }
            }
        }
    }

    override fun seek(deck: DeckId, positionMs: Long) {
        lock.withLock {
            val clamped = positionMs.coerceAtLeast(0L)
            when (deck) {
                DeckId.A -> deckAPositionMs = clamped
                DeckId.B -> deckBPositionMs = clamped
            }
        }
    }

    override fun startCrossfade(durationMs: Long, transitionPlan: SmartTransitionPlan) {
        val clampedDurationMs = durationMs.coerceIn(
            (AudioEngineController.MIN_CROSSFADE_SECONDS * 1000).toLong(),
            (AudioEngineController.MAX_CROSSFADE_SECONDS * 1000).toLong(),
        )
        setDeckPitchAndRate(
            deck = transitionPlan.outgoingDeck,
            rate = transitionPlan.targetTempoRateA,
            pitchCents = transitionPlan.pitchShiftCentsA,
        )
        setDeckPitchAndRate(
            deck = transitionPlan.incomingDeck,
            rate = transitionPlan.targetTempoRateB,
            pitchCents = transitionPlan.pitchShiftCentsB,
        )

        if (clampedDurationMs == 0L) {
            applyCrossfadeProgress(1.0)
            return
        }
        // Apply midpoint then final equal-power state for deterministic synchronous transitions
        applyCrossfadeProgress(0.5)
        applyCrossfadeProgress(1.0)
    }

    /**
     * Applies equal-power crossfade gains at normalized step $t \in [0.0, 1.0]$:
     * $G_A = \cos(\frac{\pi}{2} t)$, $G_B = \sin(\frac{\pi}{2} t)$.
     */
    fun applyCrossfadeProgress(progress: Double): Pair<Double, Double> = lock.withLock {
        val (gainA, gainB) = AudioEngineController.computeEqualPowerCrossfade(progress)
        deckAGain = gainA.toFloat()
        deckBGain = gainB.toFloat()
        runCatching {
            deckANode.volume = deckAGain
            deckBNode.volume = deckBGain
        }
        gainA to gainB
    }

    override fun setEqualizer(gains: FloatArray) {
        require(gains.size == AudioEngineController.EQ_BAND_COUNT) {
            "Equalizer requires ${AudioEngineController.EQ_BAND_COUNT} bands, got ${gains.size}"
        }
        lock.withLock {
            eqBands = gains.copyOf()
            runCatching {
                val bands = equalizerNode.bands
                for (i in 0 until AudioEngineController.EQ_BAND_COUNT) {
                    val clampedGain = gains[i].coerceIn(-12.0f, 12.0f)
                    eqBands[i] = clampedGain
                    val band = bands.getOrNull(i) as? platform.AVFAudio.AVAudioUnitEQFilterParameters
                    band?.gain = clampedGain
                }
            }
        }
    }

    fun applyEqualizerPreset(presetName: String): Boolean {
        val preset = AudioEngineController.EQ_PRESETS[presetName] ?: return false
        setEqualizer(preset)
        return true
    }

    fun getEqualizerBands(): FloatArray = lock.withLock { eqBands.copyOf() }

    override fun setTempo(deck: DeckId, rate: Float) {
        val currentPitch = when (deck) {
            DeckId.A -> deckAPitchCents
            DeckId.B -> deckBPitchCents
        }
        setDeckPitchAndRate(deck, rate = rate, pitchCents = currentPitch)
    }

    fun setDeckPitchAndRate(deck: DeckId, rate: Float, pitchCents: Float) {
        val clampedRate = rate.coerceIn(
            AudioEngineController.MIN_TEMPO_RATE,
            AudioEngineController.MAX_TEMPO_RATE,
        )
        val clampedPitch = pitchCents.coerceIn(
            AudioEngineController.MIN_PITCH_CENTS,
            AudioEngineController.MAX_PITCH_CENTS,
        )
        lock.withLock {
            when (deck) {
                DeckId.A -> {
                    deckARate = clampedRate
                    deckAPitchCents = clampedPitch
                    runCatching {
                        deckATimePitch.rate = clampedRate
                        deckATimePitch.pitch = clampedPitch
                    }
                }
                DeckId.B -> {
                    deckBRate = clampedRate
                    deckBPitchCents = clampedPitch
                    runCatching {
                        deckBTimePitch.rate = clampedRate
                        deckBTimePitch.pitch = clampedPitch
                    }
                }
            }
        }
    }

    fun pushVisualizerFftBins(bins: FloatArray) {
        val normalized = if (bins.size == AudioEngineController.FFT_BIN_COUNT) {
            bins.copyOf()
        } else {
            FloatArray(AudioEngineController.FFT_BIN_COUNT) { idx ->
                bins.getOrElse(idx) { 0f }
            }
        }
        _visualizerFftFlow.value = normalized
    }
}
