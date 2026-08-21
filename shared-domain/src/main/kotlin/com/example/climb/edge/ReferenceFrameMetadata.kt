package com.example.climb.edge

import com.example.climb.colordetection.NormalizedRect

/**
 * What a captured still reference frame actually turned out to be, alongside what was requested
 * — the two are recorded separately and neither is ever derived from the other, since real
 * hardware is not guaranteed to honor a request exactly (see [CameraGeometryProfile]'s doc
 * comment). [requestedGeometryProfileVersion] is what [isCameraGeometryProfileCompatible] checks
 * against a `RouteVisionProfile`'s own calibration-time version — a mismatch there means this
 * frame cannot be trusted against that route's geometry, no matter how close the actual pixel
 * dimensions happen to look.
 *
 * [mirrored] and [actualCropRect] are honesty-limited in v1: `CameraXCameraSourceAdapter` does
 * not yet independently measure either from CameraX (no live "was this mirrored" signal is used,
 * and no viewport/crop is applied) — both currently just echo the requested
 * [CameraGeometryProfile], the same "explicitly unvalidated placeholder" honesty standard this
 * codebase already holds `RouteColorDetectionConfig`/`FixedCameraRouteRegistrationConfig` to.
 */
data class ReferenceFrameMetadata(
    val requestedGeometryProfileVersion: Int,
    val requestedWidthPx: Int,
    val requestedHeightPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int,
    val mirrored: Boolean,
    val actualCropRect: NormalizedRect,
    val capturedAtEpochMs: Long,
    val organizationId: String,
    val wallId: String,
    val cameraDeviceId: String,
) {
    init {
        require(requestedGeometryProfileVersion > 0) { "requestedGeometryProfileVersion must be positive" }
        require(requestedWidthPx > 0) { "requestedWidthPx must be positive" }
        require(requestedHeightPx > 0) { "requestedHeightPx must be positive" }
        require(widthPx > 0) { "widthPx must be positive" }
        require(heightPx > 0) { "heightPx must be positive" }
        require(actualCropRect.right > actualCropRect.left && actualCropRect.bottom > actualCropRect.top) {
            "actualCropRect must have positive width and height"
        }
        require(organizationId.isNotBlank()) { "organizationId must not be blank" }
        require(wallId.isNotBlank()) { "wallId must not be blank" }
        require(cameraDeviceId.isNotBlank()) { "cameraDeviceId must not be blank" }
    }
}
