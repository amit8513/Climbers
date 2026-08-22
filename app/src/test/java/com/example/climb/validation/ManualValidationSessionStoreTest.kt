package com.example.climb.validation

import com.example.climb.analysis.contact.Limb
import com.example.climb.clubs.AttemptResult
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import org.json.JSONArray
import org.json.JSONObject
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

    @Test
    fun `a session with every Phase 4B field populated round-trips exactly`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        val session = fullSession("session-4b", 3_000L).copy(
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
            attemptStartTimestampMs = 1_500L,
            wallSetupId = "wall-setup-1",
            expectedRouteId = 42L,
            expectedResult = AttemptResult.SEND,
        )

        store.saveSession(session)
        val loaded = store.loadSession("session-4b")

        assertEquals(session, loaded)
    }

    @Test
    fun `a session built the old way still round-trips with Phase 4B fields defaulted`() {
        val store = LocalJsonManualValidationSessionStore(tempFolder.root)
        val session = fullSession("pre-4b", 4_000L)

        store.saveSession(session)
        val loaded = store.loadSession("pre-4b")

        assertEquals(session, loaded)
        assertEquals(emptyList<ValidationRouteDefinition>(), loaded?.routeDefinitions)
        assertEquals(0L, loaded?.attemptStartTimestampMs)
        assertNull(loaded?.wallSetupId)
        assertNull(loaded?.expectedRouteId)
        assertNull(loaded?.expectedResult)
    }

    @Test
    fun `a hand-constructed JSON string missing every Phase 4B key parses without throwing`() {
        val legacyJson = JSONObject().apply {
            put("validationSessionId", "hand-written")
            put("referenceImagePath", "/local/ref.jpg")
            put("videoPath", "/local/video.mp4")
            put("wallOrFixtureId", "wall-a")
            put("cameraGeometryProfileVersion", 1)
            put("annotatedHolds", JSONArray())
            put("startHoldIds", JSONArray())
            put("finishHoldIds", JSONArray())
            put("groundTruthContacts", JSONArray())
            put("notes", JSONObject.NULL)
            put("createdAtEpochMs", 5_000L)
            // Deliberately no "routeDefinitions"/"attemptStartTimestampMs"/"wallSetupId"/
            // "expectedRouteId"/"expectedResult" keys at all - this is what a pre-Phase-4B, or any
            // other hand-authored, session JSON file looks like.
        }.toString()

        val session = legacyJson.toManualValidationSession()

        assertEquals("hand-written", session.validationSessionId)
        assertEquals(emptyList<ValidationRouteDefinition>(), session.routeDefinitions)
        assertEquals(0L, session.attemptStartTimestampMs)
        assertNull(session.wallSetupId)
        assertNull(session.expectedRouteId)
        assertNull(session.expectedResult)
    }
}
