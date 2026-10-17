package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplication

internal actual fun uptimeMillis(): Long =
    (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

// Skia on Metal supports blur effects natively on iOS.
internal actual val renderEffectBlurSupported: Boolean = true

@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}

@Composable
internal actual fun appLanguageTag(): String {
    val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String)?.replace('_', '-')
    return preferred ?: NSLocale.currentLocale.languageCode ?: "en"
}

@Composable
internal actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { PlayerBack.Entry { current.value() } }
    DisposableEffect(enabled) {
        if (enabled) PlayerBack.push(entry)
        onDispose { PlayerBack.remove(entry) }
    }
}

object PlayerBack {
    class Entry(val onBack: () -> Unit)
    private val entries = mutableListOf<Entry>()

    internal fun push(entry: Entry) {
        entries.remove(entry)
        entries.add(entry)
    }

    internal fun remove(entry: Entry) {
        entries.remove(entry)
    }

    fun dispatch(): Boolean {
        val top = entries.lastOrNull() ?: return false
        top.onBack()
        return true
    }
}

// Touch scrolling on iPadOS is dragging, so drawer follows list scroll when pulled past top.
internal actual val drawerFollowsListScroll: Boolean get() = true

internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}
