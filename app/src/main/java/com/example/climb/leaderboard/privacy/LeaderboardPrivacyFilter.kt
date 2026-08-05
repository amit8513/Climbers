package com.example.climb.leaderboard.privacy

import com.example.climb.analysis.Visibility
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.LeaderboardPrivacySettings

/**
 * Enforces every leaderboard privacy rule in one place — the "trusted data layer" the product
 * spec allows to stand in for a real backend until one exists. Never rely on the UI alone to
 * hide a field: this is called from the repository, before a [LeaderboardEntry] ever reaches
 * Compose code, so an unauthorized video URL/thumbnail/title is never even constructed here in
 * the first place, let alone rendered.
 */
object LeaderboardPrivacyFilter {

    /** Returns null when this user must not appear in [viewerUid]'s leaderboard at all —
     * participation disabled, not friends, or friends but stats sharing is off. Otherwise
     * returns a copy with video fields reduced to a safe count/booleans. */
    fun filterForViewer(
        entry: LeaderboardEntry,
        viewerUid: String,
        ownerSettings: LeaderboardPrivacySettings,
        areFriends: Boolean,
        totalOwnedVideoCount: Int,
    ): LeaderboardEntry? {
        if (entry.userId == viewerUid) {
            return entry.copy(sharedVideoCount = totalOwnedVideoCount, hasViewableVideo = totalOwnedVideoCount > 0, hasPrivateVideo = false)
        }
        if (!ownerSettings.participateInLeaderboard) return null
        if (!areFriends) return null
        if (!ownerSettings.allowFriendsToViewStats) return null

        val canViewVideo = canViewerSeeVideo(viewerUid, ownerSettings, areFriends)
        return entry.copy(
            sharedVideoCount = if (canViewVideo) totalOwnedVideoCount else 0,
            hasViewableVideo = canViewVideo && totalOwnedVideoCount > 0,
            hasPrivateVideo = !canViewVideo && totalOwnedVideoCount > 0,
        )
    }

    private fun canViewerSeeVideo(viewerUid: String, settings: LeaderboardPrivacySettings, areFriends: Boolean): Boolean = when (settings.defaultVideoVisibility) {
        Visibility.PRIVATE -> false
        Visibility.FRIENDS_ONLY -> areFriends
        Visibility.SELECTED_FRIENDS -> viewerUid in settings.selectedViewerIds
        Visibility.PUBLIC -> true
    }
}
