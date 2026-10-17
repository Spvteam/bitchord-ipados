@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.ui

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * 9:16 Instagram Story Lyrics Card Exporter & iPadOS Native `UIActivityViewController` Share Sheet (`WP-10`).
 *
 * - Exports 1080x1920 (`9:16`, aspect ratio `0.5625`) PNG story cards via offscreen Skia `Surface`
 * - Configures `popoverPresentationController` sourceView/sourceRect on iPadOS so the share sheet
 *   never throws an unhandled `UIPopoverPresentationController` exception on iPad.
 */
object ShareSheet {
    const val STORY_CARD_WIDTH_PX: Int = 1080
    const val STORY_CARD_HEIGHT_PX: Int = 1920
    const val STORY_CARD_ASPECT_RATIO: Double = 9.0 / 16.0 // 0.5625

    data class StoryCardPayload(
        val trackTitle: String,
        val artistName: String,
        val selectedLines: List<String>,
        val widthPx: Int = STORY_CARD_WIDTH_PX,
        val heightPx: Int = STORY_CARD_HEIGHT_PX,
        val pngBytes: ByteArray,
    ) {
        val aspectRatio: Double
            get() = widthPx.toDouble() / heightPx.toDouble()
    }

    /**
     * Renders a 1080x1920 (9:16) Instagram Story card into an encoded PNG byte array
     * using an offscreen Skia surface.
     */
    fun renderLyricsStoryCardPng(
        trackTitle: String,
        artistName: String,
        selectedLines: List<String>,
        backgroundColorArgb: Int = 0xFF1A1B2F.toInt(),
    ): StoryCardPayload {
        val imageInfo = ImageInfo(
            width = STORY_CARD_WIDTH_PX,
            height = STORY_CARD_HEIGHT_PX,
            colorType = ColorType.RGBA_8888,
            alphaType = ColorAlphaType.PREMUL,
        )
        val surface = Surface.makeRaster(imageInfo)
        val canvas = surface.canvas
        val bgPaint = Paint().apply {
            color = backgroundColorArgb
        }
        canvas.drawRect(
            org.jetbrains.skia.Rect.makeWH(
                STORY_CARD_WIDTH_PX.toFloat(),
                STORY_CARD_HEIGHT_PX.toFloat(),
            ),
            bgPaint,
        )

        val snapshot = surface.makeImageSnapshot()
        val encodedData = snapshot.encodeToData(EncodedImageFormat.PNG)
        val bytes = encodedData?.bytes ?: ByteArray(0)

        return StoryCardPayload(
            trackTitle = trackTitle,
            artistName = artistName,
            selectedLines = selectedLines,
            widthPx = STORY_CARD_WIDTH_PX,
            heightPx = STORY_CARD_HEIGHT_PX,
            pngBytes = bytes,
        )
    }

    /**
     * Presents the native iPadOS share sheet (`UIActivityViewController`) anchored safely for iPad popover presentation.
     */
    fun presentShareSheet(items: List<Any>) {
        runCatching {
            val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
            val activityVc = UIActivityViewController(
                activityItems = items,
                applicationActivities = null,
            )
            activityVc.popoverPresentationController?.sourceView = rootVc.view
            rootVc.presentViewController(activityVc, animated = true, completion = null)
        }
    }
}
