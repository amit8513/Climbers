package com.example.climb.colordetection

/**
 * Plain packed-ARGB pixel buffer — same channel layout as `android.graphics.Bitmap.getPixels()`
 * (`0xAARRGGBB` ints per pixel, row-major), but framework-free so tests can hand-build small
 * synthetic images without Robolectric (this project has none — see [ColorSpace]'s doc comment
 * for why that matters). The primary input type for the object-detection pipeline in this module.
 */
data class PixelBuffer(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive, got ($width, $height)" }
        require(pixels.size == width * height) {
            "pixels.size (${pixels.size}) must equal width*height (${width * height})"
        }
    }

    fun indexOf(x: Int, y: Int): Int = y * width + x

    fun rgbAt(x: Int, y: Int): RgbColor {
        val packed = pixels[indexOf(x, y)]
        return RgbColor(
            r = (packed shr 16) and 0xFF,
            g = (packed shr 8) and 0xFF,
            b = packed and 0xFF,
        )
    }

    /** Paints an axis-aligned rectangle `[x0, x1) x [y0, y1)` with [color] — a synthetic-test
     * fixture builder, mutating this buffer's pixels in place. */
    fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, color: RgbColor) {
        val packed = packArgb(color)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                pixels[indexOf(x, y)] = packed
            }
        }
    }

    /** Paints a single pixel with [color] — a synthetic-test fixture builder for noise specks /
     * isolated off-color pixels, mutating this buffer's pixels in place. */
    fun setPixel(x: Int, y: Int, color: RgbColor) {
        pixels[indexOf(x, y)] = packArgb(color)
    }

    private fun packArgb(color: RgbColor): Int =
        (0xFF shl 24) or (color.r shl 16) or (color.g shl 8) or color.b

    companion object {
        /** Builds a buffer filled entirely with [background] — a synthetic-test fixture starting
         * point, meant to be painted over with [fillRect]/[setPixel]. */
        fun filled(width: Int, height: Int, background: RgbColor): PixelBuffer {
            val buffer = PixelBuffer(width, height, IntArray(width * height))
            buffer.fillRect(0, 0, width, height, background)
            return buffer
        }

        /** Trivial pass-through from a real decoded video frame. Not unit-tested itself (no logic
         * beyond calling `getPixels()`) — `Bitmap` can't be meaningfully constructed in a plain JVM
         * unit test without Robolectric, which this project doesn't use; real-bitmap coverage for
         * this path happens via manual/on-device testing once this pipeline is wired into
         * `DetailScreen`'s review flow. */
        fun fromBitmap(bitmap: android.graphics.Bitmap): PixelBuffer {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return PixelBuffer(width, height, pixels)
        }
    }
}
