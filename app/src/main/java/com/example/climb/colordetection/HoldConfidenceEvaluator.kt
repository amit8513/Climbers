package com.example.climb.colordetection

/**
 * Phase 5's final, non-preliminary confidence score — replaces
 * [HoldComponentDetector.preliminaryConfidence] (a raw color/hue blend with no knowledge of a
 * hold's own actual pixel consistency or shape) now that real per-object validation evidence
 * exists. Combines three signals: whole-object color consistency ([HoldColorValidator]'s own
 * strongest, most direct evidence — weighted highest), hue proximity to the target (Phase 3's
 * original signal, still meaningful), and a boundary-quality/compactness signal made possible by
 * Phase 4's real per-pixel mask (a hold that leaked into a wall along one or more sides tends to
 * look sparser/more irregular relative to its own bounding box than a genuinely compact physical
 * hold).
 *
 * Still does NOT incorporate cross-frame temporal consistency — that requires tracking a hold
 * across multiple frames of a fixed camera, which is out of scope here: the "review after
 * recording" delivery mode processes a single reference frame, and live/tracking mode is
 * explicitly deferred future work (see this project's Phase 1 audit).
 */
object HoldConfidenceEvaluator {

    fun evaluate(hold: DetectedHold, validation: HoldColorValidator.HoldValidation, targetModel: TargetColorModel): Double {
        val consistencyScore = (
            (validation.colorConsistencyRatioVsOwnMedian + validation.colorConsistencyRatioVsTargetCenter) / 2.0
            ).coerceIn(0.0, 1.0)
        val hueScore = if (targetModel.isAchromatic) {
            1.0
        } else {
            (1.0 - hold.hueDistance / targetModel.hueToleranceDegrees).coerceIn(0.0, 1.0)
        }
        val compactnessScore = fillRatio(hold).coerceIn(0.0, 1.0)

        return (0.5 * consistencyScore + 0.3 * hueScore + 0.2 * compactnessScore).coerceIn(0.0, 1.0)
    }

    /**
     * Bounding-box fill ratio (`area / (bbox.width * bbox.height)`) as a compactness proxy — a
     * genuine physical hold viewed head-on is a filled blob that occupies most of its own bounding
     * box; a hold whose mask leaked outward asymmetrically along a wall tends to grow its bounding
     * box faster than its own true foreground area, lowering this ratio. Deliberately simpler than
     * an isoperimetric (perimeter-vs-area) measure from the real contour: this avoids introducing
     * floating-point contour arc-length approximation into a scoring input, while still capturing
     * the same directional signal — a leaked/irregular shape reads sparser relative to its own
     * bounding box than a compact one — with a value that's exactly, trivially computable from data
     * already on [DetectedHold].
     */
    private fun fillRatio(hold: DetectedHold): Double {
        val bboxArea = hold.boundingBox.width.toLong() * hold.boundingBox.height.toLong()
        if (bboxArea == 0L) return 0.0
        return hold.area.toDouble() / bboxArea.toDouble()
    }
}
