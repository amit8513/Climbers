package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ForeignContactEventClassifierTest {

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

    // foreignHoldIds

    @Test
    fun `foreignHoldIds includes another candidate's holds and excludes the candidate's own`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))

        val foreignHoldIds = ForeignContactEventClassifier.foreignHoldIds(ours, listOf(ours, theirs))

        assertEquals(setOf(2), foreignHoldIds)
    }

    @Test
    fun `foreignHoldIds excludes a hold id shared between the candidate and another candidate`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1), bodyHoldIds = setOf(5))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(5))

        val foreignHoldIdsForOurs = ForeignContactEventClassifier.foreignHoldIds(ours, listOf(ours, theirs))
        val foreignHoldIdsForTheirs = ForeignContactEventClassifier.foreignHoldIds(theirs, listOf(ours, theirs))

        // Hold 5 belongs to both candidates' own definitions, so it can never be foreign against
        // either side of the pair - even though each candidate's other, non-shared holds still
        // are.
        assertEquals(setOf(2), foreignHoldIdsForOurs)
        assertEquals(setOf(1), foreignHoldIdsForTheirs)
    }

    @Test
    fun `foreignHoldIds with no other candidates is empty`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))

        val foreignHoldIds = ForeignContactEventClassifier.foreignHoldIds(ours, listOf(ours))

        assertEquals(emptySet<Int>(), foreignHoldIds)
    }

    // qualifyingForeignEvents

    @Test
    fun `confident established event on another candidate's hold qualifies as foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val event = establishedEvent(holdId = 2)
        val timeline = HoldContactTimeline(events = listOf(event))

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(listOf(event), qualifying)
    }

    @Test
    fun `event on the candidate's own hold never qualifies as foreign`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 1)))

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(emptyList<HoldContactEvent>(), qualifying)
    }

    @Test
    fun `event on a hold id shared between the candidate and another candidate never qualifies`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1), bodyHoldIds = setOf(5))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(5))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 5)))

        val qualifyingForOurs = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)
        val qualifyingForTheirs = ForeignContactEventClassifier.qualifyingForeignEvents(theirs, listOf(ours, theirs), timeline, config)

        assertEquals(emptyList<HoldContactEvent>(), qualifyingForOurs)
        assertEquals(emptyList<HoldContactEvent>(), qualifyingForTheirs)
    }

    @Test
    fun `released events on a foreign hold never qualify`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(releasedEvent(holdId = 2)))

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(emptyList<HoldContactEvent>(), qualifying)
    }

    @Test
    fun `uncertain evidence quality on a foreign hold never qualifies`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(establishedEvent(holdId = 2, evidenceQuality = EvidenceQuality.UNCERTAIN)),
        )

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(emptyList<HoldContactEvent>(), qualifying)
    }

    @Test
    fun `confidence strictly below minLimbLandmarkConfidence on a foreign hold does not qualify`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(
            events = listOf(establishedEvent(holdId = 2, confidence = config.minLimbLandmarkConfidence - 0.01f)),
        )

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(emptyList<HoldContactEvent>(), qualifying)
    }

    @Test
    fun `confidence exactly at minLimbLandmarkConfidence on a foreign hold qualifies`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val event = establishedEvent(holdId = 2, confidence = config.minLimbLandmarkConfidence)
        val timeline = HoldContactTimeline(events = listOf(event))

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(listOf(event), qualifying)
    }

    @Test
    fun `multiple distinct qualifying foreign events are all counted individually`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(3, 4, 5, 6))
        val events = listOf(
            establishedEvent(holdId = 2),
            establishedEvent(holdId = 3),
            establishedEvent(holdId = 4),
            establishedEvent(holdId = 5),
            establishedEvent(holdId = 6),
        )
        val timeline = HoldContactTimeline(events = events)

        val qualifying = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config)

        assertEquals(5, qualifying.size)
        assertEquals(events, qualifying)
    }

    // Regression: proves ForeignContactPenaltyCalculator structurally derives its count from this
    // classifier's qualifying-event set, rather than a second independent computation that merely
    // happens to agree with it.

    @Test
    fun `uniqueForeignEventCount exactly equals qualifyingForeignEvents size for a simple foreign-hold fixture`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val theirs = candidate(routeVersionId = 2L, startHoldIds = setOf(2))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 2)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours, theirs), timeline, config)
        val qualifyingSize = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours, theirs), timeline, config).size

        assertEquals(qualifyingSize, count)
    }

    @Test
    fun `uniqueForeignEventCount exactly equals qualifyingForeignEvents size for a mixed-qualification fixture`() {
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1), bodyHoldIds = setOf(5))
        val second = candidate(routeVersionId = 2L, startHoldIds = setOf(2), bodyHoldIds = setOf(9))
        val third = candidate(routeVersionId = 3L, startHoldIds = setOf(3), bodyHoldIds = setOf(5, 9))
        val timeline = HoldContactTimeline(
            events = listOf(
                establishedEvent(holdId = 2),
                establishedEvent(holdId = 9),
                releasedEvent(holdId = 9),
                establishedEvent(holdId = 9),
                establishedEvent(holdId = 5),
                establishedEvent(holdId = 2, evidenceQuality = EvidenceQuality.UNCERTAIN),
                establishedEvent(holdId = 2, confidence = config.minLimbLandmarkConfidence - 0.01f),
            ),
        )
        val allCandidates = listOf(ours, second, third)

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, allCandidates, timeline, config)
        val qualifyingSize = ForeignContactEventClassifier.qualifyingForeignEvents(ours, allCandidates, timeline, config).size

        assertEquals(qualifyingSize, count)
    }

    @Test
    fun `uniqueForeignEventCount exactly equals qualifyingForeignEvents size for a custom-config fixture with no other candidates`() {
        val customConfig = RouteAttributionScoringConfig(minLimbLandmarkConfidence = 0.8f)
        val ours = candidate(routeVersionId = 1L, startHoldIds = setOf(1))
        val timeline = HoldContactTimeline(events = listOf(establishedEvent(holdId = 1, confidence = 0.95f)))

        val count = ForeignContactPenaltyCalculator.uniqueForeignEventCount(ours, listOf(ours), timeline, customConfig)
        val qualifyingSize = ForeignContactEventClassifier.qualifyingForeignEvents(ours, listOf(ours), timeline, customConfig).size

        assertEquals(qualifyingSize, count)
    }
}
