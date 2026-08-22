package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline

/**
 * THE single pure predicate for "which hold ids are foreign to a candidate" and "which
 * [HoldContactTimeline] events qualify as confident, fully-established foreign contact" — every
 * scoring-relevant or reporting-relevant consumer of this predicate, whether the real scoring math
 * ([ForeignContactPenaltyCalculator]) or debug/reporting code (`:app`'s manual-validation debug
 * UI), must derive its foreign-contact events/counts from this object, never from a second
 * independent re-implementation of the same predicate.
 *
 * This object exists specifically because a prior duplication was found and flagged during a
 * safety audit: `:app`'s `AttributionDebugDetails.kt` had its own `foreignContactEvents()`
 * function that independently re-implemented the exact same foreign-contact-event predicate
 * [ForeignContactPenaltyCalculator.uniqueForeignEventCount] already computed. Two copies of the
 * same scoring-relevant predicate could silently drift if either was ever tuned later without the
 * other being updated to match — this object is the structural fix: there is now exactly one
 * implementation, and both the calculator and any debug reporting derive from it (Phase 4B.1
 * hardening correction).
 */
object ForeignContactEventClassifier {

    /**
     * The set of hold ids that belong to some OTHER candidate's [RouteCandidate.allHoldIds] but
     * not [candidate]'s own — i.e. a hold id shared between two candidates' definitions (e.g. an
     * overlapping body hold) is never "foreign" against either of them.
     */
    fun foreignHoldIds(candidate: RouteCandidate, allCandidates: List<RouteCandidate>): Set<Int> {
        return allCandidates
            .filter { it.routeVersionId != candidate.routeVersionId }
            .flatMap { it.allHoldIds }
            .toSet() - candidate.allHoldIds
    }

    /**
     * The [timeline] events that qualify as confident, fully-established foreign contact against
     * [candidate], given the other candidates on the wall and the current
     * [RouteAttributionScoringConfig]. Only events that are "confident, fully-established" per
     * [RouteAttributionScoringConfig]'s own doc comment on
     * [RouteAttributionScoringConfig.foreignContactPenaltyWeight] qualify: type must be
     * [ContactEventType.ESTABLISHED], the event's hold id must be in
     * [foreignHoldIds] for [candidate], [EvidenceQuality] must not be
     * [EvidenceQuality.UNCERTAIN], and confidence must be at least
     * [RouteAttributionScoringConfig.minLimbLandmarkConfidence]. A shaky, fallback-landmark, or
     * low-confidence touch on a foreign hold does not qualify.
     */
    fun qualifyingForeignEvents(
        candidate: RouteCandidate,
        allCandidates: List<RouteCandidate>,
        timeline: HoldContactTimeline,
        config: RouteAttributionScoringConfig,
    ): List<HoldContactEvent> {
        val foreignHoldIds = foreignHoldIds(candidate, allCandidates)

        return timeline.events.filter { event ->
            event.type == ContactEventType.ESTABLISHED &&
                event.holdId in foreignHoldIds &&
                event.evidenceQuality != EvidenceQuality.UNCERTAIN &&
                event.confidence >= config.minLimbLandmarkConfidence
        }
    }
}
