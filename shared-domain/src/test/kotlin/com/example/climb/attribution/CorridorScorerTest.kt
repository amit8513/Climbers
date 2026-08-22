package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorridorScorerTest {

    /** A generous, roughly-centered corridor every "inside" fixture hold sits within and every
     * "outside" fixture hold sits clear of. */
    private val corridor = NormalizedRect(left = 0.3f, top = 0.3f, right = 0.7f, bottom = 0.7f)

    private fun squareHold(holdId: Int, left: Float, top: Float): HoldShape = HoldShape(
        holdId = holdId,
        contourNormalized = listOf(
            Point2D(left, top),
            Point2D(left + 0.1f, top),
            Point2D(left + 0.1f, top + 0.1f),
            Point2D(left, top + 0.1f),
        ),
    )

    // Centroid (0.45, 0.45) - clearly inside `corridor`.
    private fun insideHold(holdId: Int) = squareHold(holdId, left = 0.4f, top = 0.4f)

    // Centroid (0.85, 0.85) - clearly outside `corridor`.
    private fun outsideHold(holdId: Int) = squareHold(holdId, left = 0.8f, top = 0.8f)

    private fun establishedEvent(holdId: Int, timestampMs: Long): HoldContactEvent = HoldContactEvent(
        limb = Limb.LEFT_HAND,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = timestampMs,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    private fun candidateWithHolds(holdIds: Set<Int>, corridorNormalized: NormalizedRect?): RouteCandidate =
        RouteCandidate(
            routeVersionId = 1L,
            startHoldIds = holdIds,
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            corridorNormalized = corridorNormalized,
        )

    @Test
    fun `null corridor scores null regardless of contact evidence`() {
        val holds = listOf(insideHold(1))
        val candidate = candidateWithHolds(setOf(1), corridorNormalized = null)
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 1, timestampMs = 1000L)))

        val score = CorridorScorer.score(candidate, timeline, holds)

        assertNull(score)
    }

    @Test
    fun `established contacts entirely inside the corridor score 1`() {
        val holds = listOf(insideHold(1), insideHold(2))
        val candidate = candidateWithHolds(setOf(1, 2), corridorNormalized = corridor)
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 1, timestampMs = 1000L),
                establishedEvent(holdId = 2, timestampMs = 2000L),
            ),
        )

        val score = CorridorScorer.score(candidate, timeline, holds)

        assertEquals(1f, score!!, 0f)
    }

    @Test
    fun `established contacts entirely outside the corridor score 0`() {
        val holds = listOf(outsideHold(3), outsideHold(4))
        val candidate = candidateWithHolds(setOf(3, 4), corridorNormalized = corridor)
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 3, timestampMs = 1000L),
                establishedEvent(holdId = 4, timestampMs = 2000L),
            ),
        )

        val score = CorridorScorer.score(candidate, timeline, holds)

        assertEquals(0f, score!!, 0f)
    }

    @Test
    fun `mix of inside and outside established contacts scores the fraction inside, excluding holds with no established contact at all`() {
        // Holds 1,2 inside the corridor; holds 3,4 outside; hold 5 is part of the candidate but
        // never has an ESTABLISHED event at all, so it must not be silently treated as "outside"
        // and drag the score down - it should be excluded from both numerator and denominator.
        val holds = listOf(insideHold(1), insideHold(2), outsideHold(3), outsideHold(4), outsideHold(5))
        val candidate = candidateWithHolds(setOf(1, 2, 3, 4, 5), corridorNormalized = corridor)
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 1, timestampMs = 1000L),
                establishedEvent(holdId = 2, timestampMs = 2000L),
                establishedEvent(holdId = 3, timestampMs = 3000L),
                establishedEvent(holdId = 4, timestampMs = 4000L),
            ),
        )

        val score = CorridorScorer.score(candidate, timeline, holds)

        assertEquals(0.5f, score!!, 0f)
    }

    @Test
    fun `corridor set but zero established contacts on the candidates own holds scores 0, not null`() {
        val holds = listOf(insideHold(1))
        val candidate = candidateWithHolds(setOf(1), corridorNormalized = corridor)
        val timeline = HoldContactTimeline(events = emptyList())

        val score = CorridorScorer.score(candidate, timeline, holds)

        assertEquals(0f, score!!, 0f)
    }
}
