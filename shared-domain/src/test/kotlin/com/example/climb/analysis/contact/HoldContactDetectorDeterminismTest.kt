package com.example.climb.analysis.contact

import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldContactDetectorDeterminismTest {

    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.1f): HoldShape =
        HoldShape(
            id,
            listOf(
                Point2D(centerX - halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY - halfWidth),
                Point2D(centerX + halfWidth, centerY + halfWidth),
                Point2D(centerX - halfWidth, centerY + halfWidth),
            ),
        )

    private fun rightHandFrame(timestampMs: Long, point: Point2D, confidence: Float = 0.9f): ContactPoseFrame =
        ContactPoseFrame(
            timestampMs,
            mapOf(
                ContactLandmarkType.RIGHT_INDEX to ContactLandmark(point, confidence),
                ContactLandmarkType.RIGHT_PINKY to ContactLandmark(point, confidence),
                ContactLandmarkType.RIGHT_THUMB to ContactLandmark(point, confidence),
            ),
        )

    private fun leftHandFrame(timestampMs: Long, point: Point2D, confidence: Float = 0.9f): ContactPoseFrame =
        ContactPoseFrame(
            timestampMs,
            mapOf(
                ContactLandmarkType.LEFT_INDEX to ContactLandmark(point, confidence),
                ContactLandmarkType.LEFT_PINKY to ContactLandmark(point, confidence),
                ContactLandmarkType.LEFT_THUMB to ContactLandmark(point, confidence),
            ),
        )

    /**
     * One continuous, real-time approach: a left hand travels straight up (x held fixed at 0.5)
     * from [startY] at t=0 to [holdCenterY] at t=[travelDurationMs], then sits still at
     * [holdCenterY] for the remainder of [totalDurationMs] - a real grip-and-hold motion, not a
     * synthetic per-frame trick. [stepMs] is the only thing that differs between a "dense" and a
     * "sparse" sampling of this exact same underlying path.
     */
    private fun sampleVerticalApproach(
        stepMs: Long,
        travelDurationMs: Long,
        totalDurationMs: Long,
        x: Float,
        startY: Float,
        holdCenterY: Float,
    ): List<ContactPoseFrame> =
        (0L..totalDurationMs step stepMs).map { t ->
            val progress = (t.toFloat() / travelDurationMs.toFloat()).coerceIn(0f, 1f)
            val y = startY + (holdCenterY - startY) * progress
            leftHandFrame(t, Point2D(x, y))
        }

    // --- Scenario 1: transform.apply() is actually load-bearing -----------------------------

    @Test
    fun `a non-identity transform correctly relocates a capture-space point onto a hold`() {
        val hold = squareHold(id = 7, centerX = 0.2f, centerY = 0.2f, halfWidth = 0.05f)
        val detector = HoldContactDetector(listOf(hold))
        // 180-degree rotation maps (x,y) -> (1-x,1-y), so this capture-space point maps exactly
        // onto the hold's center once (and only once) the transform is actually applied.
        val rotated180 = CaptureToReferenceTransform.identity(wallCalibrationId = 99L).copy(rotationDegrees = 180)
        val captureSpacePoint = Point2D(0.8f, 0.8f)

        val firstFrameEvents = detector.processFrame(rightHandFrame(0L, captureSpacePoint), rotated180)
        assertTrue("first frame should only start a candidate, not establish yet", firstFrameEvents.isEmpty())

        val secondFrameEvents = detector.processFrame(rightHandFrame(400L, captureSpacePoint), rotated180)
        assertEquals(1, secondFrameEvents.size)
        assertEquals(ContactEventType.ESTABLISHED, secondFrameEvents[0].type)
        assertEquals(7, secondFrameEvents[0].holdId)
        assertEquals(Limb.RIGHT_HAND, secondFrameEvents[0].limb)
        assertEquals(1, detector.timeline.establishedEventCount())
    }

    @Test
    fun `the same raw capture-space point through the identity transform never establishes`() {
        val hold = squareHold(id = 7, centerX = 0.2f, centerY = 0.2f, halfWidth = 0.05f)
        val detector = HoldContactDetector(listOf(hold))
        val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 99L)
        // Same raw capture point as the previous test - but fed through identity this time, so it
        // is used as-is: (0.8, 0.8) is nowhere near the hold at (0.2, 0.2).
        val captureSpacePoint = Point2D(0.8f, 0.8f)

        detector.processFrame(rightHandFrame(0L, captureSpacePoint), identity)
        detector.processFrame(rightHandFrame(400L, captureSpacePoint), identity)

        assertTrue("an untransformed far-away point must never establish contact", detector.timeline.events.isEmpty())
        assertEquals(0, detector.timeline.establishedEventCount())
    }

    // --- Scenario 2: deterministic event sequence --------------------------------------------

    /**
     * Timings are chosen so every frame-to-frame jump stays within
     * [HoldContactConfig.maxPlausibleNormalizedDisplacementPerMs] of the *previous* frame - e.g.
     * the far-away -> hold-center jump (displacement ~0.636) needs at least ~159ms elapsed at the
     * default 0.004/ms budget to read as plausible motion rather than a tracking failure; 200ms
     * comfortably clears that so this test exercises the ESTABLISHED/RELEASED path, not the
     * separate implausible-jump-reset path (that has its own dedicated test elsewhere).
     */
    private fun buildMixedScenarioFrames(): List<ContactPoseFrame> {
        val holdCenter = Point2D(0.5f, 0.5f)
        val farAway = Point2D(0.05f, 0.05f)
        return listOf(
            rightHandFrame(0L, farAway),
            rightHandFrame(200L, holdCenter), // becomes a candidate (plausible 200ms approach)
            rightHandFrame(600L, holdCenter), // 400ms dwell >= 300ms threshold -> ESTABLISHED
            rightHandFrame(700L, holdCenter), // stays established
            rightHandFrame(2200L, farAway), // plausible (1500ms) departure, beyond release threshold -> RELEASED
        )
    }

    @Test
    fun `two fresh detectors given the same frame sequence produce exactly the same event list`() {
        val holds = listOf(squareHold(id = 1, centerX = 0.5f, centerY = 0.5f))
        val config = HoldContactConfig()
        val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)
        val frames = buildMixedScenarioFrames()

        val detectorA = HoldContactDetector(holds, config)
        val detectorB = HoldContactDetector(holds, config)

        frames.forEach { detectorA.processFrame(it, identity) }
        frames.forEach { detectorB.processFrame(it, identity) }

        assertEquals(detectorA.timeline.events, detectorB.timeline.events)
        // Sanity check that this scenario actually exercised something non-trivial, so the
        // equality assertion above isn't vacuously comparing two empty lists.
        assertEquals(2, detectorA.timeline.events.size)
        assertEquals(ContactEventType.ESTABLISHED, detectorA.timeline.events[0].type)
        assertEquals(ContactEventType.RELEASED, detectorA.timeline.events[1].type)
    }

    // --- Scenario 3: unique contact events are independent of frame rate --------------------

    @Test
    fun `dense and sparse sampling of the same continuous approach yield the same established event count`() {
        val hold = squareHold(id = 1, centerX = 0.5f, centerY = 0.5f)
        val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)

        val denseFrames = sampleVerticalApproach(
            stepMs = 10L,
            travelDurationMs = 1000L,
            totalDurationMs = 2000L,
            x = 0.5f,
            startY = 0.1f,
            holdCenterY = 0.5f,
        )
        val sparseFrames = sampleVerticalApproach(
            stepMs = 50L,
            travelDurationMs = 1000L,
            totalDurationMs = 2000L,
            x = 0.5f,
            startY = 0.1f,
            holdCenterY = 0.5f,
        )
        // Same continuous path, genuinely different sampling density.
        assertTrue(denseFrames.size > sparseFrames.size * 4)

        val denseDetector = HoldContactDetector(listOf(hold))
        val sparseDetector = HoldContactDetector(listOf(hold))

        denseFrames.forEach { denseDetector.processFrame(it, identity) }
        sparseFrames.forEach { sparseDetector.processFrame(it, identity) }

        assertEquals(1, denseDetector.timeline.establishedEventCount())
        assertEquals(1, sparseDetector.timeline.establishedEventCount())
        assertEquals(
            denseDetector.timeline.establishedEventCount(),
            sparseDetector.timeline.establishedEventCount(),
        )
    }
}
