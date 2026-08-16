package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSpaceTest {

    @Test
    fun `rgbToLab maps white to L100 a0 b0`() {
        val lab = ColorSpace.rgbToLab(RgbColor(255, 255, 255))
        assertEquals(100.0, lab.l, 0.1)
        assertEquals(0.0, lab.a, 0.1)
        assertEquals(0.0, lab.b, 0.1)
    }

    @Test
    fun `rgbToLab maps black to L0 a0 b0`() {
        val lab = ColorSpace.rgbToLab(RgbColor(0, 0, 0))
        assertEquals(0.0, lab.l, 0.1)
        assertEquals(0.0, lab.a, 0.1)
        assertEquals(0.0, lab.b, 0.1)
    }

    @Test
    fun `rgbToLab matches known reference values for pure red`() {
        // Reference (sRGB, D65): pure red ~= L 53.24, a 80.09, b 67.20.
        val lab = ColorSpace.rgbToLab(RgbColor(255, 0, 0))
        assertEquals(53.24, lab.l, 0.5)
        assertEquals(80.09, lab.a, 0.5)
        assertEquals(67.20, lab.b, 0.5)
    }

    @Test
    fun `rgbToHsv matches known hues for primary and secondary colors`() {
        assertEquals(0f, ColorSpace.rgbToHsv(RgbColor(255, 0, 0)).h, 0.01f)
        assertEquals(120f, ColorSpace.rgbToHsv(RgbColor(0, 255, 0)).h, 0.01f)
        assertEquals(240f, ColorSpace.rgbToHsv(RgbColor(0, 0, 255)).h, 0.01f)
        assertEquals(60f, ColorSpace.rgbToHsv(RgbColor(255, 255, 0)).h, 0.01f)
    }

    @Test
    fun `rgbToHsv gives zero saturation for gray`() {
        val hsv = ColorSpace.rgbToHsv(RgbColor(128, 128, 128))
        assertEquals(0f, hsv.s, 0.001f)
    }

    @Test
    fun `rgbToHsv hue stays within 0 to 360`() {
        for (r in 0..255 step 51) {
            for (g in 0..255 step 51) {
                for (b in 0..255 step 51) {
                    val hsv = ColorSpace.rgbToHsv(RgbColor(r, g, b))
                    assert(hsv.h >= 0f && hsv.h < 360f) { "hue out of range for ($r,$g,$b): ${hsv.h}" }
                }
            }
        }
    }

    @Test
    fun `fromArgbHex extracts RGB channels ignoring alpha`() {
        val rgb = RgbColor.fromArgbHex(0xFFE53935)
        assertEquals(0xE5, rgb.r)
        assertEquals(0x39, rgb.g)
        assertEquals(0x35, rgb.b)
    }
}
