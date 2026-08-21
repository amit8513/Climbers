package com.example.climb.edge

import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.ResizeStrategy

/**
 * v1 forces the back camera only (see [CameraGeometryProfile]'s init block) — front-facing
 * capture is out of scope for this POC, not merely undefaulted. The case still exists so the
 * type doesn't need to change shape if a future device/use case genuinely needs it.
 */
enum class CameraLensFacing { BACK, FRONT }

/** A simple width:height ratio, kept independent of any platform's `AspectRatio` constants since
 * this module has no Android dependency. */
data class CameraAspectRatio(val widthRatio: Int, val heightRatio: Int) {
    init {
        require(widthRatio > 0) { "widthRatio must be positive" }
        require(heightRatio > 0) { "heightRatio must be positive" }
    }

    fun toFloatRatio(): Float = widthRatio.toFloat() / heightRatio.toFloat()

    companion object {
        val RATIO_16_9 = CameraAspectRatio(16, 9)
        val RATIO_4_3 = CameraAspectRatio(4, 3)
    }
}

/**
 * The single geometry contract shared by the still-reference-frame capture path (`:edge-agent`'s
 * `CameraSourceAdapter`, this phase) and the future attempt-video capture path (a `VideoCapture`
 * adapter, Phase 2.5 — not implemented yet). Neither path may invent its own resolution/rotation/
 * mirror/crop settings — both must consume the same versioned profile via [CameraCaptureConfig].
 *
 * **The invariant this type exists to enforce**: a `RouteVisionProfile` calibrated against a
 * reference frame captured under one [CameraGeometryProfile] [version] is valid **only** for
 * attempt captures (today: another reference frame; Phase 2.5+: attempt video) produced under
 * that *exact same* version — never a different one, even if every other field looks compatible.
 * The authoritative wall reference cannot merely come from "the same physical camera" — its
 * FOV/crop/orientation must match the future attempt-video path exactly, and a version bump is
 * the only thing that's allowed to signal "this no longer matches." A wall recalibration that
 * bumps this version must invalidate every `RouteVisionProfile` depending on the old one, the
 * same way `WallCalibrationEntity`'s cascade already does for `wallCalibrationId` (see
 * NEXT_STEPS.md's "calibration-invalidation cascade"). See [isCameraGeometryProfileCompatible].
 *
 * All fields are "requested" — see `ReferenceFrameMetadata` for what a specific capture actually
 * produced, which is recorded separately and is never assumed to equal these targets.
 */
data class CameraGeometryProfile(
    val lensFacing: CameraLensFacing = CameraLensFacing.BACK,
    val requestedAspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_16_9,
    val requestedWidthPx: Int = 1920,
    val requestedHeightPx: Int = 1080,
    val requestedRotationDegrees: Int = 0,
    val mirrorExpected: Boolean = false,
    val cropRect: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f),
    val resizeStrategy: ResizeStrategy = ResizeStrategy.FIT,
    val version: Int = 1,
) {
    init {
        require(lensFacing == CameraLensFacing.BACK) {
            "Only the back camera is a valid POC configuration (Phase 1.5A/2.5) - front-camera " +
                "capture is out of scope entirely, not just undefaulted"
        }
        require(!mirrorExpected) {
            "The back camera is not expected to mirror output - a mirrored profile is not a " +
                "valid POC configuration"
        }
        require(requestedWidthPx > 0) { "requestedWidthPx must be positive" }
        require(requestedHeightPx > 0) { "requestedHeightPx must be positive" }
        require(requestedRotationDegrees % 90 == 0) { "requestedRotationDegrees must be a multiple of 90" }
        require(cropRect.right > cropRect.left && cropRect.bottom > cropRect.top) {
            "cropRect must have positive width and height"
        }
        require(version > 0) { "version must be positive" }

        val requestedRatio = requestedWidthPx.toFloat() / requestedHeightPx.toFloat()
        require(kotlin.math.abs(requestedRatio - requestedAspectRatio.toFloatRatio()) < ASPECT_RATIO_TOLERANCE) {
            "requestedAspectRatio ($requestedAspectRatio) does not match " +
                "requestedWidthPx/requestedHeightPx ($requestedWidthPx x $requestedHeightPx)"
        }
    }

    companion object {
        private const val ASPECT_RATIO_TOLERANCE = 0.05f
    }
}

/**
 * The one place that decides whether a reference frame (or, once it exists, an attempt video)
 * captured under [attemptProfileVersion] may be evaluated against a `RouteVisionProfile`
 * calibrated under [referenceProfileVersion]. Deliberately exact-match only — see the invariant
 * documented on [CameraGeometryProfile].
 */
fun isCameraGeometryProfileCompatible(referenceProfileVersion: Int, attemptProfileVersion: Int): Boolean =
    referenceProfileVersion == attemptProfileVersion
