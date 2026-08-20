package com.example.climb.analysis

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.climb.clubs.AttemptSource

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
    /** Optional link to a real gym route (see `com.example.climb.clubs`) — null for every
     * existing attempt and for any normal user who never selects a gym route. When present,
     * these enhance the analysis report with route context; the analysis pipeline itself never
     * requires them. */
    val organizationId: Long? = null,
    val venueId: Long? = null,
    val zoneId: Long? = null,
    val routeId: Long? = null,
    val routeVersionId: Long? = null,
    /** Where this attempt's video/route-link actually came from — null for every attempt logged
     * before this existed (all of which are, in effect, `PHONE_CAMERA`/`IMPORTED_VIDEO`/
     * `MANUAL_LOG` anyway, since no `WALL_CAMERA` capture pipeline exists yet). Nothing currently
     * writes a non-null value here; it exists so a real gym-camera capture flow (once built) has
     * somewhere to record its provenance without another migration. */
    val attemptSource: AttemptSource? = null,
    /** Optional link to the [com.example.climb.clubs.WallEntity]/[com.example.climb.clubs.WallCalibrationEntity]
     * this attempt's video was captured against, and to the (future) capture session that produced
     * it — all null until a real wall-camera capture pipeline exists. */
    val wallId: Long? = null,
    val wallCalibrationId: Long? = null,
    val captureSessionId: String? = null,
)
