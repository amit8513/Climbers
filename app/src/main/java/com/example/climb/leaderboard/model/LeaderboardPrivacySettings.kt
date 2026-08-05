package com.example.climb.leaderboard.model

import com.example.climb.analysis.Visibility

data class LeaderboardPrivacySettings(
    val participateInLeaderboard: Boolean,
    val allowFriendsToViewStats: Boolean,
    val defaultVideoVisibility: Visibility,
    val selectedViewerIds: Set<String> = emptySet(),
)
