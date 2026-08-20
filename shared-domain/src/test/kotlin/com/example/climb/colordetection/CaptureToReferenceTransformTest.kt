package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureToReferenceTransformTest {

    private fun assertPoint(expected: Point2D, actual: Point2D, tolerance: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }

    @Test
    fun `identity transform passes every point through unchanged`() {
        val identity = CaptureToReferenceTransform.identity(wallCalibrationId = 1L)
        assertPoint(Point2D(0f, 0f), identity.apply(Point2D(0f, 0f)))
        assertPoint(Point2D(0.3f, 0.7f), identity.apply(Point2D(0.3f, 0.7f)))
        assertPoint(Point2D(1f, 1f), identity.apply(Point2D(1f, 1f)))
    }

    @Test
    fun `horizontal mirror flips x only`() {
        val transform = CaptureToReferenceTransform.identity(1L).copy(mirrorHorizontal = true)
        assertPoint(Point2D(0.7f, 0.7f), transform.apply(Point2D(0.3f, 0.7f)))
        assertPoint(Point2D(0f, 0.5f), transform.apply(Point2D(1f, 0.5f)))
    }

    @Test
    fun `vertical mirror flips y only`() {
        val transform = CaptureToReferenceTransform.identity(1L).copy(mirrorVertical = true)
        assertPoint(Point2D(0.3f, 0.3f), transform.apply(Point2D(0.3f, 0.7f)))
    }

    @Test
    fun `both mirrors flip both axes`() {
        val transform = CaptureToReferenceTransform.identity(1L).copy(mirrorHorizontal = true, mirrorVertical = true)
        assertPoint(Point2D(0.7f, 0.3f), transform.apply(Point2D(0.3f, 0.7f)))
    }

    @Test
    fun `centered half-size crop maps corners and center correctly`() {
        val transform = CaptureToReferenceTransform.identity(1L)
            .copy(cropRectInReferenceSpace = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f))
        assertPoint(Point2D(0.25f, 0.25f), transform.apply(Point2D(0f, 0f)))
        assertPoint(Point2D(0.75f, 0.75f), transform.apply(Point2D(1f, 1f)))
        assertPoint(Point2D(0.5f, 0.5f), transform.apply(Point2D(0.5f, 0.5f)))
    }

    @Test
    fun `90-degree rotation maps the point as expected`() {
        val transform = CaptureToReferenceTransform.identity(1L).copy(rotationDegrees = 90)
        // (x=1, y=0) -> (1-y, x) = (1, 1)
        assertPoint(Point2D(1f, 1f), transform.apply(Point2D(1f, 0f)))
        // (x=0, y=0) -> (1, 0)
        assertPoint(Point2D(1f, 0f), transform.apply(Point2D(0f, 0f)))
    }

    @Test
    fun `180-degree rotation maps opposite corners`() {
        val transform = CaptureToReferenceTransform.identity(1L).copy(rotationDegrees = 180)
        assertPoint(Point2D(1f, 1f), transform.apply(Point2D(0f, 0f)))
        assertPoint(Point2D(0f, 0f), transform.apply(Point2D(1f, 1f)))
    }

    @Test
    fun `rejects a rotation that is not a multiple of 90`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CaptureToReferenceTransform.identity(1L).copy(rotationDegrees = 45)
        }
    }
}
