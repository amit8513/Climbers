package com.example.climb.colordetection

import com.example.climb.data.RouteColor

/** Where a [TargetColorModel]'s center color came from. */
enum class ColorCalibrationSource {
    /** [RouteColorProfiles]' built-in default for the selected [RouteColor]. */
    PREDEFINED,

    /** Sampled from a real ROI the user tapped on an actual hold ([ColorCalibrator]). */
    FRAME_CALIBRATED,
}

/**
 * Everything needed to decide whether a sampled color matches a chosen route color. Later phases
 * (object detection/validation) consume this; this phase only produces it.
 *
 * [hueToleranceDegrees]/[deltaEThreshold] are treated as the "loose"/possible-match gates —
 * [RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD] is the separate, tighter bar for a
 * confident match, kept in the shared config rather than per-model since it's a global policy
 * knob, not something calibration should shift per color.
 */
data class TargetColorModel(
    val selectedColor: RouteColor,
    val labCenter: LabColor,
    val hsvCenter: HsvColor,
    val hueToleranceDegrees: Float,
    val deltaEThreshold: Double,
    val saturationRange: ClosedFloatingPointRange<Float>,
    val luminanceTolerance: Float,
    val calibrationSource: ColorCalibrationSource,
) {
    /** True for [RouteColor.BLACK]/[RouteColor.WHITE] — colors with no reliable hue to gate on,
     * where luminance/saturation carry the matching decision instead. Mirrors the achromatic
     * special-case this project's original full-frame hue-isolation shader used (since replaced
     * by real per-object detection): it skipped hue matching entirely when the target's own
     * saturation was < 0.2. */
    val isAchromatic: Boolean
        get() = hueToleranceDegrees >= 180f
}
