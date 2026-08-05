package com.example.climb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "climbs")
data class ClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val videoPath: String,
    val createdAt: Long,
    val durationMs: Long,
    val vGrade: Int?,
    val routeColor: RouteColor,
    val outcome: ClimbOutcome,
    val notes: String,
    /** Last-applied color-isolation tuning for this climb's playback; null means "use the
     * effect's own defaults" rather than duplicating those defaults here. */
    val hueOffsetDegrees: Float? = null,
    val hueToleranceDegrees: Float? = null,
)
