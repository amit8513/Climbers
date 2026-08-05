package com.example.climb.analysis

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM climb_analyses WHERE attemptId = :attemptId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestAnalysis(attemptId: Long): Flow<ClimbAnalysisEntity?>
}
