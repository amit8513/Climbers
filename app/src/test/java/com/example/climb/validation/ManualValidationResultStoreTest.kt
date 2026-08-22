package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Mirrors `ManualValidationSessionStoreTest`'s own coverage shape for the analogous result store.
 * The JSON round-trip test below builds a full [ClipValidationExport] fixture (2+ route
 * candidates, several timeline events, every optional field populated) via the same
 * [ClipValidationExportBuilder] path `ClipValidationExportTest` already exercises, rather than
 * duplicating that builder's field-assembly knowledge here.
 */
class ManualValidationResultStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    private fun session(id: String) = ManualValidationSession(
        validationSessionId = id,
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
        HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.RELEASED, 500L, 0.9f, EvidenceQuality.STRONG, ReleaseReason.DISTANCE_HYSTERESIS),
        HoldContactEvent(Limb.RIGHT_HAND, 2, ContactEventType.ESTABLISHED, 100L, 0.85f, EvidenceQuality.STRONG),
        HoldContactEvent(Limb.LEFT_FOOT, 3, ContactEventType.ESTABLISHED, 200L, 0.7f, EvidenceQuality.FALLBACK),
        HoldContactEvent(Limb.RIGHT_FOOT, 4, ContactEventType.ESTABLISHED, 300L, 0.3f, EvidenceQuality.UNCERTAIN),
    )

    private val report = ManualValidationReport(
        poseFrameCount = 120,
        poseConfidenceCoveragePercent = 87.5f,
        establishedEventCount = 4,
        contactsPerLimb = mapOf(Limb.LEFT_HAND to 1, Limb.RIGHT_HAND to 1, Limb.LEFT_FOOT to 1, Limb.RIGHT_FOOT to 1),
        holdIdsTouched = setOf(1, 2, 3, 4),
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

    private fun fullExport(sessionId: String, exportedAtEpochMs: Long): ClipValidationExport =
        ClipValidationExportBuilder.build(
            session = session(sessionId),
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            config = config,
            exportedAtEpochMs = exportedAtEpochMs,
        )

    @Test
    fun `a saved result can be loaded back via loadResults`() {
        val store = LocalJsonManualValidationResultStore(tempFolder.root)
        val export = fullExport("session-1", 1_700_000_500_000L)

        store.saveResult(export)
        val loaded = store.loadResults()

        assertEquals(listOf(export), loaded)
    }

    @Test
    fun `loadResult finds a saved export by id`() {
        val store = LocalJsonManualValidationResultStore(tempFolder.root)
        val export = fullExport("session-1", 1_700_000_500_000L)

        store.saveResult(export)

        assertEquals(export, store.loadResult("session-1"))
    }

    @Test
    fun `loadResult returns null for an id that was never saved`() {
        val store = LocalJsonManualValidationResultStore(tempFolder.root)

        assertNull(store.loadResult("does-not-exist"))
    }

    @Test
    fun `deleteResult removes it from subsequent loads`() {
        val store = LocalJsonManualValidationResultStore(tempFolder.root)
        store.saveResult(fullExport("session-1", 1_700_000_500_000L))

        store.deleteResult("session-1")

        assertNull(store.loadResult("session-1"))
        assertTrue(store.loadResults().isEmpty())
    }

    @Test
    fun `toJson then toClipValidationExport round-trips a full fixture exactly`() {
        val export = fullExport("session-roundtrip", 1_700_000_500_000L)

        val roundTripped = export.toJson().toClipValidationExport()

        assertEquals(export, roundTripped)
        // Pin down the specific fields this fixture was built to exercise: 2+ route candidates,
        // several timeline events (including one with a non-null releaseReason and one UNCERTAIN
        // event), so a round-trip bug in any single field can't hide behind a trivially-empty fixture.
        assertEquals(2, roundTripped.routeCandidates.size)
        assertEquals(timelineEvents, roundTripped.timelineEvents)
        assertEquals(ReleaseReason.DISTANCE_HYSTERESIS, roundTripped.timelineEvents[1].releaseReason)
        assertEquals(EvidenceQuality.UNCERTAIN, roundTripped.timelineEvents[4].evidenceQuality)
        assertNull(roundTripped.attributionReasonCode)
    }

    @Test
    fun `loadResults returns every saved export, regardless of order`() {
        val store = LocalJsonManualValidationResultStore(tempFolder.root)
        val exportA = fullExport("session-a", 1_000L)
        val exportB = fullExport("session-b", 2_000L)
        val exportC = fullExport("session-c", 3_000L)

        store.saveResult(exportA)
        store.saveResult(exportB)
        store.saveResult(exportC)

        val loaded = store.loadResults()

        assertEquals(3, loaded.size)
        val byId = loaded.associateBy { it.validationSessionId }
        assertEquals(setOf("session-a", "session-b", "session-c"), byId.keys)
        assertEquals(exportA, byId["session-a"])
        assertEquals(exportB, byId["session-b"])
        assertEquals(exportC, byId["session-c"])
    }
}
