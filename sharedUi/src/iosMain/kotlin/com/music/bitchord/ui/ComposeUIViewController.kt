@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.music.bitchord.ui

import androidx.compose.ui.window.ComposeUIViewController
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerLooper
import platform.AVFoundation.AVQueuePlayer
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/**
 * Responsive Stage Manager / Split View / Slide Over window size classes (`WP-10`):
 * - `COMPACT`: `< 600dp` (Slide Over / iPhone portrait)
 * - `MEDIUM`: `600dp .. 839dp` (1/2 Split View / iPad portrait)
 * - `EXPANDED`: `>= 840dp` (Full-screen iPad Pro / Stage Manager wide window -> 3-pane master-detail)
 */
enum class StageManagerLayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

object BitChordComposeHost {
    const val CONTROLLER_CLASS_NAME: String = "ComposeUIViewController"
    const val IGNORES_SAFE_AREA: Boolean = true
    const val IS_OPAQUE: Boolean = true
    const val PROMOTION_120HZ_PLIST_KEY: String = "CADisableMinimumFrameDurationOnPhone"
    const val REQUIRES_FULL_SCREEN_PLIST_KEY: String = "UIRequiresFullScreen"

    /**
     * Skia SkSL Liquid Glassmorphism shader (`WP-10`).
     */
    const val LIQUID_GLASS_SKSL_SHADER: String = """
        uniform float2 u_resolution;
        uniform float u_blur_radius;
        uniform float4 u_tint;

        half4 main(float2 coord) {
            float2 uv = coord / max(u_resolution, float2(1.0, 1.0));
            float edge = smoothstep(0.0, 1.0, uv.x * uv.y) * (u_blur_radius * 0.01);
            return half4(u_tint.rgb + edge * 0.05, u_tint.a);
        }
    """

    /**
     * 4-color animated mesh gradient palette and normalized anchor coordinates (`WP-10`).
     */
    val MESH_GRADIENT_COLORS: List<String> = listOf(
        "#1A1B2F",
        "#162447",
        "#1F4068",
        "#E43F5A",
    )

    val MESH_GRADIENT_POINTS: List<Pair<Float, Float>> = listOf(
        0.0f to 0.0f,
        1.0f to 0.0f,
        0.0f to 1.0f,
        1.0f to 1.0f,
    )

    /**
     * Classifies the current iPadOS window width in `dp` into a [StageManagerLayoutMode].
     */
    fun classifyWindowWidthDp(widthDp: Int): StageManagerLayoutMode = when {
        widthDp < 600 -> StageManagerLayoutMode.COMPACT
        widthDp < 840 -> StageManagerLayoutMode.MEDIUM
        else -> StageManagerLayoutMode.EXPANDED
    }

    /**
     * Compiles the Skia [RuntimeEffect] for the Liquid Glassmorphism shader.
     */
    fun createLiquidGlassRuntimeShader(
        widthPx: Float,
        heightPx: Float,
        blurRadius: Float = 28.0f,
        tintRgba: FloatArray = floatArrayOf(0.10f, 0.11f, 0.18f, 0.72f),
    ): RuntimeShaderBuilder {
        val effect = RuntimeEffect.makeForShader(LIQUID_GLASS_SKSL_SHADER)
        return RuntimeShaderBuilder(effect).apply {
            uniform("u_resolution", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
            uniform("u_blur_radius", blurRadius.coerceAtLeast(0f))
            uniform(
                "u_tint",
                tintRgba.getOrElse(0) { 0.1f },
                tintRgba.getOrElse(1) { 0.1f },
                tintRgba.getOrElse(2) { 0.18f },
                tintRgba.getOrElse(3) { 0.72f },
            )
        }
    }

    /**
     * Creates a hardware-accelerated `AVPlayerLayer` with `AVQueuePlayer` + `AVPlayerLooper`
     * for seamless muted Spotify Canvas background video loops on iPadOS.
     */
    fun createLoopingCanvasPlayerLayer(videoUrl: String): Pair<AVPlayerLayer, AVPlayerLooper>? {
        val nsUrl = NSURL.URLWithString(videoUrl) ?: return null
        val item = AVPlayerItem.playerItemWithURL(nsUrl)
        val queuePlayer = AVQueuePlayer()
        val looper = AVPlayerLooper.playerLooperWithPlayer(queuePlayer, templateItem = item)
        val layer = AVPlayerLayer.playerLayerWithPlayer(queuePlayer)
        return layer to looper
    }

    /**
     * Creates the root `UIViewController` hosting the Compose Multiplatform hierarchy
     * with Metal 120Hz ProMotion and full edge-to-edge safe-area handling.
     */
    fun createRootViewController(): UIViewController {
        IOSPlaybackController.ensureInitialized()
        return ComposeUIViewController(
            configure = {
                opaque = IS_OPAQUE
            }
        ) {
            BitChordIosApp()
        }
    }
}
