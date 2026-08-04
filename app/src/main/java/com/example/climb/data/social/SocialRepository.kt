package com.example.climb.data.social

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

class UsernameTakenException : Exception("That username is already taken")
class FriendRequestAlreadyPendingException : Exception("A friend request between these accounts is already pending")

private const val USERS = "users"
private const val USERNAMES = "usernames"
private const val FRIEND_REQUESTS = "friendRequests"
private const val FRIENDS = "friends"

class SocialRepository(private val firestore: FirebaseFirestore) {

    suspend fun createProfile(uid: String, username: String): Result<Unit> = runCatching {
        val usernameLower = username.lowercase(Locale.US)
        val usernameRef = firestore.collection(USERNAMES).document(usernameLower)
        val userRef = firestore.collection(USERS).document(uid)
        firestore.runTransaction { transaction ->
            if (transaction.get(usernameRef).exists()) throw UsernameTakenException()
            transaction.set(usernameRef, mapOf("uid" to uid))
            transaction.set(
                userRef,
                mapOf(
                    "username" to username,
                    "usernameLower" to usernameLower,
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
    }

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUserProfile(uid))
            }
        awaitClose { registration.remove() }
    }

    suspend fun findUserByUsername(username: String): UserProfile? {
        val usernameLower = username.trim().lowercase(Locale.US)
        val uid = firestore.collection(USERNAMES).document(usernameLower).get().await()
            .getString("uid") ?: return null
        return firestore.collection(USERS).document(uid).get().await().toUserProfile(uid)
    }

    suspend fun sendFriendRequest(fromUid: String, fromUsername: String, toProfile: UserProfile): Result<Unit> = runCatching {
        require(fromUid != toProfile.uid) { "You can't send a friend request to yourself" }
        val requestRef = firestore.collection(FRIEND_REQUESTS).document("${fromUid}_${toProfile.uid}")
        firestore.runTransaction { transaction ->
            val existing = transaction.get(requestRef)
            if (existing.exists() && existing.getString("status") == FriendRequestStatus.PENDING.name) {
                throw FriendRequestAlreadyPendingException()
            }
            transaction.set(
                requestRef,
                mapOf(
                    "fromUid" to fromUid,
                    "fromUsername" to fromUsername,
                    "toUid" to toProfile.uid,
                    "toUsername" to toProfile.username,
                    "status" to FriendRequestStatus.PENDING.name,
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
    }

    fun observeIncomingRequests(uid: String): Flow<List<FriendRequest>> = observeRequests(
        firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name),
    )

    fun observeOutgoingRequests(uid: String): Flow<List<FriendRequest>> = observeRequests(
        firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("fromUid", uid)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name),
    )

    suspend fun acceptFriendRequest(request: FriendRequest): Result<Unit> = runCatching {
        // The friends-subcollection create rule checks the request's status via get(), which
        // isn't guaranteed to see writes from earlier in the same batch — so the status update
        // must fully commit before the friend docs are written, not just be queued alongside them.
        firestore.collection(FRIEND_REQUESTS).document(request.id)
            .update("status", FriendRequestStatus.ACCEPTED.name).await()

        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.set(
            firestore.collection(USERS).document(request.fromUid).collection(FRIENDS).document(request.toUid),
            mapOf("username" to request.toUsername, "since" to now),
        )
        batch.set(
            firestore.collection(USERS).document(request.toUid).collection(FRIENDS).document(request.fromUid),
            mapOf("username" to request.fromUsername, "since" to now),
        )
        batch.commit().await()
    }

    suspend fun declineFriendRequest(requestId: String): Result<Unit> = runCatching {
        firestore.collection(FRIEND_REQUESTS).document(requestId)
            .update("status", FriendRequestStatus.DECLINED.name).await()
    }

    fun observeFriends(uid: String): Flow<List<Friend>> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid).collection(FRIENDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toFriend() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    private fun observeRequests(query: Query): Flow<List<FriendRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toFriendRequest() }.orEmpty())
        }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile? {
    if (!exists()) return null
    val username = getString("username") ?: return null
    return UserProfile(uid = uid, username = username)
}

private fun DocumentSnapshot.toFriend(): Friend? {
    val username = getString("username") ?: return null
    return Friend(uid = id, username = username, since = getLong("since") ?: 0L)
}

private fun DocumentSnapshot.toFriendRequest(): FriendRequest? {
    val status = getString("status")?.let { raw -> runCatching { FriendRequestStatus.valueOf(raw) }.getOrNull() } ?: return null
    return FriendRequest(
        id = id,
        fromUid = getString("fromUid") ?: return null,
        fromUsername = getString("fromUsername") ?: return null,
        toUid = getString("toUid") ?: return null,
        toUsername = getString("toUsername") ?: return null,
        status = status,
        createdAt = getLong("createdAt") ?: 0L,
    )
}
