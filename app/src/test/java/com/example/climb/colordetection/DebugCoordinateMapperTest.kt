package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebugCoordinateMapperTest {

    @Test
    fun `mapPoint scales proportionally to the target size`() {
        val p = DebugCoordinateMapper.mapPoint(x = 50.0, y = 25.0, sourceWidth = 100, sourceHeight = 100, targetWidth = 200f, targetHeight = 200f)
        assertEquals(100f, p.x, 0.001f)
        assertEquals(50f, p.y, 0.001f)
    }

    @Test
    fun `mapPoint handles independently different x and y scale factors`() {
        val p = DebugCoordinateMapper.mapPoint(x = 10.0, y = 10.0, sourceWidth = 100, sourceHeight = 50, targetWidth = 50f, targetHeight = 100f)
        assertEquals(5f, p.x, 0.001f) // half-scale in x
        assertEquals(20f, p.y, 0.001f) // double-scale in y
    }

    @Test
    fun `mapPoint rejects a non-positive source size`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebugCoordinateMapper.mapPoint(0.0, 0.0, sourceWidth = 0, sourceHeight = 100, targetWidth = 10f, targetHeight = 10f)
        }
    }

    @Test
    fun `mapBoundingBox's mapped extent matches the box's own width and height scaled by the same factor`() {
        // A 10x20 box (inclusive bounds: x0=5,x1=14 is 10 wide; y0=5,y1=24 is 20 tall) in a 100x100
        // source scaled 2x into a 200x200 target should map to a 20x40 rect, not 18x38 (which an
        // off-by-one using x1/y1 directly, instead of x1+1/y1+1, would produce).
        val box = BoundingBox(x0 = 5, y0 = 5, x1 = 14, y1 = 24)
        assertEquals(10, box.width)
        assertEquals(20, box.height)

        val rect = DebugCoordinateMapper.mapBoundingBox(box, sourceWidth = 100, sourceHeight = 100, targetWidth = 200f, targetHeight = 200f)
        assertEquals(20f, rect.width, 0.001f)
        assertEquals(40f, rect.height, 0.001f)
        assertEquals(10f, rect.left, 0.001f)
        assertEquals(10f, rect.top, 0.001f)
    }

    @Test
    fun `mapContour maps every point independently and preserves order and count`() {
        val contour = listOf(Centroid(0.0, 0.0), Centroid(10.0, 0.0), Centroid(10.0, 10.0), Centroid(0.0, 10.0))
        val mapped = DebugCoordinateMapper.mapContour(contour, sourceWidth = 20, sourceHeight = 20, targetWidth = 40f, targetHeight = 40f)

        assertEquals(4, mapped.size)
        assertEquals(0f, mapped[0].x, 0.001f)
        assertEquals(20f, mapped[1].x, 0.001f)
        assertEquals(20f, mapped[2].y, 0.001f)
        assertEquals(20f, mapped[3].y, 0.001f)
    }

    @Test
    fun `unmapPoint is the exact inverse of mapPoint`() {
        // A 1080x1920 reference frame displayed at 300x400 (arbitrary preview size) - a tap at the
        // exact center of the displayed image should map back to the exact center of the source frame.
        val p = DebugCoordinateMapper.unmapPoint(targetX = 150f, targetY = 200f, sourceWidth = 1080, sourceHeight = 1920, targetWidth = 300f, targetHeight = 400f)
        assertEquals(540f, p.x, 0.001f)
        assertEquals(960f, p.y, 0.001f)
    }

    @Test
    fun `unmapPoint round-trips through mapPoint back to the original source point`() {
        val original = DebugCoordinateMapper.mapPoint(x = 723.0, y = 1611.0, sourceWidth = 1080, sourceHeight = 1920, targetWidth = 337f, targetHeight = 600f)
        val roundTripped = DebugCoordinateMapper.unmapPoint(original.x, original.y, sourceWidth = 1080, sourceHeight = 1920, targetWidth = 337f, targetHeight = 600f)
        assertEquals(723f, roundTripped.x, 0.01f)
        assertEquals(1611f, roundTripped.y, 0.01f)
    }

    @Test
    fun `unmapPoint rejects a non-positive target size`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebugCoordinateMapper.unmapPoint(targetX = 0f, targetY = 0f, sourceWidth = 100, sourceHeight = 100, targetWidth = 0f, targetHeight = 10f)
        }
    }
}
