package com.example.climb.analysis.contact

import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldContactDetectorEstablishmentTest {

    private val identityTransform = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)
    private val config = HoldContactConfig()

    /** A square hold centered at ([centerX], [centerY]) with the given half-width, in
     * `WallReferenceSpace`-normalized coordinates (matches the detector's `holds` contract). */
    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.1f): HoldShape =
        HoldShape(
            holdId = id,
            contourNormalized = listOf(
                Point2D(centerX - halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY + halfWidth),
                Point2D(centerX - halfWidth, centerY + halfWidth),
            ),
        )

    /** The [ContactLandmarkType]s [LimbProxyResolver] averages as the *primary* set for [limb] —
     * used to build fixtures that always resolve via the primary (non-fallback) path. */
    private fun primaryLandmarkTypesFor(limb: Limb): List<ContactLandmarkType> = when (limb) {
        Limb.LEFT_HAND -> listOf(ContactLandmarkType.LEFT_INDEX, ContactLandmarkType.LEFT_PINKY, ContactLandmarkType.LEFT_THUMB)
        Limb.RIGHT_HAND -> listOf(ContactLandmarkType.RIGHT_INDEX, ContactLandmarkType.RIGHT_PINKY, ContactLandmarkType.RIGHT_THUMB)
        Limb.LEFT_FOOT -> listOf(ContactLandmarkType.LEFT_ANKLE, ContactLandmarkType.LEFT_HEEL)
        Limb.RIGHT_FOOT -> listOf(ContactLandmarkType.RIGHT_ANKLE, ContactLandmarkType.RIGHT_HEEL)
    }

    /** Builds a [ContactPoseFrame] where [limb]'s primary landmark set all sit at [point] with
     * [confidence] — every other limb is left entirely untracked (a tracking gap for it), which
     * keeps each test focused on the one limb under test. */
    private fun frameWithLimbAt(
        timestampMs: Long,
        limb: Limb,
        point: Point2D,
        confidence: Float = 0.9f,
    ): ContactPoseFrame {
        val landmarks = primaryLandmarkTypesFor(limb).associateWith { ContactLandmark(point, confidence) }
        return ContactPoseFrame(timestampMs = timestampMs, landmarks = landmarks)
    }

    private fun eventsOf(events: List<HoldContactEvent>, type: ContactEventType): List<HoldContactEvent> =
        events.filter { it.type == type }

    @Test
    fun `limb enters and establishes hold after dwell`() {
        val hold = squareHold(id = 42, centerX = 0.5f, centerY = 0.5f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        // t=0: proxy first lands inside the hold - starts the candidate dwell timer, no event yet.
        val firstEvents = detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center), identityTransform)
        assertTrue("no ESTABLISHED event expected on the very first contact frame", firstEvents.isEmpty())

        // t=300 (== contactEstablishedDwellMs): dwell requirement is satisfied this frame.
        val secondEvents = detector.processFrame(
            frameWithLimbAt(config.contactEstablishedDwellMs, Limb.LEFT_HAND, center),
            identityTransform,
        )

        val established = eventsOf(secondEvents, ContactEventType.ESTABLISHED)
        assertEquals(1, established.size)
        assertEquals(Limb.LEFT_HAND, established[0].limb)
        assertEquals(42, established[0].holdId)
        assertEquals(config.contactEstablishedDwellMs, established[0].timestampMs)

        assertEquals(42, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
    }

    @Test
    fun `contact does not establish before dwell elapses`() {
        val hold = squareHold(id = 7, centerX = 0.5f, centerY = 0.5f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center), identityTransform)
        val eventsBeforeDwell = detector.processFrame(
            frameWithLimbAt(config.contactEstablishedDwellMs - 100L, Limb.LEFT_HAND, center),
            identityTransform,
        )

        assertTrue(
            "no ESTABLISHED event should fire before the dwell threshold elapses",
            eventsOf(eventsBeforeDwell, ContactEventType.ESTABLISHED).isEmpty(),
        )
        assertTrue(detector.timeline.events.none { it.type == ContactEventType.ESTABLISHED })
        assertNull(detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
    }

    @Test
    fun `release hysteresis prevents flicker until distance clearly exceeds the release threshold`() {
        val hold = squareHold(id = 3, centerX = 0.5f, centerY = 0.5f, halfWidth = 0.1f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center), identityTransform)
        detector.processFrame(frameWithLimbAt(config.contactEstablishedDwellMs, Limb.LEFT_HAND, center), identityTransform)
        assertEquals(3, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)

        // (0.5, 0.65) is 0.05 outside the hold's y=0.6 edge: beyond contactCandidateDistanceThreshold
        // (0.025) but still inside contactReleaseDistanceThreshold (0.06) - the hysteresis band.
        val hysteresisPoint = Point2D(0.5f, 0.65f)
        check(config.contactCandidateDistanceThreshold < 0.05f && 0.05f <= config.contactReleaseDistanceThreshold)

        var timestamp = config.contactEstablishedDwellMs
        repeat(4) {
            timestamp += 100L
            val events = detector.processFrame(frameWithLimbAt(timestamp, Limb.LEFT_HAND, hysteresisPoint), identityTransform)
            assertTrue(
                "hold must stay established while inside the release-hysteresis band",
                eventsOf(events, ContactEventType.RELEASED).isEmpty(),
            )
            assertEquals(3, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        }

        // Now move clearly beyond contactReleaseDistanceThreshold (0.06): 0.12 outside the edge.
        val farPoint = Point2D(0.5f, 0.72f)
        timestamp += 100L
        val releaseEvents = detector.processFrame(frameWithLimbAt(timestamp, Limb.LEFT_HAND, farPoint), identityTransform)

        val released = eventsOf(releaseEvents, ContactEventType.RELEASED)
        assertEquals(1, released.size)
        assertEquals(Limb.LEFT_HAND, released[0].limb)
        assertEquals(3, released[0].holdId)
        assertEquals(ReleaseReason.DISTANCE_HYSTERESIS, released[0].releaseReason)
        assertNull(detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        assertEquals(3, detector.stateOf(Limb.LEFT_HAND).previousHoldId)
    }

    @Test
    fun `one low-confidence frame does not erase an established contact`() {
        val hold = squareHold(id = 9, centerX = 0.5f, centerY = 0.5f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center, confidence = 0.9f), identityTransform)
        detector.processFrame(
            frameWithLimbAt(config.contactEstablishedDwellMs, Limb.LEFT_HAND, center, confidence = 0.9f),
            identityTransform,
        )
        assertEquals(9, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        val confidenceBeforeDrop = detector.stateOf(Limb.LEFT_HAND).establishedConfidence

        // Exactly one frame below contactMinFrameConfidence (0.5), still geometrically at the
        // hold's center - must degrade confidence, never destroy the established contact outright.
        val lowConfidenceTimestamp = config.contactEstablishedDwellMs + 50L
        val lowConfidenceEvents = detector.processFrame(
            frameWithLimbAt(lowConfidenceTimestamp, Limb.LEFT_HAND, center, confidence = 0.1f),
            identityTransform,
        )

        assertTrue(
            "a single low-confidence frame must not itself release the hold",
            eventsOf(lowConfidenceEvents, ContactEventType.RELEASED).isEmpty(),
        )
        val stateAfterDrop = detector.stateOf(Limb.LEFT_HAND)
        assertEquals(9, stateAfterDrop.establishedHoldId)
        assertTrue(
            "rolling confidence should degrade after a low-confidence frame",
            stateAfterDrop.establishedConfidence < confidenceBeforeDrop,
        )
        assertTrue("degraded confidence should not itself go negative", stateAfterDrop.establishedConfidence >= 0f)
        // Pin the exact documented formula (rollingConfidence * (frameConfidence /
        // contactMinFrameConfidence)) rather than only its direction/sign, so a wrong-but-still-
        // decreasing decay formula would actually fail this test: 0.9 * (0.1 / 0.5) = 0.18.
        assertEquals(0.9f * (0.1f / config.contactMinFrameConfidence), stateAfterDrop.establishedConfidence, 1e-4f)

        // A subsequent good-confidence frame at the same spot must not crash and must keep (or
        // recover) the established contact.
        val recoveryEvents = detector.processFrame(
            frameWithLimbAt(lowConfidenceTimestamp + 50L, Limb.LEFT_HAND, center, confidence = 0.9f),
            identityTransform,
        )
        assertTrue(eventsOf(recoveryEvents, ContactEventType.RELEASED).isEmpty())
        assertEquals(9, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        assertTrue(
            "a healthy frame should nudge rolling confidence back up",
            detector.stateOf(Limb.LEFT_HAND).establishedConfidence > stateAfterDrop.establishedConfidence,
        )
        // Pin the exact documented healthy-frame formula ((rolling + frame) / 2): (0.18 + 0.9) / 2 = 0.54.
        assertEquals((0.18f + 0.9f) / 2f, detector.stateOf(Limb.LEFT_HAND).establishedConfidence, 1e-4f)
    }

    @Test
    fun `topKNearbyHoldIds is populated nearest-first and bounded to the configured limit`() {
        // Four holds spread along a line, each just far enough apart that their approach-distance
        // zones don't overlap into ambiguity, so proximity order is unambiguous.
        val holds = listOf(
            squareHold(id = 1, centerX = 0.50f, centerY = 0.5f, halfWidth = 0.01f), // nearest
            squareHold(id = 2, centerX = 0.55f, centerY = 0.5f, halfWidth = 0.01f), // 2nd nearest
            squareHold(id = 3, centerX = 0.60f, centerY = 0.5f, halfWidth = 0.01f), // 3rd nearest
            squareHold(id = 4, centerX = 0.65f, centerY = 0.5f, halfWidth = 0.01f), // farthest - beyond topK default of 3
        )
        val configWithSmallApproach = HoldContactConfig(
            contactApproachDistanceThreshold = 0.2f, // wide enough that all 4 holds qualify as "nearby"
        )
        val detector = HoldContactDetector(holds = holds, config = configWithSmallApproach)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, Point2D(0.50f, 0.5f)), identityTransform)

        val nearby = detector.stateOf(Limb.LEFT_HAND).topKNearbyHoldIds
        assertEquals("bounded to the default topKNearbyHolds (3), not all 4 qualifying holds", 3, nearby.size)
        assertEquals(listOf(1, 2, 3), nearby)
    }

    @Test
    fun `establishment via the primary landmark set reports strong evidence quality`() {
        val hold = squareHold(id = 5, centerX = 0.5f, centerY = 0.5f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center, confidence = 0.9f), identityTransform)
        val events = detector.processFrame(
            frameWithLimbAt(config.contactEstablishedDwellMs, Limb.LEFT_HAND, center, confidence = 0.9f),
            identityTransform,
        )

        val established = eventsOf(events, ContactEventType.ESTABLISHED).single()
        assertEquals(EvidenceQuality.STRONG, established.evidenceQuality)
    }

    @Test
    fun `establishing one limb leaves other limbs untouched`() {
        val hold = squareHold(id = 11, centerX = 0.5f, centerY = 0.5f)
        val detector = HoldContactDetector(holds = listOf(hold), config = config)
        val center = Point2D(0.5f, 0.5f)

        detector.processFrame(frameWithLimbAt(0L, Limb.LEFT_HAND, center), identityTransform)
        detector.processFrame(frameWithLimbAt(config.contactEstablishedDwellMs, Limb.LEFT_HAND, center), identityTransform)

        assertEquals(11, detector.stateOf(Limb.LEFT_HAND).establishedHoldId)
        assertNull(detector.stateOf(Limb.RIGHT_HAND).establishedHoldId)
        assertNull(detector.stateOf(Limb.LEFT_FOOT).establishedHoldId)
        assertNull(detector.stateOf(Limb.RIGHT_FOOT).establishedHoldId)
        assertFalse(detector.timeline.eventsFor(Limb.RIGHT_HAND).isNotEmpty())
    }
}
