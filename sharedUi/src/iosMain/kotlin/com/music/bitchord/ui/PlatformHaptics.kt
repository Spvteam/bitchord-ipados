package com.music.bitchord.ui

import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.Haptics
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * Top-level iPadOS/iOS Haptics coordinator (`PROJECT.md` Code Layout) bridging
 * `UIImpactFeedbackGenerator` and `UISelectionFeedbackGenerator`.
 */
object PlatformHaptics {
    private val delegate by lazy { Haptics() }

    fun impactLight() {
        runCatching {
            val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    fun impactMedium() {
        runCatching {
            val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    fun selectionChanged() {
        runCatching {
            val generator = UISelectionFeedbackGenerator()
            generator.prepare()
            generator.selectionChanged()
        }
    }

    fun performTick() = delegate.play(Haptic.Tick)
    fun performTap() = delegate.play(Haptic.Tap)
    fun performConfirm() = delegate.play(Haptic.Select)
}
