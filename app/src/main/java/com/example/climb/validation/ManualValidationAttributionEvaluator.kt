package com.example.climb.validation

import com.example.climb.attribution.AttributionResult
import com.example.climb.clubs.AttributionStatus

/**
 * Compares an already-computed [AttributionResult] (produced upstream by
 * `com.example.climb.attribution.RouteAttributionEngine`, run over this debug harness's own
 * timeline/candidates) against a [ManualValidationSession]'s optional human-entered
 * [ManualValidationSession.expectedRouteId] ground truth — purely for this local/debug path's own
 * observability, never fed back into the resolver.
 *
 * [AttributionEvaluationOutcome.WRONG_WINNER] is the single most important outcome value this
 * whole phase exists to surface: it is the exact "the engine committed to a VERIFIED route, and
 * that route is the wrong one" case — a false-positive automatic attribution, as opposed to a
 * merely-unconfident result. Every other outcome value here is comparatively low-stakes (either
 * there was no ground truth to check against, or the engine itself already declined to commit).
 *
 * [evaluate] is read-only with respect to [AttributionResult]: it never mutates, wraps, re-scores,
 * or feeds anything back into any resolver call. It only ever compares two already-final values —
 * [ManualValidationSession.expectedRouteId] and the fields already present on the passed-in
 * [AttributionResult] — and is therefore provably unable to influence how that result was computed
 * in the first place. See `ManualValidationAttributionEvaluatorTest`'s "expected route comparison
 * never affects resolver output" test for the property this guarantees.
 */
enum class AttributionEvaluationOutcome {
    /** [ManualValidationSession.expectedRouteId] was never set — nothing to compare against. */
    NOT_LABELED,

    /** The engine committed to [AttributionStatus.VERIFIED] and its winner matches the
     * human-labeled expected route. */
    CORRECT_WINNER,

    /** The engine committed to [AttributionStatus.VERIFIED], but its winner does NOT match the
     * human-labeled expected route — a false VERIFIED route assignment. See this file's own doc
     * comment for why this is the single most important outcome value this whole phase exists to
     * surface. */
    WRONG_WINNER,

    /** The engine itself declined to commit to a winner and asked for human review
     * ([AttributionStatus.REVIEW_REQUIRED]) — never counted as either a correct or a wrong
     * automatic winner, since no automatic winner was ever claimed. */
    REVIEW_REQUIRED,

    /** The engine could not resolve a winner at all ([AttributionStatus.UNRESOLVED]) while ground
     * truth was labeled — distinct from [REVIEW_REQUIRED] (a human was asked to look) and from
     * [WRONG_WINNER]/[CORRECT_WINNER] (no winner was ever claimed here either way). */
    UNRESOLVED,
}

/** The full comparison result — [status]/[predictedRouteId] are read straight off the passed-in
 * [AttributionResult] (never recomputed), and [expectedRouteId] straight off the session, so this
 * type is fully reconstructible from its two inputs alone. */
data class AttributionEvaluation(
    val outcome: AttributionEvaluationOutcome,
    val expectedRouteId: Long?,
    val predictedRouteId: Long?,
    val status: AttributionStatus,
)

object ManualValidationAttributionEvaluator {

    /**
     * Read-only comparison of [session]'s human-entered [ManualValidationSession.expectedRouteId]
     * against [result]'s already-final [AttributionResult.status]/
     * [AttributionResult.winningRouteVersionId]. [result] must be a value some earlier, independent
     * call already produced — this function never calls
     * `com.example.climb.attribution.RouteAttributionEngine` (or any wrapper around it) itself, and
     * never mutates or re-derives anything on [result]; it only reads the two already-final values
     * off it.
     */
    fun evaluate(session: ManualValidationSession, result: AttributionResult): AttributionEvaluation {
        val expected = session.expectedRouteId
        val outcome = when {
            expected == null -> AttributionEvaluationOutcome.NOT_LABELED
            result.status == AttributionStatus.VERIFIED && result.winningRouteVersionId == expected -> AttributionEvaluationOutcome.CORRECT_WINNER
            result.status == AttributionStatus.VERIFIED -> AttributionEvaluationOutcome.WRONG_WINNER
            result.status == AttributionStatus.REVIEW_REQUIRED -> AttributionEvaluationOutcome.REVIEW_REQUIRED
            else -> AttributionEvaluationOutcome.UNRESOLVED
        }
        return AttributionEvaluation(
            outcome = outcome,
            expectedRouteId = expected,
            predictedRouteId = result.winningRouteVersionId,
            status = result.status,
        )
    }
}
