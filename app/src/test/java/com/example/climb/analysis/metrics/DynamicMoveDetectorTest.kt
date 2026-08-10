package com.example.climb.analysis.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

private val CONFIG = MetricsConfiguration()

class DynamicMoveDetectorTest {

    @Test
    fun `a single peak right at the start of a sequence is still detected`() {
        // Regression test: lastPeakMs used to start at Long.MIN_VALUE, so
        // `v.timestampMs - lastPeakMs` overflowed and wrapped negative for any timestamp near
        // zero - real video timestamps always are - silently dropping every video's first move.
        val velocities = listOf(
            TimedVelocity(100, 2.857f),
            TimedVelocity(200, 2.0f),
            TimedVelocity(300, 1.4f),
            TimedVelocity(400, 0.98f),
            TimedVelocity(500, 0.68f),
        )
        val peaks = detectLargeDynamicMoves(velocities, CONFIG)
        assertEquals(listOf(100L), peaks)
    }

    @Test
    fun `two peaks more than 500ms apart are both detected`() {
        val velocities = listOf(
            TimedVelocity(0, 1.5f),
            TimedVelocity(100, 0.2f),
            TimedVelocity(700, 1.6f),
            TimedVelocity(800, 0.1f),
        )
        val peaks = detectLargeDynamicMoves(velocities, CONFIG)
        assertEquals(listOf(0L, 700L), peaks)
    }

    @Test
    fun `a second nearby peak within 500ms of the first is suppressed`() {
        val velocities = listOf(
            TimedVelocity(0, 1.5f),
            TimedVelocity(100, 1.2f),
            TimedVelocity(200, 1.6f),
        )
        val peaks = detectLargeDynamicMoves(velocities, CONFIG)
        assertEquals(listOf(0L), peaks)
    }

    @Test
    fun `velocities below the threshold produce no peaks`() {
        val velocities = listOf(TimedVelocity(0, 0.2f), TimedVelocity(100, 0.5f), TimedVelocity(200, 0.3f))
        assertEquals(emptyList<Long>(), detectLargeDynamicMoves(velocities, CONFIG))
    }
}
