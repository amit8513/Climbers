package com.example.climb.colordetection

/**
 * Samples RGB pixel colors from a small square region of a [PixelBuffer], centered on a point and
 * clamped to the buffer's own bounds — the raw material [ColorCalibrator] turns into a calibrated
 * [TargetColorModel] for the tap-to-calibrate flow ([com.example.climb.ui.detail.DetailScreen]).
 *
 * A square, not a circle: trivially clampable per axis with no per-pixel distance check needed,
 * and at the small radii this is used at, the corner-vs-circle difference is immaterial to
 * [ColorCalibrator]'s own median/MAD outlier rejection, which already discards a tap ROI's chalk/
 * highlight/background stragglers regardless of the sampled region's exact shape.
 */
object RoiSampler {
    /** Default ROI half-width (pixels) around a tap point — side length is `2*radius + 1`. Small
     * enough to plausibly stay within a real hold even at a modest on-screen size, large enough
     * (441 samples at the default) to give [ColorCalibrator]'s outlier rejection real signal to
     * work with (it needs at least 4 samples to run MAD at all). */
    const val DEFAULT_RADIUS_PX = 10

    /**
     * @param centerX/centerY the tap point, in the buffer's own native pixel coordinates (already
     * mapped back from display space via [DebugCoordinateMapper.unmapPoint] by the caller) —
     * clamped into bounds here too, so a tap landing exactly on an edge pixel (a legitimate,
     * expected case, not an error) still samples a valid, if smaller, region rather than throwing.
     */
    fun sample(buffer: PixelBuffer, centerX: Int, centerY: Int, radiusPx: Int = DEFAULT_RADIUS_PX): List<RgbColor> {
        require(radiusPx >= 0) { "radiusPx must be >= 0, got $radiusPx" }

        val clampedCenterX = centerX.coerceIn(0, buffer.width - 1)
        val clampedCenterY = centerY.coerceIn(0, buffer.height - 1)
        val x0 = (clampedCenterX - radiusPx).coerceIn(0, buffer.width - 1)
        val x1 = (clampedCenterX + radiusPx).coerceIn(0, buffer.width - 1)
        val y0 = (clampedCenterY - radiusPx).coerceIn(0, buffer.height - 1)
        val y1 = (clampedCenterY + radiusPx).coerceIn(0, buffer.height - 1)

        val samples = ArrayList<RgbColor>((x1 - x0 + 1) * (y1 - y0 + 1))
        for (y in y0..y1) {
            for (x in x0..x1) {
                samples += buffer.rgbAt(x, y)
            }
        }
        return samples
    }
}
