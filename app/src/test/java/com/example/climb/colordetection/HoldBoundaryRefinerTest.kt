package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldBoundaryRefinerTest {

    private val gray = RgbColor(128, 128, 128)
    private val red = RgbColor.fromArgbHex(RouteColor.RED.hex)
    private val orange = RgbColor.fromArgbHex(RouteColor.ORANGE.hex)

    /** Does the (possibly grown/merged) [hold] contain global pixel ([x], [y])? */
    private fun DetectedHold.containsGlobal(x: Int, y: Int): Boolean {
        val bbox = boundingBox
        if (x !in bbox.x0..bbox.x1 || y !in bbox.y0..bbox.y1) return false
        return mask[(y - bbox.y0) * bbox.width + (x - bbox.x0)]
    }

    // --- 1. Edge-detection math on a small synthetic PixelBuffer with a known hard edge ---

    @Test
    fun `Sobel over a PixelBuffer's own Lab L field finds a known hard color edge`() {
        val width = 10
        val height = 6
        val buffer = PixelBuffer.filled(width, height, RgbColor(20, 20, 20)) // near-black
        buffer.fillRect(5, 0, width, height, RgbColor(235, 235, 235)) // near-white right half

        val labL = DoubleArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                labL[y * width + x] = ColorSpace.rgbToLab(buffer.rgbAt(x, y)).l
            }
        }
        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(labL, width, height)

        // The seam is between columns 4 and 5; columns straddling it must have a large spike,
        // while columns far from the seam (e.g. column 0 or column 9) must be near-flat.
        for (y in 0 until height) {
            assertTrue("column 4 at row $y should sit on the seam", magnitudes[y * width + 4] > 50.0)
            assertTrue("column 5 at row $y should sit on the seam", magnitudes[y * width + 5] > 50.0)
            assertTrue("column 0 at row $y should be far from the seam", magnitudes[y * width + 0] < 1.0)
            assertTrue("column 9 at row $y should be far from the seam", magnitudes[y * width + 9] < 1.0)
        }
    }

    // --- 2. Mandatory discrimination test: absorb an internal off-color patch, never cross into
    // an adjacent, differently-colored region one pixel away. ---

    @Test
    fun `region growing absorbs an internal gray patch but does not cross into a touching orange region`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(50, 50, 130, 130, red) // 80x80 red hold — sized so the growth radius formula
        // (GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT=0.05, floor 2) comfortably clears the 3 growth
        // rings a 5x5 internal hole needs to fill from its own border inward to its center (an
        // 80x80 hold gives r=4; a hold too small relative to this fraction, e.g. the original 40x40
        // fixture at r=2, can no longer fill a 5x5 hole after the fraction was reduced to fix the
        // small-hold wall-dilution rejection bug — see RouteColorDetectionConfig's own doc comment).
        buffer.fillRect(65, 65, 70, 70, gray) // 5x5 internal chalk/highlight patch, well interior
        buffer.fillRect(130, 50, 160, 130, orange) // orange region directly touching red's new right edge

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        // Sanity: only the red region is a candidate under a RED target model; the internal patch
        // is a hole in its mask (matches Phase 3's non-fragmentation behavior), the touching orange
        // region isn't a RED candidate at all.
        assertEquals(1, candidates.size)

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        assertEquals(1, refined.size)
        val hold = refined.single()

        // The internal patch must now be absorbed into the grown mask.
        for (x in 65 until 70) {
            for (y in 65 until 70) {
                assertTrue("patch pixel ($x,$y) should be absorbed by growth", hold.containsGlobal(x, y))
            }
        }

        // No pixel of the touching orange region may ever be absorbed. This is governed by the
        // color gate (orange is never color-plausible as red, regardless of growth radius), not by
        // growth reach, so it holds regardless of this fixture's hold size.
        for (x in 130 until 160) {
            for (y in 50 until 130) {
                assertFalse("orange pixel ($x,$y) must never be absorbed", hold.containsGlobal(x, y))
            }
        }
        // Note: the grown bounding box CAN extend past x=130 near the top/bottom of the window
        // (rows above 50 / below 130 are plain gray wall there, not orange, and gray passes the
        // achromatic-bridge growth test) — that's an expected background-halo effect of bounded
        // growth (see this phase's report), not a leak into the orange hold itself, which the
        // pixel-membership checks above already rule out precisely.
    }

    // --- 3. Contour tracing produces a sane, closed boundary polygon ---

    @Test
    fun `refined hold's contour is a sane, closed polygon in global frame coordinates`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(50, 50, 90, 90, red) // 40x40 red hold, no internal patch this time

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        assertEquals(1, refined.size)
        val hold = refined.single()

        val contour = requireNotNull(hold.contour) { "contour must be populated by boundary refinement" }
        assertTrue("contour must have multiple points", contour.size > 4)
        assertEquals("no duplicate contour points", contour.size, contour.toSet().size)

        // Every contour point must be a valid consecutive 8-neighbor step from the next (closed walk).
        for (i in contour.indices) {
            val a = contour[i]
            val b = contour[(i + 1) % contour.size]
            val dx = Math.abs(a.x - b.x)
            val dy = Math.abs(a.y - b.y)
            assertTrue("contour points $i,${(i + 1) % contour.size} must be 8-adjacent", dx <= 1.0 && dy <= 1.0)
        }

        // Contour is in GLOBAL frame coordinates, matching the hold's bounding box, not local-to-mask.
        val minX = contour.minOf { it.x }
        val maxX = contour.maxOf { it.x }
        val minY = contour.minOf { it.y }
        val maxY = contour.maxOf { it.y }
        assertTrue(minX >= hold.boundingBox.x0)
        assertTrue(maxX <= hold.boundingBox.x1 + 1.0)
        assertTrue(minY >= hold.boundingBox.y0)
        assertTrue(maxY <= hold.boundingBox.y1 + 1.0)
    }

    // --- 4. Phase 3's documented cutting-band fragmentation: what ACTUALLY happens now ---

    @Test
    fun `refineBoundaries collapses the documented cutting-band fragmentation back into one hold`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(50, 50, 70, 70, red) // 20x20 = 400px
        buffer.fillRect(50, 59, 70, 61, gray) // 2px-tall full-width gray band, cutting the square

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        // Confirms Phase 3's documented starting point for this regression.
        assertEquals(2, candidates.size)

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)

        assertEquals(
            "bounded edge-aware region growing should bridge the thin cutting band and merge the two fragments back into one hold",
            1,
            refined.size,
        )
        val hold = refined.single()
        // The merged hold must at minimum cover both original fragments' full area (400 - band).
        assertTrue("merged area must be at least the two original fragments combined", hold.area >= 360)
        // Both fragments' red pixels must all be present in the final mask.
        for (x in 50 until 70) {
            for (y in 50 until 70) {
                assertTrue("original red pixel ($x,$y) must survive the merge", hold.containsGlobal(x, y))
            }
        }
    }

    // --- 5. Two genuinely far-apart same-color holds must NOT be merged by bounded growth ---

    @Test
    fun `two same-colored holds farther apart than the max growth radius stay separate`() {
        val buffer = PixelBuffer.filled(300, 300, gray)
        buffer.fillRect(20, 20, 40, 40, red) // 20x20 red square
        buffer.fillRect(120, 20, 140, 40, red) // second 20x20 red square, 80px gap - far beyond MAX_GROWTH_RADIUS_PX

        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        assertEquals(2, candidates.size)

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)

        assertEquals(
            "holds far apart relative to the bounded growth radius must remain distinct",
            2,
            refined.size,
        )
    }

    // --- 6. Regression: an 8-connectivity-only ("bowtie") touch must NOT merge two holds, since
    // MooreBoundaryTracer would then trace a self-touching, duplicate-vertex contour through the
    // shared pinch point. Fixed by requiring a genuine 4-connected shared edge before merging. ---

    @Test
    fun `touches4Connected rejects a diagonal-only pair but accepts an edge-adjacent or overlapping pair`() {
        val bufferWidth = 20
        fun idx(x: Int, y: Int) = y * bufferWidth + x

        // Diagonal neighbors only (Chebyshev distance 1, Manhattan distance 2) - this is exactly
        // the touch that the old 8-connectivity test would have accepted and this fix rejects.
        assertFalse(HoldBoundaryRefiner.touches4Connected(setOf(idx(5, 5)), setOf(idx(6, 6)), bufferWidth))

        // Genuine shared edge (Manhattan distance 1) - must still be accepted.
        assertTrue(HoldBoundaryRefiner.touches4Connected(setOf(idx(5, 5)), setOf(idx(6, 5)), bufferWidth))

        // Identical/overlapping pixel - must still be accepted.
        assertTrue(HoldBoundaryRefiner.touches4Connected(setOf(idx(5, 5)), setOf(idx(5, 5)), bufferWidth))
    }

    @Test
    fun `refineBoundaries keeps two holds separate when their grown masks touch only diagonally`() {
        // Uniform background: every pixel is identical gray, so the color/edge gates trivially
        // pass everywhere and growth forms an exact Manhattan-distance-<=2 diamond around each
        // single-pixel seed (growth radius floors to MIN_GROWTH_RADIUS_PX=2 for a 1x1 bbox).
        val buffer = PixelBuffer.filled(30, 30, gray)
        val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

        // Seeds 3px apart in both x and y. Hand-verified: the two radius-2 diamonds' closest
        // points are Chebyshev-distance-1 (diagonal) apart in three places (e.g. (11,11) vs
        // (12,12)) with no 4-connected or overlapping pixel anywhere between them. Before this
        // fix (8-connectivity touch test) that diagonal graze would have merged the two holds and
        // produced a self-touching, duplicate-vertex contour - the exact bug this test targets.
        val holdA = requireNotNull(
            HoldComponentDetector.statsFromMemberIndices(buffer, listOf(buffer.indexOf(10, 10)), targetModel, id = 0),
        )
        val holdB = requireNotNull(
            HoldComponentDetector.statsFromMemberIndices(buffer, listOf(buffer.indexOf(13, 13)), targetModel, id = 1),
        )

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, listOf(holdA, holdB))

        assertEquals(
            "two holds whose grown masks touch only diagonally must stay separate under the 4-connectivity merge fix",
            2,
            refined.size,
        )
        refined.forEach { hold ->
            val contour = requireNotNull(hold.contour) { "contour must be populated by boundary refinement" }
            assertEquals("contour must have no duplicate vertices", contour.size, contour.toSet().size)
        }
    }
}
