package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorCalibratorTest {

    @Test
    fun `calibrate centers on the robust median, ignoring outlier samples`() {
        // A tap ROI on a real red hold: mostly consistent red pixels, plus a few outliers that a
        // plain average would be dragged by (a white chalk smear, a near-black shadowed edge).
        val consistentRed = List(20) { RgbColor(230 + (it % 3), 25 + (it % 3), 20 + (it % 3)) }
        val outliers = listOf(
            RgbColor(250, 250, 250), // chalk
            RgbColor(250, 250, 250),
            RgbColor(10, 10, 10), // shadow
            RgbColor(10, 10, 10),
        )

        val calibrated = ColorCalibrator.calibrate(consistentRed + outliers, RouteColor.RED)
        val plainAverageLab = ColorSpace.rgbToLab(
            RgbColor(
                r = (consistentRed + outliers).sumOf { it.r } / (consistentRed.size + outliers.size),
                g = (consistentRed + outliers).sumOf { it.g } / (consistentRed.size + outliers.size),
                b = (consistentRed + outliers).sumOf { it.b } / (consistentRed.size + outliers.size),
            ),
        )

        val trueRedLab = ColorSpace.rgbToLab(RgbColor(231, 26, 21))
        val calibratedDistanceFromTrue = Cie76DistanceMetric.distance(calibrated.labCenter, trueRedLab)
        val averageDistanceFromTrue = Cie76DistanceMetric.distance(plainAverageLab, trueRedLab)

        assertTrue(
            "robust median center should land closer to the true hold color than a plain average would",
            calibratedDistanceFromTrue < averageDistanceFromTrue,
        )
        assertEquals(ColorCalibrationSource.FRAME_CALIBRATED, calibrated.calibrationSource)
        assertEquals(RouteColor.RED, calibrated.selectedColor)
    }

    @Test
    fun `calibrate reuses the selected color's predefined tolerance and threshold`() {
        val samples = List(10) { RgbColor(230, 25, 20) }
        val calibrated = ColorCalibrator.calibrate(samples, RouteColor.RED)
        val predefined = RouteColorProfiles.defaultFor(RouteColor.RED)

        assertEquals(predefined.hueToleranceDegrees, calibrated.hueToleranceDegrees)
        assertEquals(predefined.deltaEThreshold, calibrated.deltaEThreshold, 0.0)
        assertEquals(predefined.saturationRange, calibrated.saturationRange)
    }

    @Test
    fun `calibrate handles a single sample without throwing`() {
        val calibrated = ColorCalibrator.calibrate(listOf(RgbColor(230, 25, 20)), RouteColor.RED)
        assertEquals(ColorCalibrationSource.FRAME_CALIBRATED, calibrated.calibrationSource)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calibrate rejects an empty sample list`() {
        ColorCalibrator.calibrate(emptyList(), RouteColor.RED)
    }
}
