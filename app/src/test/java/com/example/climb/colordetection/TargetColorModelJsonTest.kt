package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetColorModelJsonTest {

    @Test
    fun `a predefined model round-trips through JSON exactly`() {
        val original = RouteColorProfiles.defaultFor(RouteColor.PINK)
        val restored = original.toJson().toTargetColorModel()

        assertEquals(original, restored)
    }

    @Test
    fun `a frame-calibrated model round-trips, including its calibration source`() {
        val original = TargetColorModel(
            selectedColor = RouteColor.BLUE,
            labCenter = LabColor(l = 52.3, a = -4.1, b = -38.7),
            hsvCenter = HsvColor(h = 214.5f, s = 0.62f, v = 0.71f),
            hueToleranceDegrees = 18f,
            deltaEThreshold = 24.5,
            saturationRange = 0.25f..0.95f,
            luminanceTolerance = 30f,
            calibrationSource = ColorCalibrationSource.FRAME_CALIBRATED,
        )
        val restored = original.toJson().toTargetColorModel()

        assertEquals(original, restored)
        assertEquals(ColorCalibrationSource.FRAME_CALIBRATED, restored?.calibrationSource)
    }

    @Test
    fun `every route color round-trips`() {
        for (color in RouteColor.entries) {
            val original = RouteColorProfiles.defaultFor(color)
            assertEquals(original, original.toJson().toTargetColorModel())
        }
    }

    @Test
    fun `blank stored value yields no saved calibration rather than crashing`() {
        assertNull("".toTargetColorModel())
    }

    @Test
    fun `corrupt or shape-mismatched JSON yields no saved calibration rather than crashing`() {
        assertNull("{not valid json".toTargetColorModel())
        assertNull("""{"selectedColor":"RED"}""".toTargetColorModel())
        assertNull("""{"selectedColor":"NOT_A_REAL_COLOR","labL":1,"labA":1,"labB":1}""".toTargetColorModel())
    }

    @Test
    fun `isAchromatic survives the round trip for black and white`() {
        val black = RouteColorProfiles.defaultFor(RouteColor.BLACK).toJson().toTargetColorModel()
        val white = RouteColorProfiles.defaultFor(RouteColor.WHITE).toJson().toTargetColorModel()
        assertTrue(black != null && black.isAchromatic)
        assertTrue(white != null && white.isAchromatic)
    }
}
