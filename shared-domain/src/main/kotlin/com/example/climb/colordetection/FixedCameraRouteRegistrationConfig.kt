package com.example.climb.colordetection

/**
 * Every wall-calibration / competitive-color-classification / hold-geometry threshold for the
 * fixed-camera route-registration flow. Kept independent of `:app`'s `RouteColorDetectionConfig`
 * (which stays app-side — it's the personal-pipeline's own pixel-processing config, not a shared
 * contract) — this module cannot depend on `:app`, so [maxAbsoluteDeltaEForAnyMatch]'s default is
 * a plain literal that currently mirrors `RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD`
 * rather than a reference to it; keep the two in sync by hand if that constant's value ever
 * changes.
 *
 * All defaults below are explicitly unvalidated placeholders pending real-footage tuning — the
 * same honesty standard `RouteColorDetectionConfig`'s own doc comments hold themselves to (see its
 * `STRICT_DELTA_E_THRESHOLD` doc comment for the measured real-footage evidence behind that
 * standard).
 */
data class FixedCameraRouteRegistrationConfig(
    /** Minimum CIEDE2000 margin the best-matching route's calibrated color model must beat the
     * second-best-matching active route by, for a candidate hold to be assigned to that route
     * rather than marked ambiguous. This is the mechanism intended to replace ever just raising a
     * single global color-distance threshold — a single global threshold cannot serve both
     * "detect this photo's holds" and "keep routes apart" at once. */
    val minCompetitiveMarginDeltaE: Double = 8.0,
    /** A candidate whose best match doesn't clear this absolute distance at all (regardless of
     * margin) is never assigned to any route, full stop. Currently mirrors
     * `RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD` (22.0) — see the class doc comment. */
    val maxAbsoluteDeltaEForAnyMatch: Double = 22.0,
    /** Fingerprint-distance threshold above which a captured frame is judged too different from
     * the wall's stored calibration reference to trust — see `AlignmentCheckResult`. */
    val maxAlignmentFingerprintDistance: Double = 0.08,
    val version: Int = 1,
) {
    init {
        require(minCompetitiveMarginDeltaE > 0.0) { "minCompetitiveMarginDeltaE must be positive" }
        require(maxAbsoluteDeltaEForAnyMatch > 0.0) { "maxAbsoluteDeltaEForAnyMatch must be positive" }
        require(maxAlignmentFingerprintDistance > 0.0) { "maxAlignmentFingerprintDistance must be positive" }
    }
}
