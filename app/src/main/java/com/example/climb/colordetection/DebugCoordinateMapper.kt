package com.example.climb.colordetection

/**
 * Phase 7 debug-tooling utility: pure proportional scaling from original reference-frame pixel
 * space into a target canvas/display size. No aspect-ratio correction or letterboxing math — both
 * source and target are assumed to represent the exact same rectangular frame, just at different
 * pixel densities (a reference frame decoded at its native resolution, displayed inside a
 * `Canvas`/`Image` composable at whatever size Compose laid it out at). Used by
 * [com.example.climb.ui.detail.HoldDetectionDebugScreen] to draw candidate/refined/rejected
 * overlays at the right position regardless of the on-screen preview size, without depending on
 * any Compose/Android/Bitmap type itself.
 */
object DebugCoordinateMapper {

    data class ScaledPoint(val x: Float, val y: Float)
    data class ScaledRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    fun mapPoint(
        x: Double,
        y: Double,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): ScaledPoint {
        require(sourceWidth > 0 && sourceHeight > 0) { "sourceWidth/sourceHeight must be positive, got ($sourceWidth, $sourceHeight)" }
        val scaleX = targetWidth / sourceWidth
        val scaleY = targetHeight / sourceHeight
        return ScaledPoint((x * scaleX).toFloat(), (y * scaleY).toFloat())
    }

    /** Maps [box] to target space — the far edge is scaled at `x1 + 1`/`y1 + 1` (not `x1`/`y1`)
     * since [BoundingBox] is inclusive on both ends, so the mapped rect's own width/height lines up
     * with [BoundingBox.width]/[BoundingBox.height] scaled by the same factor, not one pixel short. */
    fun mapBoundingBox(
        box: BoundingBox,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): ScaledRect {
        val topLeft = mapPoint(box.x0.toDouble(), box.y0.toDouble(), sourceWidth, sourceHeight, targetWidth, targetHeight)
        val bottomRight = mapPoint((box.x1 + 1).toDouble(), (box.y1 + 1).toDouble(), sourceWidth, sourceHeight, targetWidth, targetHeight)
        return ScaledRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun mapContour(
        contour: List<Centroid>,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): List<ScaledPoint> = contour.map { mapPoint(it.x, it.y, sourceWidth, sourceHeight, targetWidth, targetHeight) }

    /**
     * Inverse of [mapPoint]: maps a point in target/display space back to source-frame pixel
     * coordinates. Used by tap-to-calibrate ([com.example.climb.ui.detail.DetailScreen]) to
     * translate a Compose tap offset — in the displayed reference-frame image's own laid-out
     * size — back to the underlying decoded frame's native pixel space, so [RoiSampler] samples
     * the right region of the real [PixelBuffer] regardless of how large the preview was drawn.
     * Reuses [ScaledPoint] as a plain float-pair holder even though the result here is in SOURCE
     * space, not target space — the type itself carries no space-specific meaning.
     */
    fun unmapPoint(
        targetX: Float,
        targetY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): ScaledPoint {
        require(targetWidth > 0f && targetHeight > 0f) { "targetWidth/targetHeight must be positive, got ($targetWidth, $targetHeight)" }
        val scaleX = sourceWidth / targetWidth
        val scaleY = sourceHeight / targetHeight
        return ScaledPoint(targetX * scaleX, targetY * scaleY)
    }
}
