package com.example.climb.attribution

import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus

/**
 * Phase 4A's automatic route resolver: scores one attempt's [HoldContactTimeline] against every
 * plausible [RouteCandidate] on the wall and decides a single [AttributionResult]. The whole engine
 * is built around TWO INDEPENDENT gates that must never be collapsed into one:
 *
 * 1. **Eligibility gate (hard, binary):** [StartHoldMatcher] decides whether a candidate even
 *    qualifies to win at all — [StartEvidenceStatus.START_OBSERVED_MATCH] or nothing. This is a
 *    precondition, never a weighted signal: a candidate with a flawless contact-coverage/corridor/
 *    finish score but no qualifying start contact can never become the winner, no matter how the
 *    weighted math below comes out for it.
 * 2. **Confidence gate (graded, comparative):** among ONLY the candidates that passed gate 1, the
 *    weighted [SubScoreResult.combinedScore] plus the margin over the runner-up decides whether the
 *    engine is confident enough to commit to a winner ([AttributionStatus.VERIFIED]), wants a human
 *    to look ([AttributionStatus.REVIEW_REQUIRED]), or can't decide at all
 *    ([AttributionStatus.UNRESOLVED]).
 *
 * Every candidate passed in still gets a [SubScoreResult] regardless of which gate it fails — see
 * that type's own doc comment on why the per-candidate contract is uniform — but only gate-1
 * survivors are ever considered by gate 2.
 *
 * This engine only ever produces [AttributionStatus.VERIFIED], [AttributionStatus.REVIEW_REQUIRED],
 * or [AttributionStatus.UNRESOLVED] — see [AttributionResult]'s own doc comment for why
 * `PENDING`/`CALIBRATION_INVALID` are upstream concerns (decided before this engine ever runs, per
 * docs/ROUTE_ATTRIBUTION_PLAN.md) and `REJECTED` is a downstream, human/staff decision made after
 * the fact, never something this scoring pass decides for itself.
 */
object RouteAttributionEngine {

    fun attribute(
        candidates: List<RouteCandidate>,
        holds: List<HoldShape>,
        timeline: HoldContactTimeline,
        attemptStartTimestampMs: Long,
        config: RouteAttributionScoringConfig = RouteAttributionScoringConfig(),
    ): AttributionResult {
        if (candidates.isEmpty()) {
            return AttributionResult(
                winningRouteVersionId = null,
                status = AttributionStatus.UNRESOLVED,
                reasonCode = AttributionReasonCode.NO_CANDIDATES,
                margin = null,
                subScores = emptyList(),
            )
        }

        val subScores = candidates
            .map { candidate -> scoreCandidate(candidate, candidates, holds, timeline, attemptStartTimestampMs, config) }
            .sortedBy { it.routeVersionId }

        val eligible = subScores.filter { it.startEvidenceStatus == StartEvidenceStatus.START_OBSERVED_MATCH }

        if (eligible.isEmpty()) {
            val anyMismatch = subScores.any { it.startEvidenceStatus == StartEvidenceStatus.START_OBSERVED_MISMATCH }
            return AttributionResult(
                winningRouteVersionId = null,
                status = AttributionStatus.UNRESOLVED,
                reasonCode = if (anyMismatch) AttributionReasonCode.START_MISMATCH else AttributionReasonCode.START_NOT_OBSERVED,
                margin = null,
                subScores = subScores,
            )
        }

        // Highest combinedScore wins; a true tie is broken by ascending routeVersionId (lowest id
        // wins), so the ranking is fully deterministic regardless of the candidates list's input
        // order (subScores itself is already routeVersionId-sorted above, but eligible's score-based
        // order still needs its own explicit, stable tiebreak).
        val rankedEligible = eligible.sortedWith(compareByDescending<SubScoreResult> { it.combinedScore }.thenBy { it.routeVersionId })
        val winner = rankedEligible.first()
        val runnerUpScore = rankedEligible
            .filter { it.routeVersionId != winner.routeVersionId }
            .maxOfOrNull { it.combinedScore }
            ?: 0f
        val margin = (winner.combinedScore - runnerUpScore).coerceIn(0f, 1f)

        val status: AttributionStatus
        val reasonCode: AttributionReasonCode?
        val winningRouteVersionId: Long?
        when {
            winner.combinedScore >= config.verifiedMinScore && margin >= config.minWinnerMargin -> {
                status = AttributionStatus.VERIFIED
                reasonCode = null
                winningRouteVersionId = winner.routeVersionId
            }
            winner.combinedScore >= config.reviewMinScore -> {
                // A REVIEW_REQUIRED result never claims an automatic winner - that decision is left
                // to a human. AttributionReasonCode has no dedicated "score too low" code distinct
                // from "margin too small": MARGIN_TOO_SMALL is deliberately reused here for both "the
                // margin itself was too small" and "the winning score never even cleared the review
                // bar," since RouteAttributionEntities.kt's enum is an already-approved Phase 1
                // contract this phase isn't extending. Known simplification - a future phase may want
                // to split these into distinct reason codes.
                status = AttributionStatus.REVIEW_REQUIRED
                reasonCode = AttributionReasonCode.MARGIN_TOO_SMALL
                winningRouteVersionId = null
            }
            else -> {
                // Same MARGIN_TOO_SMALL reuse as above, for the "didn't even clear reviewMinScore"
                // case - see the comment in the branch above.
                status = AttributionStatus.UNRESOLVED
                reasonCode = AttributionReasonCode.MARGIN_TOO_SMALL
                winningRouteVersionId = null
            }
        }

        return AttributionResult(
            winningRouteVersionId = winningRouteVersionId,
            status = status,
            reasonCode = reasonCode,
            margin = margin,
            subScores = subScores,
        )
    }

    private fun scoreCandidate(
        candidate: RouteCandidate,
        allCandidates: List<RouteCandidate>,
        holds: List<HoldShape>,
        timeline: HoldContactTimeline,
        attemptStartTimestampMs: Long,
        config: RouteAttributionScoringConfig,
    ): SubScoreResult {
        val startStatus = StartHoldMatcher.evaluate(candidate, allCandidates, timeline, attemptStartTimestampMs, config)
        val contactCoverageScore = ContactCoverageScorer.score(candidate, timeline)
        val corridorScore = CorridorScorer.score(candidate, timeline, holds)
        val finishScore = FinishEvidenceScorer.score(candidate, timeline)
        val foreignEventCount = ForeignContactPenaltyCalculator.uniqueForeignEventCount(candidate, allCandidates, timeline, config)
        val penaltyDeduction = ForeignContactPenaltyCalculator.penaltyDeduction(foreignEventCount, config)

        // Ineligible candidates get zero credit for the start component here - eligibility to WIN is
        // decided separately (the hard gate in attribute()); this is purely the numeric
        // weighted-sum contribution for a candidate that did pass the gate.
        val startComponentScore = if (startStatus == StartEvidenceStatus.START_OBSERVED_MATCH) 1f else 0f

        val totalConfiguredWeight =
            config.startHoldWeight + config.contactCoverageWeight + config.corridorWeight + config.finishWeight
        var availableWeight = config.startHoldWeight + config.contactCoverageWeight
        if (corridorScore != null) availableWeight += config.corridorWeight
        if (finishScore != null) availableWeight += config.finishWeight

        // Renormalize the four positive weights so the TOTAL positive-weight budget always sums
        // back to totalConfiguredWeight, no matter which of corridor/finish are unavailable this
        // call. This is the property RouteAttributionScoringConfig's own doc comment calls out: a
        // candidate missing corridor/finish data must NOT have its score capped low just because
        // those weights go unused - it must still be able to reach the same maximum positive score
        // as a candidate with every signal available.
        val renormalizationFactor = if (availableWeight > 0f) totalConfiguredWeight / availableWeight else 0f

        val positiveWeightedSum =
            config.startHoldWeight * renormalizationFactor * startComponentScore +
                config.contactCoverageWeight * renormalizationFactor * contactCoverageScore +
                (if (corridorScore != null) config.corridorWeight * renormalizationFactor * corridorScore else 0f) +
                (if (finishScore != null) config.finishWeight * renormalizationFactor * finishScore else 0f)

        // The explicit coerceIn is a documented final safety clamp - the formula above shouldn't
        // normally exceed [0,1], but this is the one place that guarantees it never does.
        val combinedScore = (positiveWeightedSum - penaltyDeduction).coerceIn(0f, 1f)

        return SubScoreResult(
            routeVersionId = candidate.routeVersionId,
            startEvidenceStatus = startStatus,
            contactCoverageScore = contactCoverageScore,
            corridorScore = corridorScore,
            finishScore = finishScore,
            foreignContactEventCount = foreignEventCount,
            foreignContactPenalty = penaltyDeduction,
            combinedScore = combinedScore,
        )
    }
}
