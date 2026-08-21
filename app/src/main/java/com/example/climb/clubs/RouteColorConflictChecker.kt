package com.example.climb.clubs

import com.example.climb.colordetection.Ciede2000DistanceMetric
import com.example.climb.colordetection.ColorSpace
import com.example.climb.colordetection.FixedCameraRouteRegistrationConfig
import com.example.climb.colordetection.RgbColor

/**
 * Enforces the plan doc's §12 "one active dominant route color per wall" POC rule: a candidate
 * route color that isn't at least [FixedCameraRouteRegistrationConfig.minCompetitiveMarginDeltaE]
 * away (CIEDE2000) from every other *active* route color already claimed on the same wall is
 * blocked outright. There is deliberately no force-override here, matching the plan doc — the
 * only ways past a real conflict are explicit manual hold partitioning or retiring/recoloring the
 * competing route first, neither of which this checker performs.
 */
object RouteColorConflictChecker {

    data class ConflictCheckResult(val hasConflict: Boolean, val conflictingColorHexes: List<Long>)

    fun checkConflicts(
        candidateColorHex: Long,
        activeColorHexesOnSameWall: List<Long>,
        config: FixedCameraRouteRegistrationConfig = FixedCameraRouteRegistrationConfig(),
    ): ConflictCheckResult {
        val candidateLab = ColorSpace.rgbToLab(RgbColor.fromArgbHex(candidateColorHex))
        val conflicting = activeColorHexesOnSameWall.filter { otherColorHex ->
            val otherLab = ColorSpace.rgbToLab(RgbColor.fromArgbHex(otherColorHex))
            Ciede2000DistanceMetric.distance(candidateLab, otherLab) < config.minCompetitiveMarginDeltaE
        }
        return ConflictCheckResult(hasConflict = conflicting.isNotEmpty(), conflictingColorHexes = conflicting)
    }
}
