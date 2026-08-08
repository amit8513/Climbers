package com.example.climb.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.climb.analysis.Visibility

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
    /** Whether friends/anyone can see this climb's video and details. Defaults to private —
     * sharing is opt-in per climb, never on by default. Only PRIVATE/FRIENDS_ONLY/PUBLIC are
     * wired up for cloud sync today; SELECTED_FRIENDS is a documented follow-up. */
    val visibility: Visibility = Visibility.PRIVATE,
    /** Optional link to a real gym route (see `com.example.climb.clubs`) — null for every
     * existing climb and for any normal/outdoor user who never selects a gym route. */
    val organizationId: Long? = null,
    val venueId: Long? = null,
    val zoneId: Long? = null,
    val routeId: Long? = null,
    val routeVersionId: Long? = null,
)
