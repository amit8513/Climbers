package com.example.climb.attribution

/**
 * Every weight/threshold/margin for the (Phase 4) automatic route-attribution scoring engine. No
 * `RouteAttributionEngine`/`StartHoldMatcher`/`ContactCoverageScorer`/etc. exist yet to consume
 * this. All defaults are explicitly unvalidated hypotheses pending real-footage tuning, not
 * biomechanical truths — same honesty standard as every other config object in this codebase.
 */
data class RouteAttributionScoringConfig(
    /** First 3-5s (default 4s) after a detected attempt start — the window start-hold evidence is
     * evaluated within. */
    val startObservationWindowMs: Long = 4000L,
    val startEstablishmentDwellMs: Long = 500L,
    val minLimbLandmarkConfidence: Float = 0.55f,
    /** Minimum total combined score for `AttributionStatus.VERIFIED` — combined with
     * [minWinnerMargin] and a hard `StartEvidenceStatus.START_OBSERVED_MATCH` precondition (never
     * satisfied by score alone). */
    val verifiedMinScore: Float = 0.75f,
    val reviewMinScore: Float = 0.55f,
    val minWinnerMargin: Float = 0.15f,
    /** Positive-evidence weights, renormalized among whichever of corridor/finish are actually
     * available for a given attempt — start-hold evidence itself is never renormalized away (it's
     * a hard gate, evaluated separately, not part of this weighted sum). */
    val startHoldWeight: Float = 0.40f,
    val contactCoverageWeight: Float = 0.25f,
    val corridorWeight: Float = 0.10f,
    val finishWeight: Float = 0.05f,
    /** Foreign-contact penalty is always applied (never renormalized away); computed from unique,
     * confident, fully-established contact *events* on a different candidate's holds, not raw
     * frame counts. */
    val foreignContactPenaltyWeight: Float = 0.20f,
    val foreignContactPenaltyPerEvent: Float = 0.25f,
    val version: Int = 1,
) {
    init {
        require(reviewMinScore <= verifiedMinScore) { "reviewMinScore must not exceed verifiedMinScore" }
        require(minWinnerMargin > 0f) { "minWinnerMargin must be positive" }
        require(minLimbLandmarkConfidence in 0f..1f) { "minLimbLandmarkConfidence must be in [0,1]" }
    }
}
