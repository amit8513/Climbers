package com.example.climb.sharing

import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SHARED_CLIMBS = "sharedClimbs"
private const val TAG = "FriendClimbsRepository"

/**
 * Reads a friend's shared climbs. Firestore security rules (`firestore.rules`'s `sharedClimbs`
 * block) — not this class — decide which documents the query actually returns: a Private climb,
 * or a Friends-only one from someone who isn't actually an accepted friend, is never present in
 * the result at all, so there's nothing for the UI to accidentally leak.
 *
 * Firestore rules aren't a post-hoc filter — a query is rejected outright unless every field the
 * rule reads is already constrained by the query's own filters, since Firestore has to prove the
 * query is safe without evaluating each document. `visibility` is filtered here to match exactly
 * what `firestore.rules` checks, so it can prove that.
 */
class FriendClimbsRepository(private val firestore: FirebaseFirestore) {
    fun observeSharedClimbs(friendUid: String): Flow<List<SharedClimb>> = callbackFlow {
        val registration = firestore.collection(SHARED_CLIMBS)
            .whereEqualTo("ownerUid", friendUid)
            .whereIn("visibility", listOf(Visibility.FRIENDS_ONLY.name, Visibility.PUBLIC.name))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Never let a Firestore hiccup (permission edge case, missing index, a blip
                    // offline) crash the app — surface as "nothing to show" instead.
                    Log.w(TAG, "Failed to load shared climbs for $friendUid", error)
                    trySend(emptyList())
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
