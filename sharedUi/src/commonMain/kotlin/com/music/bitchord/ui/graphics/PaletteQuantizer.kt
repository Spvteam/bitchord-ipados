package com.music.bitchord.ui.graphics

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object PaletteQuantizer {
    private const val RESIZE_BITMAP_AREA = 112 * 112
    private const val QUANTIZE_WORD_WIDTH = 5
    private const val QUANTIZE_WORD_MASK = (1 shl QUANTIZE_WORD_WIDTH) - 1

    private const val COMPONENT_RED = -3
    private const val COMPONENT_GREEN = -2
    private const val COMPONENT_BLUE = -1

    fun swatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
        val pixels = scaledPixels(image)
        return Quantizer(pixels, maxColors, useDefaultFilter = !clearFilters).quantizedColors
    }

    private fun scaledPixels(image: ImageBitmap): IntArray {
        val source = image.argbPixels()
        val width = image.width
        val height = image.height
        val area = width * height
        if (area <= RESIZE_BITMAP_AREA) return source
        val ratio = sqrt(RESIZE_BITMAP_AREA / area.toDouble())
        val scaledWidth = ceil(width * ratio).toInt().coerceAtLeast(1)
        val scaledHeight = ceil(height * ratio).toInt().coerceAtLeast(1)
        val out = IntArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            val sy = ((y + 0.5) * height / scaledHeight).toInt().coerceIn(0, height - 1)
            for (x in 0 until scaledWidth) {
                val sx = ((x + 0.5) * width / scaledWidth).toInt().coerceIn(0, width - 1)
                out[y * scaledWidth + x] = source[sy * width + sx]
            }
        }
        return out
    }

    private class Quantizer(pixels: IntArray, maxColors: Int, private val useDefaultFilter: Boolean) {
        private val histogram = IntArray(1 shl (QUANTIZE_WORD_WIDTH * 3))
        private val colors: IntArray
        private val tempHsl = FloatArray(3)
        val quantizedColors: List<Swatch>

        init {
            for (i in pixels.indices) {
                val quantized = quantizeFromRgb888(pixels[i])
                pixels[i] = quantized
                histogram[quantized]++
            }
            var distinct = 0
            for (color in histogram.indices) {
                if (histogram[color] > 0 && shouldIgnoreColor565(color)) histogram[color] = 0
                if (histogram[color] > 0) distinct++
            }
            colors = IntArray(distinct)
            var index = 0
            for (color in histogram.indices) {
                if (histogram[color] > 0) colors[index++] = color
            }
            quantizedColors = if (distinct <= maxColors) {
                colors.map { Swatch(approximateToRgb888(it), histogram[it]) }
            } else {
                quantizePixels(maxColors)
            }
        }

        private fun quantizePixels(maxColors: Int): List<Swatch> {
            val boxes = ArrayList<Vbox>(maxColors)
            boxes.add(Vbox(0, colors.size - 1))
            while (boxes.size < maxColors) {
                val largestIndex = boxes.indices.maxByOrNull { boxes[it].volume } ?: break
                val vbox = boxes[largestIndex]
                if (vbox.canSplit()) {
                    boxes.removeAt(largestIndex)
                    boxes.add(vbox.splitBox())
                    boxes.add(vbox)
                } else {
                    break
                }
            }
            return boxes.mapNotNull { vbox ->
                vbox.averageColor().takeUnless { shouldIgnoreColor(it.rgb) }
            }
        }

        private fun shouldIgnoreColor565(color565: Int): Boolean =
            useDefaultFilter && shouldIgnoreColor(approximateToRgb888(color565))

        private fun shouldIgnoreColor(rgb888: Int): Boolean {
            if (!useDefaultFilter) return false
            ColorUtils.colorToHSL(rgb888, tempHsl)
            val h = tempHsl[0]
            val s = tempHsl[1]
            val l = tempHsl[2]
            val isWhite = l >= 0.95f
            val isBlack = l <= 0.05f
            val isNearRedILine = h in 10f..37f && s <= 0.82f
            return isWhite || isBlack || isNearRedILine
        }

        private inner class Vbox(private val lowerIndex: Int, private var upperIndex: Int) {
            var minR = 0; var maxR = 0
            var minG = 0; var maxG = 0
            var minB = 0; var maxB = 0
            var volume = 0

            init { fitBox() }

            fun canSplit(): Boolean = upperIndex > lowerIndex

            fun fitBox() {
                var rMin = Int.MAX_VALUE; var gMin = Int.MAX_VALUE; var bMin = Int.MAX_VALUE
                var rMax = Int.MIN_VALUE; var gMax = Int.MIN_VALUE; var bMax = Int.MIN_VALUE
                for (i in lowerIndex..upperIndex) {
                    val color = colors[i]
                    val r = quantizedRed(color)
                    val g = quantizedGreen(color)
                    val b = quantizedBlue(color)
                    if (r > rMax) rMax = r; if (r < rMin) rMin = r
                    if (g > gMax) gMax = g; if (g < gMin) gMin = g
                    if (b > bMax) bMax = b; if (b < bMin) bMin = b
                }
                minR = rMin; maxR = rMax
                minG = gMin; maxG = gMax
                minB = bMin; maxB = bMax
                volume = (maxR - minR + 1) * (maxG - minG + 1) * (maxB - minB + 1)
            }

            fun splitBox(): Vbox {
                val splitDimension = longestColorDimension()
                modifySignificantOctet(splitDimension, lowerIndex, upperIndex)
                colors.sort(lowerIndex, upperIndex + 1)
                modifySignificantOctet(splitDimension, lowerIndex, upperIndex)
                val midPoint = findSplitPoint()
                val newBox = Vbox(midPoint + 1, upperIndex)
                upperIndex = midPoint
                fitBox()
                return newBox
            }

            fun longestColorDimension(): Int {
                val rLength = maxR - minR
                val gLength = maxG - minG
                val bLength = maxB - minB
                return if (rLength >= gLength && rLength >= bLength) COMPONENT_RED
                else if (gLength >= rLength && gLength >= bLength) COMPONENT_GREEN
                else COMPONENT_BLUE
            }

            fun findSplitPoint(): Int {
                var total = 0
                for (i in lowerIndex..upperIndex) total += histogram[colors[i]]
                var count = 0
                for (i in lowerIndex until upperIndex) {
                    count += histogram[colors[i]]
                    if (count >= total / 2) return i
                }
                return lowerIndex
            }

            fun averageColor(): Swatch {
                var rSum = 0L; var gSum = 0L; var bSum = 0L
                var total = 0
                for (i in lowerIndex..upperIndex) {
                    val color = colors[i]
                    val count = histogram[color]
                    total += count
                    rSum += count * quantizedRed(color)
                    gSum += count * quantizedGreen(color)
                    bSum += count * quantizedBlue(color)
                }
                return if (total == 0) Swatch(0, 0)
                else {
                    val r = (rSum.toFloat() / total).roundToInt()
                    val g = (gSum.toFloat() / total).roundToInt()
                    val b = (bSum.toFloat() / total).roundToInt()
                    Swatch(approximateToRgb888(r, g, b), total)
                }
            }

            private fun modifySignificantOctet(dimension: Int, low: Int, high: Int) {
                when (dimension) {
                    COMPONENT_RED -> Unit
                    COMPONENT_GREEN -> for (i in low..high) {
                        val c = colors[i]
                        colors[i] = (quantizedGreen(c) shl 10) or (quantizedRed(c) shl 5) or quantizedBlue(c)
                    }
                    COMPONENT_BLUE -> for (i in low..high) {
                        val c = colors[i]
                        colors[i] = (quantizedBlue(c) shl 10) or (quantizedGreen(c) shl 5) or quantizedRed(c)
                    }
                }
            }
        }

        private fun quantizeFromRgb888(color: Int): Int =
            (modifyWordWidth((color shr 16) and 0xFF, 8, QUANTIZE_WORD_WIDTH) shl (QUANTIZE_WORD_WIDTH * 2)) or
            (modifyWordWidth((color shr 8) and 0xFF, 8, QUANTIZE_WORD_WIDTH) shl QUANTIZE_WORD_WIDTH) or
            modifyWordWidth(color and 0xFF, 8, QUANTIZE_WORD_WIDTH)

        private fun approximateToRgb888(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or
            (modifyWordWidth(r, QUANTIZE_WORD_WIDTH, 8) shl 16) or
            (modifyWordWidth(g, QUANTIZE_WORD_WIDTH, 8) shl 8) or
            modifyWordWidth(b, QUANTIZE_WORD_WIDTH, 8)

        private fun approximateToRgb888(color: Int): Int =
            approximateToRgb888(quantizedRed(color), quantizedGreen(color), quantizedBlue(color))

        private fun quantizedRed(color: Int): Int = (color shr (QUANTIZE_WORD_WIDTH * 2)) and QUANTIZE_WORD_MASK
        private fun quantizedGreen(color: Int): Int = (color shr QUANTIZE_WORD_WIDTH) and QUANTIZE_WORD_MASK
        private fun quantizedBlue(color: Int): Int = color and QUANTIZE_WORD_MASK

        private fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int =
            if (targetWidth > currentWidth) (value shl (targetWidth - currentWidth))
            else (value shr (currentWidth - targetWidth))
    }
}
