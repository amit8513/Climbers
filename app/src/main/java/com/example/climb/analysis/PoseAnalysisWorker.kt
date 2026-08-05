package com.example.climb.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.climb.pose.PoseAnalysisConfiguration
import com.example.climb.pose.PoseAnalysisPhase
import com.example.climb.pose.PoseAnalysisResult
import com.example.climb.pose.PoseEstimator
import com.example.climb.pose.VideoSource
import kotlinx.coroutines.runBlocking

/**
 * Runs pose analysis for one [ClimbAttemptEntity] in the background, surviving navigation away
 * from the progress screen. Real progress (via [setProgress]) — no fake timer — is reported as
 * the estimator works through preparing/extracting/tracking phases; the DB row itself is only
 * touched at the start (queued) and end (complete/failed), not once per phase, since WorkManager's
 * own progress `Data` is the channel [com.example.climb.ui.analysis.AnalysisProgressScreen] reads.
 */
class PoseAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
    private val analysisRepository: AnalysisRepository,
    private val poseEstimator: PoseEstimator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val attemptId = inputData.getLong(KEY_ATTEMPT_ID, -1L)
        if (attemptId <= 0L) return Result.failure()
        val attempt = analysisRepository.getAttempt(attemptId) ?: return Result.failure()

        val analysisId = analysisRepository.createQueuedAnalysis(attemptId, System.currentTimeMillis())
        val analysis = analysisRepository.getAnalysis(analysisId) ?: return Result.failure()

        setProgress(progressData(analysisId, AnalysisStatus.PREPARING, 0f))

        val result = poseEstimator.analyzeVideo(
            source = VideoSource.LocalFile(attempt.videoPath),
            configuration = PoseAnalysisConfiguration(),
        ) { progress ->
            // analyzeVideo runs this callback synchronously from its own background dispatcher
            // (never the main thread), so a short blocking publish here is safe.
            runBlocking { setProgress(progressData(analysisId, statusFor(progress.phase), progress.fractionComplete)) }
        }

        return when (result) {
            is PoseAnalysisResult.Success -> {
                setProgress(progressData(analysisId, AnalysisStatus.SAVING, 1f))
                analysisRepository.completeWithFrames(
                    analysis = analysis,
                    frames = result.frames,
                    videoDurationMs = result.videoDurationMs,
                    videoWidth = result.videoWidth,
                    videoHeight = result.videoHeight,
                )
                Result.success(workDataOf(KEY_ANALYSIS_ID to analysisId))
            }
            is PoseAnalysisResult.Failure -> {
                analysisRepository.completeWithFailure(analysis, result.reason)
                Result.failure(workDataOf(KEY_ANALYSIS_ID to analysisId, KEY_FAILURE_REASON to result.reason))
            }
        }
    }

    private fun statusFor(phase: PoseAnalysisPhase): AnalysisStatus = when (phase) {
        PoseAnalysisPhase.PREPARING -> AnalysisStatus.PREPARING
        PoseAnalysisPhase.EXTRACTING_FRAMES -> AnalysisStatus.EXTRACTING_FRAMES
        PoseAnalysisPhase.TRACKING_POSE -> AnalysisStatus.ESTIMATING_POSE
    }

    private fun progressData(analysisId: Long, status: AnalysisStatus, fraction: Float) = workDataOf(
        KEY_ANALYSIS_ID to analysisId,
        KEY_PHASE to status.name,
        KEY_FRACTION to fraction,
    )

    companion object {
        private const val KEY_ATTEMPT_ID = "attemptId"
        const val KEY_ANALYSIS_ID = "analysisId"
        const val KEY_PHASE = "phase"
        const val KEY_FRACTION = "fraction"
        const val KEY_FAILURE_REASON = "failureReason"

        fun uniqueWorkName(attemptId: Long) = "pose_analysis_$attemptId"

        fun buildRequest(attemptId: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<PoseAnalysisWorker>()
                .setInputData(workDataOf(KEY_ATTEMPT_ID to attemptId))
                .build()
    }
}
