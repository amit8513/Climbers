package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MooreBoundaryTracerTest {

    /** Independently-computed (not via the tracer) set of "border" cell centers — any true cell
     * with at least one background/out-of-bounds 8-neighbor. For a hole-free blob this is exactly
     * the outer perimeter, giving an independent oracle to check the tracer's output against. */
    private fun borderCellCenters(mask: BooleanArray, width: Int, height: Int): Set<Pair<Double, Double>> {
        fun isTrue(x: Int, y: Int): Boolean {
            if (x < 0 || x >= width || y < 0 || y >= height) return false
            return mask[y * width + x]
        }
        val result = HashSet<Pair<Double, Double>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!isTrue(x, y)) continue
                val hasBackgroundNeighbor = (-1..1).any { dy -> (-1..1).any { dx -> (dx != 0 || dy != 0) && !isTrue(x + dx, y + dy) } }
                if (hasBackgroundNeighbor) result += (x + 0.5) to (y + 0.5)
            }
        }
        return result
    }

    private fun toPairs(points: List<Centroid>): List<Pair<Double, Double>> = points.map { it.x to it.y }

    /** Asserts [points] form a valid closed 8-connected walk: all distinct, consecutive points
     * (including wraparound) are Chebyshev-adjacent (i.e. real 8-neighbor steps), matching what a
     * genuine Moore-neighbor walk must produce. */
    private fun assertIsValidClosedWalk(points: List<Pair<Double, Double>>) {
        assertEquals("no duplicate points", points.size, points.toSet().size)
        for (i in points.indices) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % points.size]
            val dx = Math.abs(x1 - x2)
            val dy = Math.abs(y1 - y2)
            assertTrue("points $i and ${(i + 1) % points.size} must be 8-adjacent, got dx=$dx dy=$dy", dx <= 1.0 && dy <= 1.0 && (dx > 0.0 || dy > 0.0))
        }
    }

    @Test
    fun `3x3 solid square traces exactly its 8 perimeter cells, excluding the center`() {
        val width = 3
        val height = 3
        val mask = BooleanArray(width * height) { true }

        val contour = MooreBoundaryTracer.traceOuterContour(mask, width, height)
        val points = toPairs(contour)

        assertEquals(8, points.size)
        assertEquals(borderCellCenters(mask, width, height), points.toSet())
        assertTrue("center (1.5,1.5) must be excluded", (1.5 to 1.5) !in points.toSet())
        assertIsValidClosedWalk(points)
    }

    @Test
    fun `solid 6x4 rectangle traces exactly its known perimeter-cell count`() {
        val width = 6
        val height = 4
        val mask = BooleanArray(width * height) { true }

        val contour = MooreBoundaryTracer.traceOuterContour(mask, width, height)
        val points = toPairs(contour)

        // border count = w*h - interior, interior = max(0,w-2)*max(0,h-2)
        val expectedCount = width * height - (width - 2) * (height - 2)
        assertEquals(16, expectedCount) // sanity on the hand formula itself
        assertEquals(expectedCount, points.size)
        assertEquals(borderCellCenters(mask, width, height), points.toSet())
        assertIsValidClosedWalk(points)
    }

    @Test
    fun `L-shaped mask traces its full perimeter, legitimately skipping only its concave inner corner`() {
        // 5x5 L: a 2-wide vertical bar down the left plus a 2-tall horizontal bar across the top.
        val width = 5
        val height = 5
        val mask = BooleanArray(width * height)
        fun set(x: Int, y: Int) {
            mask[y * width + x] = true
        }
        for (y in 0 until height) for (x in 0..1) set(x, y) // left vertical bar
        for (x in 0 until width) for (y in 0..1) set(x, y) // top horizontal bar
        fun isTrue(x: Int, y: Int): Boolean {
            if (x < 0 || x >= width || y < 0 || y >= height) return false
            return mask[y * width + x]
        }

        val contour = MooreBoundaryTracer.traceOuterContour(mask, width, height)
        val points = toPairs(contour)
        assertIsValidClosedWalk(points)

        val oracle = borderCellCenters(mask, width, height)
        val outputSet = points.toSet()
        assertTrue("every traced point must be a genuine border cell", outputSet.all { it in oracle })

        // Moore-Neighbor tracing can legitimately "cut the corner" at a concave inner corner cell —
        // one whose only exposure to background is diagonal (all 4 orthogonal neighbors are
        // foreground) — because the clockwise neighbor search can find the far diagonal foreground
        // cell before ever considering that corner cell itself. This is a well-documented property
        // of the algorithm, not a bug: any oracle cell missing from the output must be exactly such
        // a concave corner.
        val skipped = oracle - outputSet
        for ((cx, cy) in skipped) {
            val x = (cx - 0.5).toInt()
            val y = (cy - 0.5).toInt()
            val allOrthogonalNeighborsForeground = isTrue(x - 1, y) && isTrue(x + 1, y) && isTrue(x, y - 1) && isTrue(x, y + 1)
            assertTrue(
                "skipped cell ($x,$y) must be a concave corner (all 4 orthogonal neighbors foreground)",
                allOrthogonalNeighborsForeground,
            )
        }
    }

    @Test
    fun `a mask with a single interior hole traces the same outer contour as without the hole`() {
        val width = 5
        val height = 5
        val solid = BooleanArray(width * height) { true }
        val withHole = BooleanArray(width * height) { true }
        withHole[2 * width + 2] = false // remove the dead-center pixel, an enclosed non-member hole

        val solidContour = toPairs(MooreBoundaryTracer.traceOuterContour(solid, width, height))
        val holeContour = toPairs(MooreBoundaryTracer.traceOuterContour(withHole, width, height))

        assertEquals(
            "the hole is fully enclosed and must not change the outer boundary",
            solidContour.toSet(),
            holeContour.toSet(),
        )
        assertTrue("the hole's own boundary cell must not appear in the outer contour", (2.5 to 2.5) !in holeContour.toSet())
        assertIsValidClosedWalk(holeContour)
    }

    @Test
    fun `a single-pixel mask returns exactly its own center point`() {
        val width = 4
        val height = 4
        val mask = BooleanArray(width * height)
        mask[2 * width + 1] = true // (1, 2)

        val contour = MooreBoundaryTracer.traceOuterContour(mask, width, height)

        assertEquals(listOf(Centroid(1.5, 2.5)), contour)
    }

    @Test
    fun `an empty mask returns an empty contour`() {
        val contour = MooreBoundaryTracer.traceOuterContour(BooleanArray(16), 4, 4)
        assertTrue(contour.isEmpty())
    }
}
