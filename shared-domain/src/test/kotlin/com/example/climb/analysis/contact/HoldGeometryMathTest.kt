package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldGeometryMathTest {

    /** Axis-aligned square centered at (0.5, 0.5) with half-width 0.1 — corners at (0.4,0.4) and
     * (0.6,0.6). Used as the baseline fixture for hand-computed distances. */
    private fun squareHold(): HoldShape = HoldShape(
        holdId = 1,
        contourNormalized = listOf(
            Point2D(0.4f, 0.4f),
            Point2D(0.6f, 0.4f),
            Point2D(0.6f, 0.6f),
            Point2D(0.4f, 0.6f),
        ),
    )

    /** A "house"-shaped, non-square pentagon: a flat-topped square body from y=0.4..0.6 with a
     * triangular roof whose apex sits at (0.5, 0.2). Neither an axis-aligned square nor a simple
     * regular convex shape, so it exercises the general polygon path (multiple edges of differing
     * slope contributing to both the inside test and the nearest-boundary-point search). */
    private fun pentagonHold(): HoldShape = HoldShape(
        holdId = 2,
        contourNormalized = listOf(
            Point2D(0.3f, 0.6f), // A: bottom-left
            Point2D(0.7f, 0.6f), // B: bottom-right
            Point2D(0.7f, 0.4f), // C: right shoulder
            Point2D(0.5f, 0.2f), // D: roof apex
            Point2D(0.3f, 0.4f), // E: left shoulder
        ),
    )

    private fun distance(a: Point2D, b: Point2D): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    // --- point clearly inside a hold's mask -----------------------------------------------------

    @Test
    fun `point inside a square hold has distance zero and is reported inside`() {
        val hold = squareHold()
        val center = Point2D(0.5f, 0.5f)

        assertTrue(HoldGeometryMath.isInsideHold(center, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(center, hold), 0f)
    }

    @Test
    fun `point inside a pentagon hold has distance zero and is reported inside`() {
        val hold = pentagonHold()
        // Sits in the square-bodied lower half of the house shape, clearly interior.
        val interior = Point2D(0.5f, 0.45f)

        assertTrue(HoldGeometryMath.isInsideHold(interior, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(interior, hold), 0f)
    }

    // --- point clearly outside, matched against a hand-computed nearest-edge distance ------------

    @Test
    fun `point clearly outside a square hold has distance equal to hand-computed nearest-edge distance`() {
        val hold = squareHold()
        // (0.9, 0.5) sits due right of the square, level with its vertical midline, so the
        // nearest boundary point is the perpendicular foot (0.6, 0.5) on the right edge — a
        // straight horizontal gap of 0.9 - 0.6 = 0.3.
        val point = Point2D(0.9f, 0.5f)
        val expectedDistance = 0.3f

        assertFalse(HoldGeometryMath.isInsideHold(point, hold))
        assertEquals(expectedDistance, HoldGeometryMath.distanceToHold(point, hold), 1e-4f)
    }

    @Test
    fun `point clearly outside a square hold on a different side matches hand-computed distance`() {
        val hold = squareHold()
        // (0.5, 0.95) sits due below the square (level with its horizontal midline), so the
        // nearest boundary point is (0.5, 0.6) on the bottom edge: a gap of 0.95 - 0.6 = 0.35.
        val point = Point2D(0.5f, 0.95f)
        val expectedDistance = 0.35f

        assertFalse(HoldGeometryMath.isInsideHold(point, hold))
        assertEquals(expectedDistance, HoldGeometryMath.distanceToHold(point, hold), 1e-4f)
    }

    @Test
    fun `point outside a pentagon hold matches hand-computed distance to the nearest vertex`() {
        val hold = pentagonHold()
        // (0.5, 0.1) sits directly above the roof apex D=(0.5, 0.2). Projecting it onto either
        // roof edge (C-D or D-E) clamps to the apex itself (the projection parameter falls
        // outside [0,1] on both), so the nearest boundary point is exactly D, a vertical gap of
        // 0.2 - 0.1 = 0.1.
        val point = Point2D(0.5f, 0.1f)
        val expectedDistance = 0.1f

        assertFalse(HoldGeometryMath.isInsideHold(point, hold))
        assertEquals(expectedDistance, HoldGeometryMath.distanceToHold(point, hold), 1e-4f)
    }

    // --- point exactly on the boundary -------------------------------------------------------------
    // HoldGeometryMath.isInsideHold is the standard ray-casting (PNPOLY-style) test: for each edge
    // it only counts a crossing when one endpoint's y is strictly greater than the query point's y
    // and the other's is not ("vi.y > point.y != vj.y > point.y"), then compares point.x against
    // the edge's x at that y using a strict "<". Because one side of each comparison is strict and
    // the other isn't, this is the classic "top-left fill rule": tracing it by hand (confirmed by
    // walking the loop for all four edges) shows a point sitting exactly on the square's TOP or
    // LEFT edge is reported inside (true), while a point on the BOTTOM or RIGHT edge is reported
    // not inside (false). Either way distanceToHold is 0, since the point lies exactly on the
    // contour: when isInsideHold is true it short-circuits to 0 directly, and when it's false the
    // boundary-distance search still finds the point sitting exactly on a segment.
    @Test
    fun `point exactly on a square holds right edge is reported not inside but has zero distance`() {
        val hold = squareHold()
        val onRightEdge = Point2D(0.6f, 0.5f)

        assertFalse(HoldGeometryMath.isInsideHold(onRightEdge, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(onRightEdge, hold), 0f)
    }

    @Test
    fun `point exactly on a square holds bottom edge is reported not inside but has zero distance`() {
        val hold = squareHold()
        val onBottomEdge = Point2D(0.5f, 0.6f)

        assertFalse(HoldGeometryMath.isInsideHold(onBottomEdge, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(onBottomEdge, hold), 0f)
    }

    @Test
    fun `point exactly on a square holds top edge is reported inside under the top-left fill rule, with zero distance`() {
        val hold = squareHold()
        val onTopEdge = Point2D(0.5f, 0.4f)

        assertTrue(HoldGeometryMath.isInsideHold(onTopEdge, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(onTopEdge, hold), 0f)
    }

    @Test
    fun `point exactly on a square holds left edge is reported inside under the top-left fill rule, with zero distance`() {
        val hold = squareHold()
        val onLeftEdge = Point2D(0.4f, 0.5f)

        assertTrue(HoldGeometryMath.isInsideHold(onLeftEdge, hold))
        assertEquals(0f, HoldGeometryMath.distanceToHold(onLeftEdge, hold), 0f)
    }

    // --- sanity check on the hand-computed expectations themselves --------------------------------

    @Test
    fun `hand-computed distance helper agrees with the square hold fixtures nearest edge`() {
        // Cross-check the "hand computed" expected distances above against an independently
        // written point-to-point distance, anchored on the known nearest boundary point, so the
        // fixture's arithmetic isn't just asserted against itself.
        assertEquals(0.3f, distance(Point2D(0.9f, 0.5f), Point2D(0.6f, 0.5f)), 1e-4f)
        assertEquals(0.35f, distance(Point2D(0.5f, 0.95f), Point2D(0.5f, 0.6f)), 1e-4f)
    }

    // --- HoldShape validation ----------------------------------------------------------------------

    @Test
    fun `hold shape rejects a contour with fewer than three vertices`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldShape(holdId = 3, contourNormalized = listOf(Point2D(0.4f, 0.4f), Point2D(0.6f, 0.6f)))
        }
    }

    @Test
    fun `hold shape rejects an empty contour`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldShape(holdId = 4, contourNormalized = emptyList())
        }
    }

    @Test
    fun `hold shape accepts a contour with exactly three vertices`() {
        // Not a required-to-fail case: confirms the boundary of the validation (>= 3) is inclusive,
        // not just that "too few" throws.
        val triangle = HoldShape(
            holdId = 5,
            contourNormalized = listOf(Point2D(0.4f, 0.4f), Point2D(0.6f, 0.4f), Point2D(0.5f, 0.6f)),
        )
        assertEquals(3, triangle.contourNormalized.size)
    }
}
