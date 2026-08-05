package com.example.climb.leaderboard

import com.example.climb.analysis.Visibility
import com.example.climb.leaderboard.model.LeaderboardPrivacySettings
import com.example.climb.leaderboard.privacy.LeaderboardPrivacyFilter
import com.example.climb.leaderboard.scoring.calculateEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LeaderboardPrivacyFilterTest {

    private val zone = ZoneId.of("UTC")

    private fun ownerEntry(uid: String) = calculateEntry(
        uid, uid, null,
        listOf(attempt("p1", grade = 4, attemptNumber = 1, completed = true, userId = uid, videoId = "video1")),
        emptyList(), zone,
    )

    @Test
    fun `a user who disabled leaderboard participation is excluded entirely`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = false, allowFriendsToViewStats = true, defaultVideoVisibility = Visibility.PUBLIC)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "viewer", settings, areFriends = true, totalOwnedVideoCount = 1)
        assertNull(filtered)
    }

    @Test
    fun `a non-friend never sees the entry even if stats sharing is on`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = true, allowFriendsToViewStats = true, defaultVideoVisibility = Visibility.PUBLIC)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "viewer", settings, areFriends = false, totalOwnedVideoCount = 1)
        assertNull(filtered)
    }

    @Test
    fun `a friend who disallowed stats sharing does not appear`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = true, allowFriendsToViewStats = false, defaultVideoVisibility = Visibility.PUBLIC)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "viewer", settings, areFriends = true, totalOwnedVideoCount = 1)
        assertNull(filtered)
    }

    @Test
    fun `friends-only video is visible to a friend`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = true, allowFriendsToViewStats = true, defaultVideoVisibility = Visibility.FRIENDS_ONLY)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "viewer", settings, areFriends = true, totalOwnedVideoCount = 2)
        assertTrue(filtered!!.hasViewableVideo)
        assertEquals(2, filtered.sharedVideoCount)
        assertFalse(filtered.hasPrivateVideo)
    }

    @Test
    fun `selected-friends video is only visible to a viewer on the allow-list`() {
        val settings = LeaderboardPrivacySettings(
            participateInLeaderboard = true, allowFriendsToViewStats = true,
            defaultVideoVisibility = Visibility.SELECTED_FRIENDS, selectedViewerIds = setOf("allowed_viewer"),
        )
        val allowed = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "allowed_viewer", settings, areFriends = true, totalOwnedVideoCount = 1)
        val notAllowed = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "other_viewer", settings, areFriends = true, totalOwnedVideoCount = 1)

        assertTrue(allowed!!.hasViewableVideo)
        assertFalse(notAllowed!!.hasViewableVideo)
        assertTrue(notAllowed.hasPrivateVideo)
        assertEquals(0, notAllowed.sharedVideoCount)
    }

    @Test
    fun `private video is never exposed to anyone but the owner`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = true, allowFriendsToViewStats = true, defaultVideoVisibility = Visibility.PRIVATE)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "viewer", settings, areFriends = true, totalOwnedVideoCount = 3)
        assertFalse(filtered!!.hasViewableVideo)
        assertTrue(filtered.hasPrivateVideo)
        assertEquals(0, filtered.sharedVideoCount)
    }

    @Test
    fun `the owner always sees their own videos regardless of visibility settings`() {
        val settings = LeaderboardPrivacySettings(participateInLeaderboard = true, allowFriendsToViewStats = true, defaultVideoVisibility = Visibility.PRIVATE)
        val filtered = LeaderboardPrivacyFilter.filterForViewer(ownerEntry("owner"), "owner", settings, areFriends = false, totalOwnedVideoCount = 3)
        assertTrue(filtered!!.hasViewableVideo)
        assertEquals(3, filtered.sharedVideoCount)
    }
}
