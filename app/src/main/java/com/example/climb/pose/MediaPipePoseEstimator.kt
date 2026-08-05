package com.example.climb.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"

/**
 * Frame reliability is judged only on the limb/torso landmarks the metrics actually use — face
 * points (nose, eyes, ears, mouth) are excluded. In climbing footage the climber's face is
 * often turned away from the camera or occluded, so including them dragged the whole-frame
 * average down even when hips/knees/ankles/wrists were tracked perfectly well, which made
 * movement-derived metrics (pauses, lock-offs, foot adjustments, efficiency) starve for
 * "reliable" frames and read as zero.
 */
private val CONFIDENCE_LANDMARK_TYPES = setOf(
    PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST,
    PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE,
    PoseLandmarkType.LEFT_HEEL, PoseLandmarkType.RIGHT_HEEL,
    PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmarkType.RIGHT_FOOT_INDEX,
)

/**
 * The concrete Android pose estimator, backed by MediaPipe's Pose Landmarker. All MediaPipe
 * types (NormalizedLandmark, PoseLandmarkerResult, MPImage, ...) are mapped to this app's own
 * [PoseFrame]/[PoseLandmark] before returning — nothing MediaPipe-specific crosses out of this
 * file, per [PoseEstimator]'s contract.
 *
 * Frames are pulled one at a time via [MediaMetadataRetriever] at the configured target FPS and
 * released immediately after detection, rather than decoding the whole video up front — a
 * multi-minute climb video would otherwise hold every frame in memory at once.
 */
class MediaPipePoseEstimator(private val context: Context) : PoseEstimator {

    override suspend fun analyzeVideo(
        source: VideoSource,
        configuration: PoseAnalysisConfiguration,
        onProgress: (PoseAnalysisProgress) -> Unit,
    ): PoseAnalysisResult = withContext(Dispatchers.Default) {
        val path = (source as VideoSource.LocalFile).path
        val file = File(path)
        if (!file.exists()) {
            return@withContext PoseAnalysisResult.Failure("Video file not found")
        }
        if (file.length() > configuration.maxFileSizeBytes) {
            return@withContext PoseAnalysisResult.Failure("Video file is larger than the ${configuration.maxFileSizeBytes / (1024 * 1024)}MB limit")
        }

        onProgress(PoseAnalysisProgress(PoseAnalysisPhase.PREPARING, 0f, 0, 0))

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
        } catch (e: Exception) {
            retriever.release()
            return@withContext PoseAnalysisResult.Failure("Couldn't read this video — it may be corrupted or an unsupported format")
        }

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

        if (durationMs == null || durationMs <= 0) {
            retriever.release()
            return@withContext PoseAnalysisResult.Failure("Couldn't determine the video's duration")
        }
        if (durationMs > configuration.maxDurationMs) {
            retriever.release()
            return@withContext PoseAnalysisResult.Failure("Video is longer than the ${configuration.maxDurationMs / 1000}s limit")
        }
        if (minOf(width, height) < configuration.minResolutionPx) {
            retriever.release()
            return@withContext PoseAnalysisResult.Failure("Video resolution is below the ${configuration.minResolutionPx}px minimum")
        }

        val poseLandmarker = try {
            val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_ASSET_PATH).build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setMinPoseDetectionConfidence(configuration.minLandmarkConfidence)
                .setMinPosePresenceConfidence(configuration.minLandmarkConfidence)
                .setMinTrackingConfidence(configuration.minLandmarkConfidence)
                .build()
            PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            retriever.release()
            return@withContext PoseAnalysisResult.Failure("Pose detection model failed to load: ${e.message}")
        }

        val frameStepMs = 1000L / configuration.targetFps
        val totalFramesEstimate = (durationMs / frameStepMs).toInt().coerceAtLeast(1)
        val frames = mutableListOf<PoseFrame>()

        try {
            var timestampMs = 0L
            var processed = 0
            onProgress(PoseAnalysisProgress(PoseAnalysisPhase.EXTRACTING_FRAMES, 0f, 0, totalFramesEstimate))

            while (timestampMs < durationMs) {
                val bitmap: Bitmap? = retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val result = poseLandmarker.detectForVideo(mpImage, timestampMs)
                    frames += result.landmarks().firstOrNull()?.let { landmarks ->
                        mapToPoseFrame(timestampMs, landmarks, configuration)
                    } ?: unreliableFrame(timestampMs)
                    bitmap.recycle()
                }
                processed++
                timestampMs += frameStepMs
                onProgress(
                    PoseAnalysisProgress(
                        phase = PoseAnalysisPhase.TRACKING_POSE,
                        fractionComplete = (processed.toFloat() / totalFramesEstimate).coerceIn(0f, 1f),
                        processedFrames = processed,
                        totalFramesEstimate = totalFramesEstimate,
                    ),
                )
            }
        } catch (e: Exception) {
            return@withContext PoseAnalysisResult.Failure("Pose detection failed partway through: ${e.message}")
        } finally {
            poseLandmarker.close()
            retriever.release()
        }

        PoseAnalysisResult.Success(
            frames = frames,
            videoDurationMs = durationMs,
            videoWidth = width,
            videoHeight = height,
        )
    }

    private fun mapToPoseFrame(
        timestampMs: Long,
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        configuration: PoseAnalysisConfiguration,
    ): PoseFrame {
        val mapped = landmarks.zip(PoseLandmarkType.entries).map { (landmark, type) ->
            PoseLandmark(
                type = type,
                normalizedX = landmark.x(),
                normalizedY = landmark.y(),
                normalizedZ = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
                presence = landmark.presence().orElse(0f),
            )
        }
        val bodyLandmarks = mapped.filter { it.type in CONFIDENCE_LANDMARK_TYPES }.ifEmpty { mapped }
        val averageConfidence = bodyLandmarks.map { it.presence }.average().toFloat()
        return PoseFrame(
            timestampMs = timestampMs,
            landmarks = mapped,
            averageConfidence = averageConfidence,
            isReliable = averageConfidence >= configuration.minReliableFrameConfidence,
            bodyBoundingBox = boundingBoxOf(mapped),
        )
    }

    private fun unreliableFrame(timestampMs: Long) = PoseFrame(
        timestampMs = timestampMs,
        landmarks = emptyList(),
        averageConfidence = 0f,
        isReliable = false,
        bodyBoundingBox = null,
    )

    private fun boundingBoxOf(landmarks: List<PoseLandmark>): BodyBoundingBox? {
        if (landmarks.isEmpty()) return null
        return BodyBoundingBox(
            left = landmarks.minOf { it.normalizedX },
            top = landmarks.minOf { it.normalizedY },
            right = landmarks.maxOf { it.normalizedX },
            bottom = landmarks.maxOf { it.normalizedY },
        )
    }
}
