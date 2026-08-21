package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimbProxyResolverTest {

    private fun frameOf(vararg entries: Pair<ContactLandmarkType, ContactLandmark>, timestampMs: Long = 0L): ContactPoseFrame =
        ContactPoseFrame(timestampMs = timestampMs, landmarks = entries.toMap())

    private fun landmark(x: Float, y: Float, confidence: Float = 0.9f): ContactLandmark =
        ContactLandmark(position = Point2D(x, y), confidence = confidence)

    private fun assertPoint(expected: Point2D, actual: Point2D, tolerance: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }

    // ---- Hand fallback (LEFT) ----

    @Test
    fun `left hand falls back to wrist when index pinky and thumb are entirely absent`() {
        val wrist = landmark(0.2f, 0.3f, confidence = 0.8f)
        val frame = frameOf(ContactLandmarkType.LEFT_WRIST to wrist)

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNotNull(proxy)
        assertTrue(proxy!!.usedFallback)
        assertPoint(wrist.position, proxy.point)
        assertEquals(wrist.confidence, proxy.confidence, 1e-4f)
    }

    @Test
    fun `left hand falls back to wrist when primary landmarks are present with zero confidence`() {
        val wrist = landmark(0.25f, 0.35f, confidence = 0.7f)
        val frame = frameOf(
            ContactLandmarkType.LEFT_INDEX to landmark(0.1f, 0.1f, confidence = 0f),
            ContactLandmarkType.LEFT_PINKY to landmark(0.1f, 0.1f, confidence = 0f),
            ContactLandmarkType.LEFT_THUMB to landmark(0.1f, 0.1f, confidence = 0f),
            ContactLandmarkType.LEFT_WRIST to wrist,
        )

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNotNull(proxy)
        assertTrue(proxy!!.usedFallback)
        assertPoint(wrist.position, proxy.point)
    }

    // ---- Foot fallback (LEFT) ----

    @Test
    fun `left foot falls back to foot index when ankle and heel are entirely absent`() {
        val footIndex = landmark(0.6f, 0.7f, confidence = 0.65f)
        val frame = frameOf(ContactLandmarkType.LEFT_FOOT_INDEX to footIndex)

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_FOOT, frame)

        assertNotNull(proxy)
        assertTrue(proxy!!.usedFallback)
        assertPoint(footIndex.position, proxy.point)
        assertEquals(footIndex.confidence, proxy.confidence, 1e-4f)
    }

    // ---- Foot fallback (RIGHT), to prove side-selection for feet too ----

    @Test
    fun `right foot falls back to foot index and never reads any left foot landmark`() {
        val rightFootIndex = landmark(0.55f, 0.75f, confidence = 0.6f)
        // A left-side foot-index present with a very different position must never leak in.
        val leftFootIndex = landmark(0.05f, 0.05f, confidence = 0.9f)
        val frame = frameOf(
            ContactLandmarkType.RIGHT_FOOT_INDEX to rightFootIndex,
            ContactLandmarkType.LEFT_FOOT_INDEX to leftFootIndex,
        )

        val proxy = LimbProxyResolver.resolve(Limb.RIGHT_FOOT, frame)

        assertNotNull(proxy)
        assertTrue(proxy!!.usedFallback)
        assertPoint(rightFootIndex.position, proxy.point)
    }

    // ---- Primary present: averaging math ----

    @Test
    fun `left hand primary landmarks all present average to their mean position`() {
        val index = landmark(0.40f, 0.40f, confidence = 0.9f)
        val pinky = landmark(0.50f, 0.50f, confidence = 0.8f)
        val thumb = landmark(0.60f, 0.60f, confidence = 0.7f)
        val frame = frameOf(
            ContactLandmarkType.LEFT_INDEX to index,
            ContactLandmarkType.LEFT_PINKY to pinky,
            ContactLandmarkType.LEFT_THUMB to thumb,
            // Presence of the fallback landmark too must not matter when primary is present.
            ContactLandmarkType.LEFT_WRIST to landmark(0.0f, 0.0f, confidence = 0.9f),
        )

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNotNull(proxy)
        assertFalse(proxy!!.usedFallback)
        // mean of (0.40,0.40), (0.50,0.50), (0.60,0.60) = (0.50, 0.50)
        assertPoint(Point2D(0.50f, 0.50f), proxy.point)
        val expectedConfidence = (0.9f + 0.8f + 0.7f) / 3f
        assertEquals(expectedConfidence, proxy.confidence, 1e-4f)
    }

    @Test
    fun `left hand primary average uses only the subset of primary landmarks actually present`() {
        // Only INDEX and THUMB present this frame; PINKY entirely absent.
        val index = landmark(0.30f, 0.10f, confidence = 0.9f)
        val thumb = landmark(0.70f, 0.50f, confidence = 0.5f)
        val frame = frameOf(
            ContactLandmarkType.LEFT_INDEX to index,
            ContactLandmarkType.LEFT_THUMB to thumb,
        )

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNotNull(proxy)
        assertFalse(proxy!!.usedFallback)
        // mean of (0.30,0.10) and (0.70,0.50) = (0.50, 0.30) -- a different position/mean than the
        // three-landmark case above, proving the averaging math itself (not a hardcoded value).
        assertPoint(Point2D(0.50f, 0.30f), proxy.point)
        val expectedConfidence = (0.9f + 0.5f) / 2f
        assertEquals(expectedConfidence, proxy.confidence, 1e-4f)
    }

    @Test
    fun `left hand primary average excludes a present landmark with zero confidence`() {
        val index = landmark(0.20f, 0.20f, confidence = 0.9f)
        val pinky = landmark(0.40f, 0.40f, confidence = 0.6f)
        val zeroConfidenceThumb = landmark(1.0f, 1.0f, confidence = 0f)
        val frame = frameOf(
            ContactLandmarkType.LEFT_INDEX to index,
            ContactLandmarkType.LEFT_PINKY to pinky,
            ContactLandmarkType.LEFT_THUMB to zeroConfidenceThumb,
        )

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNotNull(proxy)
        assertFalse(proxy!!.usedFallback)
        // If the zero-confidence thumb were wrongly included, the mean would be pulled toward
        // (1.0, 1.0); asserting the two-point mean proves it was excluded.
        assertPoint(Point2D(0.30f, 0.30f), proxy.point)
    }

    // ---- Primary present (RIGHT), to prove side-selection for hands too ----

    @Test
    fun `right hand primary landmarks average and never read any left hand landmark`() {
        val index = landmark(0.20f, 0.80f, confidence = 0.9f)
        val pinky = landmark(0.40f, 0.60f, confidence = 0.9f)
        val thumb = landmark(0.60f, 0.40f, confidence = 0.9f)
        // Left-hand landmarks present with wildly different positions must never leak into the
        // RIGHT_HAND resolution.
        val frame = frameOf(
            ContactLandmarkType.RIGHT_INDEX to index,
            ContactLandmarkType.RIGHT_PINKY to pinky,
            ContactLandmarkType.RIGHT_THUMB to thumb,
            ContactLandmarkType.LEFT_INDEX to landmark(0.01f, 0.01f, confidence = 0.9f),
            ContactLandmarkType.LEFT_PINKY to landmark(0.01f, 0.01f, confidence = 0.9f),
            ContactLandmarkType.LEFT_THUMB to landmark(0.01f, 0.01f, confidence = 0.9f),
            ContactLandmarkType.LEFT_WRIST to landmark(0.01f, 0.01f, confidence = 0.9f),
        )

        val proxy = LimbProxyResolver.resolve(Limb.RIGHT_HAND, frame)

        assertNotNull(proxy)
        assertFalse(proxy!!.usedFallback)
        // mean of (0.20,0.80),(0.40,0.60),(0.60,0.40) = (0.40, 0.60)
        assertPoint(Point2D(0.40f, 0.60f), proxy.point)
    }

    // ---- Fully untracked ----

    @Test
    fun `left hand resolves to null when neither primary nor fallback landmarks are present`() {
        val frame = frameOf(ContactLandmarkType.RIGHT_WRIST to landmark(0.5f, 0.5f, confidence = 0.9f))

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNull(proxy)
    }

    @Test
    fun `left hand resolves to null when the only present landmarks all have zero confidence`() {
        val frame = frameOf(
            ContactLandmarkType.LEFT_INDEX to landmark(0.5f, 0.5f, confidence = 0f),
            ContactLandmarkType.LEFT_WRIST to landmark(0.5f, 0.5f, confidence = 0f),
        )

        val proxy = LimbProxyResolver.resolve(Limb.LEFT_HAND, frame)

        assertNull(proxy)
    }

    @Test
    fun `right foot resolves to null on a completely empty frame`() {
        val proxy = LimbProxyResolver.resolve(Limb.RIGHT_FOOT, ContactPoseFrame(timestampMs = 0L))

        assertNull(proxy)
    }
}
