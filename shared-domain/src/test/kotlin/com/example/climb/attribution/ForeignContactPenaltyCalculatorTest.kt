package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ForeignContactPenaltyCalculatorTest {

    private val config = RouteAttributionScoringConfig()

    private fun candidate(routeVersionId: Long, startHoldIds: Set<Int>, bodyHoldIds: Set<Int> = emptySet()) =
        RouteCandidate(
            routeVersionId = routeVersionId,
            startHoldIds = startHoldIds,
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = bodyHoldIds,
        )

    private fun establishedEvent(
        holdId: Int,
        confidence: Float = 0.9f,
        evidenceQuality: EvidenceQuality = EvidenceQuality.STRONG,
    ) = HoldContactEvent(
        limb = Limb.LEFT_HAND,
        holdId = holdId,
        type = ContactEventType.ESTABLISHED,
        timestampMs = 0L,
        confidence = confidence,
        evidenceQuality = evidenceQuality,
    )

    private fun releasedEvent(holdId: Int) = HoldContactEvent(
        limb = Limb.LEFT_HAND,
        holdId = holdId,
        type = ContactEventType.RELEASED,
        timestampMs = 100L,
        confidence = 0.9f,
        evidenceQuality = EvidenceQuality.STRONG,
    )

    // uniqueForeignEventCount

    @Test
    fun `confident established event on another candidate's hold counts as foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 2)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(1, count)
    }

    @Test
    fun `event on the candidate's own hold is never counted as foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 1)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(0, count)
    }

    @Test
    fun `a hold id shared between the candidate and another candidate is not foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1), bodyHoldIds = setOf(5))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(5))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 5)))

        val countAgainstOurs = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)
        val countAgainstTheirs = ForeignContactPenaltyCalculator.uniqueForeignEventCount(theirs, listOf(ours, theirs), timeline, config)

        // Hold 5 belongs to both candidates' own definitions, so it can never count as "foreign"
        // against either side of the pair - not just the one under test in the other cases above.
        assertEquals(0, countAgainstOurs)
        assertEquals(0, countAgainstTheirs)
    }

    @Test
    fun `a hold shared between two other candidates is still only counted once`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val second = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(9))
        val third = candidate(routeVersionId = 3L, startHoldIds = setOf(3), bodyHoldIds = setOf(9))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 9)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, second, third), timeline, config)

        assertEquals(1, count)
    }

    @Test
    fun `released events on a foreign hold are never counted`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(releasedEvent(holdId = 2)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(0, count)
    }

    @Test
    fun `uncertain evidence quality on a foreign hold is never counted`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(establishedEvent(holdId = 2, evidenceQuality = EvidenceQuality.UNCERTAIN)),
        )

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(0, count)
    }

    @Test
    fun `confidence strictly below minLimbLandmarkConfidence on a foreign hold is not counted`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(establishedEvent(holdId = 2, confidence = config.minLimbLandmarkConfidence - 0.01f)),
        )

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(0, count)
    }

    @Test
    fun `confidence exactly at minLimbLandmarkConfidence on a foreign hold is counted`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(establishedEvent(holdId = 2, confidence = config.minLimbLandmarkConfidence)),
        )

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(1, count)
    }

    @Test
    fun `repeated establish-release-establish on the same foreign hold counts each established event separately`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 2),
                releasedEvent(holdId = 2),
                establishedEvent(holdId = 2),
            ),
        )

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(2, count)
    }

    @Test
    fun `no other candidates means no hold can ever be foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 1)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours), timeline, config)

        assertEquals(0, count)
    }

    @Test
    fun `five distinct foreign established events count as exactly five`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(3, 4, 5, 6))
        // Five distinct foreign holds, one ESTABLISHED event each - this is a count of qualifying
        // EVENTS, not a duration/frame-derived number (this calculator never sees raw frames at
        // all, only already-deduplicated HoldContactTimeline events).
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 2),
                establishedEvent(holdId = 3),
                establishedEvent(holdId = 4),
                establishedEvent(holdId = 5),
                establishedEvent(holdId = 6),
            ),
        )

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(5, count)
    }

    @Test
    fun `a long single unbroken contact counts identically to a brief one`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        // HoldContactTimeline only ever carries one ESTABLISHED event per unbroken contact,
        // regardless of how many frames it spanned - so a single event here stands in for both a
        // 2-frame and a 500-frame contact equally.
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 2)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)

        assertEquals(1, count)
    }

    // penaltyDeduction

    @Test
    fun `zero foreign events deduct nothing`() {
        assertEquals(0f, ForeignContactPenaltyCalculator.penaltyDeduction(0, config), 1e-6f)
    }

    @Test
    fun `foreign event count under the cap scales linearly with weight`() {
        // config default foreignContactPenaltyPerEvent=0.25f, foreignContactPenaltyWeight=0.20f -
        // 2 events -> ratio 0.5 -> deduction 0.10.
        val deduction = ForeignContactPenaltyCalculator.penaltyDeduction(2, config)
        assertEquals(0.10f, deduction, 1e-6f)
    }

    @Test
    fun `foreign event count exactly at the cap deducts the full penalty weight`() {
        // 0.25f * 4 = 1.0 ratio exactly.
        val deduction = ForeignContactPenaltyCalculator.penaltyDeduction(4, config)
        assertEquals(config.foreignContactPenaltyWeight, deduction, 1e-6f)
    }

    @Test
    fun `foreignContactPenaltyPerEvent of 0_25 with 10 foreign events deducts the same as 4 events`() {
        // The exact scenario the cap exists for: once foreignContactPenaltyPerEvent * count would
        // exceed 1.0, additional events beyond the cap-reaching count contribute nothing further.
        val fourEvents = ForeignContactPenaltyCalculator.penaltyDeduction(4, config)
        val tenEvents = ForeignContactPenaltyCalculator.penaltyDeduction(10, config)

        assertEquals(fourEvents, tenEvents, 1e-6f)
        assertEquals(config.foreignContactPenaltyWeight, tenEvents, 1e-6f)
    }

    @Test
    fun `penaltyDeduction scales linearly with a custom foreignContactPenaltyPerEvent below the cap`() {
        // A distinct config from the default, to prove the linear region is driven by whatever
        // foreignContactPenaltyPerEvent/foreignContactPenaltyWeight the caller passes in, not a
        // value baked into the calculator itself.
        val customConfig = RouteAttributionScoringConfig(foreignContactPenaltyPerEvent = 0.1f, foreignContactPenaltyWeight = 0.5f)

        // 1 event -> ratio 0.1 -> deduction 0.05; 3 events -> ratio 0.3 -> deduction 0.15; all
        // strictly below the ratio=1.0 cap (reached at 10 events for this config).
        assertEquals(0.05f, ForeignContactPenaltyCalculator.penaltyDeduction(1, customConfig), 1e-6f)
        assertEquals(0.15f, ForeignContactPenaltyCalculator.penaltyDeduction(3, customConfig), 1e-6f)
        assertEquals(0.25f, ForeignContactPenaltyCalculator.penaltyDeduction(5, customConfig), 1e-6f)
    }

    @Test
    fun `foreign event count beyond the cap does not deduct more than the penalty weight`() {
        val atCap = ForeignContactPenaltyCalculator.penaltyDeduction(4, config)
        val wayOverCap = ForeignContactPenaltyCalculator.penaltyDeduction(50, config)

        assertEquals(atCap, wayOverCap, 1e-6f)
        assertEquals(config.foreignContactPenaltyWeight, wayOverCap, 1e-6f)
    }
}
