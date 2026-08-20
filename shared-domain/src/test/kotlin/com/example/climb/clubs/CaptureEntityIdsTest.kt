package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral coverage for [CaptureEntityIds] — a prior round's reflection-only test ("id field is
 * a String") was explicitly called out as not sufficient. This verifies the actual contract:
 * [CaptureEntityIds.newSessionId] is genuinely random/unique, while every derived id is a stable,
 * deterministic function of its inputs (so a retried upload/re-run resolves to the same id rather
 * than minting an orphaned duplicate), and the derivation scheme never collides across entity
 * types or across different session ids.
 */
class CaptureEntityIdsTest {

    @Test
    fun `newSessionId produces all-unique values across many calls`() {
        val ids = (1..1000).map { CaptureEntityIds.newSessionId() }
        assertEquals("expected 1000 unique session ids", 1000, ids.toSet().size)
    }

    @Test
    fun `videoAssetId is deterministic for the same session id`() {
        val sessionId = CaptureEntityIds.newSessionId()
        assertEquals(CaptureEntityIds.videoAssetId(sessionId), CaptureEntityIds.videoAssetId(sessionId))
    }

    @Test
    fun `poseArtifactId is deterministic for the same session id and version`() {
        val sessionId = CaptureEntityIds.newSessionId()
        assertEquals(
            CaptureEntityIds.poseArtifactId(sessionId, 1),
            CaptureEntityIds.poseArtifactId(sessionId, 1),
        )
    }

    @Test
    fun `attributionResultId is deterministic for the same session id`() {
        val sessionId = CaptureEntityIds.newSessionId()
        assertEquals(
            CaptureEntityIds.attributionResultId(sessionId),
            CaptureEntityIds.attributionResultId(sessionId),
        )
    }

    @Test
    fun `inboxItemId is deterministic for the same session id`() {
        val sessionId = CaptureEntityIds.newSessionId()
        assertEquals(CaptureEntityIds.inboxItemId(sessionId), CaptureEntityIds.inboxItemId(sessionId))
    }

    @Test
    fun `different session ids produce different videoAssetId`() {
        val a = CaptureEntityIds.newSessionId()
        val b = CaptureEntityIds.newSessionId()
        assertNotEquals(CaptureEntityIds.videoAssetId(a), CaptureEntityIds.videoAssetId(b))
    }

    @Test
    fun `different session ids produce different poseArtifactId`() {
        val a = CaptureEntityIds.newSessionId()
        val b = CaptureEntityIds.newSessionId()
        assertNotEquals(CaptureEntityIds.poseArtifactId(a, 1), CaptureEntityIds.poseArtifactId(b, 1))
    }

    @Test
    fun `different session ids produce different attributionResultId`() {
        val a = CaptureEntityIds.newSessionId()
        val b = CaptureEntityIds.newSessionId()
        assertNotEquals(CaptureEntityIds.attributionResultId(a), CaptureEntityIds.attributionResultId(b))
    }

    @Test
    fun `different session ids produce different inboxItemId`() {
        val a = CaptureEntityIds.newSessionId()
        val b = CaptureEntityIds.newSessionId()
        assertNotEquals(CaptureEntityIds.inboxItemId(a), CaptureEntityIds.inboxItemId(b))
    }

    @Test
    fun `poseArtifactId differs across versions for the same session id, matches for the same version`() {
        val sessionId = CaptureEntityIds.newSessionId()
        val v1First = CaptureEntityIds.poseArtifactId(sessionId, 1)
        val v1Second = CaptureEntityIds.poseArtifactId(sessionId, 1)
        val v2 = CaptureEntityIds.poseArtifactId(sessionId, 2)

        assertEquals("same session+version must resolve to the same id (idempotent re-run)", v1First, v1Second)
        assertNotEquals("a new pose-extraction version must never collide with v1", v1First, v2)
    }

    @Test
    fun `no derived id ever equals the raw session id`() {
        val sessionId = CaptureEntityIds.newSessionId()

        assertNotEquals(sessionId, CaptureEntityIds.videoAssetId(sessionId))
        assertNotEquals(sessionId, CaptureEntityIds.poseArtifactId(sessionId, 1))
        assertNotEquals(sessionId, CaptureEntityIds.attributionResultId(sessionId))
        assertNotEquals(sessionId, CaptureEntityIds.inboxItemId(sessionId))
    }

    @Test
    fun `no two derived-id functions collide for the same session id`() {
        val sessionId = CaptureEntityIds.newSessionId()

        val derived = listOf(
            CaptureEntityIds.videoAssetId(sessionId),
            CaptureEntityIds.poseArtifactId(sessionId, 1),
            CaptureEntityIds.attributionResultId(sessionId),
            CaptureEntityIds.inboxItemId(sessionId),
        )

        assertTrue(
            "expected all 4 derived ids to be distinct from each other for one session id, got: $derived",
            derived.toSet().size == derived.size,
        )
    }
}
