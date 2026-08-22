package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.HoldContactTimeline

/**
 * Phase 4A's "was this hold ever gripped at all" coverage signal — deliberately existence-only,
 * never weighted by how many times a hold was gripped, how long it was held, or which limb did it.
 * Unlike corridor/finish evidence, this is always available (never `null` in a [SubScoreResult]):
 * it needs no attempt-start timestamp and no observation window, since it measures coverage across
 * the *whole* timeline rather than a window near the start. Finer-grained treatment (dwell-weighted,
 * limb-aware, etc.) is explicitly out of scope here — future-phase work.
 */
object ContactCoverageScorer {

    fun score(candidate: RouteCandidate, timeline: HoldContactTimeline): Float {
        val allHoldIds = candidate.allHoldIds
        if (allHoldIds.isEmpty()) return 0f

        val establishedHoldIds = timeline.events
            .filter { it.type == ContactEventType.ESTABLISHED }
            .mapTo(mutableSetOf()) { it.holdId }

        val coveredCount = allHoldIds.count { it in establishedHoldIds }
        return coveredCount.toFloat() / allHoldIds.size
    }
}
