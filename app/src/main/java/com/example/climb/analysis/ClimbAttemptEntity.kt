package com.example.climb.analysis

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "climb_attempts")
data class ClimbAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    /** Set when this attempt was created from an already-logged [com.example.climb.data.ClimbEntity]
     * (the "use an existing climb video" path), so its result can be linked back from that
     * climb's detail screen. Null when the video was recorded/imported fresh for analysis. */
    val sourceClimbId: Long? = null,
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
