package com.example.climb.validation

import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down [ValidationDatasetSummaryBuilder.build]'s pure tallying over a batch of
 * [ClipValidationExport]s — every count here is hand-computed from the fixture list below, never
 * copied from the implementation, per this phase's own testing standard. See
 * [ValidationDatasetSummary]'s own KDoc for why [ValidationDatasetSummary.wrongWinners] (the FALSE
 * VERIFIED ROUTE ASSIGNMENTS headline metric) is the single most important number this whole phase
 * exists to surface, and why the dedicated test below proves it is never conflated with
 * [ValidationDatasetSummary.reviewRequiredCount]/[ValidationDatasetSummary.unresolvedCount].
 */
class ValidationDatasetSummaryTest {

    /** A minimal, directly-constructed [ClipValidationExport] — the fields this builder never
     * reads (pose/timeline/route-candidate detail) are filled with cheap placeholder values, since
     * [ValidationDatasetSummaryBuilder.build] only ever reads [ClipValidationExport.attributionStatus]
     * and [ClipValidationExport.evaluationOutcome]. */
    private fun export(
        status: AttributionStatus,
        outcome: AttributionEvaluationOutcome,
        expectedRouteId: Long? = null,
        winningRouteId: Long? = null,
        lowPoseCoverage: Boolean = false,
        wasRejectedBeforeAttribution: Boolean = false,
    ): ClipValidationExport = ClipValidationExport(
        exportFormatVersion = ClipValidationExportBuilder.CURRENT_EXPORT_FORMAT_VERSION,
        validationSessionId = "session",
        wallOrFixtureId = "wall-a",
        wallSetupId = null,
        cameraGeometryProfileVersion = 1,
        poseFrameCount = 0,
        poseConfidenceCoveragePercent = 0f,
        establishedEventCount = 0,
        contactsPerLimb = emptyMap<Limb, Int>(),
        holdIdsTouched = emptySet(),
        timelineEvents = emptyList(),
        routeCandidates = emptyList(),
        winningRouteId = winningRouteId,
        secondPlaceRouteId = null,
        margin = null,
        attributionStatus = status,
        attributionReasonCode = if (status == AttributionStatus.VERIFIED) null else AttributionReasonCode.MARGIN_TOO_SMALL,
        expectedRouteId = expectedRouteId,
        evaluationOutcome = outcome,
        createdAtEpochMs = 0L,
        lowPoseCoverage = lowPoseCoverage,
        wasRejectedBeforeAttribution = wasRejectedBeforeAttribution,
    )

    // --- 1. Empty batch -> all-zero counts --------------------------------------------------------

    @Test
    fun `build on an empty list produces all-zero counts`() {
        val summary = ValidationDatasetSummaryBuilder.build(emptyList())

        assertEquals(0, summary.videosProcessed)
        assertEquals(0, summary.correctWinners)
        assertEquals(0, summary.wrongWinners)
        assertEquals(0, summary.verifiedCorrectCount)
        assertEquals(0, summary.verifiedIncorrectCount)
        assertEquals(0, summary.reviewRequiredCount)
        assertEquals(0, summary.unresolvedCount)
        assertEquals(0, summary.notLabeledCount)
        assertEquals(0, summary.totalLabeledClips)
        assertEquals(0, summary.clipsRejectedBeforeAttribution)
        assertEquals(0, summary.clipsWithLowPoseCoverage)
    }

    // --- 2. A mix across every evaluationOutcome/attributionStatus combination --------------------

    @Test
    fun `build tallies a mixed batch across every evaluationOutcome and attributionStatus combination`() {
        val exports = listOf(
            // Two correct, VERIFIED winners.
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 100L, winningRouteId = 100L),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 200L, winningRouteId = 200L),
            // One wrong, VERIFIED winner - a false VERIFIED route assignment.
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.WRONG_WINNER, expectedRouteId = 300L, winningRouteId = 301L),
            // The engine itself declined to commit to a winner - never counted as correct/wrong.
            export(AttributionStatus.REVIEW_REQUIRED, AttributionEvaluationOutcome.REVIEW_REQUIRED, expectedRouteId = 400L),
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.UNRESOLVED, expectedRouteId = 500L),
            // No ground truth labeled yet - regardless of what the engine itself decided.
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.NOT_LABELED, expectedRouteId = null),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.NOT_LABELED, expectedRouteId = null, winningRouteId = 600L),
        )

        val summary = ValidationDatasetSummaryBuilder.build(exports)

        assertEquals(7, summary.videosProcessed)
        assertEquals(2, summary.correctWinners)
        assertEquals(1, summary.wrongWinners)
        assertEquals(2, summary.verifiedCorrectCount)
        assertEquals(1, summary.verifiedIncorrectCount)
        assertEquals(1, summary.reviewRequiredCount)
        assertEquals(1, summary.unresolvedCount)
        assertEquals(2, summary.notLabeledCount)
    }

    // --- 3. wrongWinners is exactly the false-VERIFIED-assignment count, never conflated with ------
    // --- reviewRequiredCount/unresolvedCount --------------------------------------------------------

    @Test
    fun `wrongWinners counts only WRONG_WINNER exports and is distinct from reviewRequiredCount and unresolvedCount`() {
        val exports = listOf(
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 1L, winningRouteId = 1L),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 2L, winningRouteId = 2L),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 3L, winningRouteId = 3L),
            // The single false VERIFIED route assignment in this batch.
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.WRONG_WINNER, expectedRouteId = 4L, winningRouteId = 40L),
            export(AttributionStatus.REVIEW_REQUIRED, AttributionEvaluationOutcome.REVIEW_REQUIRED, expectedRouteId = 5L),
            export(AttributionStatus.REVIEW_REQUIRED, AttributionEvaluationOutcome.REVIEW_REQUIRED, expectedRouteId = 6L),
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.UNRESOLVED, expectedRouteId = 7L),
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.UNRESOLVED, expectedRouteId = 8L),
        )

        val summary = ValidationDatasetSummaryBuilder.build(exports)

        assertEquals(1, summary.wrongWinners)
        assertEquals(1, summary.verifiedIncorrectCount)
        assertEquals(2, summary.reviewRequiredCount)
        assertEquals(2, summary.unresolvedCount)
        // The headline metric must never be the reviewRequired/unresolved totals, nor their sum.
        assertEquals(1, summary.wrongWinners)
        assert(summary.wrongWinners != summary.reviewRequiredCount)
        assert(summary.wrongWinners != summary.unresolvedCount)
        assert(summary.wrongWinners != summary.reviewRequiredCount + summary.unresolvedCount)
    }

    // --- 4. totalLabeledClips excludes NOT_LABELED exports, and only those -------------------------

    @Test
    fun `totalLabeledClips counts every export whose evaluationOutcome is not NOT_LABELED`() {
        val exports = listOf(
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 1L, winningRouteId = 1L),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.WRONG_WINNER, expectedRouteId = 2L, winningRouteId = 20L),
            export(AttributionStatus.REVIEW_REQUIRED, AttributionEvaluationOutcome.REVIEW_REQUIRED, expectedRouteId = 3L),
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.UNRESOLVED, expectedRouteId = 4L),
            // Two unlabeled clips - no ground truth was ever entered for these.
            export(AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.NOT_LABELED, expectedRouteId = null),
            export(AttributionStatus.VERIFIED, AttributionEvaluationOutcome.NOT_LABELED, expectedRouteId = null, winningRouteId = 5L),
        )

        val summary = ValidationDatasetSummaryBuilder.build(exports)

        assertEquals(6, summary.videosProcessed)
        assertEquals(2, summary.notLabeledCount)
        assertEquals(4, summary.totalLabeledClips)
        // totalLabeledClips is exactly videosProcessed minus notLabeledCount - never re-derived from
        // the correct/wrong/review/unresolved counts themselves.
        assertEquals(summary.videosProcessed - summary.notLabeledCount, summary.totalLabeledClips)
    }

    // --- 5. clipsRejectedBeforeAttribution and clipsWithLowPoseCoverage are independent tallies -----
    // --- of each other, and of the evaluationOutcome/attributionStatus counts -----------------------

    @Test
    fun `clipsRejectedBeforeAttribution and clipsWithLowPoseCoverage are tallied independently of each other and of the outcome counts`() {
        val exports = listOf(
            // Rejected before attribution ever ran - never low pose coverage in this fixture.
            export(
                AttributionStatus.UNRESOLVED, AttributionEvaluationOutcome.UNRESOLVED, expectedRouteId = 10L,
                wasRejectedBeforeAttribution = true, lowPoseCoverage = false,
            ),
            // Low pose coverage, but attribution still ran and produced a correct VERIFIED winner.
            export(
                AttributionStatus.VERIFIED, AttributionEvaluationOutcome.CORRECT_WINNER, expectedRouteId = 20L, winningRouteId = 20L,
                wasRejectedBeforeAttribution = false, lowPoseCoverage = true,
            ),
            // Both flags set on the same clip - must be counted in both tallies, not merged into one.
            export(
                AttributionStatus.REVIEW_REQUIRED, AttributionEvaluationOutcome.REVIEW_REQUIRED, expectedRouteId = 30L,
                wasRejectedBeforeAttribution = true, lowPoseCoverage = true,
            ),
            // Neither flag set - an ordinary wrong-winner clip.
            export(
                AttributionStatus.VERIFIED, AttributionEvaluationOutcome.WRONG_WINNER, expectedRouteId = 40L, winningRouteId = 41L,
                wasRejectedBeforeAttribution = false, lowPoseCoverage = false,
            ),
        )

        val summary = ValidationDatasetSummaryBuilder.build(exports)

        assertEquals(4, summary.videosProcessed)
        assertEquals(2, summary.clipsRejectedBeforeAttribution)
        assertEquals(2, summary.clipsWithLowPoseCoverage)
        // The outcome/status tallies are unaffected by, and don't conflate with, these two new counts.
        assertEquals(1, summary.correctWinners)
        assertEquals(1, summary.wrongWinners)
        assertEquals(1, summary.reviewRequiredCount)
        assertEquals(1, summary.unresolvedCount)
    }
}
