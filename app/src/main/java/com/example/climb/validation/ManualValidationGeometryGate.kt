package com.example.climb.validation

import com.example.climb.colordetection.AlignmentCheckResult
import com.example.climb.edge.isCameraGeometryProfileCompatible
import kotlin.math.abs

/** Decoded pixel dimensions of a reference image or a video's actual frames — never a requested
 * target, always what was really measured. */
data class ImageDimensions(val widthPx: Int, val heightPx: Int) {
    init {
        require(widthPx > 0) { "widthPx must be positive" }
        require(heightPx > 0) { "heightPx must be positive" }
    }

    val aspectRatio: Float get() = widthPx.toFloat() / heightPx.toFloat()
}

/**
 * Phase 3B's deliberately simple geometry check for the manual-validation path — NOT a real
 * `CameraAlignmentChecker` (that's later, real Phase 3 work). The capture assumption this checks
 * is: one fixed phone, back camera, that never moved between the reference photo and the video —
 * same orientation, zoom, aspect ratio, camera geometry profile, no crop change. It never attempts
 * to correct for anything that contradicts that assumption — only [AlignmentCheckResult.ValidIdentity]
 * or [AlignmentCheckResult.CalibrationInvalid] can ever come out of this; [ManualValidationPipeline]
 * only ever applies the identity transform, matching that — see this class's two checks below,
 * which deliberately never touch [com.example.climb.colordetection.CaptureToReferenceTransform]'s
 * known crop+scale bug (flagged in NEXT_STEPS.md, out of scope here) since nothing here ever
 * constructs a non-identity transform in the first place.
 */
object ManualValidationGeometryGate {

    /** Aspect-ratio drift beyond this is treated as a real geometry mismatch — deliberately tight,
     * since the only expected real-world case is a floating-point-negligible difference. */
    private const val ASPECT_RATIO_TOLERANCE = 0.02f

    fun check(
        session: ManualValidationSession,
        referenceImageDimensions: ImageDimensions,
        videoDimensions: ImageDimensions,
        expectedGeometryProfileVersion: Int,
        wallCalibrationId: Long = LOCAL_VALIDATION_WALL_CALIBRATION_ID,
    ): AlignmentCheckResult {
        if (!isCameraGeometryProfileCompatible(session.cameraGeometryProfileVersion, expectedGeometryProfileVersion)) {
            return AlignmentCheckResult.CalibrationInvalid(
                "VALIDATION_GEOMETRY_MISMATCH: cameraGeometryProfileVersion=${session.cameraGeometryProfileVersion} " +
                    "does not match the expected version $expectedGeometryProfileVersion",
            )
        }

        val diff = abs(referenceImageDimensions.aspectRatio - videoDimensions.aspectRatio)
        if (diff > ASPECT_RATIO_TOLERANCE) {
            return AlignmentCheckResult.CalibrationInvalid(
                "VALIDATION_GEOMETRY_MISMATCH: reference image aspect ratio " +
                    "${referenceImageDimensions.widthPx}x${referenceImageDimensions.heightPx} does not match " +
                    "the video's ${videoDimensions.widthPx}x${videoDimensions.heightPx} (orientation/zoom/crop " +
                    "must not have changed between the reference photo and the video)",
            )
        }

        return AlignmentCheckResult.ValidIdentity(wallCalibrationId)
    }
}

/** No real `WallCalibrationEntity` exists for a manual validation session — this sentinel makes
 * that fact visible at every call site rather than reusing a plausible-looking real id. */
const val LOCAL_VALIDATION_WALL_CALIBRATION_ID = -1L
