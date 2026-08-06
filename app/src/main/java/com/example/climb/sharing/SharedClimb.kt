package com.example.climb.sharing

import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor

/** The cloud-facing shape of a shared climb — a friend viewing this only ever sees fields that
 * survived [com.example.climb.leaderboard.privacy] / Firestore-rule-style filtering, never a raw
 * local [com.example.climb.data.ClimbEntity]. */
data class SharedClimb(
    val id: String,
    val ownerUid: String,
    val ownerUsername: String,
    val vGrade: Int?,
    val routeColor: RouteColor,
    val outcome: ClimbOutcome,
    val notes: String,
    val createdAt: Long,
    val durationMs: Long,
    val visibility: Visibility,
    val videoStoragePath: String,
)

/** Deterministic id shared by the Firestore doc and the Storage object path, so both sides of
 * the sync (upload and delete) always agree on where a given local climb lives in the cloud —
 * derivable from just (ownerUid, climbId), without needing a live [com.example.climb.data.ClimbEntity]
 * (e.g. after the local row has already been deleted). */
fun sharedClimbDocId(ownerUid: String, climbId: Long): String = "${ownerUid}_$climbId"

fun sharedClimbVideoPath(ownerUid: String, climbId: Long): String = "climb_videos/$ownerUid/$climbId.mp4"
