package com.example.climb.analysis.contact

import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers `HoldContactDetector`'s tracking-gap zones (short/decaying/reset, per
 * `HoldContactConfig.contactShortGapMaxMs` / `contactTrackingGapResetMs`) and its immediate,
 * non-decaying implausible-jump reset path (`maxPlausibleNormalizedDisplacementPerMs`).
 */
class HoldContactDetectorGapTest {

    private val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)

    // Square hold centered at (0.5, 0.5), half-width 0.1 - matches the baseline fixture convention.
    private val holdCenter = Point2D(0.5f, 0.5f)
    private val hold = HoldShape(
        holdId = 1,
        contourNormalized = listOf(
            Point2D(0.4f, 0.4f),
            Point2D(0.6f, 0.4f),
            Point2D(0.6f, 0.6f),
            Point2D(0.4f, 0.6f),
        ),
    )

    private fun leftHandFrame(timestampMs: Long, position: Point2D, confidence: Float = 0.9f): ContactPoseFrame =
        ContactPoseFrame(
            timestampMs = timestampMs,
            landmarks = mapOf(
                ContactLandmarkType.LEFT_INDEX to ContactLandmark(position, confidence),
                ContactLandmarkType.LEFT_PINKY to ContactLandmark(position, confidence),
                ContactLandmarkType.LEFT_THUMB to ContactLandmark(position, confidence),
            ),
        )

    /** A frame where LEFT_HAND has no landmarks at all - `LimbProxyResolver.resolve` returns
     * null for it, i.e. a genuine tracking gap rather than a low-confidence reading. */
    private fun untrackedFrame(timestampMs: Long): ContactPoseFrame = ContactPoseFrame(timestampMs = timestampMs)

    /** Drives LEFT_HAND onto [hold] via the normal candidate-then-dwell path: a first frame at
     * t=0 that sets the candidate, then a frame at t=contactEstablishedDwellMs (default 300)
     * that satisfies the dwell requirement and promotes it to established. Returns the detector's
     * events from the promoting (second) frame. */
    private fun establishLeftHandOnHold(
        detector: HoldContactDetector,
        config: com.example.climb.analysis.metrics.HoldContactConfig = com.example.climb.analysis.metrics.HoldContactConfig(),
    ): List<HoldContactEvent> {
        detector.processFrame(leftHandFrame(0L, holdCenter), identity)
        return detector.processFrame(leftHandFrame(config.contactEstablishedDwellMs, holdCenter), identity)
    }

    @Test
    fun `short occlusion well under the short-gap threshold retains established contact`() {
        val detector = HoldContactDetector(listOf(hold))
        val establishEvents = establishLeftHandOnHold(detector)
        assertEquals(1, establishEvents.size)
        assertEquals(ContactEventType.ESTABLISHED, establishEvents.single().type)
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // Gap of 50ms since the last resolved frame (t=300) - clearly under the 200ms
        // contactShortGapMaxMs default, so this must be treated as a harmless blip.
        val gapEvents = detector.processFrame(untrackedFrame(350L), identity)
        assertTrue("a short gap must not emit any event", gapEvents.isEmpty())
        val duringGap = detector.stateOf(Limb.LEFT_HAND)
        assertEquals(1, duringGap.establishedHoldId)
        assertEquals(GapState.SHORT, duringGap.gapState)

        // Tracked again, at the same hold position.
        val reacquireEvents = detector.processFrame(leftHandFrame(380L, holdCenter), identity)
        assertTrue("re-tracking after a short gap must not itself emit a RELEASED event", reacquireEvents.none { it.type == ContactEventType.RELEASED })
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // establishedHoldId was 1 the entire time - never null - and no RELEASED event was ever
        // recorded in the full accumulated timeline.
        assertTrue(detector.timeline.events.none { it.type == ContactEventType.RELEASED })
    }

    @Test
    fun `multiple short gaps in a row still retain established contact`() {
        val detector = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detector)
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // Two consecutive untracked frames, each only 60ms after the previous *resolved* frame's
        // timestamp (300) - LimbContactState.lastSeenAtMs is only updated on a resolved frame, so
        // both of these gap frames measure their gap against the same t=300 anchor.
        detector.processFrame(untrackedFrame(340L), identity)
        val secondGapEvents = detector.processFrame(untrackedFrame(360L), identity)

        assertTrue(secondGapEvents.isEmpty())
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        assertEquals(GapState.SHORT, detector.stateOf(Limb.LEFT_HAND).gapState)
    }

    @Test
    fun `occlusion at or beyond the tracking-gap reset threshold releases and clears established contact`() {
        val detector = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detector)
        val establishedConfidenceBefore = detector.stateOf(Limb.LEFT_HAND).establishedConfidence
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // Gap of exactly 500ms since the last resolved frame (t=300) - at the
        // contactTrackingGapResetMs default, so this must hard-reset rather than merely decay.
        val events = detector.processFrame(untrackedFrame(800L), identity)

        assertEquals(1, events.size)
        val released = events.single()
        assertEquals(ContactEventType.RELEASED, released.type)
        assertEquals(Limb.LEFT_HAND, released.limb)
        assertEquals(1, released.holdId)
        assertEquals(800L, released.timestampMs)
        assertEquals(EvidenceQuality.UNCERTAIN, released.evidenceQuality)
        assertEquals(establishedConfidenceBefore, released.confidence, 1e-6f)
        assertEquals(ReleaseReason.LONG_GAP_RESET, released.releaseReason)

        val state = detector.stateOf(Limb.LEFT_HAND)
        assertNull(state.establishedHoldId)
        assertEquals(1, state.previousHoldId)
        assertEquals(GapState.RESET, state.gapState)
        assertEquals(0f, state.establishedConfidence, 1e-6f)
    }

    @Test
    fun `gap duration strictly between the short and reset thresholds decays confidence without releasing`() {
        val detector = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detector)

        // Gap of 250ms - inside [contactShortGapMaxMs=200, contactTrackingGapResetMs=500), so
        // confidence should decay linearly (fraction = (250-200)/(500-200) = 1/6) but the hold
        // must remain established, unlike the >=500ms case above.
        val events = detector.processFrame(untrackedFrame(550L), identity)

        assertTrue(events.isEmpty())
        val state = detector.stateOf(Limb.LEFT_HAND)
        assertEquals(1, state.establishedHoldId)
        assertEquals(GapState.DECAYING, state.gapState)
        assertEquals(0.9f * (1f - 50f / 300f), state.establishedConfidence, 1e-4f)
    }

    @Test
    fun `implausible body jump resets contact immediately rather than through gap decay`() {
        val detector = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detector)
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // Only 10ms elapsed since the last resolved frame (t=300) - default
        // maxPlausibleNormalizedDisplacementPerMs=0.004 allows at most 0.04 normalized units of
        // movement in that time. Jumping to (0.95, 0.95) is a displacement of roughly 0.636,
        // far beyond what's plausible as real motion - this is a resolved frame (not a gap), so
        // it must be caught by the implausible-jump check, not the gap-decay path.
        val farPoint = Point2D(0.95f, 0.95f)
        val events = detector.processFrame(leftHandFrame(310L, farPoint, confidence = 0.9f), identity)

        assertEquals(1, events.size)
        val released = events.single()
        assertEquals(ContactEventType.RELEASED, released.type)
        assertEquals(Limb.LEFT_HAND, released.limb)
        assertEquals(1, released.holdId)
        assertEquals(310L, released.timestampMs)
        assertEquals(EvidenceQuality.UNCERTAIN, released.evidenceQuality)
        assertEquals(ReleaseReason.IMPLAUSIBLE_JUMP, released.releaseReason)

        val state = detector.stateOf(Limb.LEFT_HAND)
        assertNull("establishedHoldId must be cleared the same frame as the jump, not after further decay", state.establishedHoldId)
        assertEquals(1, state.previousHoldId)
        // The implausible-jump path is an immediate hard reset - GapState.NONE, never the gradual
        // GapState.DECAYING a real tracking gap of the same nominal magnitude would produce.
        assertEquals(GapState.NONE, state.gapState)
        assertEquals(0f, state.establishedConfidence, 1e-6f)
    }

    @Test
    fun `gap-decay confidence is identical regardless of how many intermediate gap frames are polled`() {
        // Regression test: decay must be a pure function of elapsed gap time since the last
        // resolved frame, anchored at a FIXED confidence snapshot - never compounded by however
        // many intermediate untracked frames the caller happens to poll during the same gap.
        val detectorPolledOnce = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detectorPolledOnce)
        detectorPolledOnce.processFrame(untrackedFrame(750L), identity) // one poll, 450ms after t=300

        val detectorPolledRepeatedly = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detectorPolledRepeatedly)
        // Same underlying 450ms gap (t=300 -> t=750), but sampled every 50ms along the way.
        for (t in 350L..750L step 50L) {
            detectorPolledRepeatedly.processFrame(untrackedFrame(t), identity)
        }

        val confidenceOnce = detectorPolledOnce.stateOf(Limb.LEFT_HAND).establishedConfidence
        val confidenceRepeated = detectorPolledRepeatedly.stateOf(Limb.LEFT_HAND).establishedConfidence
        val expected = 0.9f * (1f - (450f - 200f) / (500f - 200f))

        assertEquals(expected, confidenceOnce, 1e-4f)
        assertEquals(
            "polling the same real-time gap more densely must not compound the decay",
            expected,
            confidenceRepeated,
            1e-4f,
        )
    }

    @Test
    fun `a tracking gap's elapsed time is excluded from candidate dwell, not counted toward it`() {
        // Regression test: a limb must not be promoted to ESTABLISHED using dwell time that
        // includes a blackout where its actual position was unknown.
        val secondHold = HoldShape(
            holdId = 2,
            contourNormalized = listOf(Point2D(0.7f, 0.7f), Point2D(0.9f, 0.7f), Point2D(0.9f, 0.9f), Point2D(0.7f, 0.9f)),
        )
        val detector = HoldContactDetector(listOf(secondHold))
        val holdCenter2 = Point2D(0.8f, 0.8f)

        // Candidate starts at t=0.
        val firstEvents = detector.processFrame(leftHandFrame(0L, holdCenter2), identity)
        assertTrue(firstEvents.isEmpty())
        assertEquals(2, detector.stateOf(Limb.LEFT_HAND).candidateHoldId)

        // A long gap - 490ms, just under the 500ms tracking-gap reset threshold, so the candidate
        // survives (not hard-reset) but its clock must not silently advance through the blackout.
        detector.processFrame(untrackedFrame(490L), identity)
        assertEquals(2, detector.stateOf(Limb.LEFT_HAND).candidateHoldId)

        // Tracked again at t=495 - naive (gap-unaware) dwell math would compute 495-0=495ms, well
        // past the 300ms establish threshold, and wrongly establish immediately. The real
        // continuously-observed dwell is only ~5ms (0 to 490 was a gap; 490 to 495 is real), so
        // this must NOT establish yet.
        val eventsRightAfterGap = detector.processFrame(leftHandFrame(495L, holdCenter2), identity)
        assertTrue(
            "a gap's dead time must not count toward candidate dwell",
            eventsRightAfterGap.none { it.type == ContactEventType.ESTABLISHED },
        )
        assertNull(detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        assertEquals(2, detector.stateOf(Limb.LEFT_HAND).candidateHoldId)

        // A full, genuinely continuous dwell period after the gap ends DOES establish it.
        val eventsAfterRealDwell = detector.processFrame(leftHandFrame(495L + 300L, holdCenter2), identity)
        assertEquals(1, eventsAfterRealDwell.size)
        assertEquals(ContactEventType.ESTABLISHED, eventsAfterRealDwell[0].type)
        assertEquals(2, eventsAfterRealDwell[0].holdId)
    }

    @Test
    fun `fast but plausible movement within the displacement bound does not reset contact`() {
        val detector = HoldContactDetector(listOf(hold))
        establishLeftHandOnHold(detector)
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // 10ms elapsed - max plausible displacement is 0.04. Moving to (0.53, 0.5) is a
        // displacement of 0.03, under that bound, and still comfortably inside the hold - real,
        // fast-but-legitimate motion must not trigger the implausible-jump reset.
        val nearbyPoint = Point2D(0.53f, 0.5f)
        val events = detector.processFrame(leftHandFrame(310L, nearbyPoint, confidence = 0.9f), identity)

        assertTrue("plausible fast motion must not emit a RELEASED event", events.none { it.type == ContactEventType.RELEASED })
        assertEquals(1, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
    }
}
