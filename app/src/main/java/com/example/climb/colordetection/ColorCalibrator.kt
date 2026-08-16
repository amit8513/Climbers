package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import kotlin.math.abs

/**
 * Mode B calibration: the user taps a real hold, the caller samples pixel colors from a small ROI
 * around that tap, and this produces a [TargetColorModel] centered on that hold's *actual* color
 * instead of [RouteColorProfiles]' predefined default.
 *
 * Deliberately NOT a plain average — a tap ROI commonly straddles chalk, a specular highlight, a
 * shadowed edge, or a sliver of background wall, and a plain mean lets those drag the center away
 * from the hold's true color. Instead: take the per-channel median as a first robust estimate,
 * reject samples that are outliers against that median (via MAD / modified z-score, see
 * [RouteColorDetectionConfig.CALIBRATION_OUTLIER_MODIFIED_Z_THRESHOLD]), then recompute the final
 * center from only the retained (non-outlier) samples.
 */
object ColorCalibrator {

    /**
     * @param samples RGB colors sampled from the tap ROI (e.g. every pixel, or a stride-sampled
     * subset, of a small square/circle around the tap point). Must not be empty.
     * @param selectedColor Which [RouteColor] the user is calibrating — the resulting model reuses
     * that color's predefined hue tolerance / delta-E threshold / saturation range (Phase 2 keeps
     * per-color tolerance tuning in one place, [RouteColorProfiles], rather than re-deriving it
     * from a single tap sample); only the color *center* is replaced with the calibrated one.
     */
    fun calibrate(samples: List<RgbColor>, selectedColor: RouteColor): TargetColorModel {
        require(samples.isNotEmpty()) { "Cannot calibrate from an empty sample set" }

        val labSamples = samples.map { ColorSpace.rgbToLab(it) }
        val retained = rejectOutliers(labSamples)
        val finalSamples = retained.ifEmpty { labSamples }

        val calibratedLab = medianLab(finalSamples)
        val calibratedRgb = finalSamples
        val calibratedHsv = medianHsv(samples.filterIndexed { index, _ -> labSamples[index] in finalSamples })

        val defaults = RouteColorProfiles.defaultFor(selectedColor)
        return defaults.copy(
            labCenter = calibratedLab,
            hsvCenter = calibratedHsv,
            calibrationSource = ColorCalibrationSource.FRAME_CALIBRATED,
        )
    }

    /** Per-Lab-channel median-based outlier rejection. A sample is rejected if ANY of its L/a/b
     * channels has a modified z-score beyond the configured threshold against that channel's
     * median — one bad channel (e.g. a chalk-whitened L* on an otherwise on-hue sample) is enough
     * to disqualify it, since the goal is a clean color center, not a lenient one. */
    private fun rejectOutliers(labSamples: List<LabColor>): List<LabColor> {
        if (labSamples.size < 4) return labSamples // too few samples for MAD to be meaningful

        val ls = labSamples.map { it.l }
        val as_ = labSamples.map { it.a }
        val bs = labSamples.map { it.b }

        val lMedian = median(ls)
        val aMedian = median(as_)
        val bMedian = median(bs)

        val lMad = medianAbsoluteDeviation(ls, lMedian)
        val aMad = medianAbsoluteDeviation(as_, aMedian)
        val bMad = medianAbsoluteDeviation(bs, bMedian)

        fun modifiedZ(value: Double, channelMedian: Double, mad: Double): Double {
            if (mad == 0.0) return 0.0 // no spread on this channel -> can't flag via this channel
            return 0.6745 * abs(value - channelMedian) / mad
        }

        val threshold = RouteColorDetectionConfig.CALIBRATION_OUTLIER_MODIFIED_Z_THRESHOLD
        return labSamples.filter { lab ->
            modifiedZ(lab.l, lMedian, lMad) <= threshold &&
                modifiedZ(lab.a, aMedian, aMad) <= threshold &&
                modifiedZ(lab.b, bMedian, bMad) <= threshold
        }
    }

    private fun medianLab(samples: List<LabColor>): LabColor = LabColor(
        l = median(samples.map { it.l }),
        a = median(samples.map { it.a }),
        b = median(samples.map { it.b }),
    )

    /** Hue needs circular median handling; s/v are ordinary. With few retained samples this is an
     * approximation (see comment below) but stays simple, matching this phase's mode-agnostic,
     * OpenCV-free scope. */
    private fun medianHsv(samples: List<RgbColor>): HsvColor {
        val hsvSamples = samples.map { ColorSpace.rgbToHsv(it) }
        // Circular median-of-angles via the median of unit vectors' angle: robust enough for the
        // tight, single-hold hue spread expected in a calibration ROI (no genuine wraparound
        // straddling is expected within one physical hold's own color).
        val sinSum = hsvSamples.sumOf { kotlin.math.sin(Math.toRadians(it.h.toDouble())) }
        val cosSum = hsvSamples.sumOf { kotlin.math.cos(Math.toRadians(it.h.toDouble())) }
        var hueDeg = Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)).toFloat()
        if (hueDeg < 0f) hueDeg += 360f

        return HsvColor(
            h = hueDeg,
            s = median(hsvSamples.map { it.s.toDouble() }).toFloat(),
            v = median(hsvSamples.map { it.v.toDouble() }).toFloat(),
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun medianAbsoluteDeviation(values: List<Double>, aboutMedian: Double): Double {
        val deviations = values.map { abs(it - aboutMedian) }
        return median(deviations)
    }
}
