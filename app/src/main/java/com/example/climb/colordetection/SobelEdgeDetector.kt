package com.example.climb.colordetection

import kotlin.math.sqrt

/**
 * Sobel 3x3 gradient-magnitude operator over a plain scalar field (e.g. a Lab L* window) — pure
 * math, no [PixelBuffer]/color dependency, so it's independently unit-testable with small
 * hand-built `DoubleArray` fixtures where the expected magnitude at a known step edge can be
 * hand-computed exactly (see [SobelEdgeDetectorTest]).
 *
 * Sobel over Scharr: [HoldBoundaryRefiner] only needs gradient *magnitude* to answer a binary
 * per-pixel question ("is this a real boundary or not") — it never needs gradient *direction*
 * (no non-max-suppression/hysteresis step is planned, unlike a full Canny pipeline), which is
 * exactly where Scharr's better rotational isotropy would actually matter. Sobel's kernel is the
 * simpler, more standard choice, and its integer weights make expected magnitudes trivial to
 * hand-compute for synthetic test fixtures — the same style already used for
 * [ConnectedComponentsTest]/[HoldComponentDetectorTest].
 */
object SobelEdgeDetector {

    // Standard 3x3 Sobel kernels.
    private val GX = arrayOf(
        intArrayOf(-1, 0, 1),
        intArrayOf(-2, 0, 2),
        intArrayOf(-1, 0, 1),
    )
    private val GY = arrayOf(
        intArrayOf(-1, -2, -1),
        intArrayOf(0, 0, 0),
        intArrayOf(1, 2, 1),
    )

    /**
     * @param field row-major scalar samples, size `width * height`.
     * @return per-pixel gradient magnitude `sqrt(gx^2 + gy^2)`, same size/layout as [field].
     * Out-of-range 3x3 neighborhood samples at the border are clamped (edge-replicated) to the
     * field's own border rather than treated as zero/wrapped — avoids manufacturing a fake
     * gradient spike purely from the field's edge.
     */
    fun gradientMagnitudeField(field: DoubleArray, width: Int, height: Int): DoubleArray {
        require(field.size == width * height) {
            "field.size (${field.size}) must equal width*height (${width * height})"
        }

        fun sample(x: Int, y: Int): Double {
            val cx = x.coerceIn(0, width - 1)
            val cy = y.coerceIn(0, height - 1)
            return field[cy * width + cx]
        }

        val magnitudes = DoubleArray(field.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var gx = 0.0
                var gy = 0.0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = sample(x + dx, y + dy)
                        gx += GX[dy + 1][dx + 1] * v
                        gy += GY[dy + 1][dx + 1] * v
                    }
                }
                magnitudes[y * width + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return magnitudes
    }
}
