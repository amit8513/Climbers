package com.example.climb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "climbs")
data class ClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoPath: String,
    val createdAt: Long,
    val durationMs: Long,
    val vGrade: Int?,
    val routeColor: RouteColor,
    val outcome: ClimbOutcome,
    val notes: String,
)
