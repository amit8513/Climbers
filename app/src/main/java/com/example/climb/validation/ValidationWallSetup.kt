package com.example.climb.validation

/**
 * One physical wall setup — a reference photo, its annotated hold geometry, and the candidate
 * routes a developer has drawn against it — captured once so it doesn't need to be re-annotated
 * for every new video clip filmed against the same wall.
 *
 * This is purely a developer convenience for seeding new [ManualValidationSession]s: applying a
 * [ValidationWallSetup] to a new session (via [applyTo]) simply COPIES its fields into that
 * session's own, already self-contained fields. There is no later runtime lookup or foreign-key
 * dependency from a session back to the [ValidationWallSetup] it was built from — a session's
 * [ManualValidationSession.wallSetupId] is traceability metadata only. Deleting a wall setup never
 * breaks, invalidates, or orphans any session already created from it.
 */
data class ValidationWallSetup(
    val wallSetupId: String,
    val wallOrFixtureId: String,
    val referenceImagePath: String,
    val cameraGeometryProfileVersion: Int,
    val annotatedHolds: List<ValidationHoldAnnotation>,
    val routeDefinitions: List<ValidationRouteDefinition> = emptyList(),
    val createdAtEpochMs: Long,
) {
    init {
        require(wallSetupId.isNotBlank()) { "wallSetupId must not be blank" }
        require(wallOrFixtureId.isNotBlank()) { "wallOrFixtureId must not be blank" }
        require(referenceImagePath.isNotBlank()) { "referenceImagePath must not be blank" }
        require(cameraGeometryProfileVersion > 0) { "cameraGeometryProfileVersion must be positive" }
    }
}

/**
 * Copies this wall setup's fields into a brand-new [ManualValidationSession] for a freshly
 * recorded clip against the same physical wall. [startHoldIds]/[finishHoldIds]/
 * [ManualValidationSession.groundTruthContacts]/[ManualValidationSession.notes]/
 * [ManualValidationSession.attemptStartTimestampMs]/[ManualValidationSession.expectedRouteId]/
 * [ManualValidationSession.expectedResult] are deliberately left at their own class defaults —
 * those are per-clip observations, never copied from a wall setup.
 */
fun ValidationWallSetup.applyTo(
    validationSessionId: String,
    videoPath: String,
    createdAtEpochMs: Long,
): ManualValidationSession = ManualValidationSession(
    validationSessionId = validationSessionId,
    referenceImagePath = referenceImagePath,
    videoPath = videoPath,
    wallOrFixtureId = wallOrFixtureId,
    cameraGeometryProfileVersion = cameraGeometryProfileVersion,
    annotatedHolds = annotatedHolds,
    routeDefinitions = routeDefinitions,
    wallSetupId = wallSetupId,
    createdAtEpochMs = createdAtEpochMs,
)
