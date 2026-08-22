package com.example.climb.validation

import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.AttemptResult
import com.example.climb.colordetection.Point2D

/**
 * One manually-recorded climbing video, paired with one manually-captured "clean wall" reference
 * photo and manually-annotated hold geometry — a developer/debug fixture for hand-checking
 * `HoldContactDetector` against real footage before any real Edge Capture Agent hardware exists
 * (Phase 1.25's NFC spike and Phase 1.5A's real-camera smoke test are both still hardware-pending).
 *
 * Deliberately structurally unrelated to `WallCaptureSession`/`AttemptSource`/
 * `RouteAttributionResultEntity`/`ClubRepository` — this type has no field of either of the first
 * two types, and nothing in this package (`com.example.climb.validation`) imports `ClubRepository`
 * or writes to Firestore at all. See `ManualValidationTrustBoundaryTest` for the enforced
 * guarantee this is never accidentally treated as, or promoted into, official club-camera data.
 *
 * Phase 4B adds five fields for wiring this harness up to
 * `com.example.climb.attribution.RouteAttributionEngine`, purely inside this debug path:
 * [routeDefinitions] are Phase 4B's candidate routes for the attribution engine, kept deliberately
 * separate from the older, Phase 3B [startHoldIds]/[finishHoldIds] fields above — those remain
 * exactly as they were (still used by whatever consumed them before) and are not consumed by the
 * new attribution path at all. [attemptStartTimestampMs] has no real wristband-tap equivalent in
 * this dev harness, so it defaults to the start of the clip and is meant to be adjustable from the
 * debug UI once a developer decides where in the clip the "attempt" actually begins.
 * [wallSetupId] is a pure grouping/traceability convenience (see ValidationWallSetup.kt) with no
 * runtime lookup dependency — a session remains fully self-contained even if the wall setup it was
 * copied from is later deleted. [expectedRouteId]/[expectedResult] are optional human-entered
 * ground truth for comparison against the engine's own output only — never fed into the resolver
 * itself.
 */
data class ManualValidationSession(
    val validationSessionId: String,
    val referenceImagePath: String,
    val videoPath: String,
    /** A free-form local label ("wall-a", "gym-visit-2026-08-21") grouping sessions that share one
     * physical camera setup — never a real `WallEntity` id, never resolved against any backend. */
    val wallOrFixtureId: String,
    val cameraGeometryProfileVersion: Int,
    val annotatedHolds: List<ValidationHoldAnnotation> = emptyList(),
    val startHoldIds: List<Int> = emptyList(),
    val finishHoldIds: List<Int> = emptyList(),
    /** Optional — a report never claims formal accuracy when this is empty; see
     * `ManualValidationReportBuilder`. */
    val groundTruthContacts: List<GroundTruthContactAnnotation> = emptyList(),
    val notes: String? = null,
    val createdAtEpochMs: Long,
    /** Phase 4B's candidate routes for `RouteAttributionEngine` — see this class's own doc
     * comment for how this relates to the older [startHoldIds]/[finishHoldIds] fields above. */
    val routeDefinitions: List<ValidationRouteDefinition> = emptyList(),
    val attemptStartTimestampMs: Long = 0L,
    val wallSetupId: String? = null,
    val expectedRouteId: Long? = null,
    val expectedResult: AttemptResult? = null,
) {
    init {
        require(validationSessionId.isNotBlank()) { "validationSessionId must not be blank" }
        require(referenceImagePath.isNotBlank()) { "referenceImagePath must not be blank" }
        require(videoPath.isNotBlank()) { "videoPath must not be blank" }
        require(wallOrFixtureId.isNotBlank()) { "wallOrFixtureId must not be blank" }
        require(cameraGeometryProfileVersion > 0) { "cameraGeometryProfileVersion must be positive" }
    }
}

/** One manually-drawn hold contour, in `WallReferenceSpace`-normalized coordinates against the
 * session's own [ManualValidationSession.referenceImagePath] — the same shape
 * `HoldContactDetector` consumes via `HoldShape`, just before that conversion. */
data class ValidationHoldAnnotation(val holdId: Int, val contourNormalized: List<Point2D>) {
    init {
        require(contourNormalized.size >= 3) { "a hold contour needs at least 3 vertices, got ${contourNormalized.size}" }
    }
}

/** A developer's manual "yes, I watched this and their right hand touched hold 7 around here"
 * observation — never algorithmically produced, always a human judgment call entered for
 * comparison against the detector's own output. */
data class GroundTruthContactAnnotation(
    val limb: Limb,
    val holdId: Int,
    val approxTimestampMs: Long,
    val note: String? = null,
)
