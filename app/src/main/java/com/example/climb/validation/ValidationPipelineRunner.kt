package com.example.climb.validation

import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseAnalysisProgress
import com.example.climb.pose.PoseAnalysisResult
import com.example.climb.pose.PoseEstimator
import java.io.File

/**
 * A NEW, purely advisory, debug-tooling-only quality gate - NOT part of [HoldContactConfig] or
 * [RouteAttributionScoringConfig], and it never blocks or alters contact/attribution computation.
 * A low-coverage clip still gets a full run through [ValidationPipelineRunner.run]; this constant
 * only decides the value of [ValidationPipelineRunResult.lowPoseCoverage], which merely FLAGS the
 * result for human attention (and is later rolled up into the dataset summary's "low pose
 * coverage" count) - it is never consulted by [ManualValidationPipeline],
 * `com.example.climb.analysis.contact.HoldContactDetector`, or
 * `com.example.climb.attribution.RouteAttributionEngine` themselves.
 */
const val MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT: Float = 50f

/** Generic, honest invalidation messages - Phase 4C's cache layers don't retain the OLD entry's
 * own key once a lookup misses (see each store's `load` - a mismatched key is just a clean miss,
 * never surfaced), so the exact field that changed can't be pinpointed without re-reading raw
 * stored JSON the runner has no business parsing itself. Naming which STAGE's inputs changed is
 * honest and sufficient for the debug UI these are surfaced to. */
private const val POSE_INVALIDATION_REASON =
    "a previous pose artifact existed for this video, but its cache key no longer matches " +
        "(video content, pose extractor version, target fps, or pose analysis config changed)"
private const val CONTACT_INVALIDATION_REASON =
    "a previous contact-analysis cache entry existed for this session, but its cache key no " +
        "longer matches (pose artifact, hold geometry, HoldContactConfig, reference image " +
        "dimensions, or camera geometry profile version changed)"
private const val ATTRIBUTION_INVALIDATION_REASON =
    "a previous attribution cache entry existed for this session, but its cache key no longer " +
        "matches (contact analysis, route definitions, attempt start timestamp, or " +
        "RouteAttributionScoringConfig changed)"

/**
 * The full outcome of one [ValidationPipelineRunner.run] call. [report]/[attributionResult] are
 * non-null exactly when [outcome] is [ManualValidationOutcome.Processed] - both null together
 * whenever [outcome] is [ManualValidationOutcome.Rejected], matching this phase's own invariant
 * that a rejected run never claims a partial report from a stage it didn't actually complete.
 */
data class ValidationPipelineRunResult(
    val outcome: ManualValidationOutcome,
    /** `null` only when [outcome] is [ManualValidationOutcome.Rejected]. */
    val report: ManualValidationReport?,
    /** `null` only when [outcome] is [ManualValidationOutcome.Rejected]. */
    val attributionResult: AttributionResult?,
    val provenance: ValidationPipelineProvenance,
    /** Non-null only on a real pipeline failure - NOT set merely for low pose coverage (see
     * [lowPoseCoverage]'s own doc comment). */
    val error: ValidationPipelineError?,
    /** `true` when [report] is non-null and its
     * [ManualValidationReport.poseConfidenceCoveragePercent] falls below
     * [MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT] - purely advisory, see that constant's own doc
     * comment. Always `false` when [report] is `null`. */
    val lowPoseCoverage: Boolean,
)

/**
 * Phase 4C's ONE orchestrator tying pose extraction, contact analysis, and attribution together
 * WITH local caching and cache-provenance observability - never re-implementing any decision
 * [ManualValidationPipeline]/[ManualValidationAttributionRunner]/
 * `com.example.climb.attribution.RouteAttributionEngine`/
 * `com.example.climb.analysis.contact.HoldContactDetector` already make. Every expensive call this
 * file makes is the exact same call `com.example.climb.ui.validation.ValidationDebugViewModel`
 * already makes today, just consulted-through-cache-first here instead of unconditionally re-run.
 */
object ValidationPipelineRunner {

    suspend fun run(
        session: ManualValidationSession,
        poseEstimator: PoseEstimator,
        referenceImageDimensions: ImageDimensions,
        expectedGeometryProfileVersion: Int,
        holdContactConfig: HoldContactConfig,
        routeAttributionScoringConfig: RouteAttributionScoringConfig,
        poseArtifactStore: PoseArtifactStore,
        contactAnalysisStore: ContactAnalysisStore,
        attributionCacheStore: AttributionCacheStore,
        onProgress: (PoseAnalysisProgress) -> Unit = {},
        /** Approximate, best-effort per-stage progress for a batch/list UI — fired once as this run
         * REACHES each stage (whether that stage then turns out to be a cache hit or a real
         * recompute). Never affects, gates, or gets fed back into anything this function decides —
         * purely an observability callback. Defaults to a no-op so every existing caller (including
         * [ValidationPipelineRunnerTest]'s real disk-backed tests) keeps compiling and behaving
         * exactly as before. */
        onStageChanged: (ClipBatchStatus) -> Unit = {},
    ): ValidationPipelineRunResult {
        // --- 1. POSE STAGE -------------------------------------------------------------------
        onStageChanged(ClipBatchStatus.EXTRACTING_POSE)
        val videoFingerprintValue = videoFingerprint(File(session.videoPath))
        val poseAnalysisConfig = PoseAnalysisConfiguration(targetFps = MANUAL_VALIDATION_TARGET_FPS)
        val poseKey = PoseArtifactCacheKey(
            videoFingerprint = videoFingerprintValue,
            poseExtractorVersion = MANUAL_VALIDATION_POSE_EXTRACTOR_VERSION,
            targetFps = MANUAL_VALIDATION_TARGET_FPS,
            poseAnalysisConfigFingerprint = poseAnalysisConfigFingerprint(poseAnalysisConfig),
            artifactSchemaVersion = CURRENT_POSE_ARTIFACT_SCHEMA_VERSION,
        )

        val cachedPoseArtifact = poseArtifactStore.load(videoFingerprintValue, poseKey)
        val poseProvenance: StageProvenance
        val poseSuccess: PoseAnalysisResult.Success

        if (cachedPoseArtifact != null) {
            poseProvenance = StageProvenance(CacheOutcome.CACHE_HIT)
            poseSuccess = cachedPoseArtifact.toPoseAnalysisSuccess()
        } else {
            val hadPriorPoseEntry = poseArtifactStore.hasAnyEntryFor(videoFingerprintValue)
            val poseInvalidationReason = if (hadPriorPoseEntry) POSE_INVALIDATION_REASON else null

            val poseResult = try {
                ManualValidationPipeline.extractPose(session, poseEstimator, onProgress)
            } catch (e: Exception) {
                return rejectedResult(
                    reason = e.message ?: "unexpected pose extraction failure",
                    provenance = ValidationPipelineProvenance(
                        pose = StageProvenance(CacheOutcome.RECOMPUTED, poseInvalidationReason),
                        contact = null,
                        attribution = null,
                    ),
                    errorCode = ValidationPipelineErrorCode.POSE_EXTRACTION_FAILED,
                    errorMessage = e.message ?: "unexpected pose extraction failure",
                )
            }

            val success = poseResult as? PoseAnalysisResult.Success
            if (success == null) {
                val reason = (poseResult as PoseAnalysisResult.Failure).reason
                return rejectedResult(
                    reason = reason,
                    provenance = ValidationPipelineProvenance(
                        pose = StageProvenance(CacheOutcome.RECOMPUTED, poseInvalidationReason),
                        contact = null,
                        attribution = null,
                    ),
                    errorCode = ValidationPipelineErrorCode.POSE_EXTRACTION_FAILED,
                    errorMessage = reason,
                )
            }

            poseArtifactStore.save(success.toPoseArtifact(poseKey, System.currentTimeMillis()))
            poseProvenance = StageProvenance(CacheOutcome.RECOMPUTED, poseInvalidationReason)
            poseSuccess = success
        }

        // --- 2. CONTACT STAGE -----------------------------------------------------------------
        onStageChanged(ClipBatchStatus.CONTACT_ANALYSIS)
        val contactKey = ContactAnalysisCacheKey(
            poseArtifactCacheKey = poseKey,
            holdGeometryFingerprint = holdGeometryFingerprint(session.annotatedHolds),
            holdContactConfigFingerprint = holdContactConfigFingerprint(holdContactConfig),
            referenceImageDimensionsFingerprint = referenceImageDimensionsFingerprint(referenceImageDimensions),
            cameraGeometryProfileVersion = session.cameraGeometryProfileVersion,
            expectedGeometryProfileVersion = expectedGeometryProfileVersion,
            artifactSchemaVersion = CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION,
        )

        val cachedContactArtifact = contactAnalysisStore.load(session.validationSessionId, contactKey)
        val contactProvenance: StageProvenance
        val processed: ManualValidationOutcome.Processed

        if (cachedContactArtifact != null) {
            contactProvenance = StageProvenance(CacheOutcome.CACHE_HIT)
            processed = cachedContactArtifact.toProcessedOutcome()
        } else {
            val hadPriorContactEntry = contactAnalysisStore.hasAnyEntryFor(session.validationSessionId)
            val contactInvalidationReason = if (hadPriorContactEntry) CONTACT_INVALIDATION_REASON else null

            val contactOutcome = try {
                ManualValidationPipeline.runContactAnalysis(
                    session = session,
                    poseSuccess = poseSuccess,
                    referenceImageDimensions = referenceImageDimensions,
                    expectedGeometryProfileVersion = expectedGeometryProfileVersion,
                    holdContactConfig = holdContactConfig,
                )
            } catch (e: Exception) {
                return rejectedResult(
                    reason = e.message ?: "unexpected contact analysis failure",
                    provenance = ValidationPipelineProvenance(
                        pose = poseProvenance,
                        contact = StageProvenance(CacheOutcome.RECOMPUTED, contactInvalidationReason),
                        attribution = null,
                    ),
                    errorCode = ValidationPipelineErrorCode.CONTACT_ANALYSIS_FAILED,
                    errorMessage = e.message ?: "unexpected contact analysis failure",
                )
            }

            val processedResult = contactOutcome as? ManualValidationOutcome.Processed
            if (processedResult == null) {
                val reason = (contactOutcome as ManualValidationOutcome.Rejected).reason
                return rejectedResult(
                    reason = reason,
                    provenance = ValidationPipelineProvenance(
                        pose = poseProvenance,
                        contact = StageProvenance(CacheOutcome.RECOMPUTED, contactInvalidationReason),
                        attribution = null,
                    ),
                    errorCode = ValidationPipelineErrorCode.VALIDATION_GEOMETRY_MISMATCH,
                    errorMessage = reason,
                )
            }

            // A Rejected contact outcome is deliberately never cached above - nothing expensive to
            // save, and caching a rejection adds complexity for no benefit.
            contactAnalysisStore.save(
                session.validationSessionId,
                processedResult.toContactAnalysisArtifact(contactKey, System.currentTimeMillis()),
            )
            contactProvenance = StageProvenance(CacheOutcome.RECOMPUTED, contactInvalidationReason)
            processed = processedResult
        }

        val report = ManualValidationReportBuilder.build(
            frameDiagnostics = processed.frameDiagnostics,
            timeline = processed.timeline,
            groundTruthContacts = session.groundTruthContacts,
        )
        val lowPoseCoverage = report.poseConfidenceCoveragePercent < MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT

        // --- 3. ATTRIBUTION STAGE --------------------------------------------------------------
        onStageChanged(ClipBatchStatus.ATTRIBUTION)
        // ManualValidationAttributionRunner.run always scores against RouteAttributionEngine's own
        // RouteAttributionScoringConfig() default internally (see that object's doc comment) - the
        // [routeAttributionScoringConfig] parameter passed into this function has no effect on what
        // actually gets computed, so the cache key is fingerprinted against the SAME default the
        // real call uses, never against whatever this parameter happens to hold, so the cache key
        // never lies about what was actually used to produce the cached result.
        val actualRouteAttributionScoringConfig = RouteAttributionScoringConfig()
        val attributionKey = AttributionCacheKey(
            contactAnalysisCacheKey = contactKey,
            routeDefinitionsFingerprint = routeDefinitionsFingerprint(session.routeDefinitions),
            attemptStartTimestampMs = session.attemptStartTimestampMs,
            routeAttributionScoringConfigFingerprint = routeAttributionScoringConfigFingerprint(actualRouteAttributionScoringConfig),
            artifactSchemaVersion = CURRENT_ATTRIBUTION_CACHE_SCHEMA_VERSION,
        )

        val cachedAttributionResult = attributionCacheStore.load(session.validationSessionId, attributionKey)
        val attributionProvenance: StageProvenance
        val attributionResult: AttributionResult

        if (cachedAttributionResult != null) {
            attributionProvenance = StageProvenance(CacheOutcome.CACHE_HIT)
            attributionResult = cachedAttributionResult
        } else {
            val hadPriorAttributionEntry = attributionCacheStore.hasAnyEntryFor(session.validationSessionId)
            val attributionInvalidationReason = if (hadPriorAttributionEntry) ATTRIBUTION_INVALIDATION_REASON else null

            val computed = try {
                ManualValidationAttributionRunner.run(
                    routeDefinitions = session.routeDefinitions,
                    holds = session.annotatedHolds,
                    timeline = processed.timeline,
                    attemptStartTimestampMs = session.attemptStartTimestampMs,
                )
            } catch (e: Exception) {
                return rejectedResult(
                    reason = e.message ?: "unexpected attribution failure",
                    provenance = ValidationPipelineProvenance(
                        pose = poseProvenance,
                        contact = contactProvenance,
                        attribution = StageProvenance(CacheOutcome.RECOMPUTED, attributionInvalidationReason),
                    ),
                    errorCode = ValidationPipelineErrorCode.ATTRIBUTION_FAILED,
                    errorMessage = e.message ?: "unexpected attribution failure",
                )
            }

            attributionCacheStore.save(
                session.validationSessionId,
                AttributionCacheEntry(attributionKey, computed, System.currentTimeMillis()),
            )
            attributionProvenance = StageProvenance(CacheOutcome.RECOMPUTED, attributionInvalidationReason)
            attributionResult = computed
        }

        return ValidationPipelineRunResult(
            outcome = processed,
            report = report,
            attributionResult = attributionResult,
            provenance = ValidationPipelineProvenance(pose = poseProvenance, contact = contactProvenance, attribution = attributionProvenance),
            error = null,
            lowPoseCoverage = lowPoseCoverage,
        )
    }

    /** Every early-exit path shares the exact same shape: a real failure never claims a partial
     * report/attribution result (both always `null` together here, per
     * [ValidationPipelineRunResult]'s own doc comment) and is never treated as merely a low-coverage
     * advisory (`lowPoseCoverage` is always `false` here). */
    private fun rejectedResult(
        reason: String,
        provenance: ValidationPipelineProvenance,
        errorCode: ValidationPipelineErrorCode,
        errorMessage: String,
    ): ValidationPipelineRunResult = ValidationPipelineRunResult(
        outcome = ManualValidationOutcome.Rejected(reason),
        report = null,
        attributionResult = null,
        provenance = provenance,
        error = ValidationPipelineError(errorCode, errorMessage),
        lowPoseCoverage = false,
    )
}
