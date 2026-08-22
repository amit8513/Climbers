package com.example.climb.validation

import com.example.climb.pose.BodyBoundingBox
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseAnalysisResult
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Bump this whenever [PoseArtifact]'s own JSON shape changes in a way an already-persisted
 * artifact on disk could no longer be parsed the same way - a stored artifact whose own
 * `artifactSchemaVersion` doesn't match this is treated as a clean cache miss, never a crash (see
 * [LocalJsonPoseArtifactStore.load]). Same convention as this codebase's other version literals
 * (`cameraGeometryProfileVersion`, `ClipValidationExportBuilder.CURRENT_EXPORT_FORMAT_VERSION`,
 * `visionProfileFormatVersion`). */
const val CURRENT_POSE_ARTIFACT_SCHEMA_VERSION: Int = 1

/** Identifies which real MediaPipe model/behavior produced a cached [PoseArtifact] - bump this
 * literal by hand whenever [com.example.climb.pose.MediaPipePoseEstimator]'s underlying model or
 * extraction behavior changes, so an artifact cached under the old behavior is never silently
 * reused under the new one (a changed value simply fails to match [PoseArtifactCacheKey.poseExtractorVersion]
 * on the next cache lookup, producing a clean re-extraction rather than a stale hit). */
const val MANUAL_VALIDATION_POSE_EXTRACTOR_VERSION: String = "mediapipe-pose_landmarker_lite-v1"

/**
 * Everything that must match, exactly, for a previously-computed [PoseArtifact] to be considered
 * valid for reuse instead of re-running MediaPipe. Phase 4C's local caching layer around the
 * existing [ManualValidationPipeline.extractPose] call - never changes what MediaPipe decides,
 * only whether it needs to run again for the same inputs.
 */
data class PoseArtifactCacheKey(
    val videoFingerprint: String,
    val poseExtractorVersion: String,
    val targetFps: Int,
    val poseAnalysisConfigFingerprint: String,
    val artifactSchemaVersion: Int,
)

/** A cached [PoseAnalysisResult.Success], plus the [cacheKey] it was computed under and when it was
 * produced - see [toPoseArtifact]/[toPoseAnalysisSuccess] for the two conversions to/from the real
 * pose-estimator result shape. */
data class PoseArtifact(
    val cacheKey: PoseArtifactCacheKey,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoDurationMs: Long,
    val frames: List<PoseFrame>,
    val createdAtEpochMs: Long,
)

/**
 * A cheap-but-real content fingerprint of a video file: a SHA-256 digest of at most the file's
 * first 1,000,000 bytes, combined with the file's exact total byte length. Deliberately NOT a
 * full-file hash - clips can be large, and this must stay cheap enough to call before every
 * cache lookup. Two different videos sharing both the same first-1MB hash AND the exact same
 * total length is not a realistic concern for this dev tool; this is a documented approximation,
 * same honesty standard this codebase already applies to its other POC-level approximations (e.g.
 * [ManualValidationGeometryGate]'s identity-only transform).
 */
fun videoFingerprint(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var totalRead = 0
        while (totalRead < MAX_FINGERPRINT_BYTES) {
            val toRead = minOf(buffer.size, MAX_FINGERPRINT_BYTES - totalRead)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            totalRead += read
        }
    }
    val hex = digest.digest().joinToString("") { "%02x".format(it) }
    return "$hex:${file.length()}"
}

private const val MAX_FINGERPRINT_BYTES = 1_000_000

/** [PoseAnalysisConfiguration] is already a data class whose auto-generated `toString()` covers
 * every field - reused directly rather than hand-writing a second field-by-field fingerprint that
 * could drift out of sync with the real class. */
fun poseAnalysisConfigFingerprint(config: PoseAnalysisConfiguration): String = config.toString()

fun PoseAnalysisResult.Success.toPoseArtifact(cacheKey: PoseArtifactCacheKey, createdAtEpochMs: Long): PoseArtifact =
    PoseArtifact(cacheKey, videoWidth, videoHeight, videoDurationMs, frames, createdAtEpochMs)

fun PoseArtifact.toPoseAnalysisSuccess(): PoseAnalysisResult.Success =
    PoseAnalysisResult.Success(frames = frames, videoDurationMs = videoDurationMs, videoWidth = videoWidth, videoHeight = videoHeight)

/**
 * Local-only persistence for [PoseArtifact]s, keyed by the source video's [videoFingerprint] -
 * lets Phase 4C's manual-validation iteration loop skip re-running expensive MediaPipe pose
 * extraction for a clip whose video content and pose-extraction inputs haven't changed since the
 * last run. [LocalJsonPoseArtifactStore] is the only implementation; there is no backend-synced
 * cache and never will be one (this package's local-only trust boundary - see
 * `ManualValidationTrustBoundaryTest`).
 */
interface PoseArtifactStore {
    fun save(artifact: PoseArtifact)
    fun load(videoFingerprint: String, expectedKey: PoseArtifactCacheKey): PoseArtifact?
    fun delete(videoFingerprint: String)

    /** True when SOME artifact - regardless of whether its own cache key would still match a
     * caller's current inputs - is currently stored for [videoFingerprint]. Phase 4C's
     * [ValidationPipelineRunner] uses this (checked BEFORE overwriting via [save]) to tell an
     * ordinary first run (nothing cached yet for this video) apart from a real cache invalidation
     * (a stale entry exists under a no-longer-matching key) - see [StageProvenance]'s own doc
     * comment for why that distinction matters. Deliberately trivial - a plain existence check,
     * never a partial/expensive read. */
    fun hasAnyEntryFor(videoFingerprint: String): Boolean
}

class LocalJsonPoseArtifactStore(private val directory: File) : PoseArtifactStore {

    override fun save(artifact: PoseArtifact) {
        if (!directory.exists()) directory.mkdirs()
        val target = fileFor(artifact.cacheKey.videoFingerprint)
        val tmp = tmpFileFor(artifact.cacheKey.videoFingerprint)
        tmp.writeText(artifact.toJson())
        // Atomic swap: the visible ".json" file is never observable in a partially-written state,
        // even if the process is killed mid-write - a kill only ever leaves a ".json.tmp" behind,
        // which load() never looks at.
        if (!tmp.renameTo(target)) {
            // renameTo can fail across filesystems on some platforms - fall back to an explicit
            // copy-then-delete rather than leaving the cache entry unwritten.
            target.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    override fun load(videoFingerprint: String, expectedKey: PoseArtifactCacheKey): PoseArtifact? {
        val file = fileFor(videoFingerprint)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val cacheKeyJson = json.getJSONObject("cacheKey")

            // Schema-version check happens before any other field is interpreted - a structurally
            // incompatible old schema is never partially parsed.
            val storedSchemaVersion = cacheKeyJson.getInt("artifactSchemaVersion")
            if (storedSchemaVersion != CURRENT_POSE_ARTIFACT_SCHEMA_VERSION) return@runCatching null

            val parsedKey = cacheKeyJson.toPoseArtifactCacheKey()
            if (parsedKey != expectedKey) return@runCatching null

            json.toPoseArtifact(parsedKey)
        }.getOrNull()
    }

    override fun delete(videoFingerprint: String) {
        fileFor(videoFingerprint).delete()
        tmpFileFor(videoFingerprint).delete()
    }

    override fun hasAnyEntryFor(videoFingerprint: String): Boolean = fileFor(videoFingerprint).exists()

    /** ':' is the only character a [videoFingerprint] can contain that isn't already
     * filesystem-safe on every platform this app targets. */
    private fun sanitize(videoFingerprint: String): String = videoFingerprint.replace(':', '_')

    private fun fileFor(videoFingerprint: String): File = File(directory, "${sanitize(videoFingerprint)}.json")

    private fun tmpFileFor(videoFingerprint: String): File = File(directory, "${sanitize(videoFingerprint)}.json.tmp")
}

// --- JSON serialization (org.json, matching HoldContactTimelineJson.kt/ClipValidationExport.kt) ---

private fun PoseArtifact.toJson(): String {
    val json = JSONObject()
    json.put("cacheKey", cacheKey.toJsonObject())
    json.put("videoWidth", videoWidth)
    json.put("videoHeight", videoHeight)
    json.put("videoDurationMs", videoDurationMs)
    json.put("createdAtEpochMs", createdAtEpochMs)
    json.put("frames", JSONArray(frames.map { it.toJsonObject() }))
    return json.toString()
}

private fun PoseArtifactCacheKey.toJsonObject(): JSONObject = JSONObject().apply {
    put("videoFingerprint", videoFingerprint)
    put("poseExtractorVersion", poseExtractorVersion)
    put("targetFps", targetFps)
    put("poseAnalysisConfigFingerprint", poseAnalysisConfigFingerprint)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toPoseArtifactCacheKey(): PoseArtifactCacheKey = PoseArtifactCacheKey(
    videoFingerprint = getString("videoFingerprint"),
    poseExtractorVersion = getString("poseExtractorVersion"),
    targetFps = getInt("targetFps"),
    poseAnalysisConfigFingerprint = getString("poseAnalysisConfigFingerprint"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

private fun JSONObject.toPoseArtifact(cacheKey: PoseArtifactCacheKey): PoseArtifact {
    val framesArray = getJSONArray("frames")
    val frames = (0 until framesArray.length()).map { framesArray.getJSONObject(it).toPoseFrame() }
    return PoseArtifact(
        cacheKey = cacheKey,
        videoWidth = getInt("videoWidth"),
        videoHeight = getInt("videoHeight"),
        videoDurationMs = getLong("videoDurationMs"),
        frames = frames,
        createdAtEpochMs = getLong("createdAtEpochMs"),
    )
}

private fun PoseFrame.toJsonObject(): JSONObject = JSONObject().apply {
    put("timestampMs", timestampMs)
    put("averageConfidence", averageConfidence.toDouble())
    put("isReliable", isReliable)
    put("bodyBoundingBox", bodyBoundingBox?.toJsonObject() ?: JSONObject.NULL)
    put("landmarks", JSONArray(landmarks.map { it.toJsonObject() }))
}

private fun JSONObject.toPoseFrame(): PoseFrame {
    val landmarksArray = getJSONArray("landmarks")
    val landmarks = (0 until landmarksArray.length()).map { landmarksArray.getJSONObject(it).toPoseLandmark() }
    val boundingBoxValue = opt("bodyBoundingBox")?.takeIf { it != JSONObject.NULL } as? JSONObject
    return PoseFrame(
        timestampMs = getLong("timestampMs"),
        landmarks = landmarks,
        averageConfidence = getDouble("averageConfidence").toFloat(),
        isReliable = getBoolean("isReliable"),
        bodyBoundingBox = boundingBoxValue?.toBodyBoundingBox(),
    )
}

private fun BodyBoundingBox.toJsonObject(): JSONObject = JSONObject().apply {
    put("left", left.toDouble())
    put("top", top.toDouble())
    put("right", right.toDouble())
    put("bottom", bottom.toDouble())
}

private fun JSONObject.toBodyBoundingBox(): BodyBoundingBox = BodyBoundingBox(
    left = getDouble("left").toFloat(),
    top = getDouble("top").toFloat(),
    right = getDouble("right").toFloat(),
    bottom = getDouble("bottom").toFloat(),
)

private fun PoseLandmark.toJsonObject(): JSONObject = JSONObject().apply {
    put("type", type.name)
    put("normalizedX", normalizedX.toDouble())
    put("normalizedY", normalizedY.toDouble())
    put("normalizedZ", normalizedZ.toDouble())
    put("visibility", visibility.toDouble())
    put("presence", presence.toDouble())
}

private fun JSONObject.toPoseLandmark(): PoseLandmark = PoseLandmark(
    type = PoseLandmarkType.valueOf(getString("type")),
    normalizedX = getDouble("normalizedX").toFloat(),
    normalizedY = getDouble("normalizedY").toFloat(),
    normalizedZ = getDouble("normalizedZ").toFloat(),
    visibility = getDouble("visibility").toFloat(),
    presence = getDouble("presence").toFloat(),
)
