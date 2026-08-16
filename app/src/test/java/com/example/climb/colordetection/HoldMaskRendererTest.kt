package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldMaskRendererTest {

    private fun solidSquareHold(x0: Int, y0: Int, size: Int): DetectedHold {
        val bbox = BoundingBox(x0, y0, x0 + size - 1, y0 + size - 1)
        val mask = BooleanArray(size * size) { true }
        return DetectedHold(
            id = 0,
            boundingBox = bbox,
            mask = mask,
            area = size * size,
            centroid = Centroid((x0 + size / 2).toDouble(), (y0 + size / 2).toDouble()),
            meanLab = LabColor(50.0, 0.0, 0.0),
            medianLab = LabColor(50.0, 0.0, 0.0),
            meanHsv = HsvColor(0f, 0f, 0.5f),
            colorDistance = 0.0,
            hueDistance = 0f,
            confidence = 1.0,
        )
    }

    @Test
    fun `deep inside a hold the mask field is exactly 1`() {
        val hold = solidSquareHold(x0 = 10, y0 = 10, size = 20)
        val field = HoldMaskRenderer.renderMaskField(width = 50, height = 50, holds = listOf(hold))
        assertEquals(1f, field[19 * 50 + 19], 0.0001f)
    }

    @Test
    fun `far background pixels are exactly 0`() {
        val hold = solidSquareHold(x0 = 10, y0 = 10, size = 20)
        val field = HoldMaskRenderer.renderMaskField(width = 50, height = 50, holds = listOf(hold))
        assertEquals(0f, field[0 * 50 + 0], 0.0001f)
        assertEquals(0f, field[49 * 50 + 49], 0.0001f)
    }

    @Test
    fun `pixels adjacent to a mask edge get a genuine soft value, not a hard step`() {
        val hold = solidSquareHold(x0 = 10, y0 = 10, size = 20) // spans x/y 10..29
        val field = HoldMaskRenderer.renderMaskField(width = 50, height = 50, holds = listOf(hold))
        val lastHoldColumn = field[19 * 50 + 29]
        val firstBackgroundColumn = field[19 * 50 + 30]
        assertTrue("last hold-column pixel should be softened below 1.0", lastHoldColumn < 1f)
        assertTrue("first background-column pixel should be softened above 0.0", firstBackgroundColumn > 0f)
    }

    @Test
    fun `empty hold list produces an all-zero field`() {
        val field = HoldMaskRenderer.renderMaskField(width = 10, height = 10, holds = emptyList())
        assertTrue(field.all { it == 0f })
    }

    @Test
    fun `two separate holds each stamp their own region independently`() {
        val holdA = solidSquareHold(x0 = 0, y0 = 0, size = 10)
        val holdB = solidSquareHold(x0 = 30, y0 = 30, size = 10)
        val field = HoldMaskRenderer.renderMaskField(width = 50, height = 50, holds = listOf(holdA, holdB))
        assertEquals(1f, field[4 * 50 + 4], 0.0001f)
        assertEquals(1f, field[34 * 50 + 34], 0.0001f)
        assertEquals(0f, field[20 * 50 + 20], 0.0001f)
    }
}
