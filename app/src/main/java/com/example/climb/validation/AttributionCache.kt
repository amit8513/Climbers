package com.example.climb.validation

import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Bump this whenever this file's own JSON shape changes in a way an already-persisted cache entry
 * on disk could no longer be parsed the same way - a stored entry whose own `artifactSchemaVersion`
 * doesn't match this is treated as a clean cache miss, never a crash (see
 * [LocalJsonAttributionCacheStore.load]). Same convention as
 * [CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION]/[CURRENT_POSE_ARTIFACT_SCHEMA_VERSION]/
 * `ClipValidationExportBuilder.CURRENT_EXPORT_FORMAT_VERSION`. */
const val CURRENT_ATTRIBUTION_CACHE_SCHEMA_VERSION: Int = 1

/**
 * Everything that must match, exactly, for a previously-computed [AttributionResult] to be
 * considered valid for reuse instead of re-running [ManualValidationAttributionRunner.run] (and,
 * through it, the real `RouteAttributionEngine`) against the same session's contact analysis.
 * Phase 4C's local caching layer around that existing call - never changes what the engine decides,
 * only whether it needs to run again for the same inputs. Nests [contactAnalysisCacheKey] so a
 * change to the underlying contact analysis (or its own inputs, including pose extraction further
 * upstream) automatically invalidates any downstream attribution cache entry too, without
 * duplicating those fields here.
 */
data class AttributionCacheKey(
    val contactAnalysisCacheKey: ContactAnalysisCacheKey,
    val routeDefinitionsFingerprint: String,
    val attemptStartTimestampMs: Long,
    val routeAttributionScoringConfigFingerprint: String,
    val artifactSchemaVersion: Int,
)

/** A cached [AttributionResult], plus the [cacheKey] it was computed under and when it was
 * produced. */
data class AttributionCacheEntry(
    val cacheKey: AttributionCacheKey,
    val result: AttributionResult,
    val createdAtEpochMs: Long,
)

/**
 * A deterministic fingerprint of a session's manually-defined route candidates - sorted by
 * [ValidationRouteDefinition.routeId] ascending first (a `List` has a defined order, but the
 * caller's own list order isn't guaranteed to be routeId-sorted), so the same logical set of route
 * definitions always produces the same fingerprint regardless of incidental input list ordering.
 * Encoded via [ValidationRouteDefinition.toJsonObject] (which already sorts every set-valued field
 * - start/body/finish hold ids - ascending) rather than hand-joining fields with ad hoc `:`/`|`
 * delimiters: [ValidationRouteDefinition.name] is arbitrary developer-entered free text that could
 * itself contain those delimiter characters, and proper JSON string escaping (unlike unescaped
 * manual concatenation) guarantees two logically-different route definition lists can never
 * encode to the same string. Hashes a short in-memory string (not a file), same approach as
 * [ContactAnalysisCache.kt]'s `holdGeometryFingerprint`.
 */
fun routeDefinitionsFingerprint(routeDefinitions: List<ValidationRouteDefinition>): String {
    val encoded = JSONArray(routeDefinitions.sortedBy { it.routeId }.map { it.toJsonObject() }).toString()
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** [RouteAttributionScoringConfig] is already a data class whose auto-generated `toString()` covers
 * every field - reused directly rather than hand-writing a second field-by-field fingerprint that
 * could drift out of sync with the real class. Same approach as
 * [ContactAnalysisCache.kt]'s `holdContactConfigFingerprint`. */
fun routeAttributionScoringConfigFingerprint(config: RouteAttributionScoringConfig): String = config.toString()

/**
 * Local-only persistence for an [AttributionResult], keyed by the clip's own stable
 * [ManualValidationSession.validationSessionId] - same per-session keying convention as
 * [ContactAnalysisStore]/[PoseArtifactStore]. [LocalJsonAttributionCacheStore] is the only
 * implementation; there is no backend-synced cache and never will be one (this package's
 * local-only trust boundary - see `ManualValidationTrustBoundaryTest`).
 */
interface AttributionCacheStore {
    fun save(validationSessionId: String, entry: AttributionCacheEntry)
    fun load(validationSessionId: String, expectedKey: AttributionCacheKey): AttributionResult?
    fun delete(validationSessionId: String)

    /** True when SOME entry - regardless of whether its own cache key would still match a
     * caller's current inputs - is currently stored for [validationSessionId]. Same
     * first-run-vs-invalidation distinguishing purpose as [PoseArtifactStore.hasAnyEntryFor] - see
     * that method's own doc comment. */
    fun hasAnyEntryFor(validationSessionId: String): Boolean
}

class LocalJsonAttributionCacheStore(private val directory: File) : AttributionCacheStore {

    override fun save(validationSessionId: String, entry: AttributionCacheEntry) {
        if (!directory.exists()) directory.mkdirs()
        val target = fileFor(validationSessionId)
        val tmp = tmpFileFor(validationSessionId)
        tmp.writeText(entry.toJson())
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

    override fun load(validationSessionId: String, expectedKey: AttributionCacheKey): AttributionResult? {
        val file = fileFor(validationSessionId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val cacheKeyJson = json.getJSONObject("cacheKey")

            // Schema-version check happens before any other field is interpreted - a structurally
            // incompatible old schema is never partially parsed.
            val storedSchemaVersion = cacheKeyJson.getInt("artifactSchemaVersion")
            if (storedSchemaVersion != CURRENT_ATTRIBUTION_CACHE_SCHEMA_VERSION) return@runCatching null

            val parsedKey = cacheKeyJson.toAttributionCacheKey()
            if (parsedKey != expectedKey) return@runCatching null

            json.getJSONObject("result").toAttributionResult()
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

// --- JSON serialization (org.json, matching ContactAnalysisCache.kt/HoldContactTimelineJson.kt) ---
//
// This is a NEW, separate JSON shape from ClipValidationExport.kt's SubScoreExport - do not reuse
// or confuse the two. This one mirrors the RAW shared-domain AttributionResult/SubScoreResult
// fields exactly, with no route-name lookup or derived UI fields mixed in.

private fun AttributionCacheEntry.toJson(): String {
    val json = JSONObject()
    json.put("cacheKey", cacheKey.toJsonObject())
    json.put("createdAtEpochMs", createdAtEpochMs)
    json.put("result", result.toJsonObject())
    return json.toString()
}

private fun AttributionCacheKey.toJsonObject(): JSONObject = JSONObject().apply {
    put("contactAnalysisCacheKey", contactAnalysisCacheKey.toNestedJsonObject())
    put("routeDefinitionsFingerprint", routeDefinitionsFingerprint)
    put("attemptStartTimestampMs", attemptStartTimestampMs)
    put("routeAttributionScoringConfigFingerprint", routeAttributionScoringConfigFingerprint)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toAttributionCacheKey(): AttributionCacheKey = AttributionCacheKey(
    contactAnalysisCacheKey = getJSONObject("contactAnalysisCacheKey").toNestedContactAnalysisCacheKey(),
    routeDefinitionsFingerprint = getString("routeDefinitionsFingerprint"),
    attemptStartTimestampMs = getLong("attemptStartTimestampMs"),
    routeAttributionScoringConfigFingerprint = getString("routeAttributionScoringConfigFingerprint"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

/** [ContactAnalysisCacheKey]'s own JSON mapping (`ContactAnalysisCache.kt`'s `toJsonObject()`/
 * `toContactAnalysisCacheKey()`) is file-private there, so this nests the same fields independently
 * rather than reaching into that file - a small, deliberate duplication instead of widening that
 * file's visibility for a field mapping this unlikely to drift. Mirrors [ContactAnalysisCache.kt]'s
 * own nesting of [PoseArtifactCacheKey] one level deeper still. */
private fun ContactAnalysisCacheKey.toNestedJsonObject(): JSONObject = JSONObject().apply {
    put("poseArtifactCacheKey", poseArtifactCacheKey.toDoublyNestedJsonObject())
    put("holdGeometryFingerprint", holdGeometryFingerprint)
    put("holdContactConfigFingerprint", holdContactConfigFingerprint)
    put("referenceImageDimensionsFingerprint", referenceImageDimensionsFingerprint)
    put("cameraGeometryProfileVersion", cameraGeometryProfileVersion)
    put("expectedGeometryProfileVersion", expectedGeometryProfileVersion)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toNestedContactAnalysisCacheKey(): ContactAnalysisCacheKey = ContactAnalysisCacheKey(
    poseArtifactCacheKey = getJSONObject("poseArtifactCacheKey").toDoublyNestedPoseArtifactCacheKey(),
    holdGeometryFingerprint = getString("holdGeometryFingerprint"),
    holdContactConfigFingerprint = getString("holdContactConfigFingerprint"),
    referenceImageDimensionsFingerprint = getString("referenceImageDimensionsFingerprint"),
    cameraGeometryProfileVersion = getInt("cameraGeometryProfileVersion"),
    expectedGeometryProfileVersion = getInt("expectedGeometryProfileVersion"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

private fun PoseArtifactCacheKey.toDoublyNestedJsonObject(): JSONObject = JSONObject().apply {
    put("videoFingerprint", videoFingerprint)
    put("poseExtractorVersion", poseExtractorVersion)
    put("targetFps", targetFps)
    put("poseAnalysisConfigFingerprint", poseAnalysisConfigFingerprint)
    put("artifactSchemaVersion", artifactSchemaVersion)
}

private fun JSONObject.toDoublyNestedPoseArtifactCacheKey(): PoseArtifactCacheKey = PoseArtifactCacheKey(
    videoFingerprint = getString("videoFingerprint"),
    poseExtractorVersion = getString("poseExtractorVersion"),
    targetFps = getInt("targetFps"),
    poseAnalysisConfigFingerprint = getString("poseAnalysisConfigFingerprint"),
    artifactSchemaVersion = getInt("artifactSchemaVersion"),
)

private fun AttributionResult.toJsonObject(): JSONObject = JSONObject().apply {
    put("winningRouteVersionId", winningRouteVersionId ?: JSONObject.NULL)
    put("status", status.name)
    put("reasonCode", reasonCode?.name ?: JSONObject.NULL)
    put("margin", margin?.toDouble() ?: JSONObject.NULL)
    put("subScores", JSONArray(subScores.map { it.toJsonObject() }))
}

private fun JSONObject.toAttributionResult(): AttributionResult {
    val subScoresArray = getJSONArray("subScores")
    val subScores = (0 until subScoresArray.length()).map { subScoresArray.getJSONObject(it).toSubScoreResult() }
    return AttributionResult(
        winningRouteVersionId = (opt("winningRouteVersionId")?.takeIf { it != JSONObject.NULL } as? Number)?.toLong(),
        status = AttributionStatus.valueOf(getString("status")),
        reasonCode = (opt("reasonCode")?.takeIf { it != JSONObject.NULL } as? String)?.let { AttributionReasonCode.valueOf(it) },
        margin = (opt("margin")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
        subScores = subScores,
    )
}

private fun SubScoreResult.toJsonObject(): JSONObject = JSONObject().apply {
    put("routeVersionId", routeVersionId)
    put("startEvidenceStatus", startEvidenceStatus.name)
    put("contactCoverageScore", contactCoverageScore.toDouble())
    put("corridorScore", corridorScore?.toDouble() ?: JSONObject.NULL)
    put("finishScore", finishScore?.toDouble() ?: JSONObject.NULL)
    put("foreignContactEventCount", foreignContactEventCount)
    put("foreignContactPenalty", foreignContactPenalty.toDouble())
    put("combinedScore", combinedScore.toDouble())
}

private fun JSONObject.toSubScoreResult(): SubScoreResult = SubScoreResult(
    routeVersionId = getLong("routeVersionId"),
    startEvidenceStatus = StartEvidenceStatus.valueOf(getString("startEvidenceStatus")),
    contactCoverageScore = getDouble("contactCoverageScore").toFloat(),
    corridorScore = (opt("corridorScore")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
    finishScore = (opt("finishScore")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
    foreignContactEventCount = getInt("foreignContactEventCount"),
    foreignContactPenalty = getDouble("foreignContactPenalty").toFloat(),
    combinedScore = getDouble("combinedScore").toFloat(),
)
