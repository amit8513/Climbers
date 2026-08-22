package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.analysis.contact.Limb
import com.example.climb.attribution.ForeignContactEventClassifier
import com.example.climb.attribution.ForeignContactPenaltyCalculator
import com.example.climb.attribution.RouteAttributionEngine
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.RouteCandidate
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the two consistency guarantees [AttributionDebugDetails.kt]'s doc comment promises:
 * [normalizedWeightsUsed]'s output must always sum back to the engine's real total configured
 * weight budget (never a smaller one just because a signal was unavailable), and
 * [foreignContactEvents] must always agree in size with the real
 * [ForeignContactPenaltyCalculator.uniqueForeignEventCount] the engine actually scored against.
 * Basic filter correctness for the remaining helpers is covered too.
 */
class AttributionDebugDetailsTest {

    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.05f): HoldShape = HoldShape(
        holdId = id,
        contourNormalized = listOf(
            Point2D(centerX - halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY + halfWidth),
            Point2D(centerX - halfWidth, centerY + halfWidth),
        ),
    )

    private fun cleanEstablish(limb: Limb, holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.UNCERTAIN,
    )

    private fun foreignEstablish(
        limb: Limb,
        holdId: Int,
        timestampMs: Long,
        confidence: Float = 0.9f,
        evidenceQuality: EvidenceQuality = EvidenceQuality.STRONG,
    ): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = confidence,
        evidenceQuality = evidenceQuality,
    )

    // --- normalizedWeightsUsed: weight-budget conservation -----------------------------------

    @Test
    fun `normalized weights sum to the total configured weight when every signal is available`() {
        val config = RouteAttributionScoringConfig()
        val subScore = SubScoreResult(
            routeVersionId = 1L,
            startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MATCH,
            contactCoverageScore = 0.5f,
            corridorScore = 0.5f,
            finishScore = 0.5f,
            foreignContactEventCount = 0,
            foreignContactPenalty = 0f,
            combinedScore = 0.5f,
        )

        val weights = normalizedWeightsUsed(subScore, config)
        val sum = weights.startHoldWeight + weights.contactCoverageWeight + weights.corridorWeight + weights.finishWeight
        val totalConfigured = config.startHoldWeight + config.contactCoverageWeight + config.corridorWeight + config.finishWeight

        assertEquals(totalConfigured, sum, 0.0001f)
    }

    @Test
    fun `normalized weights still sum to the full total configured weight when corridor and finish are unavailable`() {
        val config = RouteAttributionScoringConfig()
        val subScore = SubScoreResult(
            routeVersionId = 1L,
            startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MATCH,
            contactCoverageScore = 0.5f,
            corridorScore = null,
            finishScore = null,
            foreignContactEventCount = 0,
            foreignContactPenalty = 0f,
            combinedScore = 0.5f,
        )

        val weights = normalizedWeightsUsed(subScore, config)
        val sum = weights.startHoldWeight + weights.contactCoverageWeight + weights.corridorWeight + weights.finishWeight
        val totalConfigured = config.startHoldWeight + config.contactCoverageWeight + config.corridorWeight + config.finishWeight

        // A candidate missing optional signals must reach the SAME weight budget as one with every
        // signal present - never a smaller one (that's the whole point of renormalization).
        assertEquals(totalConfigured, sum, 0.0001f)
        assertEquals(0f, weights.corridorWeight)
        assertEquals(0f, weights.finishWeight)
    }

    // --- normalizedWeightsUsed: reconstructs the real engine's combinedScore -----------------

    @Test
    fun `derived weights applied by hand reconstruct combinedScore for a candidate with every signal available`() {
        val config = RouteAttributionScoringConfig()
        val candidate = RouteCandidate(
            routeVersionId = 42L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
            finishHoldIds = setOf(3),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val holds = listOf(squareHold(1, 0.1f, 0.1f), squareHold(2, 0.5f, 0.5f), squareHold(3, 0.9f, 0.9f))
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidate), holds, timeline, attemptStartTimestampMs = 0L, config = config)
        val subScore = result.subScores.single()
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScore.startEvidenceStatus)

        assertReconstructsCombinedScore(subScore, config)
    }

    @Test
    fun `derived weights applied by hand reconstruct combinedScore for a candidate missing corridor and finish signals`() {
        val config = RouteAttributionScoringConfig()
        val candidateP = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
            finishHoldIds = setOf(3),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val candidateQ = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(11),
        )
        val holds = listOf(squareHold(1, 0.1f, 0.1f), squareHold(2, 0.5f, 0.5f), squareHold(3, 0.9f, 0.9f))
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.RIGHT_HAND, 10, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 11, 100L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidateP, candidateQ), holds, timeline, attemptStartTimestampMs = 0L, config = config)
        val subScoreQ = result.subScores.single { it.routeVersionId == 2L }
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScoreQ.startEvidenceStatus)
        assertNull(subScoreQ.corridorScore)
        assertNull(subScoreQ.finishScore)

        assertReconstructsCombinedScore(subScoreQ, config)
    }

    /** Reconstructs [SubScoreResult.combinedScore] purely from [normalizedWeightsUsed]'s output
     * applied to [subScore]'s own public fields, mirroring exactly what [RouteAttributionEngine]
     * itself computes - proving this display helper's numbers genuinely agree with the real
     * scoring math, not just "look plausible". */
    private fun assertReconstructsCombinedScore(subScore: SubScoreResult, config: RouteAttributionScoringConfig) {
        val weights = normalizedWeightsUsed(subScore, config)
        val startComponentScore = if (subScore.startEvidenceStatus == StartEvidenceStatus.START_OBSERVED_MATCH) 1f else 0f
        val positiveWeightedSum = weights.startHoldWeight * startComponentScore +
            weights.contactCoverageWeight * subScore.contactCoverageScore +
            weights.corridorWeight * (subScore.corridorScore ?: 0f) +
            weights.finishWeight * (subScore.finishScore ?: 0f)
        val reconstructed = (positiveWeightedSum - subScore.foreignContactPenalty).coerceIn(0f, 1f)

        assertEquals(subScore.combinedScore, reconstructed, 0.001f)
    }

    // --- foreignContactEvents: must always agree with ForeignContactPenaltyCalculator --------

    private fun candidate(routeVersionId: Long, startHoldIds: Set<Int>, bodyHoldIds: Set<Int> = emptySet()) = RouteCandidate(
        routeVersionId = routeVersionId,
        startHoldIds = startHoldIds,
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        bodyHoldIds = bodyHoldIds,
    )

    private fun assertForeignContactEventsAgreesWithCalculator(
        candidate: RouteCandidate,
        allCandidates: List<RouteCandidate>,
        timeline: HoldContactTimeline,
        config: RouteAttributionScoringConfig = RouteAttributionScoringConfig(),
    ) {
        val displayedEvents = foreignContactEvents(candidate, allCandidates, timeline, config)
        val realCount = ForeignContactPenaltyCalculator.uniqueForeignEventCount(candidate, allCandidates, timeline, config)
        assertEquals(realCount, displayedEvents.size)
    }

    @Test
    fun `foreignContactEvents agrees with the real calculator for a single qualifying foreign event`() {
        val ours = candidate(1L, setOf(1))
        val theirs = candidate(2L, setOf(2))
        val timeline = HoldContactTimeline(listOf(foreignEstablish(Limb.LEFT_HAND, 2, 0L)))

        assertForeignContactEventsAgreesWithCalculator(ours, listOf(ours, theirs), timeline)
    }

    @Test
    fun `foreignContactEvents agrees with the real calculator when events are excluded by uncertain quality`() {
        val ours = candidate(1L, setOf(1))
        val theirs = candidate(2L, setOf(2))
        val timeline = HoldContactTimeline(
            listOf(
                foreignEstablish(Limb.LEFT_HAND, 2, 0L, evidenceQuality = EvidenceQuality.UNCERTAIN),
                foreignEstablish(Limb.RIGHT_HAND, 2, 100L, evidenceQuality = EvidenceQuality.STRONG),
            ),
        )

        assertForeignContactEventsAgreesWithCalculator(ours, listOf(ours, theirs), timeline)
        assertEquals(1, foreignContactEvents(ours, listOf(ours, theirs), timeline, RouteAttributionScoringConfig()).size)
    }

    @Test
    fun `foreignContactEvents agrees with the real calculator when events are excluded by low confidence`() {
        val config = RouteAttributionScoringConfig()
        val ours = candidate(1L, setOf(1))
        val theirs = candidate(2L, setOf(2))
        val timeline = HoldContactTimeline(
            listOf(
                foreignEstablish(Limb.LEFT_HAND, 2, 0L, confidence = config.minLimbLandmarkConfidence - 0.01f),
                foreignEstablish(Limb.RIGHT_HAND, 2, 100L, confidence = config.minLimbLandmarkConfidence),
            ),
        )

        assertForeignContactEventsAgreesWithCalculator(ours, listOf(ours, theirs), timeline, config)
        assertEquals(1, foreignContactEvents(ours, listOf(ours, theirs), timeline, config).size)
    }

    @Test
    fun `foreignContactEvents agrees with the real calculator when a hold is shared between candidates`() {
        val ours = candidate(1L, setOf(1), bodyHoldIds = setOf(5))
        val theirs = candidate(2L, setOf(2), bodyHoldIds = setOf(5))
        val timeline = HoldContactTimeline(listOf(foreignEstablish(Limb.LEFT_HAND, 5, 0L)))

        assertForeignContactEventsAgreesWithCalculator(ours, listOf(ours, theirs), timeline)
        assertTrue(foreignContactEvents(ours, listOf(ours, theirs), timeline, RouteAttributionScoringConfig()).isEmpty())
    }

    @Test
    fun `foreignContactEvents agrees with the real calculator across several candidates and mixed qualifying events`() {
        val config = RouteAttributionScoringConfig()
        val ours = candidate(1L, setOf(1))
        val second = candidate(2L, setOf(2), bodyHoldIds = setOf(9))
        val third = candidate(3L, setOf(3), bodyHoldIds = setOf(9))
        val timeline = HoldContactTimeline(
            listOf(
                foreignEstablish(Limb.LEFT_HAND, 2, 0L),
                foreignEstablish(Limb.RIGHT_HAND, 9, 100L),
                foreignEstablish(Limb.LEFT_FOOT, 3, 200L, evidenceQuality = EvidenceQuality.UNCERTAIN),
                foreignEstablish(Limb.RIGHT_FOOT, 2, 300L, confidence = config.minLimbLandmarkConfidence - 0.1f),
            ),
        )

        assertForeignContactEventsAgreesWithCalculator(ours, listOf(ours, second, third), timeline, config)
        // Sanity: this fixture genuinely exercises multiple exclusion reasons, not a vacuous case.
        assertEquals(2, foreignContactEvents(ours, listOf(ours, second, third), timeline, config).size)
    }

    @Test
    fun `foreignContactEvents delegates to the shared ForeignContactEventClassifier - same list, not just same size`() {
        val config = RouteAttributionScoringConfig()

        val ours = candidate(1L, setOf(1))
        val theirs = candidate(2L, setOf(2))
        val timeline = HoldContactTimeline(listOf(foreignEstablish(Limb.LEFT_HAND, 2, 0L)))
        assertEquals(
            ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config),
            foreignContactEvents(ours, listOf(ours, theirs), timeline, config),
        )

        val second = candidate(2L, setOf(2), bodyHoldIds = setOf(9))
        val third = candidate(3L, setOf(3), bodyHoldIds = setOf(9))
        val mixedTimeline = HoldContactTimeline(
            listOf(
                foreignEstablish(Limb.LEFT_HAND, 2, 0L),
                foreignEstablish(Limb.RIGHT_HAND, 9, 100L),
                foreignEstablish(Limb.LEFT_FOOT, 3, 200L, evidenceQuality = EvidenceQuality.UNCERTAIN),
                foreignEstablish(Limb.RIGHT_FOOT, 2, 300L, confidence = config.minLimbLandmarkConfidence - 0.1f),
            ),
        )
        assertEquals(
            ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, second, third), mixedTimeline, config),
            foreignContactEvents(ours, listOf(ours, second, third), mixedTimeline, config),
        )
    }

    // --- startHoldEventsFor / finishHoldEventsFor --------------------------------------------

    @Test
    fun `startHoldEventsFor returns only events on the candidate's own start holds`() {
        val candidate = candidate(1L, startHoldIds = setOf(1, 2))
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_FOOT, 3, 200L),
            ),
        )

        val events = startHoldEventsFor(candidate, timeline)

        assertEquals(2, events.size)
        assertTrue(events.all { it.holdId in setOf(1, 2) })
    }

    @Test
    fun `finishHoldEventsFor returns only events on the candidate's own finish holds`() {
        val candidate = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            finishHoldIds = setOf(9),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
        )
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 9, 100L),
            ),
        )

        val events = finishHoldEventsFor(candidate, timeline)

        assertEquals(1, events.size)
        assertEquals(9, events.single().holdId)
    }

    @Test
    fun `finishHoldEventsFor returns empty when the candidate has no finish holds defined`() {
        val candidate = candidate(1L, startHoldIds = setOf(1))
        val timeline = HoldContactTimeline(listOf(cleanEstablish(Limb.LEFT_HAND, 1, 0L)))

        assertTrue(finishHoldEventsFor(candidate, timeline).isEmpty())
    }

    // --- eventsNearTimestamp ------------------------------------------------------------------

    @Test
    fun `eventsNearTimestamp includes events exactly at the window boundary on both sides`() {
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 400L),
                cleanEstablish(Limb.LEFT_HAND, 2, 500L),
                cleanEstablish(Limb.LEFT_HAND, 3, 600L),
                cleanEstablish(Limb.LEFT_HAND, 4, 399L),
                cleanEstablish(Limb.LEFT_HAND, 5, 601L),
            ),
        )

        val events = eventsNearTimestamp(timeline, timestampMs = 500L, windowMs = 100L)

        assertEquals(setOf(1, 2, 3), events.map { it.holdId }.toSet())
    }

    @Test
    fun `eventsNearTimestamp excludes events strictly outside the window`() {
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 10_000L),
            ),
        )

        val events = eventsNearTimestamp(timeline, timestampMs = 0L, windowMs = 50L)

        assertEquals(listOf(1), events.map { it.holdId })
    }

    // --- secondPlaceCandidate -----------------------------------------------------------------

    @Test
    fun `secondPlaceCandidate returns the best eligible non-winner when a winner exists`() {
        val winner = SubScoreResult(1L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.9f, null, null, 0, 0f, 0.9f)
        val runnerUp = SubScoreResult(2L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.7f, null, null, 0, 0f, 0.7f)
        val worse = SubScoreResult(3L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.5f, null, null, 0, 0f, 0.5f)
        val ineligible = SubScoreResult(4L, StartEvidenceStatus.START_NOT_OBSERVED, 0.99f, null, null, 0, 0f, 0.99f)
        val result = com.example.climb.attribution.AttributionResult(
            winningRouteVersionId = 1L,
            status = com.example.climb.clubs.AttributionStatus.VERIFIED,
            reasonCode = null,
            margin = 0.2f,
            subScores = listOf(winner, runnerUp, worse, ineligible),
        )

        val secondPlace = secondPlaceCandidate(result)

        assertEquals(2L, secondPlace?.routeVersionId)
    }

    @Test
    fun `secondPlaceCandidate falls back to the best eligible candidate when there is no winner`() {
        val best = SubScoreResult(1L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.6f, null, null, 0, 0f, 0.6f)
        val second = SubScoreResult(2L, StartEvidenceStatus.START_OBSERVED_MATCH, 0.55f, null, null, 0, 0f, 0.55f)
        val result = com.example.climb.attribution.AttributionResult(
            winningRouteVersionId = null,
            status = com.example.climb.clubs.AttributionStatus.REVIEW_REQUIRED,
            reasonCode = com.example.climb.clubs.AttributionReasonCode.MARGIN_TOO_SMALL,
            margin = 0.05f,
            subScores = listOf(best, second),
        )

        val secondPlace = secondPlaceCandidate(result)

        // No winningRouteVersionId is recorded for REVIEW_REQUIRED, so every eligible candidate is
        // treated as a runner-up - the highest-scoring eligible candidate is returned.
        assertEquals(1L, secondPlace?.routeVersionId)
    }

    @Test
    fun `secondPlaceCandidate returns null when no candidate is eligible`() {
        val ineligible = SubScoreResult(1L, StartEvidenceStatus.START_NOT_OBSERVED, 0.9f, null, null, 0, 0f, 0.9f)
        val result = com.example.climb.attribution.AttributionResult(
            winningRouteVersionId = null,
            status = com.example.climb.clubs.AttributionStatus.UNRESOLVED,
            reasonCode = com.example.climb.clubs.AttributionReasonCode.START_NOT_OBSERVED,
            margin = null,
            subScores = listOf(ineligible),
        )

        assertNull(secondPlaceCandidate(result))
    }
}
