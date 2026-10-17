package com.music.bitchord.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

actual class Haptics {
    private val lightImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }
    private val mediumImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium) }
    private val heavyImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy) }
    private val softImpact by lazy { UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft) }
    private val selectionFeedback by lazy { UISelectionFeedbackGenerator() }

    actual fun play(haptic: Haptic) {
        when (haptic) {
            Haptic.Tick -> selectionFeedback.selectionChanged()
            Haptic.Tap -> lightImpact.impactOccurred()
            Haptic.Select -> selectionFeedback.selectionChanged()
            Haptic.ToggleOn -> mediumImpact.impactOccurred()
            Haptic.ToggleOff -> lightImpact.impactOccurred()
            Haptic.SkipNext -> mediumImpact.impactOccurred()
            Haptic.SkipPrevious -> lightImpact.impactOccurred()
            Haptic.Resume -> mediumImpact.impactOccurred()
            Haptic.Pause -> softImpact.impactOccurred()
            Haptic.Expand -> heavyImpact.impactOccurred()
        }
    }
}

private val IosHapticsInstance = Haptics()

@Composable
actual fun rememberHaptics(): Haptics = remember { IosHapticsInstance }
