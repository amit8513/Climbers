package com.example.climb.validation

import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.ForeignContactEventClassifier
import com.example.climb.attribution.ForeignContactPenaltyCalculator
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.RouteCandidate
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.StartEvidenceStatus
import kotlin.math.abs

/**
 * Pure, display-only helpers the (Phase 4B) debug UI uses to explain WHY
 * `com.example.climb.attribution.RouteAttributionEngine` (`:shared-domain`, read-only, never
 * modified here) produced the [AttributionResult]/[SubScoreResult] it did for one manual
 * validation session. Nothing here is a scoring or gating DECISION — every function is either a
 * pure re-derivation of a fact the engine's own public output/config already implies (the
 * renormalized weight budget), or a plain filter/sort over already-public data
 * ([AttributionResult.subScores], [HoldContactTimeline.events]) with no new judgment of its own.
 * This file must never diverge from the real engine's behavior — see the corresponding test file
 * for the consistency checks that pin that down (most importantly, [foreignContactEvents]'s result
 * must always agree with [ForeignContactPenaltyCalculator.uniqueForeignEventCount] for the same
 * inputs — guaranteed structurally now, since both derive from the same
 * [ForeignContactEventClassifier]).
 */

/** The four renormalized weights [RouteAttributionEngine] actually used for one [SubScoreResult] —
 * a display-only mirror of [RouteAttributionEngine]'s own weight-renormalization formula (see
 * [RouteAttributionScoringConfig]'s doc comment on why corridor/finish weight is redistributed,
 * never left as a silent zero, when the corresponding signal is structurally unavailable for a
 * candidate). [corridorWeight]/[finishWeight] are `0f` exactly when [SubScoreResult.corridorScore]/
 * [SubScoreResult.finishScore] were `null` for this candidate — never because the config weight
 * itself was configured to zero (that case is indistinguishable from "unavailable" by design, since
 * a caller who genuinely wants zero corridor weight and a caller whose candidate simply lacks
 * corridor data end up with the same renormalized `0f` either way, exactly as the engine itself
 * would compute).
 */
data class NormalizedScoringWeights(
    val startHoldWeight: Float,
    val contactCoverageWeight: Float,
    /** `0f` when this candidate's [SubScoreResult.corridorScore] was `null`. */
    val corridorWeight: Float,
    /** `0f` when this candidate's [SubScoreResult.finishScore] was `null`. */
    val finishWeight: Float,
)

/**
 * Re-derives the renormalized weight budget [RouteAttributionEngine] actually applied to
 * [subScore], purely from [subScore]'s own public nullability
 * ([SubScoreResult.corridorScore]/[SubScoreResult.finishScore] being `null` already tells us which
 * signals were unavailable for this candidate) plus [config]'s public fields — see
 * [RouteAttributionScoringConfig]'s own doc comment and [RouteAttributionEngine]'s KDoc for the
 * renormalization rule this mirrors: the total positive-weight budget
 * ([RouteAttributionScoringConfig.startHoldWeight] + [RouteAttributionScoringConfig.contactCoverageWeight] +
 * [RouteAttributionScoringConfig.corridorWeight] + [RouteAttributionScoringConfig.finishWeight])
 * always gets fully redistributed among whichever of the four weights are actually available for
 * this candidate, so a candidate missing an optional signal reaches the identical weight budget as
 * one with every signal present — never a smaller one.
 */
fun normalizedWeightsUsed(subScore: SubScoreResult, config: RouteAttributionScoringConfig): NormalizedScoringWeights {
    val totalConfiguredWeight = config.startHoldWeight + config.contactCoverageWeight + config.corridorWeight + config.finishWeight
    val availableWeight = config.startHoldWeight + config.contactCoverageWeight +
        (if (subScore.corridorScore != null) config.corridorWeight else 0f) +
        (if (subScore.finishScore != null) config.finishWeight else 0f)
    val factor = if (availableWeight > 0f) totalConfiguredWeight / availableWeight else 0f
    return NormalizedScoringWeights(
        startHoldWeight = config.startHoldWeight * factor,
        contactCoverageWeight = config.contactCoverageWeight * factor,
        corridorWeight = if (subScore.corridorScore != null) config.corridorWeight * factor else 0f,
        finishWeight = if (subScore.finishScore != null) config.finishWeight * factor else 0f,
    )
}

/**
 * The runner-up candidate for the debug UI's "how close was this?" display — purely a sort/filter
 * over [AttributionResult.subScores], no new eligibility or scoring logic of its own. Only
 * candidates that passed the hard start-eligibility gate
 * ([StartEvidenceStatus.START_OBSERVED_MATCH]) are ever considered, matching
 * `RouteAttributionEngine`'s own eligibility gate.
 *
 * **[AttributionResult.winningRouteVersionId] is non-null case:** returns the highest-
 * [SubScoreResult.combinedScore] eligible candidate OTHER than the winner — the genuine runner-up.
 *
 * **[AttributionResult.winningRouteVersionId] is `null` case** (i.e. [AttributionResult.status] is
 * `REVIEW_REQUIRED` or `UNRESOLVED` — [AttributionResult] itself never records a winner for either
 * of those statuses, so there is no winner id to exclude): every eligible candidate is treated as a
 * runner-up, and this simply returns the highest-scoring eligible one (or `null` if none are
 * eligible at all). This works out for free below because [SubScoreResult.routeVersionId] is a
 * non-nullable `Long`, so `it.routeVersionId != winnerId` is `it.routeVersionId != null`, which is
 * always `true` — no eligible candidate is ever excluded when there is no winner to exclude.
 */
fun secondPlaceCandidate(result: AttributionResult): SubScoreResult? {
    val eligible = result.subScores.filter { it.startEvidenceStatus == StartEvidenceStatus.START_OBSERVED_MATCH }
    val winnerId = result.winningRouteVersionId
    return eligible.filter { it.routeVersionId != winnerId }
        .maxWithOrNull(compareBy { it.combinedScore })
}

/** Every [HoldContactTimeline] event landing on one of [candidate]'s own start holds — a plain
 * filter for the debug UI's video-scrub panel, no new decision logic. */
fun startHoldEventsFor(candidate: RouteCandidate, timeline: HoldContactTimeline): List<HoldContactEvent> =
    timeline.events.filter { it.holdId in candidate.startHoldIds }

/** Every [HoldContactTimeline] event landing on one of [candidate]'s own finish holds — a plain
 * filter for the debug UI's video-scrub panel, no new decision logic. */
fun finishHoldEventsFor(candidate: RouteCandidate, timeline: HoldContactTimeline): List<HoldContactEvent> =
    timeline.events.filter { it.holdId in candidate.finishHoldIds }

/**
 * Every [HoldContactTimeline] event that actually counts as a "foreign contact" against
 * [candidate] for display in the debug UI — delegates to [ForeignContactEventClassifier], the
 * single shared predicate that [ForeignContactPenaltyCalculator.uniqueForeignEventCount] also
 * derives from, so the displayed EVENTS a developer can scrub to always agree with
 * [SubScoreResult.foreignContactEventCount], the real number the engine actually scored against.
 * See this file's own doc comment and the corresponding test file for the enforced consistency
 * guarantee between this function's result and that calculator's count.
 */
fun foreignContactEvents(
    candidate: RouteCandidate,
    allCandidates: List<RouteCandidate>,
    timeline: HoldContactTimeline,
    config: RouteAttributionScoringConfig,
): List<HoldContactEvent> =
    ForeignContactEventClassifier.qualifyingForeignEvents(candidate, allCandidates, timeline, config)

/** Every [HoldContactTimeline] event within [windowMs] (inclusive both ends) of [timestampMs] — the
 * debug UI's "what happened right around this point in the scrub" filter, no new decision logic. */
fun eventsNearTimestamp(timeline: HoldContactTimeline, timestampMs: Long, windowMs: Long): List<HoldContactEvent> =
    timeline.events.filter { abs(it.timestampMs - timestampMs) <= windowMs }
