package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.PoseAnalysisConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContactAnalysisCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun samplePoseArtifactCacheKey(targetFps: Int = 15) = PoseArtifactCacheKey(
        videoFingerprint = "abc123:1000",
        poseExtractorVersion = MANUAL_VALIDATION_POSE_EXTRACTOR_VERSION,
        targetFps = targetFps,
        poseAnalysisConfigFingerprint = poseAnalysisConfigFingerprint(PoseAnalysisConfiguration(targetFps = targetFps)),
        artifactSchemaVersion = CURRENT_POSE_ARTIFACT_SCHEMA_VERSION,
    )

    private fun sampleHolds() = listOf(
        ValidationHoldAnnotation(1, listOf(Point2D(0.1f, 0.1f), Point2D(0.2f, 0.1f), Point2D(0.15f, 0.2f))),
        ValidationHoldAnnotation(2, listOf(Point2D(0.5f, 0.5f), Point2D(0.6f, 0.5f), Point2D(0.55f, 0.6f))),
    )

    private fun sampleKey(holds: List<ValidationHoldAnnotation> = sampleHolds()) = ContactAnalysisCacheKey(
        poseArtifactCacheKey = samplePoseArtifactCacheKey(),
        holdGeometryFingerprint = holdGeometryFingerprint(holds),
        holdContactConfigFingerprint = holdContactConfigFingerprint(HoldContactConfig()),
        referenceImageDimensionsFingerprint = referenceImageDimensionsFingerprint(ImageDimensions(1920, 1080)),
        cameraGeometryProfileVersion = 1,
        expectedGeometryProfileVersion = 1,
        artifactSchemaVersion = CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION,
    )

    private fun sampleFrameDiagnostics(): List<ManualValidationFrameDiagnostics> = listOf(
        ManualValidationFrameDiagnostics(
            timestampMs = 0L,
            isReliable = true,
            gapStatesByLimb = mapOf(
                Limb.LEFT_HAND to GapState.NONE,
                Limb.RIGHT_HAND to GapState.NONE,
                Limb.LEFT_FOOT to GapState.SHORT,
                Limb.RIGHT_FOOT to GapState.NONE,
            ),
            establishedHoldByLimb = mapOf(Limb.LEFT_HAND to 1, Limb.RIGHT_HAND to null, Limb.LEFT_FOOT to null, Limb.RIGHT_FOOT to 2),
            candidateHoldByLimb = mapOf(Limb.LEFT_HAND to null, Limb.RIGHT_HAND to 2, Limb.LEFT_FOOT to null, Limb.RIGHT_FOOT to null),
            proxyPositionByLimb = mapOf(
                Limb.LEFT_HAND to Point2D(0.12f, 0.11f),
                Limb.RIGHT_HAND to Point2D(0.51f, 0.49f),
                Limb.LEFT_FOOT to null,
                Limb.RIGHT_FOOT to Point2D(0.58f, 0.58f),
            ),
            establishedConfidenceByLimb = mapOf(Limb.LEFT_HAND to 0.9f, Limb.RIGHT_HAND to 0f, Limb.LEFT_FOOT to 0f, Limb.RIGHT_FOOT to 0.75f),
        ),
        ManualValidationFrameDiagnostics(
            timestampMs = 66L,
            isReliable = false,
            gapStatesByLimb = mapOf(
                Limb.LEFT_HAND to GapState.DECAYING,
                Limb.RIGHT_HAND to GapState.RESET,
                Limb.LEFT_FOOT to GapState.NONE,
                Limb.RIGHT_FOOT to GapState.NONE,
            ),
            establishedHoldByLimb = mapOf(Limb.LEFT_HAND to null, Limb.RIGHT_HAND to null, Limb.LEFT_FOOT to 1, Limb.RIGHT_FOOT to 2),
            candidateHoldByLimb = mapOf(Limb.LEFT_HAND to null, Limb.RIGHT_HAND to null, Limb.LEFT_FOOT to null, Limb.RIGHT_FOOT to null),
            proxyPositionByLimb = mapOf(
                Limb.LEFT_HAND to null,
                Limb.RIGHT_HAND to null,
                Limb.LEFT_FOOT to Point2D(0.13f, 0.19f),
                Limb.RIGHT_FOOT to Point2D(0.57f, 0.59f),
            ),
            establishedConfidenceByLimb = mapOf(Limb.LEFT_HAND to 0.3f, Limb.RIGHT_HAND to 0f, Limb.LEFT_FOOT to 0.8f, Limb.RIGHT_FOOT to 0.7f),
        ),
    )

    private fun sampleTimeline(): HoldContactTimeline = HoldContactTimeline(
        listOf(
            HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 0L, 0.9f, EvidenceQuality.STRONG),
            HoldContactEvent(Limb.RIGHT_FOOT, 2, ContactEventType.ESTABLISHED, 0L, 0.75f, EvidenceQuality.FALLBACK),
            HoldContactEvent(
                Limb.LEFT_HAND,
                1,
                ContactEventType.RELEASED,
                66L,
                0.3f,
                EvidenceQuality.UNCERTAIN,
                releaseReason = ReleaseReason.LONG_GAP_RESET,
            ),
        ),
    )

    private fun sampleArtifact(cacheKey: ContactAnalysisCacheKey) = ContactAnalysisArtifact(
        cacheKey = cacheKey,
        frameDiagnostics = sampleFrameDiagnostics(),
        videoDurationMs = 5_000L,
        timeline = sampleTimeline(),
        createdAtEpochMs = 123_456L,
    )

    // --- holdGeometryFingerprint ---

    @Test
    fun `holdGeometryFingerprint is identical for the same holds regardless of input list order`() {
        val holds = sampleHolds()
        val reversed = holds.reversed()

        assertEquals(holdGeometryFingerprint(holds), holdGeometryFingerprint(reversed))
    }

    @Test
    fun `holdGeometryFingerprint differs when a hold's contour changes`() {
        val original = sampleHolds()
        val edited = listOf(
            ValidationHoldAnnotation(1, listOf(Point2D(0.1f, 0.1f), Point2D(0.25f, 0.1f), Point2D(0.15f, 0.2f))),
            original[1],
        )

        assertNotEquals(holdGeometryFingerprint(original), holdGeometryFingerprint(edited))
    }

    @Test
    fun `holdGeometryFingerprint differs when a hold is added`() {
        val original = sampleHolds()
        val withExtra = original + ValidationHoldAnnotation(3, listOf(Point2D(0.8f, 0.8f), Point2D(0.9f, 0.8f), Point2D(0.85f, 0.9f)))

        assertNotEquals(holdGeometryFingerprint(original), holdGeometryFingerprint(withExtra))
    }

    @Test
    fun `holdGeometryFingerprint differs when a hold is removed`() {
        val original = sampleHolds()
        val fewer = original.drop(1)

        assertNotEquals(holdGeometryFingerprint(original), holdGeometryFingerprint(fewer))
    }

    // --- LocalJsonContactAnalysisStore ---

    @Test
    fun `save then load with the same expected key returns an equal artifact - exact round trip`() {
        val store = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        val artifact = sampleArtifact(key)

        store.save("session-1", artifact)
        val loaded = store.load("session-1", key)

        assertEquals(artifact, loaded)
    }

    @Test
    fun `load with a different expected key than what was saved returns null`() {
        val store = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        store.save("session-2", sampleArtifact(key))

        // Simulates a hold-annotation edit after the cache entry was written.
        val editedHolds = sampleHolds().drop(1)
        val differentKey = key.copy(holdGeometryFingerprint = holdGeometryFingerprint(editedHolds))

        assertNull(store.load("session-2", differentKey))
    }

    @Test
    fun `load on a session id where no file exists returns null rather than crashing`() {
        val store = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("cache"))

        assertNull(store.load("never-saved-session", sampleKey()))
    }

    @Test
    fun `load on a file containing garbage JSON returns null rather than crashing`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonContactAnalysisStore(directory)
        File(directory, "corrupt-session.json").writeText("{ this is not valid json at all ][")

        assertNull(store.load("corrupt-session", sampleKey()))
    }

    @Test
    fun `load on a file whose stored artifactSchemaVersion does not match the current version returns null`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonContactAnalysisStore(directory)
        val key = sampleKey()
        store.save("mismatched-schema-session", sampleArtifact(key))

        val file = File(directory, "mismatched-schema-session.json")
        val mutated = file.readText().replace(
            "\"artifactSchemaVersion\":$CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION",
            "\"artifactSchemaVersion\":999",
        )
        file.writeText(mutated)

        assertNull(store.load("mismatched-schema-session", key))
    }

    @Test
    fun `save never leaves a visible json tmp file behind after a successful save`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonContactAnalysisStore(directory)

        store.save("tmp-check-session", sampleArtifact(sampleKey()))

        val tmpFiles = directory.listFiles { file -> file.name.endsWith(".json.tmp") }.orEmpty()
        assertEquals(0, tmpFiles.size)
    }

    // --- hasAnyEntryFor ---

    @Test
    fun `hasAnyEntryFor is false when nothing has ever been saved for this session`() {
        val store = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("cache"))

        assertEquals(false, store.hasAnyEntryFor("never-saved-session"))
    }

    @Test
    fun `hasAnyEntryFor is true once something is saved for this session, even under a different key`() {
        val store = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        store.save("has-entry-session", sampleArtifact(key))

        assertEquals(true, store.hasAnyEntryFor("has-entry-session"))
        val differentKey = key.copy(holdGeometryFingerprint = holdGeometryFingerprint(sampleHolds().drop(1)))
        assertNull(store.load("has-entry-session", differentKey))
    }
}
