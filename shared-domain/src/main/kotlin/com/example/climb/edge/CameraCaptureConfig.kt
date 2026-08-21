package com.example.climb.edge

import com.example.climb.colordetection.NormalizedRect

/**
 * What one capture use case (still-reference-frame today; the future `VideoCapture` adapter,
 * Phase 2.5) requests from the camera. Deliberately just a thin holder around
 * [CameraGeometryProfile] — never its own resolution/rotation/mirror/crop fields — so a future
 * `VideoCapture` adapter is structurally forced to consume the exact same profile a reference
 * frame was captured under, rather than inventing separate settings that could quietly drift
 * apart. See [CameraGeometryProfile]'s documented invariant for why that drift would matter.
 */
data class CameraCaptureConfig(
    val geometryProfile: CameraGeometryProfile = CameraGeometryProfile(),
) {
    val targetWidthPx: Int get() = geometryProfile.requestedWidthPx
    val targetHeightPx: Int get() = geometryProfile.requestedHeightPx
    val targetRotationDegrees: Int get() = geometryProfile.requestedRotationDegrees
    val mirrored: Boolean get() = geometryProfile.mirrorExpected
    val cropRect: NormalizedRect get() = geometryProfile.cropRect
    val version: Int get() = geometryProfile.version
}
