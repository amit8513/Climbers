package com.example.climb.analysis

import com.example.climb.pose.PoseFrame
import kotlinx.coroutines.flow.Flow

/** Bumped whenever the pose model or the metrics/event algorithms change meaningfully, so old
 * stored analyses can be told apart from ones produced by a newer pipeline. */
const val CURRENT_MODEL_VERSION = "pose_landmarker_lite_v1"
const val CURRENT_ALGORITHM_VERSION = 1

class AnalysisRepository(private val dao: AnalysisDao) {
    suspend fun createAttempt(attempt: ClimbAttemptEntity): Long = dao.insertAttempt(attempt)

    suspend fun getAttempt(attemptId: Long): ClimbAttemptEntity? = dao.getAttempt(attemptId)

    fun observeAttempt(attemptId: Long): Flow<ClimbAttemptEntity?> = dao.observeAttempt(attemptId)

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
    ) {
        val reliableFraction = if (frames.isEmpty()) 0f else frames.count { it.isReliable }.toFloat() / frames.size
        dao.updateAnalysis(
            analysis.copy(
                status = AnalysisStatus.COMPLETE,
                confidence = reliableFraction,
                videoDurationMs = videoDurationMs,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                poseFramesJson = frames.toJson(),
            ),
        )
    }
}
