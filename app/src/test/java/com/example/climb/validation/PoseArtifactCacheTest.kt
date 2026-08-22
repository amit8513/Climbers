package com.example.climb.validation

import com.example.climb.pose.BodyBoundingBox
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PoseArtifactCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun sampleKey(targetFps: Int = 15, videoFingerprint: String = "abc123:1000") = PoseArtifactCacheKey(
        videoFingerprint = videoFingerprint,
        poseExtractorVersion = MANUAL_VALIDATION_POSE_EXTRACTOR_VERSION,
        targetFps = targetFps,
        poseAnalysisConfigFingerprint = poseAnalysisConfigFingerprint(PoseAnalysisConfiguration(targetFps = targetFps)),
        artifactSchemaVersion = CURRENT_POSE_ARTIFACT_SCHEMA_VERSION,
    )

    private fun sampleFrame(timestampMs: Long, withBoundingBox: Boolean) = PoseFrame(
        timestampMs = timestampMs,
        landmarks = listOf(
            PoseLandmark(PoseLandmarkType.LEFT_WRIST, 0.1f, 0.2f, 0.3f, 0.9f, 0.8f),
            PoseLandmark(PoseLandmarkType.RIGHT_WRIST, 0.4f, 0.5f, 0.6f, 0.7f, 0.6f),
            PoseLandmark(PoseLandmarkType.LEFT_ANKLE, 0.15f, 0.85f, -0.1f, 0.5f, 0.4f),
        ),
        averageConfidence = 0.75f,
        isReliable = true,
        bodyBoundingBox = if (withBoundingBox) BodyBoundingBox(0.1f, 0.2f, 0.9f, 0.95f) else null,
    )

    private fun sampleArtifact(cacheKey: PoseArtifactCacheKey) = PoseArtifact(
        cacheKey = cacheKey,
        videoWidth = 1920,
        videoHeight = 1080,
        videoDurationMs = 12_345L,
        frames = listOf(
            sampleFrame(0L, withBoundingBox = true),
            sampleFrame(66L, withBoundingBox = false),
            sampleFrame(133L, withBoundingBox = true),
        ),
        createdAtEpochMs = 999_000L,
    )

    // --- videoFingerprint ---

    @Test
    fun `videoFingerprint is stable across two calls on the same file`() {
        val file = temporaryFolder.newFile("clip.mp4")
        file.writeBytes(ByteArray(5000) { it.toByte() })

        assertEquals(videoFingerprint(file), videoFingerprint(file))
    }

    @Test
    fun `videoFingerprint differs for two files with different content`() {
        val fileA = temporaryFolder.newFile("a.mp4")
        val fileB = temporaryFolder.newFile("b.mp4")
        fileA.writeBytes(ByteArray(5000) { it.toByte() })
        fileB.writeBytes(ByteArray(5000) { (it + 1).toByte() })

        assertNotEquals(videoFingerprint(fileA), videoFingerprint(fileB))
    }

    @Test
    fun `videoFingerprint is content-based not path-based - identical content under different names produces the same fingerprint`() {
        val content = ByteArray(10_000) { (it * 7).toByte() }
        val fileA = temporaryFolder.newFile("original-name.mp4")
        val nestedDir = temporaryFolder.newFolder("nested")
        val fileB = File(nestedDir, "totally-different-name.mov")
        fileA.writeBytes(content)
        fileB.writeBytes(content)

        assertEquals(videoFingerprint(fileA), videoFingerprint(fileB))
    }

    // --- poseAnalysisConfigFingerprint ---

    @Test
    fun `poseAnalysisConfigFingerprint differs when targetFps differs`() {
        val configA = PoseAnalysisConfiguration(targetFps = 10)
        val configB = PoseAnalysisConfiguration(targetFps = 15)

        assertNotEquals(poseAnalysisConfigFingerprint(configA), poseAnalysisConfigFingerprint(configB))
    }

    @Test
    fun `poseAnalysisConfigFingerprint differs when minLandmarkConfidence differs`() {
        val configA = PoseAnalysisConfiguration(minLandmarkConfidence = 0.5f)
        val configB = PoseAnalysisConfiguration(minLandmarkConfidence = 0.7f)

        assertNotEquals(poseAnalysisConfigFingerprint(configA), poseAnalysisConfigFingerprint(configB))
    }

    @Test
    fun `poseAnalysisConfigFingerprint is identical for two separately-constructed but field-identical configurations`() {
        val configA = PoseAnalysisConfiguration(targetFps = 12, minLandmarkConfidence = 0.6f)
        val configB = PoseAnalysisConfiguration(targetFps = 12, minLandmarkConfidence = 0.6f)

        assertEquals(poseAnalysisConfigFingerprint(configA), poseAnalysisConfigFingerprint(configB))
    }

    // --- LocalJsonPoseArtifactStore ---

    @Test
    fun `save then load with the same expected key returns an equal artifact - exact round trip`() {
        val store = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        val artifact = sampleArtifact(key)

        store.save(artifact)
        val loaded = store.load(key.videoFingerprint, key)

        assertEquals(artifact, loaded)
    }

    @Test
    fun `load with a different expected key than what was saved returns null`() {
        val store = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey(targetFps = 15)
        store.save(sampleArtifact(key))

        val differentKey = sampleKey(targetFps = 10, videoFingerprint = key.videoFingerprint)

        assertNull(store.load(key.videoFingerprint, differentKey))
    }

    @Test
    fun `load on a path where no file exists returns null rather than crashing`() {
        val store = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("cache"))

        assertNull(store.load("never-saved:123", sampleKey(videoFingerprint = "never-saved:123")))
    }

    @Test
    fun `load on a file containing garbage JSON returns null rather than crashing`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonPoseArtifactStore(directory)
        val key = sampleKey(videoFingerprint = "corrupt:1")
        File(directory, "corrupt_1.json").writeText("{ this is not valid json at all ][")

        assertNull(store.load(key.videoFingerprint, key))
    }

    @Test
    fun `load on a file whose stored artifactSchemaVersion does not match the current version returns null`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonPoseArtifactStore(directory)
        val key = sampleKey(videoFingerprint = "mismatched-schema:1")
        val artifact = sampleArtifact(key)

        // Save a structurally-plausible artifact, then hand-edit the stored schema version to
        // simulate a stale on-disk cache entry from a since-changed schema.
        store.save(artifact)
        val file = File(directory, "mismatched-schema_1.json")
        val mutated = file.readText().replace(
            "\"artifactSchemaVersion\":${CURRENT_POSE_ARTIFACT_SCHEMA_VERSION}",
            "\"artifactSchemaVersion\":999",
        )
        file.writeText(mutated)

        assertNull(store.load(key.videoFingerprint, key))
    }

    @Test
    fun `save never leaves a visible json tmp file behind after a successful save`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonPoseArtifactStore(directory)
        val key = sampleKey(videoFingerprint = "tmp-check:1")

        store.save(sampleArtifact(key))

        val tmpFiles = directory.listFiles { file -> file.name.endsWith(".json.tmp") }.orEmpty()
        assertEquals(0, tmpFiles.size)
    }

    // --- hasAnyEntryFor ---

    @Test
    fun `hasAnyEntryFor is false when nothing has ever been saved for this video fingerprint`() {
        val store = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("cache"))

        assertEquals(false, store.hasAnyEntryFor("never-saved:1"))
    }

    @Test
    fun `hasAnyEntryFor is true once something is saved for this video fingerprint, even under a different key`() {
        val store = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey(targetFps = 15, videoFingerprint = "has-entry:1")
        store.save(sampleArtifact(key))

        assertEquals(true, store.hasAnyEntryFor("has-entry:1"))
        // Still true even though a lookup under a different (now-mismatched) key is a clean miss -
        // hasAnyEntryFor answers "is there SOMETHING here at all", not "does it still match".
        assertNull(store.load("has-entry:1", sampleKey(targetFps = 10, videoFingerprint = "has-entry:1")))
    }
}
