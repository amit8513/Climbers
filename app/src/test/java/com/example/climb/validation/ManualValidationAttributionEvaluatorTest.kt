package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionEngine
import com.example.climb.attribution.RouteCandidate
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Covers all five [AttributionEvaluationOutcome] values with hand-built [AttributionResult]
 * fixtures, plus the two properties this whole phase exists to guarantee: that ground truth never
 * leaks into the resolver (see the "never affects resolver output" test), and that a false
 * VERIFIED assignment is actually detected (see the "false VERIFIED is detected" test).
 */
class ManualValidationAttributionEvaluatorTest {

    private fun session(expectedRouteId: Long?, validationSessionId: String = "session-1"): ManualValidationSession =
        ManualValidationSession(
            validationSessionId = validationSessionId,
            referenceImagePath = "/tmp/ref.jpg",
            videoPath = "/tmp/clip.mp4",
            wallOrFixtureId = "wall-a",
            cameraGeometryProfileVersion = 1,
            createdAtEpochMs = 0L,
            expectedRouteId = expectedRouteId,
        )

    private fun subScore(routeVersionId: Long, startEvidenceStatus: StartEvidenceStatus, combinedScore: Float): SubScoreResult =
        SubScoreResult(
            routeVersionId = routeVersionId,
            startEvidenceStatus = startEvidenceStatus,
            contactCoverageScore = combinedScore,
            corridorScore = null,
            finishScore = null,
            foreignContactEventCount = 0,
            foreignContactPenalty = 0f,
            combinedScore = combinedScore,
        )

    // --- All five outcome values -------------------------------------------------------------

    @Test
    fun `expectedRouteId null yields NOT_LABELED regardless of the result`() {
        val result = AttributionResult(
            winningRouteVersionId = 7L,
            status = AttributionStatus.VERIFIED,
            reasonCode = null,
            margin = 0.5f,
            subScores = listOf(subScore(7L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.9f)),
        )

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session(expectedRouteId = null), result)

        assertEquals(AttributionEvaluationOutcome.NOT_LABELED, evaluation.outcome)
        assertNull(evaluation.expectedRouteId)
        assertEquals(7L, evaluation.predictedRouteId)
        assertEquals(AttributionStatus.VERIFIED, evaluation.status)
    }

    @Test
    fun `VERIFIED winner matching expectedRouteId yields CORRECT_WINNER`() {
        val result = AttributionResult(
            winningRouteVersionId = 7L,
            status = AttributionStatus.VERIFIED,
            reasonCode = null,
            margin = 0.5f,
            subScores = listOf(subScore(7L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.9f)),
        )

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session(expectedRouteId = 7L), result)

        assertEquals(AttributionEvaluationOutcome.CORRECT_WINNER, evaluation.outcome)
        assertEquals(7L, evaluation.expectedRouteId)
        assertEquals(7L, evaluation.predictedRouteId)
        assertEquals(AttributionStatus.VERIFIED, evaluation.status)
    }

    @Test
    fun `false VERIFIED is detected - VERIFIED winner 7 against expectedRouteId 9 yields WRONG_WINNER`() {
        val result = AttributionResult(
            winningRouteVersionId = 7L,
            status = AttributionStatus.VERIFIED,
            reasonCode = null,
            margin = 0.5f,
            subScores = listOf(subScore(7L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.9f)),
        )

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session(expectedRouteId = 9L), result)

        assertEquals(AttributionEvaluationOutcome.WRONG_WINNER, evaluation.outcome)
        assertEquals(9L, evaluation.expectedRouteId)
        assertEquals(7L, evaluation.predictedRouteId)
        assertEquals(AttributionStatus.VERIFIED, evaluation.status)
    }

    @Test
    fun `REVIEW_REQUIRED status with a labeled expectedRouteId yields REVIEW_REQUIRED`() {
        val result = AttributionResult(
            winningRouteVersionId = null,
            status = AttributionStatus.REVIEW_REQUIRED,
            reasonCode = AttributionReasonCode.MARGIN_TOO_SMALL,
            margin = 0.05f,
            subScores = listOf(
                subScore(7L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.6f),
                subScore(9L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.58f),
            ),
        )

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session(expectedRouteId = 7L), result)

        assertEquals(AttributionEvaluationOutcome.REVIEW_REQUIRED, evaluation.outcome)
        assertEquals(7L, evaluation.expectedRouteId)
        assertNull(evaluation.predictedRouteId)
        assertEquals(AttributionStatus.REVIEW_REQUIRED, evaluation.status)
    }

    @Test
    fun `UNRESOLVED status with a labeled expectedRouteId yields UNRESOLVED`() {
        val result = AttributionResult(
            winningRouteVersionId = null,
            status = AttributionStatus.UNRESOLVED,
            reasonCode = AttributionReasonCode.START_NOT_OBSERVED,
            margin = null,
            subScores = listOf(subScore(7L, StartEvidenceStatus.START_NOT_OBSERVED, 0f)),
        )

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session(expectedRouteId = 7L), result)

        assertEquals(AttributionEvaluationOutcome.UNRESOLVED, evaluation.outcome)
        assertEquals(7L, evaluation.expectedRouteId)
        assertNull(evaluation.predictedRouteId)
        assertEquals(AttributionStatus.UNRESOLVED, evaluation.status)
    }

    // --- Property 1: ground truth never leaks into the resolver -----------------------------

    @Test
    fun `expected route comparison never affects resolver output`() {
        val candidate = RouteCandidate(
            routeVersionId = 42L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
        )
        val timeline = HoldContactTimeline(
            listOf(
                HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 0L, 0.9f, EvidenceQuality.UNCERTAIN),
                HoldContactEvent(Limb.LEFT_HAND, 2, ContactEventType.ESTABLISHED, 100L, 0.9f, EvidenceQuality.UNCERTAIN),
            ),
        )
        val candidates = listOf(candidate)

        val computedResult = RouteAttributionEngine.attribute(
            candidates = candidates,
            holds = emptyList(),
            timeline = timeline,
            attemptStartTimestampMs = 0L,
        )
        // Sanity check the fixture is genuinely non-trivial (a real winner was actually decided),
        // so the identity/equality assertions below aren't vacuously comparing empty results.
        assertEquals(AttributionStatus.VERIFIED, computedResult.status)
        assertEquals(42L, computedResult.winningRouteVersionId)

        val sessionNoLabel = session(expectedRouteId = null, validationSessionId = "no-label")
        val sessionCorrectLabel = session(expectedRouteId = 42L, validationSessionId = "correct-label")
        val sessionWrongLabel = session(expectedRouteId = 999L, validationSessionId = "wrong-label")

        // Calling evaluate() with three different labels against the SAME already-computed result
        // instance must never mutate or replace that instance - the exact same object comes back
        // out of AttributionEvaluation.status/predictedRouteId every time.
        val evaluationNoLabel = ManualValidationAttributionEvaluator.evaluate(sessionNoLabel, computedResult)
        val evaluationCorrectLabel = ManualValidationAttributionEvaluator.evaluate(sessionCorrectLabel, computedResult)
        val evaluationWrongLabel = ManualValidationAttributionEvaluator.evaluate(sessionWrongLabel, computedResult)

        assertSame(computedResult.status, evaluationNoLabel.status)
        assertSame(computedResult.status, evaluationCorrectLabel.status)
        assertSame(computedResult.status, evaluationWrongLabel.status)
        assertEquals(computedResult.winningRouteVersionId, evaluationNoLabel.predictedRouteId)
        assertEquals(computedResult.winningRouteVersionId, evaluationCorrectLabel.predictedRouteId)
        assertEquals(computedResult.winningRouteVersionId, evaluationWrongLabel.predictedRouteId)
        // The three evaluations genuinely differ only in outcome/expectedRouteId - proving the
        // label was read, not ignored - while still agreeing on every resolver-derived field.
        assertNotEquals(evaluationNoLabel.outcome, evaluationCorrectLabel.outcome)
        assertNotEquals(evaluationCorrectLabel.outcome, evaluationWrongLabel.outcome)

        // A second, completely independent call to RouteAttributionEngine.attribute with the exact
        // same candidates/holds/timeline/attemptStartTimestampMs - made AFTER three evaluate() calls
        // that each saw a different expectedRouteId - still produces an equal result. This is the
        // proof that ground truth truly never leaks into scoring: nothing about evaluate() having
        // been called at all, with any label, changes what the engine itself computes.
        val independentSecondResult = RouteAttributionEngine.attribute(
            candidates = candidates,
            holds = emptyList(),
            timeline = timeline,
            attemptStartTimestampMs = 0L,
        )
        assertEquals(computedResult, independentSecondResult)
    }

    // --- Property 2: false VERIFIED is detected (also covered above; restated standalone) ----

    @Test
    fun `false VERIFIED is detected`() {
        val result = AttributionResult(
            winningRouteVersionId = 7L,
            status = AttributionStatus.VERIFIED,
            reasonCode = null,
            margin = 0.3f,
            subScores = listOf(subScore(7L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.8f)),
        )
        val session = session(expectedRouteId = 9L)

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session, result)

        assertEquals(AttributionEvaluationOutcome.WRONG_WINNER, evaluation.outcome)
    }
}
