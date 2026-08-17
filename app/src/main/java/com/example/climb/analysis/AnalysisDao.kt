package com.example.climb.analysis

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Lightweight projection for [AnalysisDao.observeLatestAnalysis] — deliberately excludes
 * [ClimbAnalysisEntity.poseFramesJson]/`metricsJson`/etc. A long/detailed climb's serialized pose
 * frames can grow past SQLite's CursorWindow row-size limit
 * (`SQLiteBlobTooBigException: Row too big to fit into CursorWindow`), which a plain `SELECT *`
 * hits even though this reactive status row (used by `DetailScreen`'s pose-analysis section) only
 * ever reads [id]/[status]/[failureReason] — real reproduced crash, not a hypothetical one.
 */
data class AnalysisStatusSummary(val id: Long, val status: AnalysisStatus, val failureReason: String?)

/** Lightweight projection for [AnalysisDao.getTimingsForAttempts] — same reasoning as
 * [AnalysisStatusSummary]: a per-route leaderboard resolving real completion durations only needs
 * [attemptId]/[status]/[climbStartMs]/[climbEndMs]/[createdAt] (to pick the latest analysis per
 * attempt), never the huge [ClimbAnalysisEntity.poseFramesJson] blob a `SELECT *` would also pull
 * in and risk the same CursorWindow row-size crash. */
data class AnalysisTimingSummary(
    val attemptId: Long,
    val status: AnalysisStatus,
    val climbStartMs: Long?,
    val climbEndMs: Long?,
    val createdAt: Long,
)

@Dao
interface AnalysisDao {
    @Insert
    suspend fun insertAttempt(attempt: ClimbAttemptEntity): Long

    @Insert
    suspend fun insertAnalysis(analysis: ClimbAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(analysis: ClimbAnalysisEntity)

    @Query("SELECT * FROM climb_attempts WHERE id = :id")
    fun observeAttempt(id: Long): Flow<ClimbAttemptEntity?>

    @Query("SELECT * FROM climb_attempts WHERE id = :id")
    suspend fun getAttempt(id: Long): ClimbAttemptEntity?

    @Query("SELECT * FROM climb_analyses WHERE id = :id")
    fun observeAnalysis(id: Long): Flow<ClimbAnalysisEntity?>

    @Query("SELECT * FROM climb_analyses WHERE id = :id")
    suspend fun getAnalysis(id: Long): ClimbAnalysisEntity?

    @Query("SELECT * FROM climb_analyses WHERE attemptId = :attemptId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestAnalysis(attemptId: Long): ClimbAnalysisEntity?

    @Query("SELECT * FROM climb_attempts WHERE sourceClimbId = :sourceClimbId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestAttemptForSourceClimb(sourceClimbId: Long): Flow<ClimbAttemptEntity?>

    @Query("SELECT id, status, failureReason FROM climb_analyses WHERE attemptId = :attemptId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestAnalysis(attemptId: Long): Flow<AnalysisStatusSummary?>

    @Query(
        "SELECT * FROM climb_attempts WHERE userId = :userId AND routeName = :routeName " +
            "AND id != :excludeAttemptId AND createdAt < :beforeCreatedAt ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun getPreviousAttemptForRoute(userId: String, routeName: String, excludeAttemptId: Long, beforeCreatedAt: Long): ClimbAttemptEntity?

    @Query("SELECT * FROM climb_attempts WHERE userId = :userId AND organizationId = :organizationId ORDER BY createdAt DESC")
    fun observeAttemptsForUserAndOrganization(userId: String, organizationId: Long): Flow<List<ClimbAttemptEntity>>

    /** One-shot, bounded to a caller-supplied list of specific attempt ids (never a long-lived
     * reactive Flow over the whole table) — see [AnalysisTimingSummary]'s doc comment for why this
     * projection excludes the pose-frame blob. Used to resolve a per-route leaderboard's real
     * completion durations for whichever attempts happen to exist in this device's local DB. */
    @Query("SELECT attemptId, status, climbStartMs, climbEndMs, createdAt FROM climb_analyses WHERE attemptId IN (:attemptIds) ORDER BY createdAt DESC")
    suspend fun getTimingsForAttempts(attemptIds: List<Long>): List<AnalysisTimingSummary>
}
