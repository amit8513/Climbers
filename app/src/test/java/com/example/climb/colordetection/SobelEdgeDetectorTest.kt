package com.example.climb.colordetection

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SobelEdgeDetectorTest {

    private val EPS = 1e-9

    @Test
    fun `flat field produces zero magnitude everywhere`() {
        val width = 4
        val height = 4
        val field = DoubleArray(width * height) { 42.0 }

        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(field, width, height)

        magnitudes.forEach { assertEquals(0.0, it, EPS) }
    }

    @Test
    fun `hard vertical step produces known magnitude at the seam and near-zero elsewhere`() {
        // 5 rows x 5 cols: rows 0-1 = 0.0, rows 2-4 = 100.0 -> a hard step between row 1 and row 2.
        val width = 5
        val height = 5
        val field = DoubleArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                field[y * width + x] = if (y <= 1) 0.0 else 100.0
            }
        }

        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(field, width, height)

        // Hand-computed: gy = 4*(field[y+1] - field[y-1]) with border-replicated y, gx = 0
        // (field is uniform along x). Rows straddling the step (1 and 2) get magnitude 400;
        // every other row is flat on both sides and gets 0.
        for (x in 0 until width) {
            assertEquals(0.0, magnitudes[0 * width + x], EPS)
            assertEquals(400.0, magnitudes[1 * width + x], EPS)
            assertEquals(400.0, magnitudes[2 * width + x], EPS)
            assertEquals(0.0, magnitudes[3 * width + x], EPS)
            assertEquals(0.0, magnitudes[4 * width + x], EPS)
        }
    }

    @Test
    fun `diagonal step produces known combined gx-gy magnitude`() {
        // 3x3 staircase diagonal step:
        //   0    0  100
        //   0  100  100
        // 100  100  100
        val width = 3
        val height = 3
        val field = doubleArrayOf(
            0.0, 0.0, 100.0,
            0.0, 100.0, 100.0,
            100.0, 100.0, 100.0,
        )

        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(field, width, height)

        // Hand-computed at the center (1,1): gx = 300, gy = 300 -> magnitude = 300*sqrt(2).
        val expected = 300.0 * sqrt(2.0)
        assertEquals(expected, magnitudes[1 * width + 1], 1e-6)
    }

    @Test
    fun `single-pixel spike is detected at its neighbors, not necessarily at its own symmetric center`() {
        // 5x5 field, all zero except a single spike of 100 at (2,2).
        val width = 5
        val height = 5
        val field = DoubleArray(width * height)
        field[2 * width + 2] = 100.0

        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(field, width, height)

        // The spike's own pixel is symmetric on both sides in both axes -> zero net gradient there.
        assertEquals(0.0, magnitudes[2 * width + 2], EPS)

        // Orthogonal neighbors see a real, large gradient toward the spike.
        assertEquals(200.0, magnitudes[2 * width + 1], EPS) // left of spike
        assertEquals(200.0, magnitudes[2 * width + 3], EPS) // right of spike
        assertEquals(200.0, magnitudes[1 * width + 2], EPS) // above spike
        assertEquals(200.0, magnitudes[3 * width + 2], EPS) // below spike

        // Diagonal neighbors also see a (smaller, combined gx/gy) gradient toward the spike.
        val diagonalExpected = 100.0 * sqrt(2.0)
        assertEquals(diagonalExpected, magnitudes[1 * width + 1], 1e-6)
        assertEquals(diagonalExpected, magnitudes[3 * width + 3], 1e-6)

        // Far corners, untouched by the spike's 3x3 neighborhood, stay at zero.
        assertEquals(0.0, magnitudes[0 * width + 0], EPS)
        assertEquals(0.0, magnitudes[4 * width + 4], EPS)
    }

    @Test
    fun `border pixels use edge-replicated clamping, not zero-padding`() {
        // A field with a step right at the border: row 0 = 100, rest = 0. If borders were
        // zero-padded instead of edge-replicated, row 0's magnitude would differ.
        val width = 3
        val height = 3
        val field = doubleArrayOf(
            100.0, 100.0, 100.0,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
        )
        val magnitudes = SobelEdgeDetector.gradientMagnitudeField(field, width, height)

        // At row 0 (top border), the "row -1" sample clamps to row 0 itself (100), so
        // gy = 4*(field[1] - field[0]) = 4*(0-100) = -400 -> magnitude 400, not some
        // zero-padded-border value.
        assertTrue(magnitudes[0 * width + 1] > 0.0)
        assertEquals(400.0, magnitudes[0 * width + 1], EPS)
    }
}
