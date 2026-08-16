package com.example.climb.colordetection

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Phase 6's mask builder: turns a list of validated [DetectedHold]s into a full-frame `[0f, 1f]`
 * "hold strength" field, for driving [com.example.climb.playback.DetectedHoldHighlightEffect]'s
 * per-pixel highlight/desaturate blend. Stamps each hold's own local [DetectedHold.mask] into the
 * full-frame grid at its [DetectedHold.boundingBox] offset, then softens the result with a small
 * box blur so the eventual rendered edge is anti-aliased rather than a hard binary step — per the
 * project's brief requiring anti-aliased mask edges, not a flat cutout.
 *
 * The stamping+blur math stays pure Kotlin (`FloatArray` in, `FloatArray` out) so it's unit
 * testable without Robolectric (this project has none); [toAlphaBitmap] is a thin, deliberately
 * untested conversion to a real GPU-uploadable `Bitmap`, mirroring [PixelBuffer.fromBitmap]'s own
 * precedent that trivial framework pass-throughs don't need unit tests — the logic feeding them
 * does.
 */
object HoldMaskRenderer {

    /** Softening radius (pixels) for the box-blur anti-aliasing pass applied near mask edges. */
    const val EDGE_SOFTEN_RADIUS_PX = 2

    /**
     * @return a `width * height`, row-major `FloatArray` in `[0f, 1f]`: `1f` deep inside a
     * validated hold, `0f` far from any hold, and a soft intermediate value within
     * [EDGE_SOFTEN_RADIUS_PX] pixels of a hold's boundary.
     */
    fun renderMaskField(width: Int, height: Int, holds: List<DetectedHold>): FloatArray {
        require(width > 0 && height > 0) { "width/height must be positive, got ($width, $height)" }
        val hard = FloatArray(width * height)
        for (hold in holds) {
            val bbox = hold.boundingBox
            for (localY in 0 until bbox.height) {
                val globalY = bbox.y0 + localY
                if (globalY !in 0 until height) continue
                for (localX in 0 until bbox.width) {
                    if (!hold.mask[localY * bbox.width + localX]) continue
                    val globalX = bbox.x0 + localX
                    if (globalX !in 0 until width) continue
                    hard[globalY * width + globalX] = 1f
                }
            }
        }
        return boxBlur(hard, width, height, EDGE_SOFTEN_RADIUS_PX)
    }

    /**
     * Direct (non-separable) box blur: for each pixel, averages every in-bounds sample in its own
     * `(2*radius+1)^2` neighborhood. Deliberately the simple, obviously-correct O(w*h*radius^2)
     * form rather than a faster separable/sliding-window version — this runs once per detected
     * reference frame (an already much more expensive full detection pass), not per video frame,
     * so the extra constant factor here is immaterial next to that cost.
     */
    private fun boxBlur(field: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius <= 0) return field
        val out = FloatArray(field.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val sy = y + dy
                    if (sy !in 0 until height) continue
                    for (dx in -radius..radius) {
                        val sx = x + dx
                        if (sx !in 0 until width) continue
                        sum += field[sy * width + sx]
                        count++
                    }
                }
                out[y * width + x] = (sum / count).coerceIn(0f, 1f)
            }
        }
        return out
    }

    /**
     * Converts a mask field into an `ALPHA_8` [Bitmap] (one byte per pixel, sampled via a texture's
     * own `.a` channel in the GL fragment shader) for GPU upload. Trivial framework pass-through —
     * not unit tested, see class doc.
     */
    fun toAlphaBitmap(field: FloatArray, width: Int, height: Int): Bitmap {
        require(field.size == width * height) { "field.size (${field.size}) must equal width*height (${width * height})" }
        val bytes = ByteArray(field.size)
        for (i in field.indices) {
            bytes[i] = (field[i].coerceIn(0f, 1f) * 255f).toInt().toByte()
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }
}
