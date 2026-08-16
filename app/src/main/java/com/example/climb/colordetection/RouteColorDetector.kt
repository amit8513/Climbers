package com.example.climb.colordetection

/**
 * Single top-level entry point chaining Phases 3-5: strict candidate detection
 * ([HoldComponentDetector]) -> edge-aware boundary refinement ([HoldBoundaryRefiner]) ->
 * whole-object color validation, rejection, and final confidence scoring ([HoldColorValidator],
 * [HoldConfidenceEvaluator]). Phase 6 (final render) is the intended caller of [detect] once this
 * pipeline is wired into the real video-review flow — this facade exists now because, with
 * detection, refinement, and validation all real, there's finally one clean call site worth
 * naming rather than making every caller re-chain three functions by hand. Still operates entirely
 * on [PixelBuffer] (no `android.graphics.Bitmap`, no OpenCV) — see [PixelBuffer]'s own doc comment
 * for why.
 */
object RouteColorDetector {

    /**
     * @return one [DetectedHold] per hold that survives Phase 5 validation, each with
     * [DetectedHold.colorConsistencyRatioVsOwnMedian]/[DetectedHold.colorConsistencyRatioVsTargetCenter]/
     * [DetectedHold.growthAreaRatio] populated and [DetectedHold.confidence] set to
     * [HoldConfidenceEvaluator]'s final (non-preliminary) score. A hold whose whole-surface color
     * validation fails ([HoldColorValidator.HoldValidation.passesFloor] is `false`) is dropped
     * entirely, not merely scored low — see [RouteColorDetectionConfig]'s Phase 5 thresholds.
     */
    fun detect(buffer: PixelBuffer, targetModel: TargetColorModel): List<DetectedHold> {
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        if (candidates.isEmpty()) return emptyList()

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)

        return refined.mapNotNull { hold ->
            val validation = HoldColorValidator.validate(buffer, targetModel, candidates, hold)
            if (!validation.passesFloor) return@mapNotNull null
            val confidence = HoldConfidenceEvaluator.evaluate(hold, validation, targetModel)
            hold.copy(
                colorConsistencyRatioVsOwnMedian = validation.colorConsistencyRatioVsOwnMedian,
                colorConsistencyRatioVsTargetCenter = validation.colorConsistencyRatioVsTargetCenter,
                growthAreaRatio = validation.growthAreaRatio,
                confidence = confidence,
            )
        }
    }

    /**
     * Phase 7 developer-tooling variant of [detect]: exposes every intermediate stage — raw
     * Phase-3 [DebugDetectionResult.candidates], Phase-4-refined [DebugDetectionResult.refined],
     * and a per-hold [DebugHoldResult] carrying the real [HoldColorValidator.HoldValidation] and
     * final confidence for every refined hold, whether it ultimately passed or failed — instead of
     * silently dropping rejects the way [detect] does. Purely additive: this independently
     * re-invokes the same underlying stage functions [detect] itself calls (rather than being
     * spliced into that function's body), so [detect] and its existing callers
     * (e.g. [com.example.climb.playback.HoldHighlightPipeline.buildMask]) are completely
     * unaffected. Built for [com.example.climb.ui.detail.HoldDetectionDebugScreen] so a developer
     * tuning thresholds can see exactly which Phase-5 gate a rejected hold failed, on a real frame.
     */
    fun detectWithDebugInfo(buffer: PixelBuffer, targetModel: TargetColorModel): DebugDetectionResult {
        val candidates = HoldComponentDetector.detectCandidates(buffer, targetModel)
        if (candidates.isEmpty()) return DebugDetectionResult(candidates, emptyList(), emptyList())

        val refined = HoldBoundaryRefiner.refineBoundaries(buffer, targetModel, candidates)
        val validated = refined.map { hold ->
            val validation = HoldColorValidator.validate(buffer, targetModel, candidates, hold)
            val confidence = HoldConfidenceEvaluator.evaluate(hold, validation, targetModel)
            DebugHoldResult(hold = hold, validation = validation, confidence = confidence)
        }
        return DebugDetectionResult(candidates, refined, validated)
    }

    /** One Phase-4-refined hold's full Phase-5 verdict — kept even when [passesFloor] is `false`,
     * unlike [detect]'s output, so a developer can see exactly why a hold was rejected. */
    data class DebugHoldResult(
        val hold: DetectedHold,
        val validation: HoldColorValidator.HoldValidation,
        val confidence: Double,
    ) {
        val passesFloor: Boolean get() = validation.passesFloor
    }

    data class DebugDetectionResult(
        val candidates: List<DetectedHold>,
        val refined: List<DetectedHold>,
        val validated: List<DebugHoldResult>,
    )
}
