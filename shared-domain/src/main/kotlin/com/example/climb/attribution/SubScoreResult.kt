package com.example.climb.attribution

import com.example.climb.clubs.StartEvidenceStatus

/**
 * The uniform per-candidate result contract a `RouteAttributionEngine` (Phase 4B+) produces —
 * EVERY [RouteCandidate] scored for an attempt gets exactly one of these, whether or not it passed
 * the start-hold gate ([startEvidenceStatus]). Nothing about this shape changes based on whether a
 * candidate was eligible to win, which is what makes it a debuggable "uniform contract" rather
 * than a shape that varies with eligibility — a `RouteAttributionResultEntity.debugArtifactStoragePath`
 * dump can always assume every candidate's row looks the same.
 *
 * [corridorScore] and [finishScore] are `null` only when the corresponding [RouteCandidate] field
 * ([RouteCandidate.corridorNormalized] / [RouteCandidate.finishHoldIds]+[RouteCandidate.finishPolicy])
 * was itself absent — i.e. "structurally unavailable for this candidate," never "zero contact was
 * observed." Zero observed contact is a real `0f` value, not `null`. This distinction is exactly
 * what lets the engine renormalize [RouteAttributionScoringConfig]'s corridor/finish weights among
 * whichever signals actually exist for a candidate, instead of unfairly zeroing out its score for
 * an optional signal the route simply hasn't had populated yet.
 */
data class SubScoreResult(
    val routeVersionId: Long,
    val startEvidenceStatus: StartEvidenceStatus,
    /** Always available, in `[0,1]`. */
    val contactCoverageScore: Float,
    /** `null` only when the candidate has no [RouteCandidate.corridorNormalized]. */
    val corridorScore: Float?,
    /** `null` only when the candidate has no finish evidence (see [RouteCandidate.finishHoldIds]/
     * [RouteCandidate.finishPolicy]). */
    val finishScore: Float?,
    val foreignContactEventCount: Int,
    /** The actual score deduction applied for [foreignContactEventCount], `>= 0`. */
    val foreignContactPenalty: Float,
    /** Final per-candidate score, clamped to `[0,1]`. */
    val combinedScore: Float,
)
