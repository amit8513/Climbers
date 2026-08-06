package com.example.climb.sharing

import android.net.Uri
import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File

private const val SHARED_CLIMBS = "sharedClimbs"

/**
 * Pushes one local [ClimbEntity] to Firestore + Storage when it's shared, and pulls it back out
 * when it isn't — this is the "trusted data layer" doing real enforcement, not just the UI: a
 * climb that's Private is never uploaded, and one that becomes Private again is actively deleted
 * from the cloud rather than left behind with a rule hoping to hide it.
 *
 * Firestore/Storage security rules (see `firestore.rules` / `storage.rules` at the repo root)
 * are the actual authority a friend's client is checked against when reading — this repository
 * writing "safe" fields is a courtesy for well-behaved clients, not the enforcement boundary.
 */
class ClimbSyncRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    suspend fun sync(climb: ClimbEntity, ownerUsername: String) {
        val docRef = firestore.collection(SHARED_CLIMBS).document(sharedClimbDocId(climb.userId, climb.id))
        val videoRef = storage.reference.child(sharedClimbVideoPath(climb.userId, climb.id))

        if (climb.visibility == Visibility.PRIVATE) {
            runCatching { docRef.delete().await() }
            runCatching { videoRef.delete().await() }
            return
        }

        val file = File(climb.videoPath)
        if (file.exists()) {
            // Visibility is stored as custom metadata on the object itself so `storage.rules`
            // can check it directly, rather than cross-calling into Firestore from Storage rules
            // — that cross-service path proved unreliable in practice (every read was denied,
            // even for Public videos, where no friendship check was even involved).
            val metadata = StorageMetadata.Builder()
                .setCustomMetadata("visibility", climb.visibility.name)
                .build()
            videoRef.putFile(Uri.fromFile(file), metadata).await()
        }

        docRef.set(
            mapOf(
                "ownerUid" to climb.userId,
                "ownerUsername" to ownerUsername,
                "vGrade" to climb.vGrade,
                "routeColorHex" to climb.routeColor.hex,
                "outcome" to climb.outcome.name,
                "notes" to climb.notes,
                "createdAt" to climb.createdAt,
                "durationMs" to climb.durationMs,
                "visibility" to climb.visibility.name,
                "videoStoragePath" to sharedClimbVideoPath(climb.userId, climb.id),
            ),
        ).await()
    }

    /** Used when the local row is already gone (deleted climb) — only the ids survive, so the
     * cloud copy is located purely from those rather than a live [ClimbEntity]. */
    suspend fun deleteSyncedClimb(ownerUid: String, climbId: Long) {
        runCatching { firestore.collection(SHARED_CLIMBS).document(sharedClimbDocId(ownerUid, climbId)).delete().await() }
        runCatching { storage.reference.child(sharedClimbVideoPath(ownerUid, climbId)).delete().await() }
    }
}
