package com.example.climb.validation

import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionEngine
import com.example.climb.attribution.RouteAttributionScoringConfig

/**
 * Phase 4B's ONLY integration point between the manual validation harness and Phase 4A's
 * `RouteAttributionEngine` (`:shared-domain`, approved, read-only). Deliberately a thin,
 * pure-wiring function — never a re-implementation of any scoring/gating logic, and never a
 * second pose-extraction path.
 *
 * [timeline] must be the EXACT SAME [HoldContactTimeline] object the caller already has (from
 * `ManualValidationOutcome.Processed.timeline`, produced by the existing `HoldContactDetector` run
 * inside [ManualValidationPipeline]) — this function never triggers pose extraction, never
 * constructs its own `HoldContactDetector`, and never accepts a pose estimator/video path/pose
 * frames as a parameter at all. Its only job is candidate-mapping plus a single delegated call to
 * [RouteAttributionEngine.attribute] with the real default [RouteAttributionScoringConfig] — never
 * a tuned instance, since this phase is about observing real-footage behavior, not tuning
 * thresholds.
 */
object ManualValidationAttributionRunner {

    fun run(
        routeDefinitions: List<ValidationRouteDefinition>,
        holds: List<ValidationHoldAnnotation>,
        timeline: HoldContactTimeline,
        attemptStartTimestampMs: Long,
    ): AttributionResult {
        val candidates = routeDefinitions.map { it.toRouteCandidate() }
        val holdShapes = holds.map { HoldShape(it.holdId, it.contourNormalized) }
        return RouteAttributionEngine.attribute(
            candidates = candidates,
            holds = holdShapes,
            timeline = timeline,
            attemptStartTimestampMs = attemptStartTimestampMs,
            config = RouteAttributionScoringConfig(),
        )
    }
}
