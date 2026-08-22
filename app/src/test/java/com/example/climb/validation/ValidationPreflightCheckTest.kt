package com.example.climb.validation

import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationPreflightCheckTest {

    private fun route(routeId: Long = 1L) = ValidationRouteDefinition(
        routeId = routeId,
        name = "route-$routeId",
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    private fun hold(holdId: Int = 1) = ValidationHoldAnnotation(
        holdId = holdId,
        contourNormalized = listOf(
            com.example.climb.colordetection.Point2D(0.1f, 0.1f),
            com.example.climb.colordetection.Point2D(0.2f, 0.1f),
            com.example.climb.colordetection.Point2D(0.15f, 0.2f),
        ),
    )

    /** Every field true, geometry compatible (same aspect ratio, matching profile version) — the
     * fully-happy baseline every "one thing missing" test below mutates exactly one argument of. */
    private fun evaluateHappyPath(
        referenceImageDimensions: ImageDimensions? = ImageDimensions(1920, 1080),
        holds: List<ValidationHoldAnnotation> = listOf(hold()),
        routeDefinitions: List<ValidationRouteDefinition> = listOf(route(1L), route(2L)),
        expectedRouteId: Long? = 1L,
        videoDimensions: ImageDimensions? = ImageDimensions(1920, 1080),
        cameraGeometryProfileVersion: Int = 1,
        expectedGeometryProfileVersion: Int = 1,
        poseArtifactCached: Boolean = false,
    ) = ValidationPreflightCheck.evaluate(
        referenceImageDimensions = referenceImageDimensions,
        holds = holds,
        routeDefinitions = routeDefinitions,
        expectedRouteId = expectedRouteId,
        videoDimensions = videoDimensions,
        cameraGeometryProfileVersion = cameraGeometryProfileVersion,
        expectedGeometryProfileVersion = expectedGeometryProfileVersion,
        poseArtifactCached = poseArtifactCached,
    )

    @Test
    fun `every flag is true and canRunAnalysis is true on the fully happy path`() {
        val check = evaluateHappyPath()

        assertTrue(check.referenceImagePresent)
        assertTrue(check.holdsAnnotated)
        assertTrue(check.hasTwoOrMoreRoutes)
        assertTrue(check.expectedRouteLabeled)
        assertTrue(check.videoReadable)
        assertTrue(check.geometryCompatible)
        assertTrue(check.canRunAnalysis)
        assertTrue(check.blockingReasons.isEmpty())
    }

    @Test
    fun `referenceImagePresent is false and blocks when no reference image dimensions are known`() {
        val check = evaluateHappyPath(referenceImageDimensions = null)

        assertFalse(check.referenceImagePresent)
        assertFalse(check.canRunAnalysis)
        // Missing reference dimensions also makes geometryCompatible false (it needs both
        // dimensions known) - two distinct, both-true reasons, not a bug.
        assertEquals(2, check.blockingReasons.size)
    }

    @Test
    fun `holdsAnnotated is false and blocks when no holds have been annotated`() {
        val check = evaluateHappyPath(holds = emptyList())

        assertFalse(check.holdsAnnotated)
        assertFalse(check.canRunAnalysis)
        assertEquals(1, check.blockingReasons.size)
    }

    @Test
    fun `hasTwoOrMoreRoutes is false and blocks with zero route definitions`() {
        val check = evaluateHappyPath(routeDefinitions = emptyList())

        assertFalse(check.hasTwoOrMoreRoutes)
        assertFalse(check.canRunAnalysis)
        assertEquals(1, check.blockingReasons.size)
    }

    @Test
    fun `hasTwoOrMoreRoutes is false and blocks with exactly one route definition`() {
        val check = evaluateHappyPath(routeDefinitions = listOf(route(1L)))

        assertFalse(check.hasTwoOrMoreRoutes)
        assertFalse(check.canRunAnalysis)
        assertEquals(1, check.blockingReasons.size)
    }

    @Test
    fun `videoReadable is false and blocks when no video dimensions are known`() {
        val check = evaluateHappyPath(videoDimensions = null)

        assertFalse(check.videoReadable)
        assertFalse(check.canRunAnalysis)
        // Missing video dimensions also makes geometryCompatible false (it needs both dimensions
        // known) - two distinct, both-true reasons, not a bug.
        assertEquals(2, check.blockingReasons.size)
    }

    @Test
    fun `geometryCompatible is false when either dimension is unknown, even though that is also covered by referenceImagePresent or videoReadable`() {
        val missingReference = evaluateHappyPath(referenceImageDimensions = null)
        val missingVideo = evaluateHappyPath(videoDimensions = null)

        assertFalse(missingReference.geometryCompatible)
        assertFalse(missingVideo.geometryCompatible)
    }

    @Test
    fun `geometryCompatible is false and blocks on a camera geometry profile version mismatch`() {
        val check = evaluateHappyPath(cameraGeometryProfileVersion = 1, expectedGeometryProfileVersion = 2)

        assertFalse(check.geometryCompatible)
        assertFalse(check.canRunAnalysis)
        assertEquals(1, check.blockingReasons.size)
    }

    @Test
    fun `geometryCompatible is false and blocks on an aspect ratio mismatch between reference and video`() {
        val check = evaluateHappyPath(
            referenceImageDimensions = ImageDimensions(1920, 1080), // landscape
            videoDimensions = ImageDimensions(1080, 1920), // portrait
        )

        assertFalse(check.geometryCompatible)
        assertFalse(check.canRunAnalysis)
        assertEquals(1, check.blockingReasons.size)
    }

    @Test
    fun `expectedRouteLabeled being false never blocks canRunAnalysis`() {
        val check = evaluateHappyPath(expectedRouteId = null)

        assertFalse(check.expectedRouteLabeled)
        assertTrue(check.canRunAnalysis)
        assertTrue(check.blockingReasons.isEmpty())
    }

    @Test
    fun `poseArtifactCached being false never blocks canRunAnalysis`() {
        val check = evaluateHappyPath(poseArtifactCached = false)

        assertFalse(check.poseArtifactCached)
        assertTrue(check.canRunAnalysis)
        assertTrue(check.blockingReasons.isEmpty())
    }

    @Test
    fun `poseArtifactCached being true is reflected as-is and still never blocks`() {
        val check = evaluateHappyPath(poseArtifactCached = true)

        assertTrue(check.poseArtifactCached)
        assertTrue(check.canRunAnalysis)
        assertTrue(check.blockingReasons.isEmpty())
    }

    @Test
    fun `blockingReasons has one distinct entry per failing required check, in checklist order`() {
        val check = evaluateHappyPath(
            referenceImageDimensions = null,
            holds = emptyList(),
            routeDefinitions = emptyList(),
            videoDimensions = null,
        )

        assertFalse(check.canRunAnalysis)
        // referenceImagePresent, holdsAnnotated, hasTwoOrMoreRoutes, videoReadable, and
        // geometryCompatible (both dimensions missing) all fail independently here.
        assertEquals(5, check.blockingReasons.size)
        assertEquals(check.blockingReasons.size, check.blockingReasons.toSet().size)
    }

    @Test
    fun `blockingReasons is empty exactly when canRunAnalysis is true, across the happy path and every single-failure case`() {
        val allChecks = listOf(
            evaluateHappyPath(),
            evaluateHappyPath(referenceImageDimensions = null),
            evaluateHappyPath(holds = emptyList()),
            evaluateHappyPath(routeDefinitions = emptyList()),
            evaluateHappyPath(videoDimensions = null),
            evaluateHappyPath(cameraGeometryProfileVersion = 1, expectedGeometryProfileVersion = 2),
            evaluateHappyPath(expectedRouteId = null),
            evaluateHappyPath(poseArtifactCached = true),
        )

        for (check in allChecks) {
            assertEquals(check.canRunAnalysis, check.blockingReasons.isEmpty())
        }
    }
}
