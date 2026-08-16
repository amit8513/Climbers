package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiSamplerTest {

    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)

    @Test
    fun `sample returns a full square of samples away from any edge`() {
        val buffer = PixelBuffer.filled(100, 100, red)
        val samples = RoiSampler.sample(buffer, centerX = 50, centerY = 50, radiusPx = 5)

        // radius 5 -> an 11x11 square = 121 samples
        assertEquals(121, samples.size)
        assertTrue(samples.all { it == red })
    }

    @Test
    fun `sample with radius 0 returns exactly the center pixel`() {
        val buffer = PixelBuffer.filled(50, 50, red)
        buffer.setPixel(25, 25, blue)

        val samples = RoiSampler.sample(buffer, centerX = 25, centerY = 25, radiusPx = 0)
        assertEquals(1, samples.size)
        assertEquals(blue, samples.single())
    }

    @Test
    fun `sample clamps to the buffer bounds near a corner instead of throwing`() {
        val buffer = PixelBuffer.filled(20, 20, red)

        // centered at the top-left corner with radius 5 - would want [-5, 5] but only [0, 5] exists
        val samples = RoiSampler.sample(buffer, centerX = 0, centerY = 0, radiusPx = 5)
        // clamped to a 6x6 square (x: 0..5, y: 0..5), not the full 11x11 an unclamped square would be
        assertEquals(36, samples.size)
        assertTrue(samples.all { it == red })
    }

    @Test
    fun `sample clamps an out-of-bounds center point rather than throwing`() {
        val buffer = PixelBuffer.filled(10, 10, red)

        val samples = RoiSampler.sample(buffer, centerX = 999, centerY = -50, radiusPx = 2)
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.all { it == red })
    }

    @Test
    fun `sample rejects a negative radius`() {
        val buffer = PixelBuffer.filled(10, 10, red)
        assertThrows(IllegalArgumentException::class.java) {
            RoiSampler.sample(buffer, centerX = 5, centerY = 5, radiusPx = -1)
        }
    }

    @Test
    fun `sample at the default radius returns 441 samples away from any edge`() {
        val buffer = PixelBuffer.filled(200, 200, red)
        val samples = RoiSampler.sample(buffer, centerX = 100, centerY = 100)
        assertEquals(441, samples.size) // (2*10+1)^2
    }
}
