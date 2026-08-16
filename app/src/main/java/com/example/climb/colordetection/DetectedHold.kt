package com.example.climb.colordetection

/** Axis-aligned integer bounding box, inclusive on both ends: `[x0, x1] x [y0, y1]`. */
data class BoundingBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val width: Int get() = x1 - x0 + 1
    val height: Int get() = y1 - y0 + 1
}

data class Centroid(val x: Double, val y: Double)

/**
 * One connected candidate region surviving the whole-frame color pass, plus the per-object
 * statistics later phases build on.
 *
 * [contour] defaults to `null` when a hold is first produced by Phase 3's raw candidate detection
 * (before any boundary refinement has run) — Phase 4's [HoldBoundaryRefiner] populates it with a
 * real traced boundary polygon (global frame coordinates) once edge-aware region growing has
 * settled each hold's final pixel membership. [mask] is real today: it's this component's own
 * per-pixel membership, cropped to [boundingBox] (local coordinates, row-major, same size as
 * `boundingBox.width * boundingBox.height`) rather than a full-frame-sized array, so it stays
 * cheap to carry around per hold — later boundary-refinement/rendering phases need this real
 * pixel membership, not just a bounding box.
 */
data class DetectedHold(
    val id: Int,
    val boundingBox: BoundingBox,
    val contour: List<Centroid>? = null,
    val mask: BooleanArray,
    val area: Int,
    val centroid: Centroid,
    val meanLab: LabColor,
    val medianLab: LabColor,
    val meanHsv: HsvColor,
    val colorDistance: Double,
    val hueDistance: Float,
    /** Set by [HoldComponentDetector]'s preliminary color/hue blend at first detection, then
     * overwritten with [HoldConfidenceEvaluator]'s real, final score once
     * [RouteColorDetector]/[HoldColorValidator] has validated this hold (Phase 5) — check
     * [colorConsistencyRatioVsOwnMedian] (non-null iff validated) to tell which one this is. */
    val confidence: Double,
    /** Phase 5 whole-object validation output — `null` until [HoldColorValidator] has actually
     * evaluated this hold (Phase 3/4 raw output leaves these three fields `null`; a `null` here
     * means "not yet validated," not "failed validation"). Fraction of this hold's own pixels
     * within [RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD] of its own [medianLab]. See
     * [HoldColorValidator]'s class doc for why this can diverge from
     * [colorConsistencyRatioVsTargetCenter]. */
    val colorConsistencyRatioVsOwnMedian: Double? = null,
    /** Same measurement as [colorConsistencyRatioVsOwnMedian], but against the selected route
     * color's generic [TargetColorModel.labCenter] instead of this hold's own (possibly drifted)
     * median. */
    val colorConsistencyRatioVsTargetCenter: Double? = null,
    /** Final (post-[HoldBoundaryRefiner]) area divided by the summed area of whichever
     * pre-refinement Phase-3 candidate(s) ended up merged into this hold — `null` until
     * validated. */
    val growthAreaRatio: Double? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedHold) return false
        return id == other.id &&
            boundingBox == other.boundingBox &&
            contour == other.contour &&
            area == other.area &&
            centroid == other.centroid &&
            meanLab == other.meanLab &&
            medianLab == other.medianLab &&
            meanHsv == other.meanHsv &&
            colorDistance == other.colorDistance &&
            hueDistance == other.hueDistance &&
            confidence == other.confidence &&
            colorConsistencyRatioVsOwnMedian == other.colorConsistencyRatioVsOwnMedian &&
            colorConsistencyRatioVsTargetCenter == other.colorConsistencyRatioVsTargetCenter &&
            growthAreaRatio == other.growthAreaRatio &&
            mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + boundingBox.hashCode()
        result = 31 * result + area
        result = 31 * result + centroid.hashCode()
        result = 31 * result + mask.contentHashCode()
        return result
    }
}
