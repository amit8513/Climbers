package com.example.climb.pose

enum class PoseAnalysisPhase { PREPARING, EXTRACTING_FRAMES, TRACKING_POSE }

data class PoseAnalysisProgress(
    val phase: PoseAnalysisPhase,
    val fractionComplete: Float,
    val processedFrames: Int,
    val totalFramesEstimate: Int,
)

sealed interface PoseAnalysisResult {
    data class Success(
        val frames: List<PoseFrame>,
        val videoDurationMs: Long,
        val videoWidth: Int,
        val videoHeight: Int,
    ) : PoseAnalysisResult

    data class Failure(val reason: String) : PoseAnalysisResult
}

/**
 * Runs pose detection over a video. Implementations own all interaction with the underlying
 * ML/CV library — only app-defined types ([PoseFrame], [PoseAnalysisProgress], etc.) cross
 * this boundary, so swapping the concrete pose library later doesn't touch callers.
 */
interface PoseEstimator {
    suspend fun analyzeVideo(
        source: VideoSource,
        configuration: PoseAnalysisConfiguration,
        onProgress: (PoseAnalysisProgress) -> Unit,
    ): PoseAnalysisResult
}
