package com.example.climb.analysis.contact

import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused coverage of [HoldContactDetector]'s adjacent-hold disambiguation and its bounded A->B
 * transition path (`HoldContactConfig.contactTransitionOverlapMs`) - the *only* way
 * [LimbContactState.establishedHoldId] is allowed to move from one non-null hold directly to a
 * different non-null hold.
 */
class HoldContactDetectorTransitionTest {

    private val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)

    private fun squareHold(holdId: Int, centerX: Float, centerY: Float, halfWidth: Float): HoldShape =
        HoldShape(
            holdId = holdId,
            contourNormalized = listOf(
                Point2D(centerX - halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY + halfWidth),
                Point2D(centerX - halfWidth, centerY + halfWidth),
            ),
        )

    /** Builds a frame where [limb]'s primary landmark set sits at [point] with [confidence] -
     * every other limb is simply absent (a tracking gap for it), which is harmless since none of
     * these tests inspect any limb but [limb]. */
    private fun frameFor(timestampMs: Long, limb: Limb, point: Point2D, confidence: Float = 0.9f): ContactPoseFrame {
        val landmarks: Map<ContactLandmarkType, ContactLandmark> = when (limb) {
            Limb.LEFT_HAND -> mapOf(
                ContactLandmarkType.LEFT_INDEX to ContactLandmark(point, confidence),
                ContactLandmarkType.LEFT_PINKY to ContactLandmark(point, confidence),
                ContactLandmarkType.LEFT_THUMB to ContactLandmark(point, confidence),
            )
            Limb.RIGHT_HAND -> mapOf(
                ContactLandmarkType.RIGHT_INDEX to ContactLandmark(point, confidence),
                ContactLandmarkType.RIGHT_PINKY to ContactLandmark(point, confidence),
                ContactLandmarkType.RIGHT_THUMB to ContactLandmark(point, confidence),
            )
            Limb.LEFT_FOOT -> mapOf(
                ContactLandmarkType.LEFT_ANKLE to ContactLandmark(point, confidence),
                ContactLandmarkType.LEFT_HEEL to ContactLandmark(point, confidence),
            )
            Limb.RIGHT_FOOT -> mapOf(
                ContactLandmarkType.RIGHT_ANKLE to ContactLandmark(point, confidence),
                ContactLandmarkType.RIGHT_HEEL to ContactLandmark(point, confidence),
            )
        }
        return ContactPoseFrame(timestampMs = timestampMs, landmarks = landmarks)
    }

    // Hold A: (0,0)-(0.1,0.1), center (0.05,0.05). Hold B: (0.12,0)-(0.22,0.1), center
    // (0.17,0.05). The 0.02 gap between A's right edge (x=0.1) and B's left edge (x=0.12) is
    // deliberately narrower than contactCandidateDistanceThreshold (0.025) but wider than 0, so a
    // proxy sitting just inside A's own edge is simultaneously a "candidate" for B.
    private val holdA = squareHold(holdId = 1, centerX = 0.05f, centerY = 0.05f, halfWidth = 0.05f)
    private val holdB = squareHold(holdId = 2, centerX = 0.17f, centerY = 0.05f, halfWidth = 0.05f)

    private fun establishHoldAAt(detector: HoldContactDetector, limb: Limb): List<HoldContactEvent> {
        detector.processFrame(frameFor(0L, limb, Point2D(0.05f, 0.05f)), identity)
        return detector.processFrame(frameFor(300L, limb, Point2D(0.05f, 0.05f)), identity)
    }

    @Test
    fun `adjacent holds do not both become established while A stays established`() {
        val detector = HoldContactDetector(holds = listOf(holdA, holdB))
        val limb = Limb.LEFT_HAND

        val establishEvents = establishHoldAAt(detector, limb)
        assertEquals(1, establishEvents.size)
        assertEquals(ContactEventType.ESTABLISHED, establishEvents[0].type)
        assertEquals(holdA.holdId, establishEvents[0].holdId)
        assertEquals(holdA.holdId, detector.stateOf(limb).establishedHoldId)

        // Proxy sits just inside A's edge: still 0 distance from A (so A never approaches its
        // release threshold), but only 0.021 from B - within B's candidate threshold (0.025) too.
        val edgeOfA = Point2D(0.099f, 0.05f)
        val checkpointsMs = listOf(350L, 450L, 550L, 649L) // dwell-since-candidate: 0,100,200,299ms - all < the 300ms dwell requirement

        for (t in checkpointsMs) {
            val events = detector.processFrame(frameFor(t, limb, edgeOfA), identity)
            assertTrue("expected no events before B's dwell threshold is reached at t=$t, got $events", events.isEmpty())

            val state = detector.stateOf(limb)
            assertEquals("at t=$t, A must still be the sole established hold", holdA.holdId, state.establishedHoldId)
            assertNotEquals("B must never become established while A still is", holdB.holdId, state.establishedHoldId)
        }

        // Across the whole run, the timeline only ever recorded A's single establishment - never
        // an ESTABLISHED for B, and never any RELEASED at all (A was never actually released).
        val allEvents = detector.timeline.events
        assertEquals(1, allEvents.size)
        assertTrue(allEvents.none { it.holdId == holdB.holdId })
        assertTrue(allEvents.none { it.type == ContactEventType.RELEASED })
    }

    @Test
    fun `a candidate that never reaches B's dwell threshold cannot be interrupted by re-approaching A`() {
        // Same adjacency setup, but this time B's candidacy is restarted partway through by a
        // brief dip back toward A's center, proving the invariant holds across a *fluctuating*
        // proxy too, not just a perfectly still one.
        val detector = HoldContactDetector(holds = listOf(holdA, holdB))
        val limb = Limb.LEFT_HAND
        establishHoldAAt(detector, limb)

        val edgeOfA = Point2D(0.099f, 0.05f)
        val centerOfA = Point2D(0.05f, 0.05f)

        detector.processFrame(frameFor(350L, limb, edgeOfA), identity) // B candidate starts, since=350
        assertEquals(holdB.holdId, detector.stateOf(limb).candidateHoldId)

        detector.processFrame(frameFor(450L, limb, centerOfA), identity) // back near A's center - B no longer within candidate range
        assertNull(detector.stateOf(limb).candidateHoldId)

        val eventsAfterReapproach = detector.processFrame(frameFor(500L, limb, edgeOfA), identity) // B candidacy restarts, since=500
        assertTrue(eventsAfterReapproach.isEmpty())
        assertEquals(holdB.holdId, detector.stateOf(limb).candidateHoldId)

        val finalEvents = detector.processFrame(frameFor(700L, limb, edgeOfA), identity) // only 200ms of restarted dwell - still short
        assertTrue(finalEvents.isEmpty())
        assertEquals(holdA.holdId, detector.stateOf(limb).establishedHoldId)
        assertNotEquals(holdB.holdId, detector.stateOf(limb).establishedHoldId)
    }

    @Test
    fun `controlled transition from A to B releases A and establishes B in the same frame`() {
        val detector = HoldContactDetector(holds = listOf(holdA, holdB))
        val limb = Limb.LEFT_HAND

        establishHoldAAt(detector, limb)
        assertEquals(holdA.holdId, detector.stateOf(limb).establishedHoldId)

        // Distance to A is 0.04 here (within the 0.06 release threshold, so A is never released
        // by simple distance hysteresis); distance to B is 0 (inside it), well within B's 0.025
        // candidate threshold - so B becomes the nearest qualifying candidate.
        val towardB = Point2D(0.14f, 0.05f)
        val candidateStartEvents = detector.processFrame(frameFor(350L, limb, towardB), identity)
        assertTrue(candidateStartEvents.isEmpty())
        assertEquals(holdB.holdId, detector.stateOf(limb).candidateHoldId)
        assertEquals(holdA.holdId, detector.stateOf(limb).establishedHoldId)

        // 350ms of continuous B candidacy: >= the 300ms establish-dwell requirement, and <= the
        // 400ms transition-overlap window - so this is the bounded A->B transition path, not a
        // release-then-reacquire via null.
        val transitionEvents = detector.processFrame(frameFor(700L, limb, towardB), identity)

        assertEquals(2, transitionEvents.size)
        assertEquals(ContactEventType.RELEASED, transitionEvents[0].type)
        assertEquals(holdA.holdId, transitionEvents[0].holdId)
        assertEquals(700L, transitionEvents[0].timestampMs)
        assertEquals(ReleaseReason.TRANSITIONED_TO_ANOTHER_HOLD, transitionEvents[0].releaseReason)
        assertEquals(ContactEventType.ESTABLISHED, transitionEvents[1].type)
        assertEquals(holdB.holdId, transitionEvents[1].holdId)
        assertEquals(700L, transitionEvents[1].timestampMs)

        val finalState = detector.stateOf(limb)
        assertEquals(holdB.holdId, finalState.establishedHoldId)
        assertEquals(holdA.holdId, finalState.previousHoldId)
        assertNull(finalState.candidateHoldId)
        assertNull(finalState.candidateSinceMs)

        // The transition pair is the only RELEASED/ESTABLISHED activity for A and B respectively.
        val allEvents = detector.timeline.events
        assertEquals(1, allEvents.count { it.type == ContactEventType.RELEASED && it.holdId == holdA.holdId })
        assertEquals(1, allEvents.count { it.type == ContactEventType.ESTABLISHED && it.holdId == holdB.holdId })
    }

    @Test
    fun `candidate dwell beyond the transition-overlap window does not force a transition`() {
        val detector = HoldContactDetector(holds = listOf(holdA, holdB))
        val limb = Limb.LEFT_HAND

        establishHoldAAt(detector, limb)
        val towardB = Point2D(0.14f, 0.05f)
        detector.processFrame(frameFor(350L, limb, towardB), identity) // B candidacy starts

        // dwell = 751 - 350 = 401ms: past the 400ms transition-overlap window, so no A->B jump
        // happens here even though B's own 300ms dwell requirement was long since satisfied.
        val events = detector.processFrame(frameFor(751L, limb, towardB), identity)
        assertTrue(events.isEmpty())

        val state = detector.stateOf(limb)
        assertEquals(holdA.holdId, state.establishedHoldId)
        assertEquals(holdB.holdId, state.candidateHoldId)
    }

    @Test
    fun `a stale past-window candidate is not instantly established just because A happens to release via distance`() {
        // Regression test: the transition-overlap bound must apply even when the OLD hold is
        // released by ordinary distance hysteresis on the exact same frame a long-overdue
        // candidate's confidence finally clears - not just when the old hold is still established
        // at the moment of promotion.
        val detector = HoldContactDetector(holds = listOf(holdA, holdB))
        val limb = Limb.LEFT_HAND
        establishHoldAAt(detector, limb)

        val towardB = Point2D(0.14f, 0.05f) // 0.04 from A (within release threshold), 0 from B
        // B becomes a candidate at t=350, but every frame here is deliberately LOW confidence
        // (< contactMinFrameConfidence=0.5) so it can never be promoted no matter how long it
        // dwells - and it dwells well past both the 300ms establish threshold and the 400ms
        // transition-overlap window while A remains established throughout (distance to A never
        // exceeds the release threshold in this stretch).
        detector.processFrame(frameFor(350L, limb, towardB, confidence = 0.1f), identity)
        detector.processFrame(frameFor(1000L, limb, towardB, confidence = 0.1f), identity)
        val stateBeforeRelease = detector.stateOf(limb)
        assertEquals(holdA.holdId, stateBeforeRelease.establishedHoldId)
        assertEquals(holdB.holdId, stateBeforeRelease.candidateHoldId)

        // Now A genuinely releases via distance (proxy moves to B's exact center, 0.12 from A -
        // well beyond the 0.06 release threshold), with GOOD confidence this time, and a plausible
        // ~0.03 displacement over 200ms. B's stale candidacy (dwell = 1200-350 = 850ms, 450ms past
        // the transition-overlap window) must NOT be instantly promoted just because A released
        // this same frame.
        val releaseEvents = detector.processFrame(frameFor(1200L, limb, Point2D(0.17f, 0.05f)), identity)

        assertEquals(1, releaseEvents.size)
        assertEquals(ContactEventType.RELEASED, releaseEvents[0].type)
        assertEquals(holdA.holdId, releaseEvents[0].holdId)

        val stateAfterRelease = detector.stateOf(limb)
        assertNull("B must not be instantly established off a stale, past-window dwell clock", stateAfterRelease.establishedHoldId)
        assertEquals("B's dwell clock must reset to start fresh from the release moment", 1200L, stateAfterRelease.candidateSinceMs)
        assertEquals(holdB.holdId, stateAfterRelease.candidateHoldId)

        // A fresh, full dwell period from the reset point DOES establish B - the fix resets the
        // clock, it doesn't block B from ever establishing.
        val eventsBeforeFreshDwell = detector.processFrame(frameFor(1400L, limb, Point2D(0.17f, 0.05f)), identity)
        assertTrue("200ms of fresh dwell is still short of the 300ms requirement", eventsBeforeFreshDwell.isEmpty())

        val eventsAfterFreshDwell = detector.processFrame(frameFor(1500L, limb, Point2D(0.17f, 0.05f)), identity)
        assertEquals(1, eventsAfterFreshDwell.size)
        assertEquals(ContactEventType.ESTABLISHED, eventsAfterFreshDwell[0].type)
        assertEquals(holdB.holdId, eventsAfterFreshDwell[0].holdId)
        assertEquals(holdB.holdId, detector.stateOf(limb).establishedHoldId)
        assertEquals(holdA.holdId, detector.stateOf(limb).previousHoldId)
    }
}
