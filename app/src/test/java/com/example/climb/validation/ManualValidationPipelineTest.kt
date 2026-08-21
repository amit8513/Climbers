package com.example.climb.validation

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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counts invocations and records the configuration it was called with - lets tests assert
 * "MediaPipe ran exactly once" and "the 15fps validation config was actually used" without any
 * real video file or MediaPipe model. */
private class CountingFakePoseEstimator(private val result: PoseAnalysisResult) : PoseEstimator {
    var invocationCount: Int = 0
        private set
    var lastConfiguration: PoseAnalysisConfiguration? = null
        private set

    override suspend fun analyzeVideo(
        source: VideoSource,
        configuration: PoseAnalysisConfiguration,
        onProgress: (PoseAnalysisProgress) -> Unit,
    ): PoseAnalysisResult {
        invocationCount++
        lastConfiguration = configuration
        return result
    }
}

class ManualValidationPipelineTest {

    private fun session(annotatedHolds: List<ValidationHoldAnnotation> = emptyList()) = ManualValidationSession(
        validationSessionId = "session-1",
        referenceImagePath = "/local/ref.jpg",
        videoPath = "/local/video.mp4",
        wallOrFixtureId = "wall-a",
        cameraGeometryProfileVersion = 1,
        annotatedHolds = annotatedHolds,
        createdAtEpochMs = 1_000L,
    )

    private fun handFrame(timestampMs: Long, point: Point2D, confidence: Float = 0.9f) = PoseFrame(
        timestampMs = timestampMs,
        landmarks = listOf(
            PoseLandmark(PoseLandmarkType.LEFT_INDEX, point.x, point.y, 0f, confidence, confidence),
            PoseLandmark(PoseLandmarkType.LEFT_PINKY, point.x, point.y, 0f, confidence, confidence),
            PoseLandmark(PoseLandmarkType.LEFT_THUMB, point.x, point.y, 0f, confidence, confidence),
        ),
        averageConfidence = confidence,
        isReliable = confidence >= 0.5f,
        bodyBoundingBox = null,
    )

    private fun squareHold(id: Int, centerX: Float, centerY: Float, halfWidth: Float = 0.1f) = ValidationHoldAnnotation(
        holdId = id,
        contourNormalized = listOf(
            Point2D(centerX - halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY - halfWidth),
            Point2D(centerX + halfWidth, centerY + halfWidth),
            Point2D(centerX - halfWidth, centerY + halfWidth),
        ),
    )

    @Test
    fun `MediaPipe pose extraction runs exactly once per validation video`() = runBlocking {
        val frames = listOf(handFrame(0L, Point2D(0.5f, 0.5f)), handFrame(300L, Point2D(0.5f, 0.5f)))
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(frames, 1000L, 1920, 1080))

        ManualValidationPipeline.run(
            session = session(listOf(squareHold(1, 0.5f, 0.5f))),
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
        )

        assertEquals(1, estimator.invocationCount)
    }

    @Test
    fun `the validation pipeline uses its own 15fps config, never the personal pipeline's 10fps default`() = runBlocking {
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(emptyList(), 0L, 1920, 1080))

        ManualValidationPipeline.run(
            session = session(),
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
        )

        assertEquals(MANUAL_VALIDATION_TARGET_FPS, estimator.lastConfiguration?.targetFps)
        assertEquals(15, estimator.lastConfiguration?.targetFps)
        // The personal pipeline's own default is untouched by this file's existence.
        assertEquals(10, PoseAnalysisConfiguration().targetFps)
    }

    @Test
    fun `a geometry mismatch rejects the clip without ever running the contact detector`() = runBlocking {
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(emptyList(), 1000L, 1080, 1920))

        val outcome = ManualValidationPipeline.run(
            session = session(),
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080), // landscape reference
            expectedGeometryProfileVersion = 1,
            // videoWidth/Height above (1080x1920) is portrait - a real aspect-ratio mismatch.
        )

        assertTrue(outcome is ManualValidationOutcome.Rejected)
        assertTrue((outcome as ManualValidationOutcome.Rejected).reason.contains("VALIDATION_GEOMETRY_MISMATCH"))
        // Pose extraction still only ran once - the geometry check happens after extraction
        // (it needs the video's real decoded dimensions), never a second time.
        assertEquals(1, estimator.invocationCount)
    }

    @Test
    fun `a pose-estimator failure is surfaced as Rejected rather than crashing`() = runBlocking {
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Failure("unsupported format"))

        val outcome = ManualValidationPipeline.run(
            session = session(),
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
        )

        assertTrue(outcome is ManualValidationOutcome.Rejected)
        assertEquals("unsupported format", (outcome as ManualValidationOutcome.Rejected).reason)
    }

    @Test
    fun `identity geometry with matching dimensions produces a Processed outcome with a real contact timeline`() = runBlocking {
        val frames = listOf(
            handFrame(0L, Point2D(0.5f, 0.5f)),
            handFrame(300L, Point2D(0.5f, 0.5f)),
        )
        val estimator = CountingFakePoseEstimator(PoseAnalysisResult.Success(frames, 1000L, 1920, 1080))

        val outcome = ManualValidationPipeline.run(
            session = session(listOf(squareHold(1, 0.5f, 0.5f))),
            poseEstimator = estimator,
            referenceImageDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
        )

        assertTrue(outcome is ManualValidationOutcome.Processed)
        val processed = outcome as ManualValidationOutcome.Processed
        assertEquals(2, processed.frameDiagnostics.size)
        assertEquals(1, processed.timeline.establishedEventCount())
    }
}
