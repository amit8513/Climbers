package com.example.climb.colordetection

/**
 * Phase 8 ("Benchmarking/Regression") segmentation-quality metrics: Precision, Recall, and IoU
 * (intersection-over-union), both at the pixel level and at the whole-object level. Pure Kotlin,
 * operating on plain global pixel-index sets (`Set<Int>`, `y * bufferWidth + x` — the same
 * indexing convention [HoldBoundaryRefiner]'s own `touches4Connected` already uses) rather than
 * `DetectedHold`/`Bitmap` directly, so ground truth can be hand-built in a test without needing a
 * real annotated image — this project has no real gym/climbing-wall photographs anywhere in its
 * repo or environment (every fixture across Phases 3-7 is a hand-built synthetic [PixelBuffer]);
 * see [RealFrameDatasetLocation]'s doc comment for where real photos would go once supplied.
 */
object SegmentationMetrics {

    /** Pixel-level overlap stats between one predicted region and one ground-truth region. */
    data class PixelMetrics(
        val intersection: Int,
        val predictedArea: Int,
        val groundTruthArea: Int,
    ) {
        val unionArea: Int get() = predictedArea + groundTruthArea - intersection
        val iou: Double get() = if (unionArea == 0) 0.0 else intersection.toDouble() / unionArea
        val precision: Double get() = if (predictedArea == 0) 0.0 else intersection.toDouble() / predictedArea
        val recall: Double get() = if (groundTruthArea == 0) 0.0 else intersection.toDouble() / groundTruthArea
    }

    fun pixelMetrics(predicted: Set<Int>, groundTruth: Set<Int>): PixelMetrics {
        val intersection = if (predicted.size <= groundTruth.size) {
            predicted.count { it in groundTruth }
        } else {
            groundTruth.count { it in predicted }
        }
        return PixelMetrics(intersection, predicted.size, groundTruth.size)
    }

    /** Converts a [DetectedHold]'s local mask (cropped to its own [BoundingBox]) into a set of
     * global pixel indices (`y * bufferWidth + x`) for use with [pixelMetrics]/[wholeObjectMetrics]
     * against a hand-built ground-truth set. */
    fun globalIndices(hold: DetectedHold, bufferWidth: Int): Set<Int> {
        val bbox = hold.boundingBox
        val indices = HashSet<Int>(hold.area * 2)
        for (localY in 0 until bbox.height) {
            for (localX in 0 until bbox.width) {
                if (!hold.mask[localY * bbox.width + localX]) continue
                val globalX = bbox.x0 + localX
                val globalY = bbox.y0 + localY
                indices += globalY * bufferWidth + globalX
            }
        }
        return indices
    }

    /**
     * How many predicted holds correspond to a real ground-truth object, at what IoU threshold.
     * A predicted region only counts as a true positive for a ground-truth object once their IoU
     * clears [iouThreshold] — matching is greedy-by-best-IoU, and each side is used at most once
     * (one predicted region can't double-count against two ground-truth objects, or vice versa).
     */
    data class WholeObjectMetrics(val truePositives: Int, val falsePositives: Int, val falseNegatives: Int) {
        val precision: Double get() = if (truePositives + falsePositives == 0) 0.0 else truePositives.toDouble() / (truePositives + falsePositives)
        val recall: Double get() = if (truePositives + falseNegatives == 0) 0.0 else truePositives.toDouble() / (truePositives + falseNegatives)
    }

    /**
     * The IoU a predicted region must clear against a ground-truth object before it's credited as
     * "the same object" for whole-object Precision/Recall. Set well above the common 0.5 PASCAL-VOC
     * "detected" convention: this whole project's stated engineering principle (repeated across
     * Phases 1-7 — precision over recall, strict color/hue discrimination over permissive matching)
     * means a sloppy, partially-overlapping, or over-grown detection should NOT be credited as a
     * correct match just because it's roughly in the right place — a weak overlap should count
     * against the pipeline (as a missed ground-truth object AND a spurious prediction), not be
     * papered over as a success. 0.75 is a deliberately strict bar for exactly that reason.
     */
    const val WHOLE_OBJECT_MATCH_IOU_THRESHOLD: Double = 0.75

    fun wholeObjectMetrics(
        predicted: List<Set<Int>>,
        groundTruth: List<Set<Int>>,
        iouThreshold: Double = WHOLE_OBJECT_MATCH_IOU_THRESHOLD,
    ): WholeObjectMetrics {
        data class Candidate(val predictedIndex: Int, val groundTruthIndex: Int, val iou: Double)

        val candidates = ArrayList<Candidate>()
        for (p in predicted.indices) {
            for (g in groundTruth.indices) {
                val iou = pixelMetrics(predicted[p], groundTruth[g]).iou
                if (iou >= iouThreshold) candidates += Candidate(p, g, iou)
            }
        }
        candidates.sortByDescending { it.iou }

        val usedPredicted = HashSet<Int>()
        val usedGroundTruth = HashSet<Int>()
        for (candidate in candidates) {
            if (candidate.predictedIndex in usedPredicted || candidate.groundTruthIndex in usedGroundTruth) continue
            usedPredicted += candidate.predictedIndex
            usedGroundTruth += candidate.groundTruthIndex
        }

        val truePositives = usedPredicted.size
        val falsePositives = predicted.size - usedPredicted.size
        val falseNegatives = groundTruth.size - usedGroundTruth.size
        return WholeObjectMetrics(truePositives, falsePositives, falseNegatives)
    }
}

/**
 * Phase 8's honest answer to "where does the real-photo test-image dataset live": nowhere yet.
 * This project has no real gym/climbing-wall photographs anywhere in its repo or build
 * environment — every fixture used across Phases 3-7's tests (and Phase 8's own regression tests
 * in `RouteColorDetectorRegressionTest.kt`) is a hand-built synthetic [PixelBuffer]. Fabricating a
 * fake "real" dataset here would misrepresent what's actually been validated, so this is
 * deliberately left as a placeholder rather than populated with synthetic images dressed up as
 * real ones.
 *
 * Reserved location for real data, once supplied: `app/src/test/resources/colordetection/realFrames/`
 * (currently contains only a `.gitkeep` placeholder — no images). Expected future convention:
 * - One reference video frame per file, named `<routeColor>_<gymOrLocationTag>_NN.jpg` (e.g.
 *   `red_homeGym_01.jpg`), matching a real gym wall photographed under real lighting.
 * - A hand-annotated ground-truth mask or polygon per frame (e.g. a same-named `.json` sidecar
 *   listing each real hold's true pixel region), produced by a human labeling the actual holds —
 *   not derived from this pipeline's own output, which would make it circular as a check.
 * - A future benchmark pass would load each frame into a [PixelBuffer] (there is already a
 *   `PixelBuffer.fromBitmap` path for real decoded images, unused in unit tests today because
 *   `android.graphics.Bitmap` can't be built in a plain JVM test without Robolectric, which this
 *   project doesn't use — loading a `.jpg` resource as a `Bitmap` would need to happen in an
 *   instrumented/androidTest, not `testDebugUnitTest`), run [RouteColorDetector.detect], and score
 *   the result against the hand-annotated ground truth via [SegmentationMetrics] — the same
 *   metrics this phase's synthetic regression tests already exercise, just against real footage
 *   instead of synthetic squares. Until real photos are supplied, this file only documents the
 *   intended shape; it does not simulate or fabricate what the real numbers would be.
 */
private object RealFrameDatasetLocation
