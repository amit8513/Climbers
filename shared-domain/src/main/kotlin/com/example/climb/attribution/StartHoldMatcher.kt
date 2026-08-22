package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy

/**
 * The Phase 4A hard start-evidence gate: the ONLY code that decides a [StartEvidenceStatus]. Per
 * [RouteAttributionScoringConfig]'s own doc comment, start-hold evidence is a hard precondition for
 * `AttributionStatus.VERIFIED`, never a weighted signal folded into the renormalized score sum — a
 * candidate with a perfect contact-coverage/corridor/finish score but no qualifying start contact
 * must never win. This object is where that separation is actually enforced; nothing downstream of
 * it is allowed to re-derive or override a [StartEvidenceStatus] from score alone.
 *
 * [evaluate] intentionally asks two DIFFERENT questions depending on which one it needs:
 * - **MATCH** ("did *this* candidate's own start policy get satisfied?") is checked using ONLY
 *   [RouteCandidate.startHoldIds]/[RouteCandidate.startPolicy] belonging to the candidate being
 *   evaluated. A different candidate's start holds are never substituted in, even if they happen to
 *   overlap — a candidate's start policy is a claim about ITS OWN holds, and must stand or fall on
 *   evidence for those holds alone.
 * - **MISMATCH vs. NOT_OBSERVED** ("given this candidate didn't match, was anything at all
 *   plausibly a start attempt?") is decided by asking whether ANY candidate in [allCandidates] —
 *   this one included, if present in that list — had a qualifying establishment on ITS OWN start
 *   holds, using the same qualifying-establishment test as MATCH but WITHOUT requiring full
 *   policy-satisfaction. This is deliberately a weaker bar than MATCH: a lone `LEFT_HAND`
 *   establishment on a `TWO_HANDS_SAME_HOLD` candidate's start hold doesn't satisfy that
 *   candidate's own policy, but it is still real start-like contact, and a climber who grabbed
 *   *some* plausible start hold on *some* route deserves `START_OBSERVED_MISMATCH` (a real, if
 *   wrong, start attempt) rather than being lumped in with `START_NOT_OBSERVED` (nothing
 *   resembling a start happened anywhere).
 *
 * Defining both questions this way — always "per-candidate against only that candidate's own
 * holds" — is what keeps the result consistent across every candidate scored in the same
 * [evaluate] call: it never depends on call order, on how many other candidates are passed in, or
 * on which candidate happens to be evaluated first. Every candidate's [StartEvidenceStatus] can be
 * computed independently and would agree with a fresh call using a different candidate list order
 * (or even a singleton list containing just the observed-on candidate), because neither question
 * ever borrows one candidate's holds to answer another candidate's status.
 */
object StartHoldMatcher {

    fun evaluate(
        candidate: RouteCandidate,
        allCandidates: List<RouteCandidate>,
        timeline: HoldContactTimeline,
        attemptStartTimestampMs: Long,
        config: RouteAttributionScoringConfig,
    ): StartEvidenceStatus {
        val ownQualifying = qualifyingEstablishments(candidate.startHoldIds, timeline, attemptStartTimestampMs, config)
        if (isPolicySatisfied(candidate.startPolicy, ownQualifying)) {
            return StartEvidenceStatus.START_OBSERVED_MATCH
        }

        val anyQualifyingObservedAnywhere = allCandidates.any { other ->
            qualifyingEstablishments(other.startHoldIds, timeline, attemptStartTimestampMs, config).isNotEmpty()
        }
        return if (anyQualifyingObservedAnywhere) {
            StartEvidenceStatus.START_OBSERVED_MISMATCH
        } else {
            StartEvidenceStatus.START_NOT_OBSERVED
        }
    }

    private fun isPolicySatisfied(policy: StartPolicy, qualifying: List<HoldContactEvent>): Boolean = when (policy) {
        StartPolicy.SINGLE_HOLD_ANY_HAND ->
            qualifying.any { it.limb == Limb.LEFT_HAND || it.limb == Limb.RIGHT_HAND }
        StartPolicy.TWO_HOLDS_ONE_PER_HAND -> {
            val leftHoldIds = qualifying.filter { it.limb == Limb.LEFT_HAND }.map { it.holdId }.toSet()
            val rightHoldIds = qualifying.filter { it.limb == Limb.RIGHT_HAND }.map { it.holdId }.toSet()
            leftHoldIds.any { left -> rightHoldIds.any { right -> right != left } }
        }
        StartPolicy.TWO_HANDS_SAME_HOLD -> {
            val leftHoldIds = qualifying.filter { it.limb == Limb.LEFT_HAND }.map { it.holdId }.toSet()
            val rightHoldIds = qualifying.filter { it.limb == Limb.RIGHT_HAND }.map { it.holdId }.toSet()
            leftHoldIds.intersect(rightHoldIds).isNotEmpty()
        }
    }

    /** Every ESTABLISHED event on [holdIds] within `[attemptStartTimestampMs,
     * attemptStartTimestampMs + config.startObservationWindowMs]` (inclusive both ends) whose dwell
     * (per [dwellSatisfiesThreshold]) meets [RouteAttributionScoringConfig.startEstablishmentDwellMs].
     * This is the one qualifying-establishment test both MATCH and MISMATCH/NOT_OBSERVED are built
     * from — see this file's class-level doc comment for why both questions reuse it against
     * whichever hold-id set is relevant to the question being asked. */
    private fun qualifyingEstablishments(
        holdIds: Set<Int>,
        timeline: HoldContactTimeline,
        attemptStartTimestampMs: Long,
        config: RouteAttributionScoringConfig,
    ): List<HoldContactEvent> {
        val windowEndMs = attemptStartTimestampMs + config.startObservationWindowMs
        return timeline.events.filter { event ->
            event.type == ContactEventType.ESTABLISHED &&
                event.holdId in holdIds &&
                event.timestampMs in attemptStartTimestampMs..windowEndMs &&
                dwellSatisfiesThreshold(event, timeline, config)
        }
    }

    /** [HoldContactTimeline] events strictly alternate ESTABLISHED/RELEASED per (limb, holdId) pair
     * (see that type's own doc comment), so there is at most one RELEASED event for this exact pair
     * with a timestamp strictly after [establishedEvent]'s — the "paired" release, if the limb ever
     * let go again during this timeline. Its absence means the hold was never released again, which
     * this treats as an unbounded (always-satisfied) dwell rather than a failure. */
    private fun dwellSatisfiesThreshold(
        establishedEvent: HoldContactEvent,
        timeline: HoldContactTimeline,
        config: RouteAttributionScoringConfig,
    ): Boolean {
        val pairedRelease = timeline.events
            .asSequence()
            .filter {
                it.type == ContactEventType.RELEASED &&
                    it.limb == establishedEvent.limb &&
                    it.holdId == establishedEvent.holdId &&
                    it.timestampMs > establishedEvent.timestampMs
            }
            .minByOrNull { it.timestampMs }

        val dwellMs = if (pairedRelease == null) {
            Long.MAX_VALUE
        } else {
            pairedRelease.timestampMs - establishedEvent.timestampMs
        }
        return dwellMs >= config.startEstablishmentDwellMs
    }
}
