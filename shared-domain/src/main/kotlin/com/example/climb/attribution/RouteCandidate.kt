package com.example.climb.attribution

import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect

/**
 * One candidate route a `RouteAttributionEngine` (Phase 4B+) scores an attempt's
 * `HoldContactTimeline` against. A single attempt is scored against every plausible candidate on
 * the wall at once (never just one route in isolation), which is why this is its own type rather
 * than something folded directly into `RouteVisionProfileEntity` — a candidate is a scoring-time
 * projection of a route version's vision profile, not a persistence shape.
 *
 * [finishHoldIds]/[finishPolicy] and [corridorNormalized] are independently optional: a route
 * version may have staff-confirmed start/body holds long before its finish evidence or corridor
 * is populated. Leaving [finishHoldIds] empty (with [finishPolicy] `null`) or [corridorNormalized]
 * `null` is how a candidate honestly signals "this evidence is not available yet for this route"
 * rather than silently scoring as if zero finish/corridor contact had been observed — see
 * `SubScoreResult`'s doc comment for how the engine is expected to renormalize weights around a
 * missing signal instead of penalizing a candidate for it.
 */
data class RouteCandidate(
    val routeVersionId: Long,
    val startHoldIds: Set<Int>,
    val startPolicy: StartPolicy,
    val bodyHoldIds: Set<Int> = emptySet(),
    val finishHoldIds: Set<Int> = emptySet(),
    val finishPolicy: FinishPolicy? = null,
    val corridorNormalized: NormalizedRect? = null,
) {
    init {
        require(startHoldIds.isNotEmpty()) { "a route candidate must have at least one start hold" }
        require((finishHoldIds.isEmpty()) == (finishPolicy == null)) {
            "finishHoldIds and finishPolicy must both be present or both be absent"
        }
    }

    /** Every hold id this candidate is defined over, across all three roles — the full set a
     * foreign-contact check needs to exclude when counting contact on OTHER candidates' holds. */
    val allHoldIds: Set<Int> get() = startHoldIds + bodyHoldIds + finishHoldIds
}
