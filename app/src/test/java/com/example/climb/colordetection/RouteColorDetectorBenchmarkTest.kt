package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 performance benchmark: `RouteColorDetector.detect()` runs post-hoc, once per reference
 * frame ("review after recording" delivery mode — not live/per-frame), so it is not
 * latency-critical the way a real-time preview would be. This test still measures and logs real
 * wall-clock timing on a video-frame-sized synthetic buffer as a basic sanity check, and only
 * fails on a wildly generous ceiling meant to catch a catastrophic regression (an infinite loop, or
 * an accidental O(n^2)-over-the-whole-frame blowup) — NOT to enforce tight timing, since dev
 * machines vary far too much for that to be a meaningful regression signal here.
 */
class RouteColorDetectorBenchmarkTest {

    /** Deliberately generous: this is a "did something go catastrophically wrong" tripwire, not a
     * performance target. A real device doing this once per recorded clip has a very different
     * latency budget than a live effect would; this bound exists only to catch a regression like an
     * accidental infinite loop or quadratic blowup over a full HD-ish frame, not to tune throughput. */
    private val CATASTROPHIC_REGRESSION_CEILING_MS = 20_000L

    @Test
    fun `detect completes on a 1080x1920-sized synthetic frame within a generous sanity ceiling`() {
        val width = 1080
        val height = 1920
        val gray = RgbColor(128, 128, 128)
        val red = RgbColor.fromArgbHex(RouteColor.RED.hex)

        val buffer = PixelBuffer.filled(width, height, gray)
        // A handful of hold-sized red squares scattered across the frame, roughly like a real
        // route. 180x180 each (not a smaller size like 60x60) - Phase 8's own regression tests
        // discovered that an isolated hold much smaller than ~150px surrounded by uniform wall on
        // all sides currently fails Phase 5's whole-object consistency floor due to a wall-halo
        // dilution effect from Phase 4's bounded growth (see RouteColorDetectorRegressionTest's own
        // class doc for the full finding) - using a size that's known to actually validate keeps
        // this benchmark measuring real detect() throughput, not accidentally re-exercising that
        // separate, already-documented gap. Spaced well beyond the max growth radius so none merge.
        val holdPositions = listOf(
            50 to 50, 50 to 300, 50 to 550, 50 to 800,
            600 to 50, 600 to 300, 600 to 550, 600 to 800,
        )
        for ((x, y) in holdPositions) {
            buffer.fillRect(x, y, x + 180, y + 180, red)
        }

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

        val startNanos = System.nanoTime()
        val holds = RouteColorDetector.detect(buffer, targetModel)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        println("RouteColorDetector.detect() on a ${width}x$height frame with ${holdPositions.size} holds took ${elapsedMs}ms and found ${holds.size} hold(s)")

        assertTrue("expected to find the ${holdPositions.size} planted holds, found ${holds.size}", holds.size == holdPositions.size)
        assertTrue(
            "detect() took ${elapsedMs}ms, past the generous $CATASTROPHIC_REGRESSION_CEILING_MS ms sanity ceiling — this suggests a real performance regression, not normal machine variance",
            elapsedMs < CATASTROPHIC_REGRESSION_CEILING_MS,
        )
    }
}
