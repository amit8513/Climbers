package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun frame(timestampMs: Long, reliable: Boolean, confidence: Float = if (reliable) 1f else 0f) = PoseFrame(
    timestampMs = timestampMs,
    landmarks = emptyList(),
    averageConfidence = confidence,
    isReliable = reliable,
    bodyBoundingBox = null,
)

class PoseQualityEvaluatorTest {

    @Test
    fun `empty frames produce an empty report`() {
        val report = evaluatePoseQuality(emptyList())
        assertEquals(0f, report.overallReliableFramePercentage)
        assertTrue(report.ranges.isEmpty())
    }

    @Test
    fun `overall reliable percentage reflects the whole sequence`() {
        val frames = (0 until 10).map { i -> frame(i * 100L, reliable = i < 7) }
        val report = evaluatePoseQuality(frames)
        assertEquals(70f, report.overallReliableFramePercentage)
    }

    @Test
    fun `a video longer than one window is split into multiple ranges`() {
        // 5 seconds of frames at a 2s window -> 3 ranges (0-2s, 2-4s, 4-5s).
        val frames = (0..50).map { i -> frame(i * 100L, reliable = true) }
        val report = evaluatePoseQuality(frames, windowMs = 2_000L)
        assertEquals(3, report.ranges.size)
        assertEquals(0L, report.ranges.first().startMs)
        assertEquals(4000L, report.ranges.last().startMs)
    }

    @Test
    fun `a poorly tracked window reports a lower reliable percentage than a well tracked one`() {
        val goodWindow = (0 until 20).map { i -> frame(i * 100L, reliable = true) }
        val badWindow = (20 until 40).map { i -> frame(i * 100L, reliable = i % 2 == 0) }
        val report = evaluatePoseQuality(goodWindow + badWindow, windowMs = 2_000L)
        assertEquals(2, report.ranges.size)
        assertEquals(100f, report.ranges[0].reliableFramePercentage)
        assertEquals(50f, report.ranges[1].reliableFramePercentage)
    }

    @Test
    fun `worstRange picks the lowest-reliability range among those long enough to matter`() {
        val frames = mutableListOf<PoseFrame>()
        // Window 1 (0-2s): fully reliable.
        frames += (0 until 20).map { i -> frame(i * 100L, reliable = true) }
        // Window 2 (2-4s): mostly unreliable - the one that should win.
        frames += (20 until 40).map { i -> frame(i * 100L, reliable = i % 5 == 0) }
        val report = evaluatePoseQuality(frames, windowMs = 2_000L)
        val worst = report.worstRange()
        assertTrue(worst != null && worst.startMs == 2000L)
    }

    @Test
    fun `worstRange ignores ranges shorter than the minimum meaningful length`() {
        // Two full 2s windows, both fully reliable, plus a ~100ms trailing window that is
        // completely unreliable - worse than the others, but too short to be worth surfacing.
        val frames = (0 until 40).map { i -> frame(i * 100L, reliable = true) } +
            listOf(frame(4000L, reliable = false), frame(4100L, reliable = false))
        val report = evaluatePoseQuality(frames, windowMs = 2_000L)
        val worst = report.worstRange(minRangeMs = 1_000L)
        assertTrue("expected the short 0%-reliable tail window to be excluded", worst == null || worst.startMs != 4000L)
    }
}
