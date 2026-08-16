package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorDistanceTest {

    @Test
    fun `circularHueDistance handles simple non-wraparound cases`() {
        assertEquals(10f, circularHueDistance(20f, 30f), 0.001f)
        assertEquals(0f, circularHueDistance(45f, 45f), 0.001f)
    }

    @Test
    fun `circularHueDistance wraps around 0 to 360`() {
        // 359 and 2 are only 3 apart going around the wheel, not 357.
        assertEquals(3f, circularHueDistance(359f, 2f), 0.001f)
        assertEquals(3f, circularHueDistance(2f, 359f), 0.001f)
    }

    @Test
    fun `circularHueDistance never exceeds 180`() {
        assertEquals(180f, circularHueDistance(0f, 180f), 0.001f)
        assertTrue(circularHueDistance(10f, 200f) <= 180f)
    }

    @Test
    fun `cie76 distance is zero for identical colors`() {
        val lab = ColorSpace.rgbToLab(RgbColor(255, 0, 0))
        assertEquals(0.0, Cie76DistanceMetric.distance(lab, lab), 1e-9)
    }

    @Test
    fun `ciede2000 distance is zero for identical colors`() {
        val lab = ColorSpace.rgbToLab(RgbColor(255, 0, 0))
        assertEquals(0.0, Ciede2000DistanceMetric.distance(lab, lab), 1e-6)
    }

    @Test
    fun `ciede2000 distance is symmetric`() {
        val red = ColorSpace.rgbToLab(RgbColor(255, 0, 0))
        val orange = ColorSpace.rgbToLab(RgbColor(255, 165, 0))
        val d1 = Ciede2000DistanceMetric.distance(red, orange)
        val d2 = Ciede2000DistanceMetric.distance(orange, red)
        assertEquals(d1, d2, 1e-9)
    }

    /**
     * Mandatory RED-vs-ORANGE test: this is a color-model-level (Lab distance + hue distance)
     * regression, NOT a full image-based discrimination test — those require the object-detection
     * layers built in later phases. What this proves: a real red hold's own color center is
     * clearly closer to itself than to a real orange hold's color, by both CIEDE2000 and circular
     * hue distance — the two signals [RouteColorProfiles] combines to give RED its tight tolerance.
     *
     * No fabricated absolute CIEDE2000 reference value is asserted (per this module's own
     * documented policy of relative-ordering tests only, since a wrong hand-copied reference
     * number would be worse than no test at all); relative ordering is what the matching logic
     * actually depends on.
     */
    @Test
    fun `ciede2000 and hue distance clearly separate red from orange`() {
        val red = RouteColorProfiles.defaultFor(RouteColor.RED)
        val orange = RouteColorProfiles.defaultFor(RouteColor.ORANGE)
        val nearRed = ColorSpace.rgbToLab(RgbColor(235, 30, 25)) // a slightly different red

        val redToItself = Ciede2000DistanceMetric.distance(red.labCenter, red.labCenter)
        val redToNearRed = Ciede2000DistanceMetric.distance(red.labCenter, nearRed)
        val redToOrange = Ciede2000DistanceMetric.distance(red.labCenter, orange.labCenter)

        assertEquals(0.0, redToItself, 1e-9)
        assertTrue("near-red should be closer to red than a different near-red sample is to itself",
            redToNearRed < redToOrange)
        assertTrue("red-orange CIEDE2000 distance should clear the strict match threshold",
            redToOrange > RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD)

        val hueDistance = circularHueDistance(red.hsvCenter.h, orange.hsvCenter.h)
        assertTrue("red and orange hues should be further apart than red's own tight tolerance",
            hueDistance > red.hueToleranceDegrees)
    }

    @Test
    fun `blue and purple are clearly separated by hue distance`() {
        val blue = RouteColorProfiles.defaultFor(RouteColor.BLUE)
        val purple = RouteColorProfiles.defaultFor(RouteColor.PURPLE)

        val hueDistance = circularHueDistance(blue.hsvCenter.h, purple.hsvCenter.h)
        assertTrue("blue and purple hues should be further apart than blue's own tolerance",
            hueDistance > blue.hueToleranceDegrees)

        val distance = Ciede2000DistanceMetric.distance(blue.labCenter, purple.labCenter)
        assertTrue("blue-purple CIEDE2000 distance should clear the strict match threshold",
            distance > RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD)
    }
}
