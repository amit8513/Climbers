package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.RouteColorConflictChecker
import com.example.climb.clubs.RouteVersionEntity
import com.example.climb.clubs.RouteVersionSnapshotValidationResult
import com.example.climb.clubs.StartPolicy
import com.example.climb.clubs.WallCalibrationEntity
import com.example.climb.clubs.WallEntity
import com.example.climb.clubs.HoldRole
import com.example.climb.clubs.RouteVisionProfileEntity
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.ReviewedHold
import com.example.climb.edge.CapturedFrame

/**
 * Pure, Android-free wizard state for the wall-camera route-registration flow (Phase 2A) — every
 * field here is plain data so [RouteRegistrationHoldSelection]/[RouteRegistrationDraftBuilder] are
 * fully unit-testable without a ViewModel/Compose runtime. [RouteRegistrationViewModel] just wraps
 * one of these in a `StateFlow` and forwards UI events to the pure functions in this package.
 */
data class RouteRegistrationDraftState(
    val organizationId: Long,
    val wall: WallEntity? = null,
    val capturedFrame: CapturedFrame? = null,
    val wallRoiNormalized: NormalizedRect? = null,
    val candidateColorHex: Long? = null,
    val grade: Int? = null,
    val gradeSystem: String = DEFAULT_GRADE_SYSTEM,
    /** Deliberately optional — see `RouteVersionSnapshotValidator.validateDraft`'s doc comment. */
    val publicNumberOrName: String? = null,
    val holds: List<ReviewedHold> = emptyList(),
    val startPolicy: StartPolicy? = null,
    val finishPolicy: FinishPolicy? = null,
) {
    val startHold: ReviewedHold? get() = holds.firstOrNull { it.role == HoldRole.START }
    val finishHold: ReviewedHold? get() = holds.firstOrNull { it.role == HoldRole.FINISH }

    companion object {
        const val DEFAULT_GRADE_SYSTEM = "V_SCALE"
    }
}

/** The three draft entities one completed wizard pass produces — none persisted anywhere yet
 * (Phase 2A is domain-flow only, see `RouteRegistrationDraftStore`), and none eligible for
 * activation (see `WallCalibrationActivationGuard`, `RouteRegistrationStatus.DRAFT`). */
data class RouteRegistrationDraftResult(
    val wallCalibration: WallCalibrationEntity,
    val visionProfile: RouteVisionProfileEntity,
    val routeVersion: RouteVersionEntity,
)

data class RouteRegistrationValidationSummary(
    val snapshotValidation: RouteVersionSnapshotValidationResult,
    val colorConflict: RouteColorConflictChecker.ConflictCheckResult,
) {
    val canSaveDraft: Boolean get() = snapshotValidation.isValid && !colorConflict.hasConflict
}
