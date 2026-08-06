package com.example.climb.sharing

import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SHARED_CLIMBS = "sharedClimbs"

/**
 * Reads a friend's shared climbs. Firestore security rules (`firestore.rules`'s `sharedClimbs`
 * block) — not this class — decide which documents the query actually returns: a Private climb,
 * or a Friends-only one from someone who isn't actually an accepted friend, is never present in
 * the result at all, so there's nothing for the UI to accidentally leak.
 */
class FriendClimbsRepository(private val firestore: FirebaseFirestore) {
    fun observeSharedClimbs(friendUid: String): Flow<List<SharedClimb>> = callbackFlow {
        val registration = firestore.collection(SHARED_CLIMBS)
            .whereEqualTo("ownerUid", friendUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toSharedClimb() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toSharedClimb(): SharedClimb? {
    val ownerUid = getString("ownerUid") ?: return null
    val visibility = getString("visibility")?.let { runCatching { Visibility.valueOf(it) }.getOrNull() } ?: return null
    val outcome = getString("outcome")?.let { runCatching { ClimbOutcome.valueOf(it) }.getOrNull() } ?: return null
    val routeColorHex = getLong("routeColorHex")
    val routeColor = RouteColor.entries.firstOrNull { it.hex == routeColorHex } ?: RouteColor.WHITE
    val videoStoragePath = getString("videoStoragePath") ?: return null
    return SharedClimb(
        id = id,
        ownerUid = ownerUid,
        ownerUsername = getString("ownerUsername").orEmpty(),
        vGrade = getLong("vGrade")?.toInt(),
        routeColor = routeColor,
        outcome = outcome,
        notes = getString("notes").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        durationMs = getLong("durationMs") ?: 0L,
        visibility = visibility,
        videoStoragePath = videoStoragePath,
    )
}
