package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactCoverageScorerTest {

    private fun established(limb: Limb, holdId: Int, timestampMs: Long = 0L): HoldContactEvent = HoldContactEvent(
        limb = limb,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    private val candidate = RouteCandidate(
        routeVersionId = 1L,
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        bodyHoldIds = setOf(2, 3),
        finishHoldIds = setOf(4),
        finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
    )

    @Test
    fun `zero holds touched scores zero`() {
        val timeline = HoldContactTimeline(events = emptyList())

        assertEquals(0f, ContactCoverageScorer.score(candidate, timeline), 1e-6f)
    }

    @Test
    fun `every candidate hold touched by a mix of limbs scores one`() {
        val timeline = HoldContactTimeline(
            events = listOf(
                established(Limb.LEFT_HAND, 1),
                established(Limb.RIGHT_HAND, 2),
                established(Limb.LEFT_FOOT, 3),
                established(Limb.RIGHT_FOOT, 4),
            ),
        )

        assertEquals(1f, ContactCoverageScorer.score(candidate, timeline), 1e-6f)
    }

    @Test
    fun `exactly half of four holds touched scores one half`() {
        val timeline = HoldContactTimeline(
            events = listOf(
                established(Limb.LEFT_HAND, 1),
                established(Limb.RIGHT_HAND, 2),
            ),
        )

        assertEquals(0.5f, ContactCoverageScorer.score(candidate, timeline), 1e-6f)
    }

    @Test
    fun `an established event on a hold outside the candidate does not count toward coverage`() {
        val smallCandidate = RouteCandidate(
            routeVersionId = 2L,
            startHoldIds = setOf(10),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        )
        // The only event on record is for holdId 99, which is not in smallCandidate.allHoldIds at all.
        val timeline = HoldContactTimeline(
            events = listOf(established(Limb.RIGHT_HAND, 99)),
        )

        assertEquals(0f, ContactCoverageScorer.score(smallCandidate, timeline), 1e-6f)
    }

    @Test
    fun `re-touching the same hold multiple times still counts it once toward coverage`() {
        val timeline = HoldContactTimeline(
            events = listOf(
                established(Limb.LEFT_HAND, 1),
                established(Limb.LEFT_HAND, 1),
                established(Limb.RIGHT_HAND, 2),
                established(Limb.LEFT_FOOT, 3),
                established(Limb.RIGHT_FOOT, 4),
                established(Limb.RIGHT_FOOT, 4),
                established(Limb.RIGHT_FOOT, 4),
            ),
        )

        assertEquals(1f, ContactCoverageScorer.score(candidate, timeline), 1e-6f)
    }
}
