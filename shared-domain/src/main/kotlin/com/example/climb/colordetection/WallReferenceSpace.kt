package com.example.climb.colordetection

/**
 * Coordinate-space contract for the gym-camera route-attribution work, shared between the member
 * app and the future Camera Edge Device module. Pure data + pure geometry math only — no
 * alignment-*checking* algorithm (that stays app-side, Phase 3's `CameraAlignmentChecker`, since
 * it needs real pixel-buffer image comparison, not just coordinate math).
 *
 * The core idea these types exist to enforce: hold geometry and pose landmarks must never be
 * compared by directly assuming they share a coordinate space — every comparison goes through an
 * explicit, versioned transform tied to a specific wall calibration, so a captured frame that
 * doesn't match the calibration's rotation/mirror/crop is caught rather than silently misaligned.
 */

/** A minimal 2D point in normalized [0,1] frame-relative coordinates — deliberately a small local
 * type rather than a dependency on `com.example.climb.analysis.metrics.Point2D` (which stays
 * app-side; this module has no dependency on `:app`). */
data class Point2D(val x: Float, val y: Float)

/** A normalized (0..1) axis-aligned rectangle, reused for wall ROIs, corridors, and crop regions —
 * always relative to whichever frame the containing type documents. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** How a captured frame's aspect ratio was reconciled against the wall's reference frame, when
 * they don't match exactly. */
enum class ResizeStrategy { FIT, FILL, STRETCH }

/** The calibrated coordinate basis one WallCalibrationEntity defines. Every persisted route hold
 * geometry (RouteVisionProfileEntity) is normalized against exactly this space, never raw pixels
 * of some arbitrary frame. */
data class WallReferenceSpace(
    val wallCalibrationId: Long,
    val referenceWidthPx: Int,
    val referenceHeightPx: Int,
    val wallRoiNormalized: NormalizedRect,
    val cameraCalibrationVersion: Int,
)

/** Maps a point from one captured attempt's own frame coordinates into its wall's
 * [WallReferenceSpace]. Resolved once per capture session by `CameraAlignmentChecker` (Phase 3,
 * app-side) — never assumed to be identity just because resolutions/aspect ratios happen to
 * match. [apply] implements only the *geometric application* of an already-known transform
 * (rotate/mirror/crop/scale a point) — estimating *which* transform applies to a given captured
 * frame (feature matching, registration search, etc.) is a separate, real-image-processing
 * concern that belongs to `CameraAlignmentChecker`, not to this pure-data/pure-geometry module. */
data class CaptureToReferenceTransform(
    val wallCalibrationId: Long,
    val rotationDegrees: Int,
    val mirrorHorizontal: Boolean,
    val mirrorVertical: Boolean,
    val cropRectInReferenceSpace: NormalizedRect,
    val scaleX: Float,
    val scaleY: Float,
    val resizeStrategy: ResizeStrategy,
) {
    init {
        require(rotationDegrees % 90 == 0) { "rotationDegrees must be a multiple of 90" }
    }

    /** Applies mirror, then rotation, then crop+scale, in that order — matching the order a
     * captured frame would actually be transformed in (sensor mirroring happens before any
     * rotation-correction, which happens before mapping into the calibration's crop/scale). For
     * the initial POC, `CameraAlignmentChecker` only ever produces the identity case
     * (rotationDegrees=0, no mirror, full-frame crop, scale=1) — see
     * `AlignmentCheckResult.ValidWithTransform`'s doc comment — but this function implements the
     * general case so the contract doesn't need to change shape once a real non-identity
     * transform is needed. */
    fun apply(pointInCaptureSpace: Point2D): Point2D {
        var x = pointInCaptureSpace.x
        var y = pointInCaptureSpace.y

        if (mirrorHorizontal) x = 1f - x
        if (mirrorVertical) y = 1f - y

        val rotated = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Point2D(x = 1f - y, y = x)
            180 -> Point2D(x = 1f - x, y = 1f - y)
            270 -> Point2D(x = y, y = 1f - x)
            else -> Point2D(x = x, y = y)
        }

        val cropWidth = cropRectInReferenceSpace.right - cropRectInReferenceSpace.left
        val cropHeight = cropRectInReferenceSpace.bottom - cropRectInReferenceSpace.top
        return Point2D(
            x = cropRectInReferenceSpace.left + rotated.x * cropWidth * scaleX,
            y = cropRectInReferenceSpace.top + rotated.y * cropHeight * scaleY,
        )
    }

    companion object {
        /** The expected, normal case for the initial POC: no rotation/mirror/crop/scale
         * correction at all — `pointInCaptureSpace` passes through unchanged. */
        fun identity(wallCalibrationId: Long): CaptureToReferenceTransform = CaptureToReferenceTransform(
            wallCalibrationId = wallCalibrationId,
            rotationDegrees = 0,
            mirrorHorizontal = false,
            mirrorVertical = false,
            cropRectInReferenceSpace = NormalizedRect(0f, 0f, 1f, 1f),
            scaleX = 1f,
            scaleY = 1f,
            resizeStrategy = ResizeStrategy.FIT,
        )
    }
}

/** Result of comparing a captured frame against a wall's stored calibration. */
sealed interface AlignmentCheckResult {
    /** The expected, normal case for the initial POC — captured frame matches the calibration
     * closely enough that no correction is needed at all. */
    data class ValidIdentity(val wallCalibrationId: Long) : AlignmentCheckResult

    /** A small, correctable drift was detected and a real [CaptureToReferenceTransform] was
     * estimated. For the initial POC, `CameraAlignmentChecker` (Phase 3) is deliberately kept
     * conservative — fixed resolution/orientation/crop assumed by convention, identity is the
     * expected case, and any meaningful mismatch goes straight to [CalibrationInvalid] instead of
     * attempting a correction. This case exists in the sealed type so the contract doesn't need to
     * change shape later, once real footage actually proves simple identity-matching
     * insufficient — it is not populated by anything in the initial POC. */
    data class ValidWithTransform(val transform: CaptureToReferenceTransform, val confidence: Float) : AlignmentCheckResult

    /** Captured frame differs from the wall's calibration too much to trust existing hold
     * geometry against it — attribution short-circuits to `AttributionStatus.CALIBRATION_INVALID`
     * rather than silently using stale/misaligned masks. */
    data class CalibrationInvalid(val reason: String) : AlignmentCheckResult
}
