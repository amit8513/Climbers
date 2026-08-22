package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Phase 4B's deterministic, local-only export of one processed manual-validation clip — the final
 * step of `reference image -> manually defined routes/holds -> imported video -> existing
 * MediaPipe pose extraction -> HoldContactDetector -> HoldContactTimeline -> RouteAttributionEngine`.
 * Assembled purely from already-computed values ([ManualValidationReport],
 * `com.example.climb.attribution.AttributionResult`, [AttributionEvaluation]) — see
 * [ClipValidationExportBuilder], the one place this is built. Nothing here re-runs or re-derives any
 * resolver decision.
 *
 * Deliberately excludes raw video bytes, per-frame pose dumps, and
 * [ManualValidationFrameDiagnostics] (one entry per pose frame, potentially hundreds) — this is a
 * compact, human/machine-readable summary of one clip's outcome, not a full trace. The accumulated
 * [com.example.climb.analysis.contact.HoldContactTimeline]'s EVENT list ([timelineEvents]) is a
 * handful of ESTABLISHED/RELEASED transitions, never one entry per frame, and is exactly the kind of
 * compact summary this export exists to carry.
 */
data class ClipValidationExport(
    val exportFormatVersion: Int,
    val validationSessionId: String,
    val wallOrFixtureId: String,
    val wallSetupId: String?,
    val cameraGeometryProfileVersion: Int,
    val poseFrameCount: Int,
    val poseConfidenceCoveragePercent: Float,
    val establishedEventCount: Int,
    val contactsPerLimb: Map<Limb, Int>,
    val holdIdsTouched: Set<Int>,
    val timelineEvents: List<HoldContactEvent>,
    val routeCandidates: List<SubScoreExport>,
    val winningRouteId: Long?,
    val secondPlaceRouteId: Long?,
    val margin: Float?,
    val attributionStatus: AttributionStatus,
    val attributionReasonCode: AttributionReasonCode?,
    val expectedRouteId: Long?,
    val evaluationOutcome: AttributionEvaluationOutcome,
    val createdAtEpochMs: Long,
    /** `null` for exports built before this phase existed, or when provenance tracking isn't
     * available for some reason - never required. */
    val provenance: ValidationPipelineProvenance? = null,
    val lowPoseCoverage: Boolean = false,
    /** `true` when the pipeline run that produced this export never reached attribution at all (a
     * Rejected outcome) - when `true`, [routeCandidates] is expected to be empty and
     * [attributionStatus]/[winningRouteId]/etc reflect whatever placeholder/absent state the
     * builder already uses for a no-attribution-ran case. */
    val wasRejectedBeforeAttribution: Boolean = false,
)

/** One [SubScoreResult] projected into this export's shape, joined against the session's own
 * [ValidationRouteDefinition] for a human-readable [routeName] — see [ClipValidationExportBuilder]
 * for the join logic (falls back to `"route-$routeId"` rather than crashing when a definition is
 * somehow missing). */
data class SubScoreExport(
    val routeId: Long,
    val routeName: String,
    val startEvidenceStatus: StartEvidenceStatus,
    /** `true` exactly when [startEvidenceStatus] is anything other than
     * [StartEvidenceStatus.START_OBSERVED_MATCH] — i.e. this candidate could never have won
     * regardless of how its weighted score came out, per `RouteAttributionEngine`'s hard
     * eligibility gate. */
    val hardGated: Boolean,
    val contactCoverageScore: Float,
    /** `null` means UNAVAILABLE (this candidate has no corridor defined), never `0f`. */
    val corridorScore: Float?,
    /** `null` means UNAVAILABLE (this candidate has no finish evidence defined), never `0f`. */
    val finishScore: Float?,
    val foreignContactEventCount: Int,
    val foreignContactPenalty: Float,
    val normalizedWeights: NormalizedScoringWeights,
    val combinedScore: Float,
)

/**
 * Pure builder combining an already-computed [ManualValidationReport], `AttributionResult`, and
 * [AttributionEvaluation] into one [ClipValidationExport] — never re-computes any resolver decision,
 * only assembles already-computed values. See [normalizedWeightsUsed] and [secondPlaceCandidate]
 * (`AttributionDebugDetails.kt`, same package) for the two pieces of derived-but-not-new math this
 * reuses rather than re-deriving a third time.
 */
object ClipValidationExportBuilder {

    /** The first (and, as of this phase, only) export format version — bump this whenever this
     * export's shape changes in a way a later consumer needs to distinguish. */
    const val CURRENT_EXPORT_FORMAT_VERSION: Int = 1

    fun build(
        session: ManualValidationSession,
        report: ManualValidationReport,
        attributionResult: AttributionResult,
        evaluation: AttributionEvaluation,
        config: RouteAttributionScoringConfig = RouteAttributionScoringConfig(),
        exportedAtEpochMs: Long,
        provenance: ValidationPipelineProvenance? = null,
        lowPoseCoverage: Boolean = false,
        wasRejectedBeforeAttribution: Boolean = false,
    ): ClipValidationExport {
        val routeDefinitionsById = session.routeDefinitions.associateBy { it.routeId }

        // The engine already returns subScores sorted ascending by routeVersionId, but this call
        // sorts explicitly too - defense-in-depth determinism, per this phase's own instructions,
        // rather than silently trusting an upstream invariant to hold forever.
        val routeCandidates = attributionResult.subScores
            .sortedBy { it.routeVersionId }
            .map { subScore -> subScore.toSubScoreExport(routeDefinitionsById, config) }

        return ClipValidationExport(
            exportFormatVersion = CURRENT_EXPORT_FORMAT_VERSION,
            validationSessionId = session.validationSessionId,
            wallOrFixtureId = session.wallOrFixtureId,
            wallSetupId = session.wallSetupId,
            cameraGeometryProfileVersion = session.cameraGeometryProfileVersion,
            poseFrameCount = report.poseFrameCount,
            poseConfidenceCoveragePercent = report.poseConfidenceCoveragePercent,
            establishedEventCount = report.establishedEventCount,
            contactsPerLimb = report.contactsPerLimb,
            holdIdsTouched = report.holdIdsTouched,
            timelineEvents = report.timeline.events,
            routeCandidates = routeCandidates,
            winningRouteId = attributionResult.winningRouteVersionId,
            secondPlaceRouteId = secondPlaceCandidate(attributionResult)?.routeVersionId,
            margin = attributionResult.margin,
            attributionStatus = attributionResult.status,
            attributionReasonCode = attributionResult.reasonCode,
            expectedRouteId = session.expectedRouteId,
            evaluationOutcome = evaluation.outcome,
            createdAtEpochMs = exportedAtEpochMs,
            provenance = provenance,
            lowPoseCoverage = lowPoseCoverage,
            wasRejectedBeforeAttribution = wasRejectedBeforeAttribution,
        )
    }

    private fun SubScoreResult.toSubScoreExport(
        routeDefinitionsById: Map<Long, ValidationRouteDefinition>,
        config: RouteAttributionScoringConfig,
    ): SubScoreExport = SubScoreExport(
        routeId = routeVersionId,
        routeName = routeDefinitionsById[routeVersionId]?.name ?: "route-$routeVersionId",
        startEvidenceStatus = startEvidenceStatus,
        hardGated = startEvidenceStatus != StartEvidenceStatus.START_OBSERVED_MATCH,
        contactCoverageScore = contactCoverageScore,
        corridorScore = corridorScore,
        finishScore = finishScore,
        foreignContactEventCount = foreignContactEventCount,
        foreignContactPenalty = foreignContactPenalty,
        normalizedWeights = normalizedWeightsUsed(this, config),
        combinedScore = combinedScore,
    )
}

// --- JSON serialization (org.json, matching HoldContactTimelineJson.kt/ManualValidationSessionStore.kt) ---

/** Deterministic: byte-identical output across repeated calls on an equal [ClipValidationExport] —
 * [contactsPerLimb] is iterated via [Limb.entries] (fixed enum order, never the map's own incidental
 * iteration order), [holdIdsTouched] is written sorted ascending, [routeCandidates] is written in
 * its already-sorted list order, and [timelineEvents] is written in its existing (already
 * chronological) list order. */
fun ClipValidationExport.toJson(): String {
    val json = JSONObject()
    json.put("exportFormatVersion", exportFormatVersion)
    json.put("validationSessionId", validationSessionId)
    json.put("wallOrFixtureId", wallOrFixtureId)
    json.put("wallSetupId", wallSetupId ?: JSONObject.NULL)
    json.put("cameraGeometryProfileVersion", cameraGeometryProfileVersion)
    json.put("poseFrameCount", poseFrameCount)
    json.put("poseConfidenceCoveragePercent", poseConfidenceCoveragePercent.toDouble())
    json.put("establishedEventCount", establishedEventCount)

    val contactsPerLimbJson = JSONObject()
    for (limb in Limb.entries) {
        contactsPerLimbJson.put(limb.name, contactsPerLimb[limb] ?: 0)
    }
    json.put("contactsPerLimb", contactsPerLimbJson)

    json.put("holdIdsTouched", JSONArray(holdIdsTouched.sorted()))
    json.put("timelineEvents", JSONArray(timelineEvents.map { it.toJsonObject() }))
    json.put("routeCandidates", JSONArray(routeCandidates.map { it.toJsonObject() }))

    json.put("winningRouteId", winningRouteId ?: JSONObject.NULL)
    json.put("secondPlaceRouteId", secondPlaceRouteId ?: JSONObject.NULL)
    json.put("margin", margin?.toDouble() ?: JSONObject.NULL)
    json.put("attributionStatus", attributionStatus.name)
    json.put("attributionReasonCode", attributionReasonCode?.name ?: JSONObject.NULL)
    json.put("expectedRouteId", expectedRouteId ?: JSONObject.NULL)
    json.put("evaluationOutcome", evaluationOutcome.name)
    json.put("createdAtEpochMs", createdAtEpochMs)

    json.put("provenance", provenance?.toJsonObject() ?: JSONObject.NULL)
    json.put("lowPoseCoverage", lowPoseCoverage)
    json.put("wasRejectedBeforeAttribution", wasRejectedBeforeAttribution)

    return json.toString()
}

private fun StageProvenance.toJsonObject(): JSONObject = JSONObject().apply {
    put("outcome", outcome.name)
    put("invalidationReason", invalidationReason ?: JSONObject.NULL)
}

private fun ValidationPipelineProvenance.toJsonObject(): JSONObject = JSONObject().apply {
    put("pose", pose.toJsonObject())
    put("contact", contact?.toJsonObject() ?: JSONObject.NULL)
    put("attribution", attribution?.toJsonObject() ?: JSONObject.NULL)
}

private fun HoldContactEvent.toJsonObject(): JSONObject = JSONObject().apply {
    put("limb", limb.name)
    put("holdId", holdId)
    put("type", type.name)
    put("timestampMs", timestampMs)
    put("confidence", confidence.toDouble())
    put("evidenceQuality", evidenceQuality.name)
    put("releaseReason", releaseReason?.name ?: JSONObject.NULL)
}

private fun SubScoreExport.toJsonObject(): JSONObject = JSONObject().apply {
    put("routeId", routeId)
    put("routeName", routeName)
    put("startEvidenceStatus", startEvidenceStatus.name)
    put("hardGated", hardGated)
    put("contactCoverageScore", contactCoverageScore.toDouble())
    put("corridorScore", corridorScore?.toDouble() ?: JSONObject.NULL)
    put("finishScore", finishScore?.toDouble() ?: JSONObject.NULL)
    put("foreignContactEventCount", foreignContactEventCount)
    put("foreignContactPenalty", foreignContactPenalty.toDouble())
    put("normalizedWeights", normalizedWeights.toJsonObject())
    put("combinedScore", combinedScore.toDouble())
}

private fun NormalizedScoringWeights.toJsonObject(): JSONObject = JSONObject().apply {
    put("startHoldWeight", startHoldWeight.toDouble())
    put("contactCoverageWeight", contactCoverageWeight.toDouble())
    put("corridorWeight", corridorWeight.toDouble())
    put("finishWeight", finishWeight.toDouble())
}

/** The mirror image of [toJson] — parses a string [toJson] produced back into a
 * [ClipValidationExport], field for field, the same store-local JSON round-trip pattern
 * `ManualValidationSessionStore.kt`'s `toManualValidationSession()` already uses. Every field
 * [toJson] always writes (this export has no "legacy JSON missing a key" concern the way a
 * hand-authored/older [ManualValidationSession] JSON file might), so nullable fields are read via
 * `opt(...)?.takeIf { it != JSONObject.NULL }` purely to distinguish an explicit JSON `null` from a
 * present value - never to tolerate a missing key. */
fun String.toClipValidationExport(): ClipValidationExport {
    val json = JSONObject(this)

    val contactsPerLimbJson = json.getJSONObject("contactsPerLimb")
    val contactsPerLimb = Limb.entries.associateWith { limb -> contactsPerLimbJson.getInt(limb.name) }

    val holdIdsTouched = json.getJSONArray("holdIdsTouched")
        .let { array -> (0 until array.length()).map { array.getInt(it) } }
        .toSet()

    val timelineEvents = json.getJSONArray("timelineEvents")
        .let { array -> (0 until array.length()).map { array.getJSONObject(it).toHoldContactEvent() } }

    val routeCandidates = json.getJSONArray("routeCandidates")
        .let { array -> (0 until array.length()).map { array.getJSONObject(it).toSubScoreExport() } }

    return ClipValidationExport(
        exportFormatVersion = json.getInt("exportFormatVersion"),
        validationSessionId = json.getString("validationSessionId"),
        wallOrFixtureId = json.getString("wallOrFixtureId"),
        wallSetupId = json.opt("wallSetupId")?.takeIf { it != JSONObject.NULL } as? String,
        cameraGeometryProfileVersion = json.getInt("cameraGeometryProfileVersion"),
        poseFrameCount = json.getInt("poseFrameCount"),
        poseConfidenceCoveragePercent = json.getDouble("poseConfidenceCoveragePercent").toFloat(),
        establishedEventCount = json.getInt("establishedEventCount"),
        contactsPerLimb = contactsPerLimb,
        holdIdsTouched = holdIdsTouched,
        timelineEvents = timelineEvents,
        routeCandidates = routeCandidates,
        winningRouteId = (json.opt("winningRouteId")?.takeIf { it != JSONObject.NULL } as? Number)?.toLong(),
        secondPlaceRouteId = (json.opt("secondPlaceRouteId")?.takeIf { it != JSONObject.NULL } as? Number)?.toLong(),
        margin = (json.opt("margin")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
        attributionStatus = AttributionStatus.valueOf(json.getString("attributionStatus")),
        attributionReasonCode = (json.opt("attributionReasonCode")?.takeIf { it != JSONObject.NULL } as? String)
            ?.let { AttributionReasonCode.valueOf(it) },
        expectedRouteId = (json.opt("expectedRouteId")?.takeIf { it != JSONObject.NULL } as? Number)?.toLong(),
        evaluationOutcome = AttributionEvaluationOutcome.valueOf(json.getString("evaluationOutcome")),
        createdAtEpochMs = json.getLong("createdAtEpochMs"),
        provenance = json.opt("provenance")?.takeIf { it != JSONObject.NULL }
            ?.let { (it as JSONObject).toValidationPipelineProvenance() },
        lowPoseCoverage = json.optBoolean("lowPoseCoverage", false),
        wasRejectedBeforeAttribution = json.optBoolean("wasRejectedBeforeAttribution", false),
    )
}

private fun JSONObject.toStageProvenance(): StageProvenance = StageProvenance(
    outcome = CacheOutcome.valueOf(getString("outcome")),
    invalidationReason = opt("invalidationReason")?.takeIf { it != JSONObject.NULL } as? String,
)

private fun JSONObject.toValidationPipelineProvenance(): ValidationPipelineProvenance = ValidationPipelineProvenance(
    pose = getJSONObject("pose").toStageProvenance(),
    contact = (opt("contact")?.takeIf { it != JSONObject.NULL } as? JSONObject)?.toStageProvenance(),
    attribution = (opt("attribution")?.takeIf { it != JSONObject.NULL } as? JSONObject)?.toStageProvenance(),
)

private fun JSONObject.toHoldContactEvent(): HoldContactEvent = HoldContactEvent(
    limb = Limb.valueOf(getString("limb")),
    holdId = getInt("holdId"),
    type = ContactEventType.valueOf(getString("type")),
    timestampMs = getLong("timestampMs"),
    confidence = getDouble("confidence").toFloat(),
    evidenceQuality = EvidenceQuality.valueOf(getString("evidenceQuality")),
    releaseReason = (opt("releaseReason")?.takeIf { it != JSONObject.NULL } as? String)?.let { ReleaseReason.valueOf(it) },
)

private fun JSONObject.toSubScoreExport(): SubScoreExport = SubScoreExport(
    routeId = getLong("routeId"),
    routeName = getString("routeName"),
    startEvidenceStatus = StartEvidenceStatus.valueOf(getString("startEvidenceStatus")),
    hardGated = getBoolean("hardGated"),
    contactCoverageScore = getDouble("contactCoverageScore").toFloat(),
    corridorScore = (opt("corridorScore")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
    finishScore = (opt("finishScore")?.takeIf { it != JSONObject.NULL } as? Number)?.toFloat(),
    foreignContactEventCount = getInt("foreignContactEventCount"),
    foreignContactPenalty = getDouble("foreignContactPenalty").toFloat(),
    normalizedWeights = getJSONObject("normalizedWeights").toNormalizedScoringWeights(),
    combinedScore = getDouble("combinedScore").toFloat(),
)

private fun JSONObject.toNormalizedScoringWeights(): NormalizedScoringWeights = NormalizedScoringWeights(
    startHoldWeight = getDouble("startHoldWeight").toFloat(),
    contactCoverageWeight = getDouble("contactCoverageWeight").toFloat(),
    corridorWeight = getDouble("corridorWeight").toFloat(),
    finishWeight = getDouble("finishWeight").toFloat(),
)

// --- Human-readable summary ---------------------------------------------------------------------

/** Deterministic for the same reason [toJson] is: the same field-ordering/iteration choices apply,
 * just rendered as plain text instead of JSON. */
fun ClipValidationExport.toHumanReadableSummary(): String {
    val lines = mutableListOf<String>()
    lines += "Clip Validation Export (format v$exportFormatVersion)"
    lines += "Session: $validationSessionId"
    lines += "Wall/fixture: $wallOrFixtureId" + (wallSetupId?.let { " (wall setup: $it)" } ?: "")
    lines += "Camera geometry profile version: $cameraGeometryProfileVersion"
    lines += ""
    lines += "Pose frames: $poseFrameCount (confidence coverage: ${percent(poseConfidenceCoveragePercent)}%)"
    lines += "Established contact events: $establishedEventCount"
    lines += "Contacts per limb:"
    for (limb in Limb.entries) {
        lines += "  ${limb.name}: ${contactsPerLimb[limb] ?: 0}"
    }
    lines += "Hold ids touched: ${holdIdsTouched.sorted().joinToString(", ")}"
    lines += "Timeline events: ${timelineEvents.size}"
    lines += ""
    lines += "Route candidates (${routeCandidates.size}):"
    for (candidate in routeCandidates) {
        lines += "  [${candidate.routeId}] ${candidate.routeName}: combinedScore=${score(candidate.combinedScore)}, " +
            "startEvidenceStatus=${candidate.startEvidenceStatus}, hardGated=${candidate.hardGated}, " +
            "contactCoverage=${score(candidate.contactCoverageScore)}, " +
            "corridor=${candidate.corridorScore?.let { score(it) } ?: "UNAVAILABLE"}, " +
            "finish=${candidate.finishScore?.let { score(it) } ?: "UNAVAILABLE"}, " +
            "foreignContactEvents=${candidate.foreignContactEventCount}, foreignContactPenalty=${score(candidate.foreignContactPenalty)}"
    }
    lines += ""
    lines += "Winning route id: ${winningRouteId ?: "none"}"
    lines += "Second place route id: ${secondPlaceRouteId ?: "none"}"
    lines += "Margin: ${margin?.let { score(it) } ?: "n/a"}"
    lines += "Attribution status: $attributionStatus" + (attributionReasonCode?.let { " ($it)" } ?: "")
    lines += "Expected route id (ground truth): ${expectedRouteId ?: "not labeled"}"
    lines += "Evaluation outcome: $evaluationOutcome"
    lines += "Exported at epoch ms: $createdAtEpochMs"

    if (lowPoseCoverage) {
        lines += ""
        lines += "LOW POSE COVERAGE"
    }

    if (provenance != null) {
        lines += ""
        lines += "Cache provenance:"
        lines += "  Pose: ${provenance.pose.toSummaryLine()}"
        lines += "  Contact: ${provenance.contact?.toSummaryLine() ?: "n/a (stage not reached)"}"
        lines += "  Attribution: ${provenance.attribution?.toSummaryLine() ?: "n/a (stage not reached)"}"
    }

    return lines.joinToString("\n")
}

private fun StageProvenance.toSummaryLine(): String {
    val status = when (outcome) {
        CacheOutcome.CACHE_HIT -> "CACHE HIT"
        CacheOutcome.RECOMPUTED -> "RECOMPUTED"
    }
    return status + (invalidationReason?.let { " (invalidated: $it)" } ?: "")
}

/** Fixed-format, locale-independent rendering — [Locale.ROOT] so the decimal separator never
 * depends on the runtime's default locale (which would otherwise break byte-identical output
 * across environments). */
private fun score(value: Float): String = String.format(Locale.ROOT, "%.3f", value)

private fun percent(value: Float): String = String.format(Locale.ROOT, "%.1f", value)
