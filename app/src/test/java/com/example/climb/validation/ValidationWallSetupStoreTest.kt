package com.example.climb.validation

import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ValidationWallSetupStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fullWallSetup(id: String, createdAt: Long) = ValidationWallSetup(
        wallSetupId = id,
        wallOrFixtureId = "wall-a",
        referenceImagePath = "/local/ref.jpg",
        cameraGeometryProfileVersion = 1,
        annotatedHolds = listOf(
            ValidationHoldAnnotation(1, listOf(Point2D(0.4f, 0.4f), Point2D(0.6f, 0.4f), Point2D(0.6f, 0.6f), Point2D(0.4f, 0.6f))),
        ),
        routeDefinitions = listOf(
            ValidationRouteDefinition(
                routeId = 42L,
                name = "red overhang",
                startHoldIds = setOf(1, 2),
                startPolicy = StartPolicy.TWO_HOLDS_ONE_PER_HAND,
                bodyHoldIds = setOf(3, 4),
                finishHoldIds = setOf(9),
                finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
                corridorNormalized = NormalizedRect(0.1f, 0.2f, 0.8f, 0.9f),
            ),
        ),
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `a saved wall setup round-trips exactly, including a non-empty routeDefinitions list`() {
        val store = LocalJsonValidationWallSetupStore(tempFolder.root)
        val setup = fullWallSetup("wall-setup-1", 1_000L)

        store.saveWallSetup(setup)
        val loaded = store.loadWallSetup("wall-setup-1")

        assertEquals(setup, loaded)
        assertTrue(loaded!!.routeDefinitions.isNotEmpty())
    }

    @Test
    fun `loadWallSetup finds a saved setup by id and returns null for an unknown id`() {
        val store = LocalJsonValidationWallSetupStore(tempFolder.root)
        val setup = fullWallSetup("wall-setup-1", 1_000L)
        store.saveWallSetup(setup)

        assertEquals(setup, store.loadWallSetup("wall-setup-1"))
        assertNull(store.loadWallSetup("does-not-exist"))
    }

    @Test
    fun `loadWallSetups returns every saved wall setup, most recent first`() {
        val store = LocalJsonValidationWallSetupStore(tempFolder.root)
        store.saveWallSetup(fullWallSetup("older", 1_000L))
        store.saveWallSetup(fullWallSetup("newer", 2_000L))

        val loaded = store.loadWallSetups()

        assertEquals(listOf("newer", "older"), loaded.map { it.wallSetupId })
    }

    @Test
    fun `deleting a wall setup removes it from subsequent loads`() {
        val store = LocalJsonValidationWallSetupStore(tempFolder.root)
        store.saveWallSetup(fullWallSetup("wall-setup-1", 1_000L))

        store.deleteWallSetup("wall-setup-1")

        assertNull(store.loadWallSetup("wall-setup-1"))
        assertTrue(store.loadWallSetups().isEmpty())
    }

    @Test
    fun `a wall setup with no candidate routes round-trips too`() {
        val store = LocalJsonValidationWallSetupStore(tempFolder.root)
        val setup = ValidationWallSetup(
            wallSetupId = "minimal",
            wallOrFixtureId = "wall-a",
            referenceImagePath = "/local/ref.jpg",
            cameraGeometryProfileVersion = 1,
            annotatedHolds = emptyList(),
            createdAtEpochMs = 1_000L,
        )

        store.saveWallSetup(setup)

        assertEquals(setup, store.loadWallSetup("minimal"))
    }

    @Test
    fun `applyTo copies the wall setup's fields into a fresh session with per-clip fields at their defaults`() {
        val setup = fullWallSetup("wall-setup-1", 1_000L)

        val session = setup.applyTo(
            validationSessionId = "session-1",
            videoPath = "/local/video.mp4",
            createdAtEpochMs = 5_000L,
        )

        assertEquals("session-1", session.validationSessionId)
        assertEquals("/local/video.mp4", session.videoPath)
        assertEquals(5_000L, session.createdAtEpochMs)
        assertEquals(setup.referenceImagePath, session.referenceImagePath)
        assertEquals(setup.annotatedHolds, session.annotatedHolds)
        assertEquals(setup.cameraGeometryProfileVersion, session.cameraGeometryProfileVersion)
        assertEquals(setup.routeDefinitions, session.routeDefinitions)
        assertEquals(setup.wallSetupId, session.wallSetupId)
        assertEquals(setup.wallOrFixtureId, session.wallOrFixtureId)

        assertEquals(emptyList<Int>(), session.startHoldIds)
        assertEquals(emptyList<Int>(), session.finishHoldIds)
        assertEquals(emptyList<GroundTruthContactAnnotation>(), session.groundTruthContacts)
        assertNull(session.notes)
        assertEquals(0L, session.attemptStartTimestampMs)
        assertNull(session.expectedRouteId)
        assertNull(session.expectedResult)
    }

    @Test
    fun `applyTo called twice with different arguments produces two independent sessions differing only by those arguments`() {
        val setup = fullWallSetup("wall-setup-1", 1_000L)

        val first = setup.applyTo(validationSessionId = "session-1", videoPath = "/local/a.mp4", createdAtEpochMs = 5_000L)
        val second = setup.applyTo(validationSessionId = "session-2", videoPath = "/local/b.mp4", createdAtEpochMs = 6_000L)

        assertEquals(
            first.copy(validationSessionId = "shared", videoPath = "shared", createdAtEpochMs = 0L),
            second.copy(validationSessionId = "shared", videoPath = "shared", createdAtEpochMs = 0L),
        )
        assertTrue(first.validationSessionId != second.validationSessionId)
        assertTrue(first.videoPath != second.videoPath)
        assertTrue(first.createdAtEpochMs != second.createdAtEpochMs)
    }
}
