package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentationMetricsTest {

    @Test
    fun `perfect overlap yields IoU precision and recall of exactly 1_0`() {
        val region = setOf(0, 1, 2, 3, 4)
        val metrics = SegmentationMetrics.pixelMetrics(region, region)
        assertEquals(5, metrics.intersection)
        assertEquals(1.0, metrics.iou, 1e-9)
        assertEquals(1.0, metrics.precision, 1e-9)
        assertEquals(1.0, metrics.recall, 1e-9)
    }

    @Test
    fun `zero overlap yields IoU precision and recall of exactly 0_0`() {
        val predicted = setOf(0, 1, 2)
        val groundTruth = setOf(10, 11, 12)
        val metrics = SegmentationMetrics.pixelMetrics(predicted, groundTruth)
        assertEquals(0, metrics.intersection)
        assertEquals(0.0, metrics.iou, 1e-9)
        assertEquals(0.0, metrics.precision, 1e-9)
        assertEquals(0.0, metrics.recall, 1e-9)
    }

    @Test
    fun `partial overlap matches hand-computed exact values`() {
        // predicted = {0,1,2,3} (4), groundTruth = {2,3,4,5} (4), intersection = {2,3} (2),
        // union = {0,1,2,3,4,5} (6).
        val predicted = setOf(0, 1, 2, 3)
        val groundTruth = setOf(2, 3, 4, 5)
        val metrics = SegmentationMetrics.pixelMetrics(predicted, groundTruth)

        assertEquals(2, metrics.intersection)
        assertEquals(6, metrics.unionArea)
        assertEquals(2.0 / 6.0, metrics.iou, 1e-9)
        assertEquals(0.5, metrics.precision, 1e-9) // 2/4
        assertEquals(0.5, metrics.recall, 1e-9) // 2/4
    }

    @Test
    fun `an empty predicted region against a non-empty ground truth scores zero precision and recall`() {
        val metrics = SegmentationMetrics.pixelMetrics(emptySet(), setOf(1, 2, 3))
        assertEquals(0.0, metrics.iou, 1e-9)
        assertEquals(0.0, metrics.precision, 1e-9) // predictedArea == 0 -> defined as 0, not NaN
        assertEquals(0.0, metrics.recall, 1e-9)
    }

    @Test
    fun `globalIndices converts a hold's local mask into the correct global pixel index set`() {
        // A 2x2 hold at bbox (10,20)-(11,21), only its top-left and bottom-right local pixels set.
        val bbox = BoundingBox(10, 20, 11, 21)
        val localMask = booleanArrayOf(
            true, false,
            false, true,
        )
        val hold = sampleHold(bbox, localMask)

        val globalIndices = SegmentationMetrics.globalIndices(hold, bufferWidth = 100)

        // (10,20) -> 20*100+10 = 2010 ; (11,21) -> 21*100+11 = 2111
        assertEquals(setOf(2010, 2111), globalIndices)
    }

    @Test
    fun `whole-object metrics correctly counts true positives, false positives, and false negatives`() {
        // Three ground-truth objects; G1 and G2 are matched well by P1/P2, G3 is missed entirely.
        // P3 is a spurious prediction that doesn't overlap any ground-truth object well.
        val g1 = (0 until 100).toSet()
        val g2 = (1000 until 1100).toSet()
        val g3 = (2000 until 2100).toSet()

        val p1 = (0 until 100).toSet() // perfect match with g1 -> IoU 1.0
        val p2 = (1000 until 1090).toSet() // 90/100 overlap with g2 -> IoU 0.9, still clears 0.75
        val p3 = (5000 until 5050).toSet() // matches nothing

        val result = SegmentationMetrics.wholeObjectMetrics(
            predicted = listOf(p1, p2, p3),
            groundTruth = listOf(g1, g2, g3),
        )

        assertEquals(2, result.truePositives) // p1<->g1, p2<->g2
        assertEquals(1, result.falsePositives) // p3
        assertEquals(1, result.falseNegatives) // g3
        assertEquals(2.0 / 3.0, result.precision, 1e-9)
        assertEquals(2.0 / 3.0, result.recall, 1e-9)
    }

    @Test
    fun `a below-threshold overlap does not count as a whole-object match`() {
        // 50 percent overlap is well below the 0.75 default threshold, so this must NOT be
        // credited as a match even though the regions do overlap somewhat.
        val groundTruth = (0 until 100).toSet()
        val predicted = (50 until 150).toSet() // intersection 50, union 150 -> IoU = 1/3

        val result = SegmentationMetrics.wholeObjectMetrics(
            predicted = listOf(predicted),
            groundTruth = listOf(groundTruth),
        )

        assertEquals(0, result.truePositives)
        assertEquals(1, result.falsePositives)
        assertEquals(1, result.falseNegatives)
    }

    private fun sampleHold(bbox: BoundingBox, localMask: BooleanArray): DetectedHold = DetectedHold(
        id = 0,
        boundingBox = bbox,
        mask = localMask,
        area = localMask.count { it },
        centroid = Centroid(bbox.x0.toDouble(), bbox.y0.toDouble()),
        meanLab = LabColor(0.0, 0.0, 0.0),
        medianLab = LabColor(0.0, 0.0, 0.0),
        meanHsv = HsvColor(0f, 0f, 0f),
        colorDistance = 0.0,
        hueDistance = 0f,
        confidence = 1.0,
    )
}
