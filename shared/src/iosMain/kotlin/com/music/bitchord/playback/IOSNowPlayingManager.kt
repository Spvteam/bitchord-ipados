@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.playback

import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.withLock
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowAirPlay
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * Manages iPadOS / iOS background audio continuity (`AVAudioSessionCategoryPlayback`),
 * Control Center / Lock Screen metadata (`MPNowPlayingInfoCenter`), and hardware / Bluetooth
 * media controls (`MPRemoteCommandCenter`).
 */
class IOSNowPlayingManager(
    private val onPlay: () -> Unit = {},
    private val onPause: () -> Unit = {},
    private val onTogglePlayPause: () -> Unit = {},
    private val onNextTrack: () -> Unit = {},
    private val onPreviousTrack: () -> Unit = {},
    private val onSeekToSeconds: (Double) -> Unit = {},
) {

    data class AudioSessionConfig(
        val category: String = "AVAudioSessionCategoryPlayback",
        val mode: String = "AVAudioSessionModeDefault",
        val options: List<String> = listOf(
            "AVAudioSessionCategoryOptionAllowAirPlay",
            "AVAudioSessionCategoryOptionAllowBluetoothA2DP",
        ),
    )

    data class ArtworkSpec(
        val width: Int = DEFAULT_ARTWORK_SIZE,
        val height: Int = DEFAULT_ARTWORK_SIZE,
        val hasImage: Boolean = true,
    )

    private val lock = PlatformLock()
    private val registeredCommands = mutableSetOf<String>()
    private var currentNowPlayingInfo = mutableMapOf<String, Any>()
    private var currentArtworkSpec = ArtworkSpec()

    /**
     * Configures the shared `AVAudioSession` for uninterrupted background audio,
     * AirPlay 2 streaming, and Bluetooth A2DP output.
     */
    fun configureAudioSession(): AudioSessionConfig = lock.withLock {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            val options = AVAudioSessionCategoryOptionAllowAirPlay or AVAudioSessionCategoryOptionAllowBluetoothA2DP
            session.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = options,
                error = null,
            )
            session.setActive(true, error = null)
        }
        AudioSessionConfig()
    }

    /**
     * Updates `MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo` with track metadata,
     * playback duration, elapsed position, playback rate, and 600x600 artwork metadata.
     */
    fun updateNowPlayingMetadata(
        title: String,
        artist: String,
        albumTitle: String = "",
        durationSeconds: Double,
        elapsedSeconds: Double,
        playbackRate: Double = 1.0,
        artworkWidth: Int = DEFAULT_ARTWORK_SIZE,
        artworkHeight: Int = DEFAULT_ARTWORK_SIZE,
        hasArtwork: Boolean = true,
    ): Map<String, Any> = lock.withLock {
        val safeDuration = durationSeconds.coerceAtLeast(0.0)
        val safeElapsed = elapsedSeconds.coerceIn(0.0, if (safeDuration > 0.0) safeDuration else Double.MAX_VALUE)

        val infoMap = mutableMapOf<String, Any>(
            "MPMediaItemPropertyTitle" to title,
            "MPMediaItemPropertyArtist" to artist,
            "MPMediaItemPropertyAlbumTitle" to albumTitle,
            "MPMediaItemPropertyPlaybackDuration" to safeDuration,
            "MPNowPlayingInfoPropertyElapsedPlaybackTime" to safeElapsed,
            "MPNowPlayingInfoPropertyPlaybackRate" to playbackRate,
        )
        currentNowPlayingInfo = infoMap
        currentArtworkSpec = ArtworkSpec(
            width = artworkWidth,
            height = artworkHeight,
            hasImage = hasArtwork,
        )

        runCatching {
            val platformDict = mutableMapOf<Any?, Any?>(
                MPMediaItemPropertyTitle to title,
                MPMediaItemPropertyArtist to artist,
                MPMediaItemPropertyAlbumTitle to albumTitle,
                MPMediaItemPropertyPlaybackDuration to safeDuration,
                MPNowPlayingInfoPropertyElapsedPlaybackTime to safeElapsed,
                MPNowPlayingInfoPropertyPlaybackRate to playbackRate,
            )
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = platformDict
        }
        infoMap.toMap()
    }

    /**
     * Registers Lock Screen, Control Center, AirPods, and keyboard media key handlers on
     * `MPRemoteCommandCenter.sharedCommandCenter()`.
     */
    fun registerRemoteCommands(): Set<String> = lock.withLock {
        registeredCommands.clear()
        registeredCommands.addAll(
            listOf(
                "playCommand",
                "pauseCommand",
                "togglePlayPauseCommand",
                "nextTrackCommand",
                "previousTrackCommand",
                "changePlaybackPositionCommand",
            )
        )

        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()

            center.playCommand.enabled = true
            center.playCommand.addTargetWithHandler {
                onPlay()
                MPRemoteCommandHandlerStatusSuccess
            }

            center.pauseCommand.enabled = true
            center.pauseCommand.addTargetWithHandler {
                onPause()
                MPRemoteCommandHandlerStatusSuccess
            }

            center.togglePlayPauseCommand.enabled = true
            center.togglePlayPauseCommand.addTargetWithHandler {
                onTogglePlayPause()
                MPRemoteCommandHandlerStatusSuccess
            }

            center.nextTrackCommand.enabled = true
            center.nextTrackCommand.addTargetWithHandler {
                onNextTrack()
                MPRemoteCommandHandlerStatusSuccess
            }

            center.previousTrackCommand.enabled = true
            center.previousTrackCommand.addTargetWithHandler {
                onPreviousTrack()
                MPRemoteCommandHandlerStatusSuccess
            }

            center.changePlaybackPositionCommand.enabled = true
            center.changePlaybackPositionCommand.addTargetWithHandler { event ->
                val posEvent = event as? MPChangePlaybackPositionCommandEvent
                val targetSec = posEvent?.positionTime ?: 0.0
                onSeekToSeconds(targetSec)
                MPRemoteCommandHandlerStatusSuccess
            }
        }

        registeredCommands.toSet()
    }

    fun getCurrentNowPlayingInfo(): Map<String, Any> = lock.withLock { currentNowPlayingInfo.toMap() }

    fun getCurrentArtworkSpec(): ArtworkSpec = lock.withLock { currentArtworkSpec }

    fun getRegisteredCommands(): Set<String> = lock.withLock { registeredCommands.toSet() }

    companion object {
        const val DEFAULT_ARTWORK_SIZE: Int = 600
    }
}
