package com.example.climb.colordetection

/**
 * Phase 5's whole-object color validation: does a Phase-4-refined [DetectedHold]'s own pixel
 * membership actually look like ONE coherent, correctly-colored physical object, or did bounded
 * boundary refinement leak into a similarly-lit, low-saturation wall ([HoldBoundaryRefiner]'s own
 * documented, accepted "halo" limitation)? This is the concrete, testable mechanism that catches
 * what Phase 4 could only bound, not eliminate.
 *
 * Computes two independent ratios per hold, since they catch different failure modes:
 * - [HoldValidation.colorConsistencyRatioVsOwnMedian]: what fraction of the hold's own pixels are
 *   within [RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD] of the hold's OWN median color.
 *   [HoldBoundaryRefiner]'s own per-ring admission test already gates new pixels against this same
 *   reference, EXCEPT for pixels admitted purely via its achromatic-bridge exception (grown
 *   because they were near-gray, regardless of actual color distance) — so a low ratio here
 *   specifically means "a lot of what got grown in only got in via the achromatic bypass, not real
 *   color similarity," i.e. wall bleed.
 * - [HoldValidation.colorConsistencyRatioVsTargetCenter]: the same measurement, but against the
 *   generically-defined [TargetColorModel.labCenter] for the selected route color, not the hold's
 *   own (possibly already-drifted) median. These two numbers can genuinely diverge: if wall pixels
 *   become a large enough fraction of a hold, they can drag the hold's OWN median toward gray,
 *   which would make consistencyVsOwnMedian look artificially healthy again even as
 *   consistencyVsTargetCenter correctly keeps showing the drift away from the true target color.
 *
 * Also computes [HoldValidation.growthAreaRatio]: final (refined) area versus the summed area of
 * whichever pre-refinement Phase-3 candidate(s) ended up inside this hold (a hold can be the
 * result of a Phase-4 merge of more than one original candidate) — a hold whose area ballooned far
 * beyond what [HoldBoundaryRefiner]'s own bounded growth radius should geometrically allow is a
 * second, independent signal of the same wall-bleed failure mode.
 *
 * Deliberately NOT attempted here: person/clothing exclusion. Building that for real would mean
 * integrating a new ML segmentation model (e.g. ML Kit Selfie Segmentation or Pose Detection) into
 * this pipeline — a new, separate dependency this pipeline has zero existing infrastructure for,
 * and disproportionate to "object validation" scope (the brief marks it explicitly optional). This
 * phase's color/growth/consistency checks provide some incidental protection (a climber's skin or
 * clothing rarely matches a specific chosen route color's tight hue+ΔE gate closely enough to seed
 * a Phase-3 candidate at all, and even if it did, it would still have to clear this phase's
 * whole-surface consistency floor) but is not a substitute for real segmentation and won't catch,
 * say, a climber wearing a shirt that happens to closely match the selected route color. If this
 * becomes a real, evidenced problem against real gym footage, a future phase should integrate a
 * segmentation model as a pre-filter mask excluding person-classified pixels before Phase 3 ever
 * seeds a candidate — not a post-hoc patch here.
 */
object HoldColorValidator {

    data class HoldValidation(
        val colorConsistencyRatioVsOwnMedian: Double,
        val colorConsistencyRatioVsTargetCenter: Double,
        val growthAreaRatio: Double,
    ) {
        /** True iff every Phase 5 rejection gate clears — see [RouteColorDetectionConfig]'s
         * Phase 5 section for the actual threshold values and their reasoning. */
        val passesFloor: Boolean
            get() = colorConsistencyRatioVsOwnMedian >= RouteColorDetectionConfig.MIN_COLOR_CONSISTENCY_RATIO &&
                colorConsistencyRatioVsTargetCenter >= RouteColorDetectionConfig.MIN_TARGET_CONSISTENCY_RATIO &&
                growthAreaRatio <= RouteColorDetectionConfig.MAX_GROWTH_AREA_RATIO
    }

    /**
     * @param preRefinementCandidates the exact [HoldComponentDetector.detectCandidates] output that
     * was fed into [HoldBoundaryRefiner.refineBoundaries] to produce [hold] — used only to compute
     * [HoldValidation.growthAreaRatio].
     */
    fun validate(
        buffer: PixelBuffer,
        targetModel: TargetColorModel,
        preRefinementCandidates: List<DetectedHold>,
        hold: DetectedHold,
    ): HoldValidation {
        val bbox = hold.boundingBox
        var withinOwnMedian = 0
        var withinTargetCenter = 0
        var total = 0
        for (localY in 0 until bbox.height) {
            for (localX in 0 until bbox.width) {
                if (!hold.mask[localY * bbox.width + localX]) continue
                total++
                val lab = ColorSpace.rgbToLab(buffer.rgbAt(bbox.x0 + localX, bbox.y0 + localY))
                if (Ciede2000DistanceMetric.distance(lab, hold.medianLab) <= RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD) {
                    withinOwnMedian++
                }
                if (Ciede2000DistanceMetric.distance(lab, targetModel.labCenter) <= RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD) {
                    withinTargetCenter++
                }
            }
        }
        val consistencyOwn = if (total == 0) 0.0 else withinOwnMedian.toDouble() / total
        val consistencyTarget = if (total == 0) 0.0 else withinTargetCenter.toDouble() / total

        val originalArea = preRefinementCandidates
            .filter { candidate -> overlapsGlobally(candidate, hold) }
            .sumOf { it.area }
        val growthRatio = if (originalArea == 0) Double.POSITIVE_INFINITY else hold.area.toDouble() / originalArea

        return HoldValidation(consistencyOwn, consistencyTarget, growthRatio)
    }

    /**
     * True iff [candidate] (a pre-refinement Phase-3 hold) ended up inside [refined]. Growth only
     * ever ADDS pixels and merging only ever UNIONs pixel sets, so a pre-refinement candidate's
     * full footprint is always entirely contained within exactly one final refined hold — testing
     * containment of a single member pixel is therefore sufficient to identify which final hold a
     * given original candidate ended up inside.
     */
    private fun overlapsGlobally(candidate: DetectedHold, refined: DetectedHold): Boolean {
        val cBbox = candidate.boundingBox
        val firstLocalIndex = candidate.mask.indexOfFirst { it }
        if (firstLocalIndex < 0) return false
        val localX = firstLocalIndex % cBbox.width
        val localY = firstLocalIndex / cBbox.width
        return containsGlobal(refined, cBbox.x0 + localX, cBbox.y0 + localY)
    }

    private fun containsGlobal(hold: DetectedHold, x: Int, y: Int): Boolean {
        val bbox = hold.boundingBox
        if (x !in bbox.x0..bbox.x1 || y !in bbox.y0..bbox.y1) return false
        return hold.mask[(y - bbox.y0) * bbox.width + (x - bbox.x0)]
    }
}
