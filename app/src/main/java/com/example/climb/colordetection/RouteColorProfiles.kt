package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import com.example.climb.colordetection.RouteColorDetectionConfig as Config

/**
 * Default (predefined, uncalibrated) [TargetColorModel] per [RouteColor], derived from each
 * color's real hex value — one data table, not `if (color == RouteColor.RED)` logic scattered
 * through the detector.
 *
 * Hue-gap analysis (computed from each [RouteColor.hex] via [ColorSpace.rgbToHsv], degrees):
 * RED 1.4° — ORANGE 33.5° — YELLOW 48.9° — GREEN 122.6° — BLUE 208.0° — PURPLE 287.5° — PINK 339.8° — (wraps to RED)
 * Gaps to each color's nearest neighbor: RED↔PINK 21.6°, RED↔ORANGE 32.1°, ORANGE↔YELLOW 15.4°,
 * YELLOW↔GREEN 73.7°, GREEN↔BLUE 85.4°, BLUE↔PURPLE 79.5°, PURPLE↔PINK 52.3°.
 *
 * RED/ORANGE/YELLOW/PINK all sit within ~15-32° of a real neighbor, so they get
 * [Config.TIGHT_HUE_TOLERANCE_DEGREES] (a ±8° window comfortably clears of the nearest neighbor
 * with margin to spare) — this narrower, color-specific window (plus the separate
 * [Config.STRICT_DELTA_E_THRESHOLD] perceptual gate) is the actual fix for "red also matches
 * orange," replacing one wide generic tolerance with per-color discrimination. GREEN/BLUE/PURPLE
 * have 70-85° of roomy clearance and use [Config.DEFAULT_HUE_TOLERANCE_DEGREES].
 *
 * BLACK/WHITE get achromatic treatment (mirroring the `hsv[1] < 0.2f` special-case this project's
 * original full-frame hue-isolation shader used, since replaced by real per-object detection) —
 * hue is meaningless for near-gray tape, so luminance/saturation carry the match instead.
 */
object RouteColorProfiles {
    private val TIGHT_HUE_COLORS = setOf(RouteColor.RED, RouteColor.ORANGE, RouteColor.YELLOW, RouteColor.PINK)
    private val ACHROMATIC_COLORS = setOf(RouteColor.BLACK, RouteColor.WHITE)

    private val cache: Map<RouteColor, TargetColorModel> = RouteColor.entries.associateWith { buildDefault(it) }

    /** The predefined default model for [color] — see class doc for the hue-tolerance-tier reasoning. */
    fun defaultFor(color: RouteColor): TargetColorModel = cache.getValue(color)

    private fun buildDefault(color: RouteColor): TargetColorModel {
        val rgb = RgbColor.fromArgbHex(color.hex)
        val lab = ColorSpace.rgbToLab(rgb)
        val hsv = ColorSpace.rgbToHsv(rgb)

        val isAchromatic = color in ACHROMATIC_COLORS
        val hueTolerance = when {
            isAchromatic -> Config.ACHROMATIC_HUE_TOLERANCE_DEGREES
            color in TIGHT_HUE_COLORS -> Config.TIGHT_HUE_TOLERANCE_DEGREES
            else -> Config.DEFAULT_HUE_TOLERANCE_DEGREES
        }
        val saturationRange = if (isAchromatic) {
            0f..Config.ACHROMATIC_SATURATION_CEILING
        } else {
            Config.MIN_CHROMATIC_SATURATION..Config.MAX_SATURATION
        }
        val luminanceTolerance = if (isAchromatic) {
            // BLACK/WHITE are told apart by luminance itself (see RouteColorDetectionConfig's
            // WHITE_MIN_LUMINANCE/BLACK_MAX_LUMINANCE) rather than a tolerance band around a
            // center value, so this field isn't the deciding factor for them — kept generous.
            50f
        } else {
            Config.CHROMATIC_LUMINANCE_TOLERANCE
        }

        return TargetColorModel(
            selectedColor = color,
            labCenter = lab,
            hsvCenter = hsv,
            hueToleranceDegrees = hueTolerance,
            deltaEThreshold = Config.LOOSE_DELTA_E_THRESHOLD,
            saturationRange = saturationRange,
            luminanceTolerance = luminanceTolerance,
            calibrationSource = ColorCalibrationSource.PREDEFINED,
        )
    }
}
