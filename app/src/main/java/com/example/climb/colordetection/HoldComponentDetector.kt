package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Object-detection entry point: whole-frame strict candidate mask -> connected components ->
 * per-object statistics -> minimum-size rejection -> preliminary confidence. Operates entirely on
 * [PixelBuffer] (no `android.graphics.Bitmap`), reusing [ColorSpace]'s conversions and
 * [ColorDistance]'s metrics — nothing here reimplements that math.
 *
 * Runs post-hoc on a reviewed recording (per the project's "review after recording" delivery
 * decision), not live during capture, so per-frame cost is not latency-critical the way a live
 * preview would be.
 */
object HoldComponentDetector {

    /**
     * @return one [DetectedHold] per surviving connected component, in arbitrary order. An empty
     * list means no candidate pixels/components cleared every gate — not an error.
     */
    fun detectCandidates(buffer: PixelBuffer, targetModel: TargetColorModel): List<DetectedHold> {
        val mask = buildStrictCandidateMask(buffer, targetModel)
        val labeling = ConnectedComponents.label(mask, buffer.width, buffer.height)
        if (labeling.componentCount == 0) return emptyList()

        val totalPixels = buffer.width * buffer.height
        val minArea = (RouteColorDetectionConfig.MIN_NORMALIZED_HOLD_AREA * totalPixels).let { minimum ->
            if (minimum < 1.0) 1 else minimum.toInt()
        }

        val holds = ArrayList<DetectedHold>(labeling.componentCount)
        for (componentId in 0 until labeling.componentCount) {
            val hold = buildComponentStats(buffer, labeling, componentId, targetModel) ?: continue
            if (hold.area < minArea) continue
            holds += hold
        }
        return holds
    }

    /**
     * Builds the strict, whole-frame candidate mask a pixel must clear ALL of to seed a
     * component: saturation range, hue (or, for achromatic colors, an absolute luminance bound
     * instead of hue), and Lab color distance — mirroring the same hue-AND-saturation-AND-value
     * three-part gate this project's original full-frame hue-isolation shader (since replaced by
     * real per-object detection) used to apply, expressed here in Lab-aware, per-color-tuned form.
     *
     * Uses [Cie76DistanceMetric], not [Ciede2000DistanceMetric]: CIEDE2000 is meaningfully more
     * expensive per comparison (several trig calls, see its own doc comment) and running it across
     * every pixel of a whole frame is wasted cost when a cheap Euclidean Lab distance is enough to
     * seed candidates — CIEDE2000 is reserved for the much smaller per-object validation pass once
     * components already exist and only a handful remain to check precisely.
     *
     * Uses [RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD], NOT [TargetColorModel]'s own
     * `deltaEThreshold` (documented on that type as the looser "possible match" gate) — these are
     * meant to be *strict* seeds. Loosening the boundary back out from a strict seed is later
     * boundary-refinement work, not something to blend into seeding.
     */
    private fun buildStrictCandidateMask(buffer: PixelBuffer, targetModel: TargetColorModel): BooleanArray {
        val mask = BooleanArray(buffer.pixels.size)
        for (i in buffer.pixels.indices) {
            val x = i % buffer.width
            val y = i / buffer.width
            val rgb = buffer.rgbAt(x, y)
            val hsv = ColorSpace.rgbToHsv(rgb)

            if (hsv.s !in targetModel.saturationRange) continue

            val lab = ColorSpace.rgbToLab(rgb)

            if (targetModel.isAchromatic) {
                val luminanceOk = when (targetModel.selectedColor) {
                    RouteColor.WHITE -> lab.l >= RouteColorDetectionConfig.WHITE_MIN_LUMINANCE
                    RouteColor.BLACK -> lab.l <= RouteColorDetectionConfig.BLACK_MAX_LUMINANCE
                    else -> true // an achromatic tolerance was set on a chromatic color; don't gate on it here
                }
                if (!luminanceOk) continue
            } else {
                val hueDist = circularHueDistance(hsv.h, targetModel.hsvCenter.h)
                if (hueDist > targetModel.hueToleranceDegrees) continue
                if (abs(lab.l - targetModel.labCenter.l) > targetModel.luminanceTolerance) continue
            }

            val colorDist = Cie76DistanceMetric.distance(lab, targetModel.labCenter)
            if (colorDist > RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD) continue

            mask[i] = true
        }
        return mask
    }

    private fun buildComponentStats(
        buffer: PixelBuffer,
        labeling: ConnectedComponents.Labeling,
        componentId: Int,
        targetModel: TargetColorModel,
    ): DetectedHold? {
        val memberIndices = ArrayList<Int>()
        for (i in labeling.labels.indices) {
            if (labeling.labels[i] != componentId) continue
            memberIndices += i
        }
        return statsFromMemberIndices(buffer, memberIndices, targetModel, componentId)
    }

    /**
     * Computes a full [DetectedHold]'s statistics (bbox, local mask, mean/median Lab, mean HSV,
     * color/hue distance, preliminary confidence) from a raw list of global pixel indices —
     * extracted out of [buildComponentStats] so later phases (boundary refinement's post-merge
     * recomputation) can reuse the exact same formulas rather than approximating by combining
     * pre-merge stats. No behavior change versus the original inline version.
     *
     * @return `null` if [memberGlobalIndices] is empty.
     */
    internal fun statsFromMemberIndices(
        buffer: PixelBuffer,
        memberGlobalIndices: List<Int>,
        targetModel: TargetColorModel,
        id: Int,
    ): DetectedHold? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        val labs = ArrayList<LabColor>()
        val hsvs = ArrayList<HsvColor>()

        for (i in memberGlobalIndices) {
            val x = i % buffer.width
            val y = i / buffer.width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            sumX += x
            sumY += y
            count++
            val rgb = buffer.rgbAt(x, y)
            labs += ColorSpace.rgbToLab(rgb)
            hsvs += ColorSpace.rgbToHsv(rgb)
        }
        if (count == 0) return null

        val bbox = BoundingBox(minX, minY, maxX, maxY)
        val localMask = BooleanArray(bbox.width * bbox.height)
        for (i in memberGlobalIndices) {
            val x = i % buffer.width
            val y = i / buffer.width
            localMask[(y - bbox.y0) * bbox.width + (x - bbox.x0)] = true
        }

        val meanLab = LabColor(
            l = labs.sumOf { it.l } / count,
            a = labs.sumOf { it.a } / count,
            b = labs.sumOf { it.b } / count,
        )
        val medianLab = LabColor(
            l = median(labs.map { it.l }),
            a = median(labs.map { it.a }),
            b = median(labs.map { it.b }),
        )
        val meanHsv = circularMeanHsv(hsvs)

        // The component's own color distance/hue distance to the target, for downstream reporting
        // and confidence scoring — informational statistics about this specific object, distinct
        // from the per-pixel gates buildStrictCandidateMask already applied to seed it.
        val colorDistance = Cie76DistanceMetric.distance(medianLab, targetModel.labCenter)
        val hueDistance = if (targetModel.isAchromatic) 0f else circularHueDistance(meanHsv.h, targetModel.hsvCenter.h)

        return DetectedHold(
            id = id,
            boundingBox = bbox,
            mask = localMask,
            area = count,
            centroid = Centroid(sumX / count, sumY / count),
            meanLab = meanLab,
            medianLab = medianLab,
            meanHsv = meanHsv,
            colorDistance = colorDistance,
            hueDistance = hueDistance,
            confidence = preliminaryConfidence(colorDistance, hueDistance, targetModel),
        )
    }

    /**
     * Preliminary only (see [DetectedHold.confidence]): a straightforward weighted blend of how
     * close this component's own color is to the target (weighted higher, since color distance is
     * already the primary gate the whole detection depends on) and how close its hue is (skipped
     * for achromatic colors, where hue isn't meaningful). Both terms are clamped to `[0, 1]` before
     * combining so neither can go negative and invert the score's meaning. Real boundary and
     * cross-frame consistency evidence — once actual segmentation/tracking exist — will refine
     * this later; this is intentionally simple for now.
     */
    internal fun preliminaryConfidence(colorDistance: Double, hueDistance: Float, targetModel: TargetColorModel): Double {
        val colorScore = (1.0 - colorDistance / RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD).coerceIn(0.0, 1.0)
        val hueScore = if (targetModel.isAchromatic) {
            1.0
        } else {
            (1.0 - hueDistance / targetModel.hueToleranceDegrees).coerceIn(0.0, 1.0)
        }
        return (0.65 * colorScore + 0.35 * hueScore).coerceIn(0.0, 1.0)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    /** Circular mean hue (via unit-vector averaging, same technique as [ColorCalibrator]'s median
     * hue handling) plus plain arithmetic mean for saturation/value. */
    private fun circularMeanHsv(hsvs: List<HsvColor>): HsvColor {
        val sinSum = hsvs.sumOf { sin(Math.toRadians(it.h.toDouble())) }
        val cosSum = hsvs.sumOf { cos(Math.toRadians(it.h.toDouble())) }
        var hueDeg = Math.toDegrees(atan2(sinSum, cosSum)).toFloat()
        if (hueDeg < 0f) hueDeg += 360f
        return HsvColor(
            h = hueDeg,
            s = (hsvs.sumOf { it.s.toDouble() } / hsvs.size).toFloat(),
            v = (hsvs.sumOf { it.v.toDouble() } / hsvs.size).toFloat(),
        )
    }
}
