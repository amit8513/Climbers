package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.PerformanceResult
import com.example.climb.coaching.CoachingTip
import com.example.climb.pose.PoseFrame
import kotlinx.coroutines.flow.Flow

/** Bumped whenever the pose model or the metrics/event algorithms change meaningfully, so old
 * stored analyses can be told apart from ones produced by a newer pipeline. */
const val CURRENT_MODEL_VERSION = "pose_landmarker_lite_v1"
const val CURRENT_ALGORITHM_VERSION = 4

class AnalysisRepository(private val dao: AnalysisDao) {
    suspend fun createAttempt(attempt: ClimbAttemptEntity): Long = dao.insertAttempt(attempt)

    suspend fun getAttempt(attemptId: Long): ClimbAttemptEntity? = dao.getAttempt(attemptId)

    fun observeAttempt(attemptId: Long): Flow<ClimbAttemptEntity?> = dao.observeAttempt(attemptId)

    fun observeLatestAttemptForSourceClimb(sourceClimbId: Long): Flow<ClimbAttemptEntity?> =
        dao.observeLatestAttemptForSourceClimb(sourceClimbId)

    /** "My club videos" — always local and always just the caller's own rows, so unlike the rest
     * of the Clubs feature this never needed to move to Firestore. */
    fun observeClubAttempts(userId: String, organizationId: Long): Flow<List<ClimbAttemptEntity>> =
        dao.observeAttemptsForUserAndOrganization(userId, organizationId)

    fun observeLatestAnalysis(attemptId: Long): Flow<AnalysisStatusSummary?> = dao.observeLatestAnalysis(attemptId)

    /**
     * Real completion durations (`climbEndMs - climbStartMs`) for whichever of [attemptIds] have a
     * COMPLETE analysis in this device's local DB, keyed by attemptId — used to resolve a per-route
     * leaderboard's real times (see [com.example.climb.clubs.RouteCompletionEntity.attemptId]'s doc
     * comment). Attempt ids from *other* users' completions simply aren't present in this local
     * table and are silently absent from the returned map, not fabricated as zero. Bounded, one-shot
     * (not a Flow), so this is safe to call with a short caller-supplied id list even though the
     * underlying query isn't the same lightweight shape as [observeLatestAnalysis] — see
     * [AnalysisTimingSummary]'s doc comment.
     */
    suspend fun getCompletedDurationsForAttempts(attemptIds: List<Long>): Map<Long, Long> {
        if (attemptIds.isEmpty()) return emptyMap()
        return dao.getTimingsForAttempts(attemptIds)
            .filter { it.status == AnalysisStatus.COMPLETE && it.climbStartMs != null && it.climbEndMs != null }
            // Latest analysis per attempt wins (an attempt could in principle be re-analyzed) —
            // query is already ORDER BY createdAt DESC, so the first match per attemptId is newest.
            .distinctBy { it.attemptId }
            .associate { it.attemptId to (it.climbEndMs!! - it.climbStartMs!!) }
    }

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
        phases: List<ClimbPhase>,
        performanceResult: PerformanceResult,
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
                phasesJson = phases.toJson(),
                categoryScoresJson = performanceResult.categoryScores.toJson(),
                overallScore = performanceResult.overallScore,
                overallConfidence = performanceResult.overallConfidence,
                scoringConfigVersion = performanceResult.scoringConfig.version,
            ),
        )
    }

    /**
     * The user's own previous attempt on the same route (matched by route name + user, not
     * [ClimbAttemptEntity.sourceClimbId] — that field links back to a pre-existing logged climb
     * video, not a general "same route" grouping), with its most recent completed analysis.
     * Returns null when there's no route name to match on, no earlier attempt, or that earlier
     * attempt's analysis never completed — a personal baseline this attempt can be compared
     * against is a bonus, not something to fabricate from partial data.
     */
    suspend fun getPreviousCompletedAnalysisForRoute(attempt: ClimbAttemptEntity): ClimbAnalysisEntity? {
        val routeName = attempt.routeName?.takeIf { it.isNotBlank() } ?: return null
        val previousAttempt = dao.getPreviousAttemptForRoute(attempt.userId, routeName, attempt.id, attempt.createdAt) ?: return null
        val previousAnalysis = dao.getLatestAnalysis(previousAttempt.id) ?: return null
        return previousAnalysis.takeIf { it.status == AnalysisStatus.COMPLETE }
    }
}
