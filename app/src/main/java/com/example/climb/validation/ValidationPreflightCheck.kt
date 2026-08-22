package com.example.climb.validation

import com.example.climb.colordetection.AlignmentCheckResult

/**
 * Phase 4C's "can Run Analysis actually be pressed" checklist for the manual-validation debug
 * screen — a pure, no-Android summary of everything [ManualValidationPipeline] itself would need
 * before it could produce a real [ManualValidationOutcome.Processed] instead of an immediate
 * [ManualValidationOutcome.Rejected]. Deliberately re-derives nothing [ManualValidationGeometryGate]
 * or the attribution engine already decide — [geometryCompatible] just calls the real gate with a
 * throwaway synthetic session (only [ManualValidationSession.cameraGeometryProfileVersion] is ever
 * read by [ManualValidationGeometryGate.check], so nothing else about that synthetic session
 * matters or is guessed at).
 */
data class ValidationPreflightCheck(
    val referenceImagePresent: Boolean,
    val holdsAnnotated: Boolean,
    val hasTwoOrMoreRoutes: Boolean,
    /** Informational only, never blocks [canRunAnalysis] — a developer may legitimately want to
     * run analysis before deciding which candidate route they expect to win. */
    val expectedRouteLabeled: Boolean,
    val videoReadable: Boolean,
    val geometryCompatible: Boolean,
    /** Informational only, never blocks [canRunAnalysis] — surfaces cache provenance to the
     * checklist UI, it never gates whether analysis can run at all. */
    val poseArtifactCached: Boolean,
) {
    val canRunAnalysis: Boolean
        get() = referenceImagePresent && holdsAnnotated && hasTwoOrMoreRoutes && videoReadable && geometryCompatible

    /** Human-readable reasons Run Analysis is disabled, empty when [canRunAnalysis] is true. Order
     * matches the checklist's own natural top-to-bottom order. */
    val blockingReasons: List<String>
        get() = buildList {
            if (!referenceImagePresent) add("No reference wall photo has been imported yet")
            if (!holdsAnnotated) add("No holds have been annotated on the reference photo yet")
            if (!hasTwoOrMoreRoutes) add("At least two candidate routes are needed for the attribution engine to discriminate between")
            if (!videoReadable) add("The video's dimensions could not be read")
            if (!geometryCompatible) {
                add(
                    "Reference photo and video geometry are not compatible yet " +
                        "(dimensions unknown, camera geometry profile mismatch, or aspect ratio drift)",
                )
            }
        }

    companion object {
        fun evaluate(
            referenceImageDimensions: ImageDimensions?,
            holds: List<ValidationHoldAnnotation>,
            routeDefinitions: List<ValidationRouteDefinition>,
            expectedRouteId: Long?,
            videoDimensions: ImageDimensions?,
            cameraGeometryProfileVersion: Int,
            expectedGeometryProfileVersion: Int,
            poseArtifactCached: Boolean,
        ): ValidationPreflightCheck {
            val geometryCompatible = referenceImageDimensions != null &&
                videoDimensions != null &&
                isGeometryCompatible(
                    referenceImageDimensions = referenceImageDimensions,
                    videoDimensions = videoDimensions,
                    cameraGeometryProfileVersion = cameraGeometryProfileVersion,
                    expectedGeometryProfileVersion = expectedGeometryProfileVersion,
                )

            return ValidationPreflightCheck(
                referenceImagePresent = referenceImageDimensions != null,
                holdsAnnotated = holds.isNotEmpty(),
                hasTwoOrMoreRoutes = routeDefinitions.size >= 2,
                expectedRouteLabeled = expectedRouteId != null,
                videoReadable = videoDimensions != null,
                geometryCompatible = geometryCompatible,
                poseArtifactCached = poseArtifactCached,
            )
        }

        private fun isGeometryCompatible(
            referenceImageDimensions: ImageDimensions,
            videoDimensions: ImageDimensions,
            cameraGeometryProfileVersion: Int,
            expectedGeometryProfileVersion: Int,
        ): Boolean {
            val syntheticSession = ManualValidationSession(
                validationSessionId = "preflight-check-synthetic-session",
                referenceImagePath = "preflight-check-synthetic-reference-path",
                videoPath = "preflight-check-synthetic-video-path",
                wallOrFixtureId = "preflight-check-synthetic-wall",
                cameraGeometryProfileVersion = cameraGeometryProfileVersion,
                createdAtEpochMs = 0L,
            )
            val result = ManualValidationGeometryGate.check(
                session = syntheticSession,
                referenceImageDimensions = referenceImageDimensions,
                videoDimensions = videoDimensions,
                expectedGeometryProfileVersion = expectedGeometryProfileVersion,
            )
            return result is AlignmentCheckResult.ValidIdentity
        }
    }
}
