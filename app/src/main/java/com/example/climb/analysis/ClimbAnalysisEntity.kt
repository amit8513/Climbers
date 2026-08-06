package com.example.climb.analysis

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [poseFramesJson] holds the compact serialized [com.example.climb.pose.PoseFrame] list (see
 * `PoseFrameJson.kt`) rather than a separate per-frame table — simplest option that keeps
 * playback-scrubbing lookups (read once, binary-search in memory) fast enough for a single
 * climb's worth of frames, without Room ever loading rows it doesn't need.
 */
@Entity(tableName = "climb_analyses")
data class ClimbAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: Long,
    val modelVersion: String,
    val algorithmVersion: Int,
    val createdAt: Long,
    val status: AnalysisStatus,
    val confidence: Float?,
    val climbStartMs: Long?,
    val climbEndMs: Long?,
    val videoDurationMs: Long?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val poseFramesJson: String,
    val metricsJson: String = "",
    val eventsJson: String = "",
    val tipsJson: String = "",
    val phasesJson: String = "",
    val categoryScoresJson: String = "",
    val overallScore: Int? = null,
    val overallConfidence: Float? = null,
    val scoringConfigVersion: Int? = null,
    val failureReason: String?,
)
