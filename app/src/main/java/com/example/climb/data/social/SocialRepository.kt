package com.example.climb.data.social

import android.net.Uri
import android.util.Log
import com.example.climb.analysis.Visibility
import com.example.climb.leaderboard.model.LeaderboardPrivacySettings
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
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
private const val LEADERBOARD_PRIVACY_FIELD = "leaderboardPrivacy"
private const val TAG = "SocialRepository"

/** Kept short and freeform on purpose — a one-line "about me", not a full profile essay. Enforced
 * both here (defensive truncation on write) and in SettingsScreen's own input (a live counter so
 * the limit is never a surprise at save time). */
const val MAX_BIO_LENGTH = 160

private fun profilePicturePath(uid: String) = "profile_pictures/$uid/photo.jpg"

/** Reasonable default for any user who hasn't touched their own leaderboard privacy settings yet
 * (participating, stats visible to friends, friends-only video) — same values
 * `LocalLeaderboardRepository` used to hardcode for every friend before this settings storage
 * existed (see LEADERBOARD.md). Used both as the fallback when a profile fails to load and as the
 * value returned for a user whose doc simply has no `leaderboardPrivacy` field yet. */
val DEFAULT_LEADERBOARD_PRIVACY_SETTINGS = LeaderboardPrivacySettings(
    participateInLeaderboard = true,
    allowFriendsToViewStats = true,
    defaultVideoVisibility = Visibility.FRIENDS_ONLY,
)

class SocialRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {

    /** Uploads straight from the picked content:// [imageUri] — Firebase Storage's `putFile`
     * reads through [android.content.ContentResolver] internally, so no local copy is needed
     * first (unlike the video-import path, which already has a local file to work from). */
    suspend fun uploadProfilePhoto(uid: String, imageUri: Uri, contentType: String?): Result<String> = runCatching {
        val ref = storage.reference.child(profilePicturePath(uid))
        val metadata = StorageMetadata.Builder().apply {
            if (contentType != null) setContentType(contentType)
        }.build()
        ref.putFile(imageUri, metadata).await()
        ref.downloadUrl.await().toString()
    }

    suspend fun createProfile(uid: String, username: String, photoUrl: String? = null): Result<Unit> = runCatching {
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
                    "photoUrl" to photoUrl,
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
    }

    /** Releases the old username reservation and reserves the new one in the same transaction,
     * so a failed/aborted change can never leave both reserved (locking the user out of their
     * own old name) or the new one unreserved (letting someone else grab it mid-change). */
    suspend fun updateUsername(uid: String, oldUsername: String, newUsername: String): Result<Unit> = runCatching {
        val oldUsernameLower = oldUsername.lowercase(Locale.US)
        val newUsernameLower = newUsername.lowercase(Locale.US)
        if (oldUsernameLower == newUsernameLower) {
            firestore.collection(USERS).document(uid).update("username", newUsername).await()
            return@runCatching
        }
        val oldUsernameRef = firestore.collection(USERNAMES).document(oldUsernameLower)
        val newUsernameRef = firestore.collection(USERNAMES).document(newUsernameLower)
        val userRef = firestore.collection(USERS).document(uid)
        firestore.runTransaction { transaction ->
            if (transaction.get(newUsernameRef).exists()) throw UsernameTakenException()
            transaction.delete(oldUsernameRef)
            transaction.set(newUsernameRef, mapOf("uid" to uid))
            transaction.update(userRef, mapOf("username" to newUsername, "usernameLower" to newUsernameLower))
        }.await()
    }

    suspend fun updateProfilePhoto(uid: String, photoUrl: String?): Result<Unit> = runCatching {
        firestore.collection(USERS).document(uid).update("photoUrl", photoUrl).await()
    }

    /** Blank clears it back to "no bio" (stored as `null`, not an empty string) rather than
     * leaving a visible-but-empty field on the profile page. */
    suspend fun updateBio(uid: String, bio: String): Result<Unit> = runCatching {
        firestore.collection(USERS).document(uid).update("bio", bio.trim().take(MAX_BIO_LENGTH).ifBlank { null }).await()
    }

    /** An array, not a single field — the same account can be signed in on more than one device,
     * and each device's token needs its own push. [arrayUnion] is a no-op (not a duplicate) if this
     * exact token is already stored, so this is safe to call on every app start/token refresh, not
     * just the first time. Never removes a stale token itself (see [ClimbMessagingService]'s doc
     * comment on why this project accepts that trade-off rather than pruning them). */
    suspend fun updateFcmToken(uid: String, token: String): Result<Unit> = runCatching {
        firestore.collection(USERS).document(uid).update("fcmTokens", FieldValue.arrayUnion(token)).await()
    }

    /** Stored as a field on the user's own profile doc (same doc as username/photoUrl), not a new
     * collection — reuses `users/{uid}`'s existing rule (owner-only write, any signed-in read),
     * matching how every other piece of profile data already syncs. */
    suspend fun updateLeaderboardPrivacySettings(uid: String, settings: LeaderboardPrivacySettings): Result<Unit> = runCatching {
        firestore.collection(USERS).document(uid).update(LEADERBOARD_PRIVACY_FIELD, settings.toFirestoreMap()).await()
    }

    /** One-shot read for a single user (e.g. resolving one friend's settings while building a
     * leaderboard entry) — falls back to [DEFAULT_LEADERBOARD_PRIVACY_SETTINGS] on any failure or
     * missing field, same fallback shape as [getProfile], never throws. */
    suspend fun getLeaderboardPrivacySettings(uid: String): LeaderboardPrivacySettings =
        runCatching { firestore.collection(USERS).document(uid).get().await().toLeaderboardPrivacySettings() }
            .getOrNull() ?: DEFAULT_LEADERBOARD_PRIVACY_SETTINGS

    /** Live view of the current user's own settings, for the Settings screen to reflect edits
     * made on another signed-in device. */
    fun observeLeaderboardPrivacySettings(uid: String): Flow<LeaderboardPrivacySettings> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Never let a Firestore hiccup (permission edge case, missing index, a blip
                    // offline) crash the app — an uncaught close(error) here propagates straight
                    // through collectAsStateWithLifecycle and kills the whole process. Degrade to
                    // the same reasonable default a missing field already gets.
                    Log.w(TAG, "Failed to observe leaderboard privacy settings for $uid", error)
                    trySend(DEFAULT_LEADERBOARD_PRIVACY_SETTINGS)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toLeaderboardPrivacySettings() ?: DEFAULT_LEADERBOARD_PRIVACY_SETTINGS)
            }
        awaitClose { registration.remove() }
    }

    // Used for one-shot batch lookups (e.g. resolving a list of club member uids to display
    // names) where a single failed/missing profile must never take the whole list down — falls
    // back to null (callers show the uid) rather than throwing.
    suspend fun getProfile(uid: String): UserProfile? =
        runCatching { firestore.collection(USERS).document(uid).get().await().toUserProfile(uid) }.getOrNull()

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // See observeLeaderboardPrivacySettings's doc comment — never crash on this.
                    Log.w(TAG, "Failed to observe profile for $uid", error)
                    trySend(null)
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
        // Keeps UserProfile.friendCount in sync on both sides — see its doc comment for why this
        // denormalized field exists at all (rules don't let one user read another's /friends
        // subcollection directly). The write to the OTHER party's own doc (whichever of
        // fromUid/toUid isn't request.auth.uid) is only legal because of firestore.rules' matching
        // "friendCount-only, accepted-request-gated" exception on /users/{uid} — this must stay a
        // single-field FieldValue.increment(1) update, never a broader write, or it'll violate that
        // rule and the whole batch (including the two friend docs above) will be rejected.
        batch.update(firestore.collection(USERS).document(request.fromUid), "friendCount", FieldValue.increment(1))
        batch.update(firestore.collection(USERS).document(request.toUid), "friendCount", FieldValue.increment(1))
        batch.commit().await()
    }

    suspend fun declineFriendRequest(requestId: String): Result<Unit> = runCatching {
        firestore.collection(FRIEND_REQUESTS).document(requestId)
            .update("status", FriendRequestStatus.DECLINED.name).await()
    }

    // Rules only allow the doc owner to read their own /friends subcollection (see
    // firestore.rules) — calling this with any uid other than the signed-in user is always
    // denied. Degrades to an empty list rather than crashing (see observeLeaderboardPrivacySettings's
    // doc comment); callers that need a REMOTE user's friend count must read
    // [UserProfile.friendCount] instead (denormalized, readable by anyone signed in).
    fun observeFriends(uid: String): Flow<List<Friend>> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid).collection(FRIENDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Failed to observe friends for $uid", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toFriend() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    private fun observeRequests(query: Query): Flow<List<FriendRequest>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Failed to observe friend requests", error)
                trySend(emptyList())
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
    return UserProfile(
        uid = uid,
        username = username,
        photoUrl = getString("photoUrl"),
        bio = getString("bio"),
        friendCount = getLong("friendCount")?.toInt() ?: 0,
    )
}

private fun DocumentSnapshot.toFriend(): Friend? {
    val username = getString("username") ?: return null
    return Friend(uid = id, username = username, since = getLong("since") ?: 0L)
}

private fun LeaderboardPrivacySettings.toFirestoreMap(): Map<String, Any> = mapOf(
    "participating" to participateInLeaderboard,
    "statsVisibleToFriends" to allowFriendsToViewStats,
    "videoVisibility" to defaultVideoVisibility.name,
    "selectedViewerIds" to selectedViewerIds.toList(),
)

/** Returns [DEFAULT_LEADERBOARD_PRIVACY_SETTINGS] both when the doc doesn't exist yet and when it
 * exists but has never had the [LEADERBOARD_PRIVACY_FIELD] map written to it — a brand-new user
 * and a pre-existing user who never opened the leaderboard privacy settings should both see the
 * same reasonable default, not a crash or an empty/zeroed-out settings object. */
private fun DocumentSnapshot.toLeaderboardPrivacySettings(): LeaderboardPrivacySettings {
    val raw = get(LEADERBOARD_PRIVACY_FIELD) as? Map<*, *> ?: return DEFAULT_LEADERBOARD_PRIVACY_SETTINGS
    val visibility = (raw["videoVisibility"] as? String)
        ?.let { runCatching { Visibility.valueOf(it) }.getOrNull() }
        ?: Visibility.FRIENDS_ONLY
    return LeaderboardPrivacySettings(
        participateInLeaderboard = raw["participating"] as? Boolean ?: true,
        allowFriendsToViewStats = raw["statsVisibleToFriends"] as? Boolean ?: true,
        defaultVideoVisibility = visibility,
        selectedViewerIds = (raw["selectedViewerIds"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
    )
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
