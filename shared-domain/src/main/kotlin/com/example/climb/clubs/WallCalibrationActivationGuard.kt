package com.example.climb.clubs

import com.example.climb.edge.isCameraGeometryProfileCompatible

data class WallCalibrationActivationEligibility(val isEligible: Boolean, val blockingReasons: List<String>)

/**
 * The one place that decides whether a [WallCalibrationEntity] may ever be treated as eligible
 * for real activation — three independent gates, none of which anything else in this codebase
 * may bypass:
 *
 * 1. [ReferenceSource.TEST_FIXTURE] calibrations are rejected outright — a fake/demo reference
 *    used for hardware-independent UI/domain-flow work (Phase 2A) must be structurally impossible
 *    to promote to an active verified wall calibration, not merely discouraged.
 * 2. [WallCalibrationEntity.hardwareValidated] must be true — nothing in Phase 2A ever sets this,
 *    by design (see that field's doc comment).
 * 3. [WallCalibrationEntity.cameraGeometryProfileVersion] must exactly match
 *    [expectedGeometryProfileVersion] (via [isCameraGeometryProfileCompatible]) — the reference
 *    frame's FOV/crop/orientation must match whatever profile version an attempt capture actually
 *    used, not merely "the same physical camera."
 *
 * All three are checked and reported independently (never short-circuited) so a caller sees every
 * reason a calibration isn't eligible, not just the first one found.
 */
object WallCalibrationActivationGuard {
    fun checkEligibility(
        calibration: WallCalibrationEntity,
        expectedGeometryProfileVersion: Int,
    ): WallCalibrationActivationEligibility {
        val reasons = mutableListOf<String>()

        if (calibration.referenceSource == ReferenceSource.TEST_FIXTURE) {
            reasons += "referenceSource is TEST_FIXTURE - a fake/demo reference can never be promoted to an active verified wall calibration"
        }
        if (!calibration.hardwareValidated) {
            reasons += "hardwareValidated is false - this calibration has not been confirmed against real capture hardware"
        }
        if (!isCameraGeometryProfileCompatible(calibration.cameraGeometryProfileVersion, expectedGeometryProfileVersion)) {
            reasons += "cameraGeometryProfileVersion (${calibration.cameraGeometryProfileVersion}) does not match the expected version ($expectedGeometryProfileVersion)"
        }

        return WallCalibrationActivationEligibility(isEligible = reasons.isEmpty(), blockingReasons = reasons)
    }
}
