package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldConfidenceEvaluatorTest {

    private val targetModel = RouteColorProfiles.defaultFor(RouteColor.RED)

    /** Minimal, directly-constructed [DetectedHold] for pure confidence-formula testing — no
     * pixel data involved, since [HoldConfidenceEvaluator.evaluate] only reads [DetectedHold.hueDistance]/
     * [DetectedHold.area]/[DetectedHold.boundingBox] plus the separately-passed [HoldColorValidator.HoldValidation]. */
    private fun holdWith(hueDistance: Float, area: Int, bboxSide: Int): DetectedHold {
        val bbox = BoundingBox(0, 0, bboxSide - 1, bboxSide - 1)
        return DetectedHold(
            id = 0,
            boundingBox = bbox,
            mask = BooleanArray(bboxSide * bboxSide) { true },
            area = area,
            centroid = Centroid(bboxSide / 2.0, bboxSide / 2.0),
            meanLab = targetModel.labCenter,
            medianLab = targetModel.labCenter,
            meanHsv = targetModel.hsvCenter,
            colorDistance = 0.0,
            hueDistance = hueDistance,
            confidence = 0.0, // overwritten by the evaluator under test
        )
    }

    @Test
    fun `a clean hold scores strictly higher confidence than a dirtier, more grown one`() {
        // Clean: perfect hue match, perfect consistency, no area inflation, fully-filled bbox.
        val cleanHold = holdWith(hueDistance = 0f, area = 400, bboxSide = 20)
        val cleanValidation = HoldColorValidator.HoldValidation(
            colorConsistencyRatioVsOwnMedian = 1.0,
            colorConsistencyRatioVsTargetCenter = 1.0,
            growthAreaRatio = 1.0,
        )

        // Dirtier, but still passing the Phase 5 floor: some hue drift, meaningfully lower
        // consistency on both axes, and real (but bounded) area growth, plus a sparser bbox fill
        // (same area packed into a bigger box, simulating asymmetric wall-ward growth).
        val dirtierHold = holdWith(hueDistance = 4f, area = 400, bboxSide = 24)
        val dirtierValidation = HoldColorValidator.HoldValidation(
            colorConsistencyRatioVsOwnMedian = 0.8,
            colorConsistencyRatioVsTargetCenter = 0.78,
            growthAreaRatio = 1.6,
        )
        assertTrue("dirtier fixture must still pass the floor for this to be a fair confidence comparison", dirtierValidation.passesFloor)

        val cleanConfidence = HoldConfidenceEvaluator.evaluate(cleanHold, cleanValidation, targetModel)
        val dirtierConfidence = HoldConfidenceEvaluator.evaluate(dirtierHold, dirtierValidation, targetModel)

        assertEquals("a perfect hold's confidence should be exactly 1.0", 1.0, cleanConfidence, 1e-9)
        assertTrue(
            "dirtier hold must score strictly lower confidence: clean=$cleanConfidence, dirtier=$dirtierConfidence",
            dirtierConfidence < cleanConfidence,
        )
        // Concrete expected value, hand-computed from the formula (0.5*consistency + 0.3*hue + 0.2*compactness):
        // consistency = (0.8+0.78)/2 = 0.79; hue = 1 - 4/8 = 0.5 (RED's tight tolerance is 8 degrees);
        // compactness = 400/(24*24) = 400/576 = 0.69444...
        // confidence = 0.5*0.79 + 0.3*0.5 + 0.2*0.694444 = 0.395 + 0.15 + 0.138888... = 0.683888...
        assertEquals(0.68388888, dirtierConfidence, 1e-6)
    }

    @Test
    fun `confidence is always clamped to the 0 to 1 range even for pathological inputs`() {
        val hold = holdWith(hueDistance = 999f, area = 1, bboxSide = 1)
        val validation = HoldColorValidator.HoldValidation(
            colorConsistencyRatioVsOwnMedian = 0.0,
            colorConsistencyRatioVsTargetCenter = 0.0,
            growthAreaRatio = 1.0,
        )
        val confidence = HoldConfidenceEvaluator.evaluate(hold, validation, targetModel)
        assertTrue(confidence in 0.0..1.0)
    }

    @Test
    fun `achromatic target models skip the hue term entirely`() {
        val blackModel = RouteColorProfiles.defaultFor(RouteColor.BLACK)
        val hold = holdWith(hueDistance = 0f, area = 100, bboxSide = 10)
        val validation = HoldColorValidator.HoldValidation(
            colorConsistencyRatioVsOwnMedian = 1.0,
            colorConsistencyRatioVsTargetCenter = 1.0,
            growthAreaRatio = 1.0,
        )
        // consistency=1.0, hue forced to 1.0 (achromatic bypass), compactness=1.0 -> confidence must be exactly 1.0
        assertEquals(1.0, HoldConfidenceEvaluator.evaluate(hold, validation, blackModel), 1e-9)
    }
}
