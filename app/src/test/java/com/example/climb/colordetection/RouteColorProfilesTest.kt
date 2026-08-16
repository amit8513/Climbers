package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteColorProfilesTest {

    @Test
    fun `defaultFor returns a PREDEFINED model for every RouteColor`() {
        for (color in RouteColor.entries) {
            val model = RouteColorProfiles.defaultFor(color)
            assertEquals(color, model.selectedColor)
            assertEquals(ColorCalibrationSource.PREDEFINED, model.calibrationSource)
        }
    }

    @Test
    fun `close-hue-neighbor colors get the tight tolerance`() {
        listOf(RouteColor.RED, RouteColor.ORANGE, RouteColor.YELLOW, RouteColor.PINK).forEach { color ->
            assertEquals(
                "expected TIGHT tolerance for $color",
                RouteColorDetectionConfig.TIGHT_HUE_TOLERANCE_DEGREES,
                RouteColorProfiles.defaultFor(color).hueToleranceDegrees,
            )
        }
    }

    @Test
    fun `roomy-hue-neighbor colors get the default tolerance`() {
        listOf(RouteColor.GREEN, RouteColor.BLUE, RouteColor.PURPLE).forEach { color ->
            assertEquals(
                "expected DEFAULT tolerance for $color",
                RouteColorDetectionConfig.DEFAULT_HUE_TOLERANCE_DEGREES,
                RouteColorProfiles.defaultFor(color).hueToleranceDegrees,
            )
        }
    }

    @Test
    fun `black and white are achromatic`() {
        assertTrue(RouteColorProfiles.defaultFor(RouteColor.BLACK).isAchromatic)
        assertTrue(RouteColorProfiles.defaultFor(RouteColor.WHITE).isAchromatic)
    }

    @Test
    fun `no two chromatic colors' tolerance windows overlap their real hue gap`() {
        val chromatic = RouteColor.entries.filterNot {
            it == RouteColor.BLACK || it == RouteColor.WHITE
        }
        for (color in chromatic) {
            val model = RouteColorProfiles.defaultFor(color)
            for (other in chromatic) {
                if (other == color) continue
                val otherModel = RouteColorProfiles.defaultFor(other)
                val gap = circularHueDistance(model.hsvCenter.h, otherModel.hsvCenter.h)
                assertTrue(
                    "$color's tolerance (${model.hueToleranceDegrees}) should not reach $other (gap=$gap)",
                    gap > model.hueToleranceDegrees,
                )
            }
        }
    }
}
