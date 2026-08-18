package com.example.climb.data.social

data class UserProfile(
    val uid: String,
    val username: String,
    val photoUrl: String? = null,
    // Optional, short freeform "about me" — never required, shown on the user's public profile
    // page (see LiveSendUserProfileScreen) alongside their photo/friend count.
    val bio: String? = null,
    // Denormalized, kept in sync by SocialRepository.acceptFriendRequest — NOT derived from a
    // live read of this user's own /friends subcollection, since Firestore rules only let the
    // OWNING user read that (see firestore.rules). This field is what LiveSendUserProfileScreen
    // shows on someone ELSE's profile; observeFriends(uid) is still used (and safe) for your OWN.
    val friendCount: Int = 0,
)
