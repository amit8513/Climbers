package com.example.climb.attribution

import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus

/**
 * The whole-attempt result a `RouteAttributionEngine` (Phase 4B+) produces from one
 * `HoldContactTimeline` scored against a list of [RouteCandidate]s. This engine only ever produces
 * [AttributionStatus.VERIFIED], [AttributionStatus.REVIEW_REQUIRED], or
 * [AttributionStatus.UNRESOLVED] — never `PENDING`/`CALIBRATION_INVALID` (those are set upstream of
 * this engine, before it ever runs) and never `REJECTED` (that is a human/downstream decision made
 * after the fact, not something this scoring engine decides for itself).
 */
data class AttributionResult(
    /** Non-null only when [status] is [AttributionStatus.VERIFIED]. */
    val winningRouteVersionId: Long?,
    val status: AttributionStatus,
    /** Null only when [status] is [AttributionStatus.VERIFIED]. */
    val reasonCode: AttributionReasonCode?,
    /** The winner's [SubScoreResult.combinedScore] minus the best-scoring OTHER eligible
     * candidate's [SubScoreResult.combinedScore]; `0f` if the winner was the only eligible
     * candidate. Null only when no candidate was eligible at all (so no winner exists to measure a
     * margin from). */
    val margin: Float?,
    /** Every candidate's result, sorted ascending by [SubScoreResult.routeVersionId] for
     * deterministic, stable output ordering. */
    val subScores: List<SubScoreResult>,
)
