package com.example.climb.validation

import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.Point2D
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Bump this whenever [ContactAnalysisArtifact]'s own JSON shape changes in a way an
 * already-persisted artifact on disk could no longer be parsed the same way - a stored artifact
 * whose own `artifactSchemaVersion` doesn't match this is treated as a clean cache miss, never a
 * crash (see [LocalJsonContactAnalysisStore.load]). Same convention as
 * [CURRENT_POSE_ARTIFACT_SCHEMA_VERSION]/`ClipValidationExportBuilder.CURRENT_EXPORT_FORMAT_VERSION`/
 * `visionProfileFormatVersion`. */
const val CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION: Int = 1

/**
 * Everything that must match, exactly, for a previously-computed [ContactAnalysisArtifact] to be
 * considered valid for reuse instead of re-running [HoldContactDetector] against a session's pose
 * frames. Phase 4C's local caching layer around the existing
 * [ManualValidationPipeline.runContactAnalysis] call - never changes what the detector decides,
 * only whether it needs to run again for the same inputs. Nests [poseArtifactCacheKey] so a change
 * to the underlying pose extraction (or its own inputs) automatically invalidates any downstream
 * contact-analysis cache entry too, without duplicating those fields here.
 */
data class ContactAnalysisCacheKey(
    val poseArtifactCacheKey: PoseArtifactCacheKey,
    val holdGeometryFingerprint: String,
    val holdContactConfigFingerprint: String,
    val referenceImageDimensionsFingerprint: String,
    val cameraGeometryProfileVersion: Int,
    val expectedGeometryProfileVersion: Int,
    val artifactSchemaVersion: Int,
)

/** A cached [ManualValidationOutcome.Processed], plus the [cacheKey] it was computed under and
 * when it was produced - see [toContactAnalysisArtifact]/[toProcessedOutcome] for the two
 * conversions to/from the real pipeline outcome shape. */
data class ContactAnalysisArtifact(
    val cacheKey: ContactAnalysisCacheKey,
    val frameDiagnostics: List<ManualValidationFrameDiagnostics>,
    val videoDurationMs: Long,
    val timeline: HoldContactTimeline,
    val createdAtEpochMs: Long,
)

/**
 * A deterministic fingerprint of a session's manually-annotated hold geometry - sorted by
 * [ValidationHoldAnnotation.holdId] ascending first (a `List` has a defined order, but the
 * caller's own list order isn't guaranteed to be holdId-sorted), so the same logical set of holds
 * always produces the same fingerprint regardless of incidental input ordering. Hashes a short
 * in-memory string (not a file), so unlike [videoFingerprint] there's no need for a byte-prefix
 * limit - the whole encoded string is hashed.
 */
fun holdGeometryFingerprint(holds: List<ValidationHoldAnnotation>): String {
    val encoded = holds.sortedBy { it.holdId }.joinToString("|") { hold ->
        val points = hold.contourNormalized.joinToString(";") { "${it.x},${it.y}" }
        "${hold.holdId}:$points"
    }
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** [HoldContactConfig] is already a data class whose auto-generated `toString()` covers every
 * field - reused directly rather than hand-writing a second field-by-field fingerprint that could
 * drift out of sync with the real class. Same approach as [poseAnalysisConfigFingerprint]. */
fun holdContactConfigFingerprint(config: HoldContactConfig): String = config.toString()

fun referenceImageDimensionsFingerprint(dimensions: ImageDimensions): String = "${dimensions.widthPx}x${dimensions.heightPx}"

fun ManualValidationOutcome.Processed.toContactAnalysisArtifact(cacheKey: ContactAnalysisCacheKey, createdAtEpochMs: Long): ContactAnalysisArtifact =
    ContactAnalysisArtifact(cacheKey, frameDiagnostics, videoDurationMs, timeline, createdAtEpochMs)

fun ContactAnalysisArtifact.toProcessedOutcome(): ManualValidationOutcome.Processed =
    ManualValidationOutcome.Processed(frameDiagnostics = frameDiagnostics, videoDurationMs = videoDurationMs, timeline = timeline)

/**
 * Local-only persistence for a [ContactAnalysisArtifact], keyed by the clip's own stable
 * [ManualValidationSession.validationSessionId] - unlike [PoseArtifactStore] (keyed by video
 * content fingerprint, since a pose artifact is reusable across sessions that happen to share a
 * video), a session has exactly one "current" contact-analysis cache entry, and
 * `validationSessionId` is already the stable identity every other per-session store in this
 * package uses. [LocalJsonContactAnalysisStore] is the only implementation; there is no
 * backend-synced cache and never will be one (this package's local-only trust boundary - see
 * `ManualValidationTrustBoundaryTest`).
 */
interface ContactAnalysisStore {
    fun save(validationSessionId: String, artifact: ContactAnalysisArtifact)
    fun load(validationSessionId: String, expectedKey: ContactAnalysisCacheKey): ContactAnalysisArtifact?
    fun delete(validationSessionId: String)

    /** True when SOME artifact - regardless of whether its own cache key would still match a
     * caller's current inputs - is currently stored for [validationSessionId]. Same
     * first-run-vs-invalidation distinguishing purpose as [PoseArtifactStore.hasAnyEntryFor] - see
     * that method's own doc comment. */
    fun hasAnyEntryFor(validationSessionId: String): Boolean
}

class LocalJsonContactAnalysisStore(private val directory: File) : ContactAnalysisStore {

    override fun save(validationSessionId: String, artifact: ContactAnalysisArtifact) {
        if (!directory.exists()) directory.mkdirs()
        val target = fileFor(validationSessionId)
        val tmp = tmpFileFor(validationSessionId)
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

    override fun load(validationSessionId: String, expectedKey: ContactAnalysisCacheKey): ContactAnalysisArtifact? {
        val file = fileFor(validationSessionId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val cacheKeyJson = json.getJSONObject("cacheKey")

            // Schema-version check happens before any other field is interpreted - a structurally
            // incompatible old schema is never partially parsed.
            val storedSchemaVersion = cacheKeyJson.getInt("artifactSchemaVersion")
            if (storedSchemaVersion != CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION) return@runCatching null

            val parsedKey = cacheKeyJson.toContactAnalysisCacheKey()
            if (parsedKey != expectedKey) return@runCatching null

            json.toContactAnalysisArtifact(parsedKey)
        }.getOrNull()
    }

    override fun delete(validationSessionId: String) {
        fileFor(validationSessionId).delete()
        tmpFileFor(validationSessionId).delete()
    }

    override fun hasAnyEntryFor(validationSessionId: String): Boolean = fileFor(validationSessionId).exists()

    private fun fileFor(validationSessionId: String): File = File(directory, "$validationSessionId.json")

    private fun tmpFileFor(validationSessionId: String): File = File(directory, "$validationSessionId.json.tmp")
}

// --- JSON serialization (org.json, matching PoseArtifactCache.kt/HoldContactTimelineJson.kt) ---

private fun ContactAnalysisArtifact.toJson(): String {
    val json = JSONObject()
    json.put("cacheKey", cacheKey.toJsonObject())
    json.put("videoDurationMs", videoDurationMs)
    json.put("createdAtEpochMs", createdAtEpochMs)
    // Stored as a plain string field (built via the existing HoldContactTimeline.toJson()
    // serializer, reloaded via String.toHoldContactTimeline()) rather than re-parsed into a
    // nested JSONArray - avoids writing a second timeline serializer while keeping the file valid,
    // non-double-escaped JSON.
    json.put("timeline", timeline.toJson())
    json.put("frameDiagnostics", JSONArray(frameDiagnostics.map { it.toJsonObject() }))
    return json.toString()
}

private fun ContactAnalysisCacheKey.toJsonObject(): JSONObject = JSONObject().apply {
    put("poseArtifactCacheKey", poseArtifactCacheKey.toNestedJsonObject())
    put("holdGeometryFingerprint", holdGeometryFingerprint)
    put("holdContactConfigFingerprint", holdContactConfigFingerprint)
    put("referenceImageDimensionsFingerprint", referenceImageDimensionsFingerprint)
    put("cameraGeometryProfileVersion", cameraGeometryProfileVersion)
    put("expectedGeometryProfileVersion", expectedGeometryProfileVersion)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toContactAnalysisCacheKey(): ContactAnalysisCacheKey = ContactAnalysisCacheKey(
    poseArtifactCacheKey = getJSONObject("poseArtifactCacheKey").toPoseArtifactCacheKeyFromNested(),
    holdGeometryFingerprint = getString("holdGeometryFingerprint"),
    holdContactConfigFingerprint = getString("holdContactConfigFingerprint"),
    referenceImageDimensionsFingerprint = getString("referenceImageDimensionsFingerprint"),
    cameraGeometryProfileVersion = getInt("cameraGeometryProfileVersion"),
    expectedGeometryProfileVersion = getInt("expectedGeometryProfileVersion"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

/** [PoseArtifactCacheKey]'s own JSON mapping (`PoseArtifactCache.kt`'s `toJsonObject()`/
 * `toPoseArtifactCacheKey()`) is file-private there, so this nests the same four-plus-one fields
 * independently rather than reaching into that file - a small, deliberate duplication instead of
 * widening that file's visibility for a field mapping this unlikely to drift. */
private fun PoseArtifactCacheKey.toNestedJsonObject(): JSONObject = JSONObject().apply {
    put("videoFingerprint", videoFingerprint)
    put("poseExtractorVersion", poseExtractorVersion)
    put("targetFps", targetFps)
    put("poseAnalysisConfigFingerprint", poseAnalysisConfigFingerprint)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toPoseArtifactCacheKeyFromNested(): PoseArtifactCacheKey = PoseArtifactCacheKey(
    videoFingerprint = getString("videoFingerprint"),
    poseExtractorVersion = getString("poseExtractorVersion"),
    targetFps = getInt("targetFps"),
    poseAnalysisConfigFingerprint = getString("poseAnalysisConfigFingerprint"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

private fun JSONObject.toContactAnalysisArtifact(cacheKey: ContactAnalysisCacheKey): ContactAnalysisArtifact {
    val diagnosticsArray = getJSONArray("frameDiagnostics")
    val diagnostics = (0 until diagnosticsArray.length()).map { diagnosticsArray.getJSONObject(it).toFrameDiagnostics() }
    return ContactAnalysisArtifact(
        cacheKey = cacheKey,
        frameDiagnostics = diagnostics,
        videoDurationMs = getLong("videoDurationMs"),
        timeline = getString("timeline").toHoldContactTimeline(),
        createdAtEpochMs = getLong("createdAtEpochMs"),
    )
}

private fun ManualValidationFrameDiagnostics.toJsonObject(): JSONObject {
    val obj = JSONObject()
    obj.put("timestampMs", timestampMs)
    obj.put("isReliable", isReliable)
    obj.putLimbGapStates("gapStatesByLimb", gapStatesByLimb)
    obj.putLimbNullableInts("establishedHoldByLimb", establishedHoldByLimb)
    obj.putLimbNullableInts("candidateHoldByLimb", candidateHoldByLimb)
    obj.putLimbFloats("establishedConfidenceByLimb", establishedConfidenceByLimb)
    obj.putLimbPoints("proxyPositionByLimb", proxyPositionByLimb)
    return obj
}

private fun JSONObject.toFrameDiagnostics(): ManualValidationFrameDiagnostics = ManualValidationFrameDiagnostics(
    timestampMs = getLong("timestampMs"),
    isReliable = getBoolean("isReliable"),
    gapStatesByLimb = getLimbGapStates("gapStatesByLimb"),
    establishedHoldByLimb = getLimbNullableInts("establishedHoldByLimb"),
    candidateHoldByLimb = getLimbNullableInts("candidateHoldByLimb"),
    proxyPositionByLimb = getLimbPoints("proxyPositionByLimb"),
    establishedConfidenceByLimb = getLimbFloats("establishedConfidenceByLimb"),
)

// One JSON object per limb-keyed map, with the four Limb.entries names (in their fixed
// declaration order, never a HashMap iteration order) as its own keys.

private fun JSONObject.putLimbGapStates(key: String, map: Map<Limb, GapState>) {
    val obj = JSONObject()
    for (limb in Limb.entries) {
        obj.put(limb.name, (map[limb] ?: GapState.NONE).name)
    }
    put(key, obj)
}

private fun JSONObject.getLimbGapStates(key: String): Map<Limb, GapState> {
    val obj = getJSONObject(key)
    return Limb.entries.associateWith { limb -> GapState.valueOf(obj.getString(limb.name)) }
}

private fun JSONObject.putLimbNullableInts(key: String, map: Map<Limb, Int?>) {
    val obj = JSONObject()
    for (limb in Limb.entries) {
        obj.put(limb.name, map[limb] ?: JSONObject.NULL)
    }
    put(key, obj)
}

private fun JSONObject.getLimbNullableInts(key: String): Map<Limb, Int?> {
    val obj = getJSONObject(key)
    return Limb.entries.associateWith { limb ->
        obj.opt(limb.name)?.takeIf { it != JSONObject.NULL } as? Int
    }
}

private fun JSONObject.putLimbFloats(key: String, map: Map<Limb, Float>) {
    val obj = JSONObject()
    for (limb in Limb.entries) {
        obj.put(limb.name, (map[limb] ?: 0f).toDouble())
    }
    put(key, obj)
}

private fun JSONObject.getLimbFloats(key: String): Map<Limb, Float> {
    val obj = getJSONObject(key)
    return Limb.entries.associateWith { limb -> obj.getDouble(limb.name).toFloat() }
}

private fun JSONObject.putLimbPoints(key: String, map: Map<Limb, Point2D?>) {
    val obj = JSONObject()
    for (limb in Limb.entries) {
        val point = map[limb]
        obj.put(
            limb.name,
            point?.let { JSONObject().apply { put("x", it.x.toDouble()); put("y", it.y.toDouble()) } } ?: JSONObject.NULL,
        )
    }
    put(key, obj)
}

private fun JSONObject.getLimbPoints(key: String): Map<Limb, Point2D?> {
    val obj = getJSONObject(key)
    return Limb.entries.associateWith { limb ->
        val value = obj.opt(limb.name)?.takeIf { it != JSONObject.NULL } as? JSONObject
        value?.let { Point2D(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) }
    }
}
