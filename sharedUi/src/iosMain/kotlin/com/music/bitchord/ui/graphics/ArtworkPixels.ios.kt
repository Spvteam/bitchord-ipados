package com.music.bitchord.ui.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

// Skia bitmaps on iOS are always CPU-readable.
actual fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = this

actual fun Image.toImageBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()

actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
    // Little-endian BGRA_8888 packing matches ARGB int byte order
    val bytes = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val p = pixels[i]
        val offset = i * 4
        bytes[offset] = (p and 0xFF).toByte()         // Blue
        bytes[offset + 1] = ((p shr 8) and 0xFF).toByte()  // Green
        bytes[offset + 2] = ((p shr 16) and 0xFF).toByte() // Red
        bytes[offset + 3] = ((p shr 24) and 0xFF).toByte() // Alpha
    }
    bitmap.installPixels(bytes)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

actual fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> =
    PaletteQuantizer.swatches(image, maxColors, clearFilters)
