package com.example.climb.leaderboard.data

import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardPeriod
import com.example.climb.leaderboard.model.LeaderboardResult

interface LeaderboardRepository {
    suspend fun getLeaderboard(viewerUserId: String, category: LeaderboardCategory, period: LeaderboardPeriod): LeaderboardResult

    suspend fun refreshLeaderboard(viewerUserId: String, category: LeaderboardCategory, period: LeaderboardPeriod): LeaderboardResult

    /** Null until [getLeaderboard]/[refreshLeaderboard] has populated this category+period at least once. */
    fun lastUpdatedAt(category: LeaderboardCategory, period: LeaderboardPeriod): Long?
}
