package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down [ClipValidationExportBuilder.build]'s field-by-field assembly plus the determinism
 * [toJson]/[toHumanReadableSummary] must have — see this phase's own instructions on why
 * byte-identical output across repeated calls on the same built export is the single most
 * important property this file exists to prove.
 */
class ClipValidationExportTest {

    private val config = RouteAttributionScoringConfig()

    private val routeOne = ValidationRouteDefinition(
        routeId = 100L,
        name = "Route One",
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    private val routeTwo = ValidationRouteDefinition(
        routeId = 200L,
        name = "Route Two",
        startHoldIds = setOf(2),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    private val session = ManualValidationSession(
        validationSessionId = "session-1",
        referenceImagePath = "/tmp/ref.jpg",
        videoPath = "/tmp/clip.mp4",
        wallOrFixtureId = "wall-a",
        cameraGeometryProfileVersion = 1,
        createdAtEpochMs = 1_700_000_000_000L,
        routeDefinitions = listOf(routeOne, routeTwo),
        wallSetupId = "setup-1",
        expectedRouteId = 100L,
    )

    private val timelineEvents = listOf(
        HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 0L, 0.9f, EvidenceQuality.STRONG),
        HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.RELEASED, 500L, 0.9f, EvidenceQuality.STRONG),
        HoldContactEvent(Limb.RIGHT_HAND, 2, ContactEventType.ESTABLISHED, 100L, 0.85f, EvidenceQuality.STRONG),
        HoldContactEvent(Limb.LEFT_FOOT, 3, ContactEventType.ESTABLISHED, 200L, 0.7f, EvidenceQuality.FALLBACK),
    )

    private val report = ManualValidationReport(
        poseFrameCount = 120,
        poseConfidenceCoveragePercent = 87.5f,
        establishedEventCount = 3,
        contactsPerLimb = mapOf(Limb.LEFT_HAND to 1, Limb.RIGHT_HAND to 1, Limb.LEFT_FOOT to 1, Limb.RIGHT_FOOT to 0),
        holdIdsTouched = setOf(1, 2, 3),
        timeline = HoldContactTimeline(timelineEvents),
        shortGapCount = 0,
        longGapResetCount = 0,
        implausibleJumpResetCount = 0,
        lowConfidencePeriodCount = 0,
        groundTruthComparison = null,
    )

    private val winnerSubScore = SubScoreResult(
        routeVersionId = 100L,
        startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MATCH,
        contactCoverageScore = 0.8f,
        corridorScore = 0.5f,
        finishScore = 0.6f,
        foreignContactEventCount = 0,
        foreignContactPenalty = 0f,
        combinedScore = 0.85f,
    )

    private val runnerUpSubScore = SubScoreResult(
        routeVersionId = 200L,
        startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MATCH,
        contactCoverageScore = 0.4f,
        corridorScore = null,
        finishScore = null,
        foreignContactEventCount = 1,
        foreignContactPenalty = 0.05f,
        combinedScore = 0.55f,
    )

    private val attributionResult = AttributionResult(
        winningRouteVersionId = 100L,
        status = AttributionStatus.VERIFIED,
        reasonCode = null,
        margin = 0.3f,
        subScores = listOf(winnerSubScore, runnerUpSubScore),
    )

    private val evaluation = AttributionEvaluation(
        outcome = AttributionEvaluationOutcome.CORRECT_WINNER,
        expectedRouteId = 100L,
        predictedRouteId = 100L,
        status = AttributionStatus.VERIFIED,
    )

    private fun buildFullExport(): ClipValidationExport = ClipValidationExportBuilder.build(
        session = session,
        report = report,
        attributionResult = attributionResult,
        evaluation = evaluation,
        config = config,
        exportedAtEpochMs = 1_700_000_500_000L,
    )

    // --- 1. Full fixture: every field matches a by-hand-computed expected value -----------------

    @Test
    fun `build assembles every field from the report, attribution result, and evaluation correctly`() {
        val export = buildFullExport()

        assertEquals(ClipValidationExportBuilder.CURRENT_EXPORT_FORMAT_VERSION, export.exportFormatVersion)
        assertEquals("session-1", export.validationSessionId)
        assertEquals("wall-a", export.wallOrFixtureId)
        assertEquals("setup-1", export.wallSetupId)
        assertEquals(1, export.cameraGeometryProfileVersion)
        assertEquals(120, export.poseFrameCount)
        assertEquals(87.5f, export.poseConfidenceCoveragePercent, 0.0001f)
        assertEquals(3, export.establishedEventCount)
        assertEquals(mapOf(Limb.LEFT_HAND to 1, Limb.RIGHT_HAND to 1, Limb.LEFT_FOOT to 1, Limb.RIGHT_FOOT to 0), export.contactsPerLimb)
        assertEquals(setOf(1, 2, 3), export.holdIdsTouched)
        assertEquals(timelineEvents, export.timelineEvents)
        assertEquals(100L, export.winningRouteId)
        assertEquals(200L, export.secondPlaceRouteId)
        assertEquals(0.3f, export.margin!!, 0.0001f)
        assertEquals(AttributionStatus.VERIFIED, export.attributionStatus)
        assertNull(export.attributionReasonCode)
        assertEquals(100L, export.expectedRouteId)
        assertEquals(AttributionEvaluationOutcome.CORRECT_WINNER, export.evaluationOutcome)
        assertEquals(1_700_000_500_000L, export.createdAtEpochMs)

        assertEquals(2, export.routeCandidates.size)

        val winnerExport = export.routeCandidates[0]
        assertEquals(100L, winnerExport.routeId)
        assertEquals("Route One", winnerExport.routeName)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, winnerExport.startEvidenceStatus)
        assertFalse(winnerExport.hardGated)
        assertEquals(0.8f, winnerExport.contactCoverageScore, 0.0001f)
        assertEquals(0.5f, winnerExport.corridorScore!!, 0.0001f)
        assertEquals(0.6f, winnerExport.finishScore!!, 0.0001f)
        assertEquals(0, winnerExport.foreignContactEventCount)
        assertEquals(0f, winnerExport.foreignContactPenalty, 0.0001f)
        assertEquals(0.85f, winnerExport.combinedScore, 0.0001f)
        // Every signal was available for this candidate, so renormalization is a no-op - the
        // renormalized weights equal the config's own raw weights.
        assertEquals(config.startHoldWeight, winnerExport.normalizedWeights.startHoldWeight, 0.0001f)
        assertEquals(config.contactCoverageWeight, winnerExport.normalizedWeights.contactCoverageWeight, 0.0001f)
        assertEquals(config.corridorWeight, winnerExport.normalizedWeights.corridorWeight, 0.0001f)
        assertEquals(config.finishWeight, winnerExport.normalizedWeights.finishWeight, 0.0001f)

        val runnerUpExport = export.routeCandidates[1]
        assertEquals(200L, runnerUpExport.routeId)
        assertEquals("Route Two", runnerUpExport.routeName)
        assertEquals(StartEvidenceStatus.START_OBSERVED_MATCH, runnerUpExport.startEvidenceStatus)
        assertFalse(runnerUpExport.hardGated)
        assertEquals(0.4f, runnerUpExport.contactCoverageScore, 0.0001f)
        assertNull(runnerUpExport.corridorScore)
        assertNull(runnerUpExport.finishScore)
        assertEquals(1, runnerUpExport.foreignContactEventCount)
        assertEquals(0.05f, runnerUpExport.foreignContactPenalty, 0.0001f)
        assertEquals(0.55f, runnerUpExport.combinedScore, 0.0001f)
        // Corridor/finish are unavailable for this candidate - the total configured weight budget
        // is redistributed across only startHold+contactCoverage, per RouteAttributionEngine's own
        // renormalization rule (computed here by hand from config's own fields, not by calling the
        // function under test).
        val totalConfigured = config.startHoldWeight + config.contactCoverageWeight + config.corridorWeight + config.finishWeight
        val availableWeight = config.startHoldWeight + config.contactCoverageWeight
        val factor = totalConfigured / availableWeight
        assertEquals(config.startHoldWeight * factor, runnerUpExport.normalizedWeights.startHoldWeight, 0.0001f)
        assertEquals(config.contactCoverageWeight * factor, runnerUpExport.normalizedWeights.contactCoverageWeight, 0.0001f)
        assertEquals(0f, runnerUpExport.normalizedWeights.corridorWeight, 0.0001f)
        assertEquals(0f, runnerUpExport.normalizedWeights.finishWeight, 0.0001f)
    }

    @Test
    fun `build falls back to a synthetic route name when no matching route definition exists`() {
        val sessionWithoutDefinitions = session.copy(routeDefinitions = emptyList())
        val export = ClipValidationExportBuilder.build(
            session = sessionWithoutDefinitions,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            config = config,
            exportedAtEpochMs = 0L,
        )

        assertEquals("route-100", export.routeCandidates[0].routeName)
        assertEquals("route-200", export.routeCandidates[1].routeName)
    }

    // --- 2. toJson(): byte-identical determinism, and no per-frame dump leaking in --------------

    @Test
    fun `toJson called twice on the same built export produces byte-identical strings`() {
        val export = buildFullExport()

        assertEquals(export.toJson(), export.toJson())
    }

    @Test
    fun `toJson never contains a per-frame diagnostics dump`() {
        val json = buildFullExport().toJson()

        assertFalse(json.contains("frameDiagnostics"))
        assertFalse(json.contains("poseFrame\":"))
    }

    // --- 3. toHumanReadableSummary(): byte-identical determinism ---------------------------------

    @Test
    fun `toHumanReadableSummary called twice on the same built export produces byte-identical strings`() {
        val export = buildFullExport()

        assertEquals(export.toHumanReadableSummary(), export.toHumanReadableSummary())
    }

    // --- 4. Zero-candidate path: no crash, honest empty output -----------------------------------

    @Test
    fun `build with zero candidates and no ground truth produces an empty routeCandidates list and NOT_LABELED outcome`() {
        val emptySession = session.copy(routeDefinitions = emptyList(), expectedRouteId = null)
        val emptyResult = AttributionResult(
            winningRouteVersionId = null,
            status = AttributionStatus.UNRESOLVED,
            reasonCode = AttributionReasonCode.NO_CANDIDATES,
            margin = null,
            subScores = emptyList(),
        )
        val emptyEvaluation = ManualValidationAttributionEvaluator.evaluate(emptySession, emptyResult)
        val emptyReport = report.copy(
            contactsPerLimb = Limb.entries.associateWith { 0 },
            holdIdsTouched = emptySet(),
            timeline = HoldContactTimeline(),
        )

        val export = ClipValidationExportBuilder.build(
            session = emptySession,
            report = emptyReport,
            attributionResult = emptyResult,
            evaluation = emptyEvaluation,
            exportedAtEpochMs = 0L,
        )

        assertTrue(export.routeCandidates.isEmpty())
        assertNull(export.winningRouteId)
        assertNull(export.secondPlaceRouteId)
        assertNull(export.margin)
        assertEquals(AttributionEvaluationOutcome.NOT_LABELED, export.evaluationOutcome)
        assertEquals(AttributionStatus.UNRESOLVED, export.attributionStatus)
        assertEquals(AttributionReasonCode.NO_CANDIDATES, export.attributionReasonCode)

        // Still produces valid, non-crashing deterministic output on the empty path.
        assertEquals(export.toJson(), export.toJson())
        assertEquals(export.toHumanReadableSummary(), export.toHumanReadableSummary())
    }

    // --- 5. Phase 4C fields: provenance/lowPoseCoverage/wasRejectedBeforeAttribution round-trip ---

    private val fullProvenance = ValidationPipelineProvenance(
        pose = StageProvenance(CacheOutcome.CACHE_HIT),
        contact = StageProvenance(CacheOutcome.RECOMPUTED, "hold geometry changed"),
        attribution = StageProvenance(CacheOutcome.RECOMPUTED),
    )

    @Test
    fun `toJson then toClipValidationExport round-trips a non-null provenance and lowPoseCoverage true`() {
        val export = ClipValidationExportBuilder.build(
            session = session,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            config = config,
            exportedAtEpochMs = 1_700_000_500_000L,
            provenance = fullProvenance,
            lowPoseCoverage = true,
            wasRejectedBeforeAttribution = false,
        )

        val roundTripped = export.toJson().toClipValidationExport()

        assertEquals(export, roundTripped)
        assertEquals(fullProvenance, roundTripped.provenance)
        assertEquals(CacheOutcome.CACHE_HIT, roundTripped.provenance!!.pose.outcome)
        assertNull(roundTripped.provenance!!.pose.invalidationReason)
        assertEquals(CacheOutcome.RECOMPUTED, roundTripped.provenance!!.contact!!.outcome)
        assertEquals("hold geometry changed", roundTripped.provenance!!.contact!!.invalidationReason)
        assertEquals(CacheOutcome.RECOMPUTED, roundTripped.provenance!!.attribution!!.outcome)
        assertNull(roundTripped.provenance!!.attribution!!.invalidationReason)
        assertTrue(roundTripped.lowPoseCoverage)
        assertFalse(roundTripped.wasRejectedBeforeAttribution)
    }

    @Test
    fun `toHumanReadableSummary shows CACHE HIT, RECOMPUTED, invalidation reason, and low pose coverage flag`() {
        val export = ClipValidationExportBuilder.build(
            session = session,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            config = config,
            exportedAtEpochMs = 1_700_000_500_000L,
            provenance = fullProvenance,
            lowPoseCoverage = true,
        )

        val summary = export.toHumanReadableSummary()

        assertTrue(summary.contains("LOW POSE COVERAGE"))
        assertTrue(summary.contains("CACHE HIT"))
        assertTrue(summary.contains("RECOMPUTED"))
        assertTrue(summary.contains("hold geometry changed"))
    }

    @Test
    fun `a hand-constructed JSON string missing the three phase 4C keys entirely still parses`() {
        // Simulates JSON written before Phase 4C existed - these keys are absent entirely, not
        // present-with-null.
        val legacyJson = JSONObject(buildFullExport().toJson()).apply {
            remove("provenance")
            remove("lowPoseCoverage")
            remove("wasRejectedBeforeAttribution")
        }.toString()

        val parsed = legacyJson.toClipValidationExport()

        assertNull(parsed.provenance)
        assertFalse(parsed.lowPoseCoverage)
        assertFalse(parsed.wasRejectedBeforeAttribution)
    }

    @Test
    fun `toJson is still byte-identical across two calls with the new fields populated`() {
        val export = ClipValidationExportBuilder.build(
            session = session,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            config = config,
            exportedAtEpochMs = 1_700_000_500_000L,
            provenance = fullProvenance,
            lowPoseCoverage = true,
            wasRejectedBeforeAttribution = true,
        )

        assertEquals(export.toJson(), export.toJson())
    }
}
