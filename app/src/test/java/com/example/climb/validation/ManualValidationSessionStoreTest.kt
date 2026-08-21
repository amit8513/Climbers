package com.example.climb.validation

import com.example.climb.analysis.contact.Limb
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManualValidationSessionStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fullSession(id: String, createdAt: Long) = ManualValidationSession(
        validationSessionId = id,
        referenceImagePath = "/local/ref.jpg",
        videoPath = "/local/video.mp4",
        wallOrFixtureId = "wall-a",
        cameraGeometryProfileVersion = 1,
        annotatedHolds = listOf(
            ValidationHoldAnnotation(1, listOf(Point2D(0.4f, 0.4f), Point2D(0.6f, 0.4f), Point2D(0.6f, 0.6f), Point2D(0.4f, 0.6f))),
        ),
        startHoldIds = listOf(1),
        finishHoldIds = listOf(2),
        groundTruthContacts = listOf(GroundTruthContactAnnotation(Limb.LEFT_HAND, 1, 1000L, "grabbed the crimp")),
        notes = "clean send, fast",
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `a saved session round-trips exactly`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        val session = fullSession("session-1", 1_000L)

        store.saveSession(session)
        val loaded = store.loadSession("session-1")

        assertEquals(session, loaded)
    }

    @Test
    fun `loading a session that was never saved returns null`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        assertNull(store.loadSession("does-not-exist"))
    }

    @Test
    fun `loadSessions returns every saved session, most recent first`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        store.saveSession(fullSession("older", 1_000L))
        store.saveSession(fullSession("newer", 2_000L))

        val loaded = store.loadSessions()

        assertEquals(listOf("newer", "older"), loaded.map { it.validationSessionId })
    }

    @Test
    fun `deleting a session removes it from subsequent loads`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        store.saveSession(fullSession("session-1", 1_000L))
        store.deleteSession("session-1")

        assertNull(store.loadSession("session-1"))
        assertTrue(store.loadSessions().isEmpty())
    }

    @Test
    fun `a minimal session with no optional fields round-trips too`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        val session = ManualValidationSession(
            validationSessionId = "minimal",
            referenceImagePath = "/local/ref.jpg",
            videoPath = "/local/video.mp4",
            wallOrFixtureId = "wall-a",
            cameraGeometryProfileVersion = 1,
            createdAtEpochMs = 1_000L,
        )

        store.saveSession(session)

        assertEquals(session, store.loadSession("minimal"))
    }
}
