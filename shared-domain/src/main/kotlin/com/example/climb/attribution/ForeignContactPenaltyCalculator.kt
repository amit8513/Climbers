package com.example.climb.attribution

import com.example.climb.analysis.contact.HoldContactTimeline

/**
 * Computes the "foreign contact" penalty a `RouteAttributionEngine` (Phase 4B+) applies to one
 * [RouteCandidate]: evidence that the climber spent confident, fully-established contact on holds
 * that belong to some OTHER candidate on the wall, not this one. Split out into its own object
 * (rather than left as a private helper buried inside the engine) specifically so this logic is
 * independently unit-testable — the "which hold ids count as foreign" set-arithmetic and the
 * "how many events convert into how much score deduction" math are each subtle enough to deserve
 * their own tests, without needing a full engine wired up around them.
 */
object ForeignContactPenaltyCalculator {

    /**
     * Counts unique ESTABLISHED [timeline] events landing on a hold id that belongs to some OTHER
     * candidate's [RouteCandidate.allHoldIds] but not [candidate]'s own — i.e. a hold id shared
     * between two candidates' definitions (e.g. an overlapping body hold) is never counted as
     * "foreign" against either of them.
     *
     * Counts EVENTS, never frames or duration: each [HoldContactTimeline] event already
     * represents one full ESTABLISHED transition by [HoldContactTimeline]'s own dedup-by-design
     * (see its doc comment), so a contact that lasts 500 frames and one that lasts 2 frames count
     * identically — exactly once each — as long as each is a single unbroken
     * ESTABLISHED-to-RELEASED contact.
     *
     * The actual "which events qualify as foreign" predicate (type must be
     * [com.example.climb.analysis.contact.ContactEventType.ESTABLISHED], the hold id must be
     * foreign, [com.example.climb.analysis.contact.EvidenceQuality] must not be
     * [com.example.climb.analysis.contact.EvidenceQuality.UNCERTAIN], and confidence must be at
     * least [RouteAttributionScoringConfig.minLimbLandmarkConfidence] — per
     * [RouteAttributionScoringConfig]'s own doc comment on
     * [RouteAttributionScoringConfig.foreignContactPenaltyWeight], so a shaky, fallback-landmark,
     * or low-confidence touch on a foreign hold is not held against a candidate) now lives in
     * [ForeignContactEventClassifier] — this delegates to it rather than re-implementing the
     * predicate, so there is exactly one place that logic can ever be tuned.
     */
    fun uniqueForeignEventCount(
        candidate: RouteCandidate,
        allCandidates: List<RouteCandidate>,
        timeline: HoldContactTimeline,
        config: RouteAttributionScoringConfig,
    ): Int {
        return ForeignContactEventClassifier.qualifyingForeignEvents(candidate, allCandidates, timeline, config).size
    }

    /**
     * Converts a [foreignEventCount] into the actual score deduction, per
     * [RouteAttributionScoringConfig]'s own doc comment on
     * [RouteAttributionScoringConfig.foreignContactPenaltyWeight] /
     * [RouteAttributionScoringConfig.foreignContactPenaltyPerEvent]:
     * [RouteAttributionScoringConfig.foreignContactPenaltyPerEvent] is how much of the max penalty
     * ratio one foreign event consumes — the ratio is capped at `1.0` total, so events beyond
     * whatever count reaches that cap don't deduct any further.
     * [RouteAttributionScoringConfig.foreignContactPenaltyWeight] is the absolute maximum score
     * deduction any candidate can ever receive from this penalty, regardless of how many foreign
     * events it accumulates.
     */
    fun penaltyDeduction(foreignEventCount: Int, config: RouteAttributionScoringConfig): Float {
        val ratio = (config.foreignContactPenaltyPerEvent * foreignEventCount).coerceIn(0f, 1f)
        return config.foreignContactPenaltyWeight * ratio
    }
}
