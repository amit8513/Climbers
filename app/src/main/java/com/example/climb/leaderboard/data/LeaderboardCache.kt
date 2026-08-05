package com.example.climb.leaderboard.data

import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardResult

/** In-memory cache keyed by category+period, so switching tabs/periods that were already loaded
 * doesn't refetch. Pull-to-refresh bypasses reads and calls [put] after a forced recompute. */
class LeaderboardCache {
    private data class Key(val category: LeaderboardCategory, val periodId: String)

    private val entries = mutableMapOf<Key, Pair<LeaderboardResult, Long>>()

    fun get(category: LeaderboardCategory, periodId: String): Pair<LeaderboardResult, Long>? = entries[Key(category, periodId)]

    fun put(category: LeaderboardCategory, periodId: String, result: LeaderboardResult, fetchedAtMs: Long) {
        entries[Key(category, periodId)] = result to fetchedAtMs
    }
}
