package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 regression tests: lock in today's known-good segmentation quality, measured via
 * [SegmentationMetrics], on synthetic fixtures built the same way as
 * [HoldComponentDetectorTest]/[HoldBoundaryRefinerTest]'s own — so a future phase or refactor that
 * silently changes segmentation quality on an already-characterized case gets caught by a real
 * numeric bar, not just "detection still returns something." Ground truth is hand-derived from
 * each fixture's own construction (the exact rectangle painted), never from this pipeline's own
 * output — otherwise this would be circular and could never fail.
 *
 * **History, kept for context:** the first version of these tests found a real, previously-uncaught
 * cross-phase gap by running the FULL [RouteColorDetector.detect] pipeline end-to-end on small-hold
 * fixtures [HoldComponentDetectorTest]/[HoldBoundaryRefinerTest] already used in isolation.
 * [HoldBoundaryRefiner]'s bounded growth (Phase 4) expands a candidate outward on EVERY side into a
 * uniform gray wall (the achromatic-bridge exception has no way to tell "wall" from "a legitimate
 * gap on the same hold," by design), and the resulting wall-diluted mask then failed
 * [HoldColorValidator]'s whole-object consistency floor (Phase 5) for anything smaller than roughly
 * 150x150px — at the time, [RouteColorDetectionConfig.GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT]'s
 * original 0.15 value grew a halo ring large enough, relative to a small/medium hold's own size, to
 * dilute its consistency ratio to ~0.61-0.66, below the 0.75 floor — so `detect()` silently rejected
 * a large share of realistically-sized holds entirely (no highlight at all in the real app). That
 * constant has since been reduced to 0.05 specifically to fix this (see its own doc comment in
 * [RouteColorDetectionConfig] for the full geometric reasoning and re-measured numbers) — the first
 * test below now locks in the FIXED, passing behavior for a 30x30 hold, with a companion test
 * confirming the fix's own honestly-acknowledged limit: holds around ~20px and under still fail,
 * because at that size [RouteColorDetectionConfig.MIN_GROWTH_RADIUS_PX]'s fixed 2px floor (not the
 * fraction) dominates the growth radius, and lowering that floor further would break the
 * cutting-band-bridging test below (which needs exactly 2px per side to close a 4px gap).
 */
class RouteColorDetectorRegressionTest {

    private val gray = RgbColor(128, 128, 128)
    private val red = RgbColor.fromArgbHex(RouteColor.RED.hex)
    private val orange = RgbColor.fromArgbHex(RouteColor.ORANGE.hex)

    private fun rectIndices(x0: Int, y0: Int, x1: Int, y1: Int, bufferWidth: Int): Set<Int> {
        val indices = HashSet<Int>()
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                indices += y * bufferWidth + x
            }
        }
        return indices
    }

    @Test
    fun `regression- an isolated 30px hold surrounded by uniform wall now clears the consistency floor after the growth-radius-fraction fix`() {
        val bufferWidth = 200
        val buffer = PixelBuffer.filled(bufferWidth, 200, gray)
        buffer.fillRect(20, 20, 50, 50, red) // 30x30, same size as HoldComponentDetectorTest's own fixture

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)

        assertEquals("Phase 3 seeding is unaffected — this is a Phase 4/5 interaction, not a seeding regression", 1, debug.candidates.size)
        assertEquals("Phase 4 refinement still runs and grows the mask", 1, debug.refined.size)
        assertEquals(1, debug.validated.size)

        val result = debug.validated.single()
        assertTrue(
            "this hold should now clear Phase 5's consistency floor after reducing " +
                "GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT — if this fails, the fix regressed",
            result.passesFloor,
        )
        // Exact measured value (post-fix), pinned so ANY future change to Phase 4's growth or
        // Phase 5's validation on this fixture is visible here, whichever direction it moves. Was
        // 0.6410256410256411 (below the 0.75 floor) before the fix.
        assertEquals(0.7867132867132867, result.validation.colorConsistencyRatioVsOwnMedian, 1e-9)

        val finalHolds = RouteColorDetector.detect(buffer, targetModel)
        assertEquals("detect() should now return this hold instead of silently dropping it", 1, finalHolds.size)
    }

    @Test
    fun `regression- isolated holds from 40px to 180px against uniform wall all clear the consistency floor with real margin`() {
        // Confirms the growth-radius-fraction fix (see this class's doc comment) genuinely helps
        // across the small-to-large range, not just the single 30px case above — and that it keeps
        // the already-passing large-hold case comfortably passing too, not just barely.
        val expectedConsistency = mapOf(
            40 to 0.8316008316008316,
            60 to 0.8310249307479224,
            100 to 0.8305647840531561,
            180 to 0.8302583025830258,
        )
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        for ((size, expected) in expectedConsistency) {
            val bufferWidth = 400
            val buffer = PixelBuffer.filled(bufferWidth, 400, gray)
            buffer.fillRect(150, 150, 150 + size, 150 + size, red)

            val holds = RouteColorDetector.detect(buffer, targetModel)
            assertEquals("size=$size: expected exactly one validated hold", 1, holds.size)

            val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)
            val ratio = debug.validated.single().validation.colorConsistencyRatioVsOwnMedian
            assertTrue(
                "size=$size: expected consistency ratio near $expected, was $ratio",
                kotlin.math.abs(ratio - expected) < 1e-6,
            )
            assertTrue(
                "size=$size: expected real margin above the 0.75 floor, was $ratio",
                ratio >= RouteColorDetectionConfig.MIN_COLOR_CONSISTENCY_RATIO + 0.05,
            )
        }
    }

    @Test
    fun `regression- isolated holds around 20px and under still fail the consistency floor - an honest, accepted remaining limit`() {
        // Documents that the growth-radius-fraction fix does NOT fully solve this for every size:
        // below ~25-30px, MIN_GROWTH_RADIUS_PX's fixed 2px floor (not the fraction) dominates the
        // growth radius, and a fixed 2px ring is inherently a large fraction of a ~15-20px hold's
        // own edge. Lowering that floor further would fix this but break the cutting-band-bridging
        // test below (which needs exactly 2px per side to close its 4px gap) - a real, accepted
        // tradeoff, not something to chase further without reconsidering the whole approach for
        // very small holds. See MIN_GROWTH_RADIUS_PX's own doc comment.
        val expectedConsistency = mapOf(
            15 to 0.6446991404011462,
            20 to 0.7092198581560284,
        )
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        for ((size, expected) in expectedConsistency) {
            val bufferWidth = 400
            val buffer = PixelBuffer.filled(bufferWidth, 400, gray)
            buffer.fillRect(150, 150, 150 + size, 150 + size, red)

            val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)
            val result = debug.validated.single()
            assertTrue(
                "size=$size: expected consistency ratio near $expected, was ${result.validation.colorConsistencyRatioVsOwnMedian}",
                kotlin.math.abs(result.validation.colorConsistencyRatioVsOwnMedian - expected) < 1e-6,
            )
            assertTrue("size=$size: this documents a known, accepted remaining limitation, not a desired outcome", !result.passesFloor)

            val finalHolds = RouteColorDetector.detect(buffer, targetModel)
            assertTrue("size=$size: detect() still drops this hold — the real, current end-to-end behavior for this size", finalHolds.isEmpty())
        }
    }

    @Test
    fun `regression- a large clean hold against gray wall segments with known-good precision and perfect recall`() {
        val bufferWidth = 400
        val buffer = PixelBuffer.filled(bufferWidth, 400, gray)
        buffer.fillRect(100, 100, 280, 280, red) // 180x180 - clears the consistency floor (measured 0.784)
        buffer.fillRect(350, 350, 380, 380, orange) // far-away orange, must not be picked up

        val groundTruth = rectIndices(100, 100, 280, 280, bufferWidth)

        val holds = RouteColorDetector.detect(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))
        assertEquals("expected exactly one validated red hold, found ${holds.size}", 1, holds.size)

        val predicted = SegmentationMetrics.globalIndices(holds.single(), bufferWidth)
        val metrics = SegmentationMetrics.pixelMetrics(predicted, groundTruth)

        // Real measured values were iou=precision=0.8302583025830258, recall=1.0 (was
        // iou=precision=0.7844276583381755 before the growth-radius-fraction fix - improved, as
        // expected, since less growth means less wall dilution) - bars set with a small safety
        // margin below that, not guessed.
        assertTrue("recall regressed: was ${metrics.recall}", metrics.recall >= 0.99)
        assertTrue("IoU/precision regressed below the known-good bar: was ${metrics.iou}", metrics.iou >= 0.75)
    }

    @Test
    fun `regression- cutting-band-fragmented large hold still recovers full recall after refinement and merge`() {
        val bufferWidth = 400
        val buffer = PixelBuffer.filled(bufferWidth, 400, gray)
        buffer.fillRect(100, 100, 280, 280, red) // 180x180 true hold
        buffer.fillRect(100, 188, 280, 192, gray) // 4px-tall full-width band, splits Phase 3's seeding

        val groundTruth = rectIndices(100, 100, 280, 280, bufferWidth)

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val debug = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)
        assertEquals("confirms Phase 3's documented starting point for this regression", 2, debug.candidates.size)
        assertEquals("bounded growth should still bridge the band and merge the two fragments", 1, debug.refined.size)

        val holds = RouteColorDetector.detect(buffer, targetModel)
        assertEquals("expected the merged hold to also pass Phase 5 validation", 1, holds.size)

        val predicted = SegmentationMetrics.globalIndices(holds.single(), bufferWidth)
        val metrics = SegmentationMetrics.pixelMetrics(predicted, groundTruth)

        // Real measured values were iou=precision=0.9180550833049983, recall=1.0 (was
        // iou=precision=0.7846556233653008 before the growth-radius-fraction fix).
        assertTrue("recall regressed: refinement should still recover the full true region, was ${metrics.recall}", metrics.recall >= 0.99)
        assertTrue("IoU/precision regressed below the known-good bar: was ${metrics.iou}", metrics.iou >= 0.75)
    }

    @Test
    fun `regression- whole-object precision and recall across a multi-hold frame with one spurious-adjacent color`() {
        val bufferWidth = 500
        val buffer = PixelBuffer.filled(bufferWidth, 500, gray)
        buffer.fillRect(20, 20, 200, 200, red) // hold 1, 180x180
        buffer.fillRect(300, 20, 480, 200, red) // hold 2, 180x180, far enough apart to stay separate
        buffer.fillRect(20, 300, 200, 480, orange) // a same-frame orange hold - must not count as a red match

        val groundTruthRedHolds = listOf(
            rectIndices(20, 20, 200, 200, bufferWidth),
            rectIndices(300, 20, 480, 200, bufferWidth),
        )

        val holds = RouteColorDetector.detect(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))
        val predicted = holds.map { SegmentationMetrics.globalIndices(it, bufferWidth) }

        val metrics = SegmentationMetrics.wholeObjectMetrics(predicted, groundTruthRedHolds)

        // Real measured values were tp=2, fp=0, fn=0, precision=recall=1.0.
        assertTrue("expected both real red holds to be found as true positives, was ${metrics.truePositives}", metrics.truePositives == 2)
        assertTrue("expected no spurious red detections from the orange hold, was ${metrics.falsePositives}", metrics.falsePositives == 0)
        assertTrue("expected no missed red holds, was ${metrics.falseNegatives}", metrics.falseNegatives == 0)
        assertTrue("whole-object precision regressed: was ${metrics.precision}", metrics.precision >= 0.99)
        assertTrue("whole-object recall regressed: was ${metrics.recall}", metrics.recall >= 0.99)
    }
}
