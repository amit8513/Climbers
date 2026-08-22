package com.example.climb.validation

/** Development/validation-pipeline error codes - NEVER converted into or confused with an official
 * AttributionStatus/AttributionReasonCode value (those stay Phase 4A's own, untouched, real
 * decisions). These describe why the LOCAL DEBUG PIPELINE couldn't produce a result, not a
 * legitimate route-attribution outcome.
 *
 * [POSE_COVERAGE_TOO_LOW] is deliberately advisory/non-fatal - a low-coverage clip still gets a
 * full contact/attribution run, this code is only ever used to FLAG the result for human attention
 * (e.g. in a report or dataset summary), never to abort the pipeline early. Every other code here
 * corresponds to a real early-exit/failure the pipeline can hit.
 */
enum class ValidationPipelineErrorCode {
    VIDEO_UNREADABLE,
    POSE_EXTRACTION_FAILED,
    POSE_COVERAGE_TOO_LOW,
    VALIDATION_GEOMETRY_MISMATCH,
    MISSING_HOLD_GEOMETRY,
    MISSING_ROUTE_DEFINITION,
    POSE_ARTIFACT_VERSION_MISMATCH,
    CONTACT_ANALYSIS_FAILED,
    ATTRIBUTION_FAILED,
}

data class ValidationPipelineError(val code: ValidationPipelineErrorCode, val message: String)
