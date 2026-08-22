package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.FinishPolicy

/**
 * Phase 4A's finish-evidence signal: was [RouteCandidate.finishPolicy] ever satisfied by a plain
 * ESTABLISHED contact event on one of [RouteCandidate.finishHoldIds], anywhere in the timeline.
 * Deliberately NOT time-windowed or dwell-gated the way `StartHoldMatcher` is — a finish only ever
 * needs to have happened once, at any point during the attempt, so there is no equivalent of a
 * start-observation window to bound the search. This makes the score a binary 1f/0f rather than a
 * graded value, which is a deliberate POC-simplicity choice (same hard-gate style already used
 * elsewhere in this engine, e.g. `StartEvidenceStatus`) that a future phase may refine — e.g. to
 * require a minimum dwell on the finish hold, or to weight by [com.example.climb.analysis.contact.EvidenceQuality].
 */
object FinishEvidenceScorer {

    /** Returns `null` when [RouteCandidate.finishHoldIds] is empty (equivalently
     * [RouteCandidate.finishPolicy] is `null`, per [RouteCandidate]'s own invariant) — finish
     * evidence is structurally unavailable for this candidate, for the engine to renormalize away,
     * never a `0f` "no contact observed" result. */
    fun score(candidate: RouteCandidate, timeline: HoldContactTimeline): Float? {
        val finishPolicy = candidate.finishPolicy ?: return null
        if (candidate.finishHoldIds.isEmpty()) return null

        val establishedOnFinish = timeline.events.filter {
            it.type == ContactEventType.ESTABLISHED && it.holdId in candidate.finishHoldIds
        }

        val satisfied = when (finishPolicy) {
            FinishPolicy.ONE_HAND_ON_FINISH ->
                establishedOnFinish.any { it.limb == Limb.LEFT_HAND || it.limb == Limb.RIGHT_HAND }
            FinishPolicy.TWO_HANDS_ON_FINISH ->
                establishedOnFinish.any { it.limb == Limb.LEFT_HAND } &&
                    establishedOnFinish.any { it.limb == Limb.RIGHT_HAND }
            FinishPolicy.TOP_OUT_ZONE ->
                establishedOnFinish.isNotEmpty()
        }

        return if (satisfied) 1f else 0f
    }
}
