package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixture-driven coverage of [StartHoldMatcher] against hand-built [HoldContactTimeline]s - see
 * that object's own doc comment for the MATCH-vs-MISMATCH/NOT_OBSERVED distinction these tests
 * exercise. Every timeline here is constructed directly via [HoldContactEvent] (never through
 * `HoldContactDetector`), so each test controls exactly which limbs/holds/timestamps/dwells
 * [StartHoldMatcher] sees, independent of any real pose-tracking behavior.
 */
class StartHoldMatcherTest {

    private val config = RouteAttributionScoringConfig()

    private fun established(limb: Limb, holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    private fun released(limb: Limb, holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.RELEASED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    private fun candidateOf(routeVersionId: Long, startHoldIds: Set<Int>, startPolicy: StartPolicy): RouteCandidate =
        RouteCandidate(routeVersionId = routeVersionId, startHoldIds = startHoldIds, startPolicy = startPolicy)

    // 1. SINGLE_HOLD_ANY_HAND

    @Test
    fun `single hold any hand matches on an unreleased establishment by either hand`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)

        val leftTimeline = HoldContactTimeline(listOf(established(Limb.LEFT_HAND, 10, 0L)))
        assertEquals(
            StartEvidenceStatus.START_OBSERVED_MATCH,
            StartHoldMatcher.evaluate(candidate, listOf(candidate), leftTimeline, 0L, config),
        )

        val rightTimeline = HoldContactTimeline(listOf(established(Limb.RIGHT_HAND, 10, 0L)))
        assertEquals(
            StartEvidenceStatus.START_OBSERVED_MATCH,
            StartHoldMatcher.evaluate(candidate, listOf(candidate), rightTimeline, 0L, config),
        )
    }

    @Test
    fun `single hold any hand matches when the release comes comfortably after the dwell threshold`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.RIGHT_HAND, 10, 0L),
                released(Limb.RIGHT_HAND, 10, config.startEstablishmentDwellMs * 5),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, result)
    }

    // 2. TWO_HOLDS_ONE_PER_HAND

    @Test
    fun `two holds one per hand matches only when left and right qualify on different holds`() {
        val candidate = candidateOf(1L, setOf(10, 11), StartPolicy.TWO_HOLDS_ONE_PER_HAND)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.LEFT_HAND, 10, 0L),
                established(Limb.RIGHT_HAND, 11, 0L),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, result)
    }

    @Test
    fun `two holds one per hand does not match when only one hand qualifies`() {
        val candidate = candidateOf(1L, setOf(10, 11), StartPolicy.TWO_HOLDS_ONE_PER_HAND)
        val timeline = HoldContactTimeline(listOf(established(Limb.LEFT_HAND, 10, 0L)))

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MISMATCH, result)
    }

    @Test
    fun `two holds one per hand does not match when both hands qualify on the same hold`() {
        val candidate = candidateOf(1L, setOf(10, 11), StartPolicy.TWO_HOLDS_ONE_PER_HAND)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.LEFT_HAND, 10, 0L),
                established(Limb.RIGHT_HAND, 10, 0L),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MISMATCH, result)
    }

    // 3. TWO_HANDS_SAME_HOLD

    @Test
    fun `two hands same hold matches when both hands qualify on the same hold`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.TWO_HANDS_SAME_HOLD)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.LEFT_HAND, 10, 0L),
                established(Limb.RIGHT_HAND, 10, 100L),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, result)
    }

    @Test
    fun `two hands same hold does not match when both hands qualify but on different holds`() {
        val candidate = candidateOf(1L, setOf(10, 11), StartPolicy.TWO_HANDS_SAME_HOLD)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.LEFT_HAND, 10, 0L),
                established(Limb.RIGHT_HAND, 11, 0L),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MISMATCH, result)
    }

    // 4. Dwell too short

    @Test
    fun `an establishment whose dwell is too short does not qualify`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(
            listOf(
                established(Limb.LEFT_HAND, 10, 0L),
                released(Limb.LEFT_HAND, 10, config.startEstablishmentDwellMs - 1),
            ),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, result)
    }

    // 5. Outside the observation window

    @Test
    fun `an establishment before the observation window start does not qualify`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(listOf(established(Limb.LEFT_HAND, 10, 999L)))

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 1000L, config)

        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, result)
    }

    @Test
    fun `an establishment after the observation window end does not qualify`() {
        val candidate = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(
            listOf(established(Limb.LEFT_HAND, 10, 1000L + config.startObservationWindowMs + 1)),
        )

        val result = StartHoldMatcher.evaluate(candidate, listOf(candidate), timeline, 1000L, config)

        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, result)
    }

    // 6. Nothing observed on either candidate

    @Test
    fun `neither candidate is observed when nothing qualifying happens on either one's start holds`() {
        val candidateA = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val candidateB = candidateOf(2L, setOf(20), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(listOf(established(Limb.LEFT_HAND, 30, 0L)))

        val resultA = StartHoldMatcher.evaluate(candidateA, listOf(candidateA, candidateB), timeline, 0L, config)
        val resultB = StartHoldMatcher.evaluate(candidateB, listOf(candidateA, candidateB), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, resultA)
        assertEquals(StartEvidenceStatus.START_NOT_OBSERVED, resultB)
    }

    // 7. Qualifying establishment on B's own holds only

    @Test
    fun `candidate A is a mismatch and candidate B is a match when only B's start holds are observed`() {
        val candidateA = candidateOf(1L, setOf(10), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val candidateB = candidateOf(2L, setOf(20), StartPolicy.SINGLE_HOLD_ANY_HAND)
        val timeline = HoldContactTimeline(listOf(established(Limb.LEFT_HAND, 20, 0L)))

        val resultA = StartHoldMatcher.evaluate(candidateA, listOf(candidateA, candidateB), timeline, 0L, config)
        val resultB = StartHoldMatcher.evaluate(candidateB, listOf(candidateA, candidateB), timeline, 0L, config)

        assertEquals(StartEvidenceStatus.START_OBSERVED_MISMATCH, resultA)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, resultB)
    }
}
