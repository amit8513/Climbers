package com.example.climb.analysis

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "climb_attempts")
data class ClimbAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val videoPath: String,
    val createdAt: Long,
    val durationMs: Long,
    val vGrade: Int?,
    val wallType: WallType,
    val attemptNumber: Int,
    val completed: Boolean,
    val flash: Boolean,
    val routeName: String?,
    val gymName: String?,
    val notes: String,
    val visibility: Visibility,
)
