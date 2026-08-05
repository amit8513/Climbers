package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.coaching.CoachingTip
import com.example.climb.pose.PoseFrame
import kotlinx.coroutines.flow.Flow

/** Bumped whenever the pose model or the metrics/event algorithms change meaningfully, so old
 * stored analyses can be told apart from ones produced by a newer pipeline. */
const val CURRENT_MODEL_VERSION = "pose_landmarker_lite_v1"
const val CURRENT_ALGORITHM_VERSION = 3

class AnalysisRepository(private val dao: AnalysisDao) {
    suspend fun createAttempt(attempt: ClimbAttemptEntity): Long = dao.insertAttempt(attempt)

    suspend fun getAttempt(attemptId: Long): ClimbAttemptEntity? = dao.getAttempt(attemptId)

    fun observeAttempt(attemptId: Long): Flow<ClimbAttemptEntity?> = dao.observeAttempt(attemptId)

    fun observeLatestAttemptForSourceClimb(sourceClimbId: Long): Flow<ClimbAttemptEntity?> =
        dao.observeLatestAttemptForSourceClimb(sourceClimbId)

    fun observeLatestAnalysis(attemptId: Long): Flow<ClimbAnalysisEntity?> = dao.observeLatestAnalysis(attemptId)

    suspend fun createQueuedAnalysis(attemptId: Long, now: Long): Long = dao.insertAnalysis(
        ClimbAnalysisEntity(
            attemptId = attemptId,
            modelVersion = CURRENT_MODEL_VERSION,
            algorithmVersion = CURRENT_ALGORITHM_VERSION,
            createdAt = now,
            status = AnalysisStatus.QUEUED,
            confidence = null,
            climbStartMs = null,
            climbEndMs = null,
            videoDurationMs = null,
            videoWidth = null,
            videoHeight = null,
            poseFramesJson = "",
            metricsJson = "",
            eventsJson = "",
            tipsJson = "",
            failureReason = null,
        ),
    )

    fun observeAnalysis(analysisId: Long): Flow<ClimbAnalysisEntity?> = dao.observeAnalysis(analysisId)

    suspend fun getAnalysis(analysisId: Long): ClimbAnalysisEntity? = dao.getAnalysis(analysisId)

    suspend fun updateStatus(analysis: ClimbAnalysisEntity, status: AnalysisStatus) =
        dao.updateAnalysis(analysis.copy(status = status))

    suspend fun completeWithFailure(analysis: ClimbAnalysisEntity, reason: String) =
        dao.updateAnalysis(analysis.copy(status = AnalysisStatus.FAILED, failureReason = reason))

    suspend fun completeWithFrames(
        analysis: ClimbAnalysisEntity,
        frames: List<PoseFrame>,
        videoDurationMs: Long,
        videoWidth: Int,
        videoHeight: Int,
        metrics: ClimbMetrics,
        events: List<ClimbEvent>,
        tips: List<CoachingTip>,
    ) {
        dao.updateAnalysis(
            analysis.copy(
                status = AnalysisStatus.COMPLETE,
                confidence = metrics.reliableFramePercentage / 100f,
                climbStartMs = metrics.climbStartMs,
                climbEndMs = metrics.climbEndMs,
                videoDurationMs = videoDurationMs,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                poseFramesJson = frames.toJson(),
                metricsJson = metrics.toJson(),
                eventsJson = events.toJson(),
                tipsJson = tips.toJson(),
            ),
        )
    }
}
