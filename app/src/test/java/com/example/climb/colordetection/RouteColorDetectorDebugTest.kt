package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteColorDetectorDebugTest {

    private val gray = RgbColor(128, 128, 128)
    private val red = RgbColor.fromArgbHex(RouteColor.RED.hex)

    @Test
    fun `detectWithDebugInfo reports a wall-leak hold as refined but rejected, matching detect's silent drop`() {
        // Same wall-leak fixture HoldColorValidatorTest uses: a hold whose bounded growth leaks
        // into a similarly-lit, low-saturation gray wall (HoldBoundaryRefiner's own documented,
        // accepted "halo" limitation) and fails the Phase 5 consistency floor. 20x20, not 40x40 —
        // see HoldColorValidatorTest's own comment on why 40x40 no longer demonstrates this after
        // the GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT fix.
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(80, 80, 100, 100, red)
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

        val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)

        assertEquals("Phase 3 must still find exactly one raw candidate", 1, debug.candidates.size)
        assertEquals("Phase 4 must still refine it into exactly one hold", 1, debug.refined.size)
        assertEquals("every refined hold must get a debug validation entry", 1, debug.validated.size)

        val debugResult = debug.validated.single()
        assertFalse("the debug result must show this hold failed the floor", debugResult.passesFloor)
        assertTrue(
            "the real numbers must be visible, not just a boolean: was ${debugResult.validation.colorConsistencyRatioVsOwnMedian}",
            debugResult.validation.colorConsistencyRatioVsOwnMedian < RouteColorDetectionConfig.MIN_COLOR_CONSISTENCY_RATIO,
        )

        // And detect() itself must still silently drop it, completely unaffected by this new path.
        val finalResult = RouteColorDetector.detect(buffer, targetModel)
        assertTrue("detect() must still reject this hold entirely, unaffected by detectWithDebugInfo existing", finalResult.isEmpty())
    }

    @Test
    fun `detectWithDebugInfo reports a clean hold as refined and passing, with a real confidence score`() {
        val black = RgbColor(10, 10, 10)
        val buffer = PixelBuffer.filled(200, 200, black)
        buffer.fillRect(80, 80, 120, 120, red)
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

        val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)

        assertEquals(1, debug.refined.size)
        val debugResult = debug.validated.single()
        assertTrue("a clean hold must pass the floor", debugResult.passesFloor)
        assertTrue("a clean hold must score high confidence, was ${debugResult.confidence}", debugResult.confidence > 0.8)

        val finalResult = RouteColorDetector.detect(buffer, targetModel)
        assertEquals(1, finalResult.size)
        assertEquals(
            "detect()'s own final confidence must match the debug path's independently-computed confidence",
            finalResult.single().confidence,
            debugResult.confidence,
            0.0001,
        )
    }

    @Test
    fun `detectWithDebugInfo returns all-empty lists when Phase 3 finds no candidates at all`() {
        val buffer = PixelBuffer.filled(50, 50, gray) // no red anywhere
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

        val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)

        assertTrue(debug.candidates.isEmpty())
        assertTrue(debug.refined.isEmpty())
        assertTrue(debug.validated.isEmpty())
    }
}
