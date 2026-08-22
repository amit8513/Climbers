package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishEvidenceScorerTest {

    private fun candidate(
        finishHoldIds: Set<Int> = emptySet(),
        finishPolicy: FinishPolicy? = null,
    ): RouteCandidate = RouteCandidate(
        routeVersionId = 1L,
        startHoldIds = setOf(0),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        finishHoldIds = finishHoldIds,
        finishPolicy = finishPolicy,
    )

    private fun event(
        limb: Limb,
        holdId: Int,
        type: ContactEventType = ContactEventType.ESTABLISHED,
        timestampMs: Long = 0L,
    ): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = type,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
        releaseReason = if (type == ContactEventType.RELEASED) ReleaseReason.DISTANCE_HYSTERESIS else null,
    )

    private fun timelineOf(vararg events: HoldContactEvent): HoldContactTimeline =
        HoldContactTimeline(events = events.toList())

    // --- structural unavailability ---------------------------------------------------------------

    @Test
    fun `returns null when candidate has no finish holds`() {
        val candidate = candidate(finishHoldIds = emptySet(), finishPolicy = null)
        val timeline = timelineOf(event(Limb.LEFT_HAND, holdId = 5))

        assertNull(FinishEvidenceScorer.score(candidate, timeline))
    }

    // --- ONE_HAND_ON_FINISH ------------------------------------------------------------------------

    @Test
    fun `one hand on finish scores 1 when left hand established on a finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)
        val timeline = timelineOf(event(Limb.LEFT_HAND, holdId = 10))

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `one hand on finish scores 1 when right hand established on a finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)
        val timeline = timelineOf(event(Limb.RIGHT_HAND, holdId = 10))

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `one hand on finish scores 0 when only a foot is established on the finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)
        val timeline = timelineOf(event(Limb.LEFT_FOOT, holdId = 10))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `one hand on finish scores 0 when a hand is established on a different hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)
        val timeline = timelineOf(event(Limb.LEFT_HAND, holdId = 99))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `one hand on finish scores 0 when the only finish-hold event is RELEASED not ESTABLISHED`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)
        val timeline = timelineOf(event(Limb.LEFT_HAND, holdId = 10, type = ContactEventType.RELEASED))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `one hand on finish scores 0 with an empty timeline`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH)

        assertEquals(0f, FinishEvidenceScorer.score(candidate, HoldContactTimeline()))
    }

    // --- TWO_HANDS_ON_FINISH -----------------------------------------------------------------------

    @Test
    fun `two hands on finish scores 1 when both hands established on the same finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH)
        val timeline = timelineOf(
            event(Limb.LEFT_HAND, holdId = 10, timestampMs = 100L),
            event(Limb.RIGHT_HAND, holdId = 10, timestampMs = 200L),
        )

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `two hands on finish scores 1 when each hand is established on a different finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10, 11), finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH)
        val timeline = timelineOf(
            event(Limb.LEFT_HAND, holdId = 10),
            event(Limb.RIGHT_HAND, holdId = 11),
        )

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `two hands on finish scores 0 when only the left hand is established`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH)
        val timeline = timelineOf(event(Limb.LEFT_HAND, holdId = 10))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `two hands on finish scores 0 when only the right hand is established`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH)
        val timeline = timelineOf(event(Limb.RIGHT_HAND, holdId = 10))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `two hands on finish scores 0 when both hands are established but on a non-finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH)
        val timeline = timelineOf(
            event(Limb.LEFT_HAND, holdId = 99),
            event(Limb.RIGHT_HAND, holdId = 99),
        )

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }

    // --- TOP_OUT_ZONE -------------------------------------------------------------------------------

    @Test
    fun `top out zone scores 1 for a hand established on a finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TOP_OUT_ZONE)
        val timeline = timelineOf(event(Limb.RIGHT_HAND, holdId = 10))

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `top out zone scores 1 for a foot established on a finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TOP_OUT_ZONE)
        val timeline = timelineOf(event(Limb.RIGHT_FOOT, holdId = 10))

        assertEquals(1f, FinishEvidenceScorer.score(candidate, timeline))
    }

    @Test
    fun `top out zone scores 0 when no limb is established on a finish hold`() {
        val candidate = candidate(finishHoldIds = setOf(10), finishPolicy = FinishPolicy.TOP_OUT_ZONE)
        val timeline = timelineOf(event(Limb.RIGHT_FOOT, holdId = 99))

        assertEquals(0f, FinishEvidenceScorer.score(candidate, timeline))
    }
}
