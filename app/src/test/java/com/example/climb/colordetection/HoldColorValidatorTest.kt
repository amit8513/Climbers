package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldColorValidatorTest {

    private val gray = RgbColor(128, 128, 128)
    private val black = RgbColor(10, 10, 10)
    private val red = RgbColor.fromArgbHex(RouteColor.RED.hex)

    // --- 1. A "real hold" case: modest/expected growth, high consistency, validates cleanly ---

    @Test
    fun `a hold surrounded by a wall distinct enough to block growth validates with near-perfect consistency`() {
        val buffer = PixelBuffer.filled(200, 200, black) // near-black wall: far from red in both L* and color
        buffer.fillRect(80, 80, 120, 120, red) // 40x40 red hold

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        assertEquals(1, refined.size)

        val validation = HoldColorValidator.validate(buffer, targetModel, candidates, refined.single())

        assertTrue("clean hold should have near-perfect own-median consistency, was ${validation.colorConsistencyRatioVsOwnMedian}", validation.colorConsistencyRatioVsOwnMedian > 0.95)
        assertTrue("clean hold should have near-perfect target-center consistency, was ${validation.colorConsistencyRatioVsTargetCenter}", validation.colorConsistencyRatioVsTargetCenter > 0.95)
        assertTrue("clean hold's growth area ratio should be modest, was ${validation.growthAreaRatio}", validation.growthAreaRatio < RouteColorDetectionConfig.MAX_GROWTH_AREA_RATIO)
        assertTrue("clean hold must pass the Phase 5 floor", validation.passesFloor)

        val detected = RouteColorDetector.detect(buffer, targetModel)
        assertEquals(1, detected.size)
        assertTrue("a clean hold must score with high final confidence, was ${detected.single().confidence}", detected.single().confidence > 0.8)
    }

    // --- 2. Mandatory hard case: wall-leak rejection. Directly reuses Phase 4's own documented,
    // accepted "halo" scenario (a hold surrounded by a flat, similarly-lit, low-saturation gray
    // wall) to prove Phase 5's validation mechanism actually catches what Phase 4 could only
    // bound, not eliminate. ---

    @Test
    fun `a hold whose halo leaked into a similarly-lit gray wall fails the Phase 5 consistency floor`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        // 20x20 red hold, open gray wall on every side. Was 40x40 before
        // GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT was reduced (0.15 -> 0.05) to fix the small-hold
        // wall-dilution rejection bug — a 40x40 hold now clears the consistency floor (measured
        // ~0.83) instead of failing it, so it no longer demonstrates a real halo-rejection case.
        // 20x20 still genuinely fails (measured ~0.71, below the 0.75 floor) — see
        // RouteColorDetectorRegressionTest's own "20px and under" regression test for the pinned
        // exact number.
        buffer.fillRect(80, 80, 100, 100, red)

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        assertEquals(1, candidates.size)
        val originalArea = candidates.single().area
        assertEquals(400, originalArea) // 20x20, sanity check on the fixture itself

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        assertEquals(1, refined.size)
        val hold = refined.single()
        // Sanity: growth actually happened (gray's L* sits close enough to red's own L* that the
        // Sobel edge gate doesn't fire at the boundary — see HoldBoundaryRefiner's own doc comment
        // on this exact limitation), i.e. this fixture is a real, not hypothetical, halo case.
        assertTrue("growth must have actually leaked area for this to be a real test of the mechanism", hold.area > originalArea)

        val validation = HoldColorValidator.validate(buffer, targetModel, candidates, hold)

        assertTrue(
            "a hold whose halo is dominated by wall pixels must show low own-median consistency, was ${validation.colorConsistencyRatioVsOwnMedian}",
            validation.colorConsistencyRatioVsOwnMedian < RouteColorDetectionConfig.MIN_COLOR_CONSISTENCY_RATIO,
        )
        assertTrue("this hold must fail the Phase 5 floor", !validation.passesFloor)

        // And the full facade must actually drop it, not merely score it low.
        val detected = RouteColorDetector.detect(buffer, targetModel)
        assertTrue("RouteColorDetector must reject the wall-leak hold entirely", detected.isEmpty())
    }

    // --- 3. colorConsistencyRatioVsOwnMedian and vsTargetCenter can genuinely diverge ---

    @Test
    fun `own-median and target-center consistency ratios diverge when wall pixels become a large share of a small hold`() {
        // A small hold relative to its (floor-clamped) growth radius, so wall pixels end up as a
        // large fraction of the final mask, pulling the hold's own median noticeably toward gray
        // even while the fixed, non-drifting target center stays pinned at true red.
        val buffer = PixelBuffer.filled(100, 100, gray)
        buffer.fillRect(45, 45, 51, 51, red) // 6x6 = 36px red hold

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        assertEquals(1, candidates.size)

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        assertEquals(1, refined.size)
        val hold = refined.single()
        assertTrue("growth must have added wall pixels for this fixture to be meaningful", hold.area > 36)

        val validation = HoldColorValidator.validate(buffer, targetModel, candidates, hold)

        // The two ratios must be real, independently computed numbers, not the same value, and
        // target-center consistency (measured against the fixed true-red center) must be strictly
        // lower than own-median consistency (measured against a median that has itself drifted
        // toward the absorbed gray pixels).
        assertTrue(
            "own-median and target-center consistency must diverge: own=${validation.colorConsistencyRatioVsOwnMedian}, target=${validation.colorConsistencyRatioVsTargetCenter}",
            validation.colorConsistencyRatioVsOwnMedian - validation.colorConsistencyRatioVsTargetCenter > 0.1,
        )
    }
}
