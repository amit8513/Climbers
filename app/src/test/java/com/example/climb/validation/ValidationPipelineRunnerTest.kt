package com.example.climb.validation

import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseAnalysisProgress
import com.example.climb.pose.PoseAnalysisResult
import com.example.climb.pose.PoseEstimator
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import com.example.climb.pose.VideoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Proves [ValidationPipelineRunner] end to end: real disk-backed caching (via real
 * `LocalJson*Store` instances over a real [TemporaryFolder], never mocks) around real
 * [ManualValidationPipeline]/[ManualValidationAttributionRunner]/
 * `com.example.climb.attribution.RouteAttributionEngine`/
 * `com.example.climb.analysis.contact.HoldContactDetector` calls, with [CountingFakePoseEstimator]
 * standing in only for the one genuinely expensive, non-deterministic dependency (MediaPipe
 * itself). This is THE test for this phase's whole reason to exist: MediaPipe must run exactly
 * once per video across repeated debug iterations, and each cache stage must invalidate
 * independently, exactly matching whichever inputs actually changed.
 */
class ValidationPipelineRunnerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Counts invocations - lets tests assert "MediaPipe ran exactly once" across repeated
     * [ValidationPipelineRunner.run] calls, the central property this whole phase exists to prove. */
    private class CountingFakePoseEstimator(private val result: PoseAnalysisResult) : PoseEstimator {
        var invocationCount: Int = 0
            private set

        override suspend fun analyzeVideo(
            source: VideoSource,
            configuration: PoseAnalysisConfiguration,
            onProgress: (PoseAnalysisProgress) -> Unit,
        ): PoseAnalysisResult {
            invocationCount++
            return result
        }
    }

    /** Real disk-backed stores + a real video file, fresh per test via [TemporaryFolder] - proves
     * real cross-call persistence, not an in-memory stand-in. */
    private class Harness(temporaryFolder: TemporaryFolder) {
        val videoFile: File = temporaryFolder.newFile("clip.mp4").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val poseStore: PoseArtifactStore = LocalJsonPoseArtifactStore(temporaryFolder.newFolder("pose-cache"))
        val contactStore: ContactAnalysisStore = LocalJsonContactAnalysisStore(temporaryFolder.newFolder("contact-cache"))
        val attributionStore: AttributionCacheStore = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("attribution-cache"))
    }

    private fun handFrame(timestampMs: Long, point: Point2D, isReliable: Boolean = true, confidence: Float = 0.9f) = PoseFrame(
        timestampMs = timestampMs,
        landmarks = listOf(
            PoseLandmark(PoseLandmarkType.LEFT_INDEX, point.x, point.y, 0f, confidence, confidence),
            PoseLandmark(PoseLandmarkType.LEFT_PINKY, point.x, point.y, 0f, confidence, confidence),
            PoseLandmark(PoseLandmarkType.LEFT_THUMB, point.x, point.y, 0f, confidence, confidence),
        ),
        averageConfidence = confidence,
        isReliable = isReliable,
        bodyBoundingBox = null,
    )

    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.03f) = ValidationHoldAnnotation(
        holdId = id,
        contourNormalized = listOf(
            Point2D(centerX - halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY + halfWidth),
            Point2D(centerX - halfWidth, centerY + halfWidth),
        ),
    )

    // A single tracked hand walks hold1 (0.1,0.1) -> hold2 (0.5,0.5) -> hold3 (0.9,0.9), dwelling
    // long enough at each to satisfy HoldContactConfig's default 300ms establish dwell AND (for
    // hold1, routeA's own start hold) RouteAttributionScoringConfig's default 500ms post-establish
    // dwell that StartHoldMatcher's qualifying-establishment test requires - this is deliberately
    // built to reach the exact same real decision RouteAttributionEngineTest's own
    // "clean single candidate with perfect evidence reaches VERIFIED end to end" fixture reaches,
    // just produced through the REAL pose -> HoldContactDetector path instead of hand-built events.
    private fun realFrames(reliable: Boolean = true): List<PoseFrame> = listOf(
        handFrame(0L, Point2D(0.1f, 0.1f), reliable),
        handFrame(300L, Point2D(0.1f, 0.1f), reliable),
        handFrame(900L, Point2D(0.1f, 0.1f), reliable),
        handFrame(1200L, Point2D(0.5f, 0.5f), reliable),
        handFrame(1500L, Point2D(0.5f, 0.5f), reliable),
        handFrame(2100L, Point2D(0.5f, 0.5f), reliable),
        handFrame(2400L, Point2D(0.9f, 0.9f), reliable),
        handFrame(2700L, Point2D(0.9f, 0.9f), reliable),
    )

    private val hold1 = squareHold(1, 0.1f, 0.1f) // routeA start
    private val hold2 = squareHold(2, 0.5f, 0.5f) // routeA body
    private val hold3 = squareHold(3, 0.9f, 0.9f) // routeA finish
    private val hold4 = squareHold(4, 0.9f, 0.1f) // routeB start - never touched by any frame

    private val routeA = ValidationRouteDefinition(
        routeId = 10L,
        name = "route-a",
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        bodyHoldIds = setOf(2),
        finishHoldIds = setOf(3),
        finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
        corridorNormalized = NormalizedRect(0f, 0f, 1f, 1f),
    )

    private val routeB = ValidationRouteDefinition(
        routeId = 20L,
        name = "route-b",
        startHoldIds = setOf(4),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    private val routeC = ValidationRouteDefinition(
        routeId = 30L,
        name = "route-c-added-later",
        startHoldIds = setOf(4),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    private fun baseSession(
        videoPath: String,
        holds: List<ValidationHoldAnnotation> = listOf(hold1, hold2, hold3, hold4),
        routeDefinitions: List<ValidationRouteDefinition> = listOf(routeA, routeB),
        validationSessionId: String = "session-1",
    ) = ManualValidationSession(
        validationSessionId = validationSessionId,
        referenceImagePath = "/local/ref.jpg",
        videoPath = videoPath,
        wallOrFixtureId = "wall-a",
        cameraGeometryProfileVersion = 1,
        annotatedHolds = holds,
        createdAtEpochMs = 1_000L,
        routeDefinitions = routeDefinitions,
        attemptStartTimestampMs = 0L,
    )

    private fun runPipeline(
        harness: Harness,
        session: ManualValidationSession,
        estimator: CountingFakePoseEstimator,
    ) = runBlocking {
        ValidationPipelineRunner.run(
            session = session,
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
            holdContactConfig = HoldContactConfig(),
            routeAttributionScoringConfig = RouteAttributionScoringConfig(),
            poseArtifactStore = harness.poseStore,
            contactAnalysisStore = harness.contactStore,
            attributionCacheStore = harness.attributionStore,
        )
    }

    // --- THE central worked example this phase exists to prove -----------------------------

    @Test
    fun `first run recomputes every stage, a repeat run hits every cache without re-invoking MediaPipe, and each targeted input change invalidates exactly the right stages`() {
        val harness = Harness(temporaryFolder)
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(realFrames(), 3000L, 1920, 1080))
        val session = baseSession(harness.videoFile.absolutePath)

        // 1. First run - everything is RECOMPUTED, as a plain first run (no invalidation reason).
        val first = runPipeline(harness, session, estimator)
        assertEquals(CacheOutcome.RECOMPUTED, first.provenance.pose.outcome)
        assertNull(first.provenance.pose.invalidationReason)
        assertEquals(CacheOutcome.RECOMPUTED, first.provenance.contact?.outcome)
        assertNull(first.provenance.contact?.invalidationReason)
        assertEquals(CacheOutcome.RECOMPUTED, first.provenance.attribution?.outcome)
        assertNull(first.provenance.attribution?.invalidationReason)
        assertEquals(1, estimator.invocationCount)
        assertNull(first.error)
        assertTrue(first.outcome is ManualValidationOutcome.Processed)
        assertNotNull(first.report)
        assertNotNull(first.attributionResult)
        // A genuine, non-trivial decision - never NO_CANDIDATES - matching
        // RouteAttributionEngineTest's own "clean single candidate reaches VERIFIED" shape.
        assertEquals(AttributionStatus.VERIFIED, first.attributionResult!!.status)
        assertEquals(10L, first.attributionResult!!.winningRouteVersionId)

        // 2. Second run, no changes at all - every stage is a CACHE_HIT, MediaPipe never runs again.
        val second = runPipeline(harness, session, estimator)
        assertEquals(CacheOutcome.CACHE_HIT, second.provenance.pose.outcome)
        assertEquals(CacheOutcome.CACHE_HIT, second.provenance.contact?.outcome)
        assertEquals(CacheOutcome.CACHE_HIT, second.provenance.attribution?.outcome)
        assertEquals(1, estimator.invocationCount)
        assertEquals(first.attributionResult, second.attributionResult)
        assertEquals(first.report, second.report)

        // 3. Change route definitions only (add a route) - pose/contact stay cached, ONLY
        // attribution recomputes, with a real invalidation reason (a prior entry existed).
        val sessionWithAddedRoute = session.copy(routeDefinitions = session.routeDefinitions + routeC)
        val third = runPipeline(harness, sessionWithAddedRoute, estimator)
        assertEquals(CacheOutcome.CACHE_HIT, third.provenance.pose.outcome)
        assertEquals(CacheOutcome.CACHE_HIT, third.provenance.contact?.outcome)
        assertEquals(CacheOutcome.RECOMPUTED, third.provenance.attribution?.outcome)
        assertNotNull(third.provenance.attribution?.invalidationReason)
        assertEquals(1, estimator.invocationCount)

        // 4. Change hold geometry only (move hold4's contour), independent of step 3's route
        // change - pose stays cached, but contact recomputes (real invalidation reason) and
        // attribution cascades to RECOMPUTED too since its own cache key nests the contact key.
        val movedHold4 = hold4.copy(contourNormalized = hold4.contourNormalized.map { Point2D(it.x - 0.01f, it.y) })
        val sessionWithMovedHold = session.copy(annotatedHolds = listOf(hold1, hold2, hold3, movedHold4))
        val fourth = runPipeline(harness, sessionWithMovedHold, estimator)
        assertEquals(CacheOutcome.CACHE_HIT, fourth.provenance.pose.outcome)
        assertEquals(CacheOutcome.RECOMPUTED, fourth.provenance.contact?.outcome)
        assertNotNull(fourth.provenance.contact?.invalidationReason)
        assertEquals(CacheOutcome.RECOMPUTED, fourth.provenance.attribution?.outcome)
        assertNotNull(fourth.provenance.attribution?.invalidationReason)
        assertEquals(1, estimator.invocationCount)
    }

    // --- Failure / advisory paths ------------------------------------------------------------

    @Test
    fun `a pose-estimator failure produces Rejected with POSE_EXTRACTION_FAILED and no contact or attribution provenance`() {
        val harness = Harness(temporaryFolder)
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Failure("unsupported format"))
        val session = baseSession(harness.videoFile.absolutePath)

        val result = runPipeline(harness, session, estimator)

        assertTrue(result.outcome is ManualValidationOutcome.Rejected)
        assertEquals("unsupported format", (result.outcome as ManualValidationOutcome.Rejected).reason)
        assertNull(result.report)
        assertNull(result.attributionResult)
        assertEquals(false, result.lowPoseCoverage)
        assertEquals(ValidationPipelineErrorCode.POSE_EXTRACTION_FAILED, result.error?.code)
        assertEquals(CacheOutcome.RECOMPUTED, result.provenance.pose.outcome)
        assertNull(result.provenance.pose.invalidationReason)
        assertNull(result.provenance.contact)
        assertNull(result.provenance.attribution)
    }

    @Test
    fun `low pose coverage is surfaced as an advisory flag only, never as an error, and still returns a real report and attribution result`() {
        val harness = Harness(temporaryFolder)
        // Only the first of 8 frames is reliable - well under MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT
        // - but every frame still carries high per-landmark confidence, so the real detector still
        // establishes real contacts and the attribution engine still reaches a real decision.
        val frames = realFrames(reliable = false).mapIndexed { index, frame -> if (index == 0) frame.copy(isReliable = true) else frame }
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(frames, 3000L, 1920, 1080))
        val session = baseSession(harness.videoFile.absolutePath)

        val result = runPipeline(harness, session, estimator)

        assertTrue(result.outcome is ManualValidationOutcome.Processed)
        assertNull(result.error)
        assertNotNull(result.report)
        assertNotNull(result.attributionResult)
        assertTrue(result.report!!.poseConfidenceCoveragePercent < MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT)
        assertTrue(result.lowPoseCoverage)
    }
}
