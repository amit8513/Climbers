package com.example.climb.validation

import com.example.climb.clubs.AttributionStatus

/**
 * Phase 4B's dataset-wide rollup over a batch of already-built [ClipValidationExport]s — pure
 * tallying, never new decision logic. Every count here is read straight off each export's own
 * [ClipValidationExport.attributionStatus] / [ClipValidationExport.evaluationOutcome] fields (both
 * already-final values produced upstream by `RouteAttributionEngine` and
 * [ManualValidationAttributionEvaluator] respectively); this type never re-runs, re-scores, or
 * re-derives an outcome for a single export, only counts how many exports in the batch landed in
 * each already-decided bucket.
 *
 * **[wrongWinners] — the FALSE VERIFIED ROUTE ASSIGNMENTS headline metric — is the single most
 * important number this whole phase exists to surface.** Per this phase's own instructions: real
 * climbing footage is about to be run through the full
 * `reference image -> ... -> HoldContactTimeline -> RouteAttributionEngine` pipeline for the first
 * time against real ground truth, and the one failure mode that matters most is the engine
 * confidently committing to [AttributionStatus.VERIFIED] on the WRONG route — a false-positive
 * automatic attribution a gym could act on. Every other count on this type is comparatively
 * low-stakes: [reviewRequiredCount]/[unresolvedCount] both mean the engine already declined to
 * commit to an automatic winner, and [notLabeledCount] just means there was no ground truth to
 * check against yet.
 *
 * This summary must NEVER be presented as a formal accuracy claim (there is deliberately no
 * percentage/rate field anywhere on this type) until enough labeled real data exists to make such a
 * claim meaningful — quoting this phase's own instructions verbatim: tuning/accuracy claims happen
 * "later, after real labeled clips exist." Until then, this type only ever exposes raw counts.
 */
data class ValidationDatasetSummary(
    val videosProcessed: Int,
    val correctWinners: Int,
    /** FALSE VERIFIED ROUTE ASSIGNMENTS — see this class's own KDoc for why this is the single
     * most important number this whole phase exists to surface. */
    val wrongWinners: Int,
    val verifiedCorrectCount: Int,
    /** Same value as [wrongWinners] whenever every [AttributionStatus.VERIFIED] clip in the batch
     * is labeled (a [AttributionEvaluationOutcome.WRONG_WINNER] outcome can only ever occur when
     * [ClipValidationExport.attributionStatus] is [AttributionStatus.VERIFIED] — see
     * [ManualValidationAttributionEvaluator]'s own decision logic) — kept as its own explicit field
     * per this phase's required dataset-summary field list rather than merged into [wrongWinners]. */
    val verifiedIncorrectCount: Int,
    val reviewRequiredCount: Int,
    val unresolvedCount: Int,
    val notLabeledCount: Int,
    /** Exports where [ClipValidationExport.evaluationOutcome] is anything other than
     * [AttributionEvaluationOutcome.NOT_LABELED] — i.e. a human actually entered ground truth for
     * this clip, regardless of what the engine itself decided. */
    val totalLabeledClips: Int,
    /** Count of clips whose pipeline run ended in [ManualValidationOutcome.Rejected] — never even
     * reached attribution (see [ClipValidationExport.wasRejectedBeforeAttribution]). */
    val clipsRejectedBeforeAttribution: Int,
    /** Count of clips flagged [ClipValidationExport.lowPoseCoverage] — purely advisory, see that
     * field's own doc comment; never a pipeline failure on its own. */
    val clipsWithLowPoseCoverage: Int,
)

/**
 * Builds one [ValidationDatasetSummary] from a batch of already-built [ClipValidationExport]s —
 * purely tallying already-computed fields, no new decision logic. See [ValidationDatasetSummary]'s
 * own KDoc for the "raw counts only, never a formal accuracy claim" rule this builder exists to
 * respect.
 */
object ValidationDatasetSummaryBuilder {

    fun build(exports: List<ClipValidationExport>): ValidationDatasetSummary = ValidationDatasetSummary(
        videosProcessed = exports.size,
        correctWinners = exports.count { it.evaluationOutcome == AttributionEvaluationOutcome.CORRECT_WINNER },
        wrongWinners = exports.count { it.evaluationOutcome == AttributionEvaluationOutcome.WRONG_WINNER },
        verifiedCorrectCount = exports.count {
            it.attributionStatus == AttributionStatus.VERIFIED &&
                it.evaluationOutcome == AttributionEvaluationOutcome.CORRECT_WINNER
        },
        verifiedIncorrectCount = exports.count {
            it.attributionStatus == AttributionStatus.VERIFIED &&
                it.evaluationOutcome == AttributionEvaluationOutcome.WRONG_WINNER
        },
        reviewRequiredCount = exports.count { it.evaluationOutcome == AttributionEvaluationOutcome.REVIEW_REQUIRED },
        unresolvedCount = exports.count { it.evaluationOutcome == AttributionEvaluationOutcome.UNRESOLVED },
        notLabeledCount = exports.count { it.evaluationOutcome == AttributionEvaluationOutcome.NOT_LABELED },
        totalLabeledClips = exports.count { it.evaluationOutcome != AttributionEvaluationOutcome.NOT_LABELED },
        clipsRejectedBeforeAttribution = exports.count { it.wasRejectedBeforeAttribution },
        clipsWithLowPoseCoverage = exports.count { it.lowPoseCoverage },
    )
}
