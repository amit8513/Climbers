package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two gates [RouteAttributionEngine] is built around (hard start-eligibility, then graded
 * confidence) are exactly the two places a scoring engine like this is most likely to quietly
 * break under refactoring — a high raw score leaking through the eligibility gate, or a start-hold
 * match on the WRONG candidate being treated as good enough. Every adversarial fixture below is
 * built by hand (direct [HoldContactEvent] lists) specifically to make those two failure modes
 * independently observable: each test pins down one gate/renormalization/penalty property against
 * the actual [RouteAttributionScoringConfig] fields that govern it, rather than against a guessed
 * magic number.
 */
class RouteAttributionEngineTest {

    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.05f): HoldShape = HoldShape(
        holdId = id,
        contourNormalized = listOf(
            Point2D(centerX - halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY + halfWidth),
            Point2D(centerX - halfWidth, centerY + halfWidth),
        ),
    )

    /** An ESTABLISHED event that fully satisfies start/coverage/corridor/finish evidence (none of
     * which ever look at [HoldContactEvent.confidence]/[HoldContactEvent.evidenceQuality]) while
     * being deliberately invisible to [ForeignContactPenaltyCalculator] (which requires
     * `evidenceQuality != UNCERTAIN`). This is what lets a fixture give TWO competing candidates
     * their own independent evidence on the SAME shared timeline without one candidate's own
     * contact accidentally reading as a foreign-contact penalty against the other - that
     * cross-contamination is a real property of the shared-timeline design, not a bug, but most of
     * the scenarios below need to isolate one variable at a time rather than exercise it. */
    private fun cleanEstablish(limb: Limb, holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.UNCERTAIN,
    )

    /** A confident, fully-established ESTABLISHED event that DOES count as a foreign-contact event
     * against any candidate that doesn't own [holdId] - see [ForeignContactPenaltyCalculator]. */
    private fun foreignEstablish(limb: Limb, holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    private fun subScoreFor(result: AttributionResult, routeVersionId: Long): SubScoreResult =
        result.subScores.single { it.routeVersionId == routeVersionId }

    // --- Ordinary coverage -----------------------------------------------------------------

    @Test
    fun `empty candidate list is unresolved with no candidates reason and no sub-scores`() {
        val result = RouteAttributionEngine.attribute(
            candidates = emptyList(),
            holds = emptyList(),
            timeline = HoldContactTimeline(),
            attemptStartTimestampMs = 0L,
            config = RouteAttributionScoringConfig(),
        )

        assertEquals(AttributionStatus.UNRESOLVED, result.status)
        assertEquals(AttributionReasonCode.NO_CANDIDATES, result.reasonCode)
        assertNull(result.winningRouteVersionId)
        assertNull(result.margin)
        assertTrue(result.subScores.isEmpty())
    }

    @Test
    fun `clean single candidate with perfect evidence reaches VERIFIED end to end`() {
        val candidate = RouteCandidate(
            routeVersionId = 42L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
            finishHoldIds = setOf(3),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val holds = listOf(
            squareHold(1, 0.1f, 0.1f),
            squareHold(2, 0.5f, 0.5f),
            squareHold(3, 0.9f, 0.9f),
        )
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
            ),
        )
        val config = RouteAttributionScoringConfig()

        val result = RouteAttributionEngine.attribute(listOf(candidate), holds, timeline, attemptStartTimestampMs = 0L, config = config)

        assertEquals(AttributionStatus.VERIFIED, result.status)
        assertEquals(42L, result.winningRouteVersionId)
        assertNull(result.reasonCode)
        val subScore = result.subScores.single()
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScore.startEvidenceStatus)
        assertTrue(subScore.combinedScore >= config.verifiedMinScore)
    }

    // --- Adversarial scenario 1 -------------------------------------------------------------

    @Test
    fun `high coverage but no observed start never becomes VERIFIED`() {
        val candidate = RouteCandidate(
            routeVersionId = 7L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2, 3),
            finishHoldIds = setOf(4),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val holds = listOf(
            squareHold(1, 0.1f, 0.1f),
            squareHold(2, 0.3f, 0.3f),
            squareHold(3, 0.5f, 0.5f),
            squareHold(4, 0.7f, 0.7f),
        )
        val config = RouteAttributionScoringConfig()
        // Start hold 1 IS established somewhere in the full timeline (so ContactCoverageScorer,
        // which never looks at the observation window, counts it) - but strictly after
        // config.startObservationWindowMs from the attempt start, so StartHoldMatcher's own
        // window-bounded qualifying-establishment test never sees it.
        val windowEndMs = config.startObservationWindowMs
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, windowEndMs + 6000L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.LEFT_HAND, 4, 300L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidate), holds, timeline, attemptStartTimestampMs = 0L, config = config)

        val subScore = result.subScores.single()
        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, subScore.startEvidenceStatus)
        assertEquals("raw contact coverage is high despite the missing start evidence", 1f, subScore.contactCoverageScore)
        assertEquals("raw corridor score is high despite the missing start evidence", 1f, subScore.corridorScore)
        assertEquals("raw finish score is high despite the missing start evidence", 1f, subScore.finishScore)

        assertTrue(result.status != AttributionStatus.VERIFIED)
        assertEquals(AttributionStatus.UNRESOLVED, result.status)
        assertEquals(AttributionReasonCode.START_NOT_OBSERVED, result.reasonCode)
        assertNull(result.winningRouteVersionId)
    }

    // --- Adversarial scenario 2 -------------------------------------------------------------

    @Test
    fun `start mismatch on one candidate never lets it win regardless of its other scores`() {
        val candidateA = RouteCandidate(
            routeVersionId = 100L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2, 3),
            finishHoldIds = setOf(4),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val candidateB = RouteCandidate(
            routeVersionId = 200L,
            startHoldIds = setOf(20),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(21),
        )
        val holds = listOf(
            squareHold(1, 0.1f, 0.1f),
            squareHold(2, 0.3f, 0.3f),
            squareHold(3, 0.5f, 0.5f),
            squareHold(4, 0.7f, 0.7f),
        )
        val config = RouteAttributionScoringConfig()
        val windowEndMs = config.startObservationWindowMs
        val timeline = HoldContactTimeline(
            listOf(
                // A's own start hold IS established, but only well outside the observation window -
                // never a qualifying establishment for A's own start policy.
                cleanEstablish(Limb.LEFT_HAND, 1, windowEndMs + 6000L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.LEFT_HAND, 4, 300L),
                // B's own start hold IS qualifyingly established within the window - this is what
                // makes A's status MISMATCH rather than NOT_OBSERVED.
                cleanEstablish(Limb.RIGHT_HAND, 20, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 21, 100L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidateA, candidateB), holds, timeline, attemptStartTimestampMs = 0L, config = config)

        val subScoreA = subScoreFor(result, 100L)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MISMATCH, subScoreA.startEvidenceStatus)
        // A's other evidence is deliberately as strong as possible, to prove a buggy engine that let
        // score leak through the eligibility gate would have picked A.
        assertEquals(1f, subScoreA.contactCoverageScore)
        assertEquals(1f, subScoreA.corridorScore)
        assertEquals(1f, subScoreA.finishScore)

        assertTrue(result.winningRouteVersionId != 100L)
    }

    // --- Adversarial scenario 3 -------------------------------------------------------------

    @Test
    fun `close winner margin between two eligible candidates triggers REVIEW_REQUIRED`() {
        val candidate1 = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2, 3, 4),
        )
        val candidate2 = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(11, 12, 13, 14),
        )
        val config = RouteAttributionScoringConfig()
        val timeline = HoldContactTimeline(
            listOf(
                // Candidate 1: perfect coverage (4/4 own holds established).
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.LEFT_HAND, 4, 300L),
                // Candidate 2: qualifying start, but only 3/5 own holds established.
                cleanEstablish(Limb.RIGHT_HAND, 10, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 11, 100L),
                cleanEstablish(Limb.RIGHT_HAND, 12, 200L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidate1, candidate2), emptyList(), timeline, attemptStartTimestampMs = 0L, config = config)

        val subScore1 = subScoreFor(result, 1L)
        val subScore2 = subScoreFor(result, 2L)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScore1.startEvidenceStatus)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScore2.startEvidenceStatus)
        // The higher-scoring candidate genuinely clears verifiedMinScore on its own - it is the
        // MARGIN, not a low winning score, that must be the reason this isn't VERIFIED.
        assertTrue(subScore1.combinedScore >= config.verifiedMinScore)

        assertNotNull(result.margin)
        assertTrue("the actual computed margin must be below config.minWinnerMargin", result.margin!! < config.minWinnerMargin)
        assertEquals(AttributionStatus.REVIEW_REQUIRED, result.status)
        assertNull(result.winningRouteVersionId)
        assertEquals(AttributionReasonCode.MARGIN_TOO_SMALL, result.reasonCode)
    }

    // --- Adversarial scenario 4 -------------------------------------------------------------

    @Test
    fun `missing optional corridor and finish signals do not lower a candidate's score`() {
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
        val holds = listOf(
            squareHold(1, 0.1f, 0.1f),
            squareHold(2, 0.5f, 0.5f),
            squareHold(3, 0.9f, 0.9f),
        )
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.RIGHT_HAND, 10, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 11, 100L),
            ),
        )
        val config = RouteAttributionScoringConfig()

        val result = RouteAttributionEngine.attribute(listOf(candidateP, candidateQ), holds, timeline, attemptStartTimestampMs = 0L, config = config)

        val subScoreP = subScoreFor(result, 1L)
        val subScoreQ = subScoreFor(result, 2L)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScoreP.startEvidenceStatus)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, subScoreQ.startEvidenceStatus)
        assertEquals(1f, subScoreP.contactCoverageScore)
        assertEquals(1f, subScoreQ.contactCoverageScore)
        assertNotNull(subScoreP.corridorScore)
        assertNotNull(subScoreP.finishScore)
        assertNull("Q has no corridor defined at all", subScoreQ.corridorScore)
        assertNull("Q has no finish evidence defined at all", subScoreQ.finishScore)

        assertEquals(
            "renormalizing the available weight budget must make Q's score equal to P's, not lower",
            subScoreP.combinedScore,
            subScoreQ.combinedScore,
            0.001f,
        )
    }

    // --- Adversarial scenario 5 -------------------------------------------------------------

    @Test
    fun `foreign contact penalty counts unique events not frame or sample count`() {
        val candidateA = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
        )
        val candidateB = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(11, 12, 13),
        )
        val config = RouteAttributionScoringConfig()

        val ownEvents = listOf(
            cleanEstablish(Limb.LEFT_HAND, 1, 0L),
            cleanEstablish(Limb.LEFT_HAND, 2, 100L),
        )

        val timelineWithOneForeignEvent = HoldContactTimeline(
            ownEvents + listOf(foreignEstablish(Limb.RIGHT_HAND, 10, 5000L)),
        )
        val timelineWithFourForeignEvents = HoldContactTimeline(
            ownEvents + listOf(
                foreignEstablish(Limb.RIGHT_HAND, 10, 5000L),
                foreignEstablish(Limb.RIGHT_HAND, 11, 5100L),
                foreignEstablish(Limb.RIGHT_HAND, 12, 5200L),
                foreignEstablish(Limb.RIGHT_HAND, 13, 5300L),
            ),
        )

        val resultWithOne = RouteAttributionEngine.attribute(
            listOf(candidateA, candidateB), emptyList(), timelineWithOneForeignEvent, attemptStartTimestampMs = 0L, config = config,
        )
        val resultWithFour = RouteAttributionEngine.attribute(
            listOf(candidateA, candidateB), emptyList(), timelineWithFourForeignEvents, attemptStartTimestampMs = 0L, config = config,
        )

        val subScoreOne = subScoreFor(resultWithOne, 1L)
        val subScoreFour = subScoreFor(resultWithFour, 1L)

        assertEquals(1, subScoreOne.foreignContactEventCount)
        assertEquals(4, subScoreFour.foreignContactEventCount)
        assertTrue(
            "more foreign contact events must actually reduce the score further",
            subScoreFour.combinedScore < subScoreOne.combinedScore,
        )
    }

    // --- Adversarial scenario 6 -------------------------------------------------------------

    @Test
    fun `combined score is always clamped to the 0 to 1 range even with extreme weights`() {
        val candidateA = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
            finishHoldIds = setOf(3),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        )
        val candidateB = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(11),
        )
        val holds = listOf(
            squareHold(1, 0.1f, 0.1f),
            squareHold(2, 0.5f, 0.5f),
            squareHold(3, 0.9f, 0.9f),
        )
        // Deliberately extreme: a positive-weight budget of 20 and a maximum foreign penalty of 50
        // - either one, alone, would push the raw pre-clamp arithmetic outside [0,1].
        val extremeConfig = RouteAttributionScoringConfig(
            startHoldWeight = 5f,
            contactCoverageWeight = 5f,
            corridorWeight = 5f,
            finishWeight = 5f,
            foreignContactPenaltyWeight = 50f,
            foreignContactPenaltyPerEvent = 0.5f,
            verifiedMinScore = 0.75f,
            reviewMinScore = 0.55f,
            minWinnerMargin = 0.15f,
        )
        val timeline = HoldContactTimeline(
            listOf(
                // Candidate A: every signal perfect, zero foreign contact - would score 20 raw
                // without the clamp.
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                // Candidate B: zero of its own evidence, but several confident foreign events on
                // A's holds - would score -50 raw without the clamp.
                foreignEstablish(Limb.RIGHT_HAND, 1, 9000L),
                foreignEstablish(Limb.LEFT_HAND, 1, 9100L),
                foreignEstablish(Limb.LEFT_FOOT, 1, 9200L),
            ),
        )

        val result = RouteAttributionEngine.attribute(listOf(candidateA, candidateB), holds, timeline, attemptStartTimestampMs = 0L, config = extremeConfig)

        val subScoreA = subScoreFor(result, 1L)
        val subScoreB = subScoreFor(result, 2L)
        assertEquals("clamped down from a raw value far above 1", 1f, subScoreA.combinedScore)
        assertEquals("clamped up from a raw value far below 0", 0f, subScoreB.combinedScore)

        result.subScores.forEach { subScore ->
            assertTrue("combinedScore must never be below 0: was ${subScore.combinedScore}", subScore.combinedScore >= 0f)
            assertTrue("combinedScore must never be above 1: was ${subScore.combinedScore}", subScore.combinedScore <= 1f)
        }
    }

    // --- Adversarial scenario 7 -------------------------------------------------------------

    @Test
    fun `repeated calls with identical inputs produce identical results`() {
        val candidateA = RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = setOf(1),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(2),
            finishHoldIds = setOf(3),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
        )
        val candidateB = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(11),
        )
        val timeline = HoldContactTimeline(
            listOf(
                cleanEstablish(Limb.LEFT_HAND, 1, 0L),
                cleanEstablish(Limb.LEFT_HAND, 2, 100L),
                cleanEstablish(Limb.LEFT_HAND, 3, 200L),
                cleanEstablish(Limb.RIGHT_HAND, 10, 0L),
                cleanEstablish(Limb.RIGHT_HAND, 11, 100L),
                // At least one real foreign contact, so this fixture is genuinely non-trivial.
                foreignEstablish(Limb.LEFT_FOOT, 10, 5000L),
            ),
        )
        val candidates = listOf(candidateA, candidateB)
        val config = RouteAttributionScoringConfig()

        val firstResult = RouteAttributionEngine.attribute(candidates, emptyList(), timeline, attemptStartTimestampMs = 0L, config = config)
        val secondResult = RouteAttributionEngine.attribute(candidates, emptyList(), timeline, attemptStartTimestampMs = 0L, config = config)
        val thirdResult = RouteAttributionEngine.attribute(candidates, emptyList(), timeline, attemptStartTimestampMs = 0L, config = config)

        // Sanity check the fixture actually exercised a foreign contact, so the equality checks
        // below aren't vacuously comparing trivially-empty results.
        assertTrue(subScoreFor(firstResult, 1L).foreignContactEventCount > 0)

        assertEquals(firstResult, secondResult)
        assertEquals(secondResult, thirdResult)
        assertEquals(firstResult, thirdResult)
    }
}
