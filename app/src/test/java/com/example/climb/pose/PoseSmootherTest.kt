package com.example.climb.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private fun lm(x: Float, y: Float) = PoseLandmark(PoseLandmarkType.LEFT_HIP, x, y, 0f, 1f, 1f)

private fun frame(timestampMs: Long, x: Float, y: Float, reliable: Boolean = true) = PoseFrame(
    timestampMs = timestampMs,
    landmarks = listOf(lm(x, y)),
    averageConfidence = if (reliable) 1f else 0f,
    isReliable = reliable,
    bodyBoundingBox = null,
)

private fun PoseFrame.x() = landmarks.first().normalizedX

class PoseSmootherTest {

    @Test
    fun `empty sequence passes through unchanged`() {
        assertEquals(emptyList<PoseFrame>(), smoothPoseSequence(emptyList()))
    }

    @Test
    fun `jitter around a still position is reduced`() {
        val random = Random(42)
        val frames = (0 until 30).map { i ->
            frame(i * 33L, 0.5f + (random.nextFloat() - 0.5f) * 0.02f, 0.5f)
        }
        val smoothed = smoothPoseSequence(frames)

        fun jitter(seq: List<PoseFrame>) = (1 until seq.size).map { abs(seq[it].x() - seq[it - 1].x()) }.average()
        val rawJitter = jitter(frames)
        val smoothedJitter = jitter(smoothed)
        assertTrue("expected smoothing to reduce frame-to-frame jitter (raw=$rawJitter, smoothed=$smoothedJitter)", smoothedJitter < rawJitter)
    }

    @Test
    fun `a fast deliberate move is not blurred away to near-stillness`() {
        // A clean, fast linear sweep across the frame over ~300ms - a deadpoint-speed move.
        val frames = (0..10).map { i -> frame(i * 30L, 0.1f + i * 0.08f, 0.5f) }
        val smoothed = smoothPoseSequence(frames)
        val rawTravel = frames.last().x() - frames.first().x()
        val smoothedTravel = smoothed.last().x() - smoothed.first().x()
        assertTrue(
            "expected a fast deliberate move to survive smoothing mostly intact (raw=$rawTravel, smoothed=$smoothedTravel)",
            smoothedTravel > rawTravel * 0.7f,
        )
    }

    @Test
    fun `unreliable frames pass through untouched`() {
        val frames = listOf(
            frame(0, 0.5f, 0.5f),
            frame(100, 0.5f, 0.5f),
            frame(200, 0.9f, 0.9f, reliable = false),
        )
        val smoothed = smoothPoseSequence(frames)
        assertEquals(0.9f, smoothed[2].x())
        assertEquals(false, smoothed[2].isReliable)
    }

    @Test
    fun `filters reset after an unreliable frame rather than blending across it`() {
        // Two reliable frames at the same spot, an unreliable spike far away, then a reliable
        // frame back near the original spot - the post-reset filter is fed a first value, so it
        // must return that value untouched rather than something dragged toward the spike.
        val frames = listOf(
            frame(0, 0.5f, 0.5f),
            frame(33, 0.5f, 0.5f),
            frame(66, 0.99f, 0.99f, reliable = false),
            frame(99, 0.51f, 0.5f),
        )
        val smoothed = smoothPoseSequence(frames)
        assertEquals(0.51f, smoothed[3].x())
    }

    @Test
    fun `a large time gap between reliable frames resets smoothing state`() {
        val frames = listOf(
            frame(0, 0.5f, 0.5f),
            frame(33, 0.5f, 0.5f),
            frame(2000, 0.9f, 0.9f), // gap > maxGapMs
        )
        val smoothed = smoothPoseSequence(frames, maxGapMs = 500L)
        // First frame seen after a reset returns the raw value unchanged.
        assertEquals(0.9f, smoothed[2].x())
    }

    @Test
    fun `preserves frame count, order, and timestamps`() {
        val frames = (0 until 5).map { i -> frame(i * 100L, 0.5f, 0.5f) }
        val smoothed = smoothPoseSequence(frames)
        assertEquals(frames.map { it.timestampMs }, smoothed.map { it.timestampMs })
    }
}
