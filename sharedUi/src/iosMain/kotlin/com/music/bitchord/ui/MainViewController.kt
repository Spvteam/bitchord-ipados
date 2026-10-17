package com.music.bitchord.ui

import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return BitChordComposeHost.createRootViewController()
}
