package com.example.climb.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.climb.analysis.Visibility
import com.example.climb.clubs.AttemptSource

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
    /** A successful "Calibrate on this hold" result (see `TargetColorModelJson.kt`), so reopening
     * this climb restores it instead of requiring the user to tap-to-calibrate again every time.
     * Null for every climb that's never been calibrated. */
    val calibratedColorModelJson: String? = null,
    /** Where this climb's video actually came from (see `AttemptSource`'s own doc comment for
     * exact semantics). Null means "logged before source tracking existed, or genuinely unknown
     * provenance" — kept nullable here (rather than defaulting to the LEGACY_UNKNOWN enum value)
     * so existing rows deserialize with a real, honest "we have no idea" absence rather than an
     * enum value implying a migration wrote something. */
    val attemptSource: AttemptSource? = null,
)
