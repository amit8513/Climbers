package com.example.climb.data.social

enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }

data class FriendRequest(
    val id: String,
    val fromUid: String,
    val fromUsername: String,
    val toUid: String,
    val toUsername: String,
    val status: FriendRequestStatus,
    val createdAt: Long,
)
