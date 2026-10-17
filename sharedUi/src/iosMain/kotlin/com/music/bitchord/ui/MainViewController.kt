package com.music.bitchord.ui

import com.music.bitchord.playback.IOSNowPlayingManager
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val nowPlaying = IOSNowPlayingManager()
    nowPlaying.configureAudioSession()
    nowPlaying.registerRemoteCommands()
    return BitChordComposeHost.createRootViewController()
}
