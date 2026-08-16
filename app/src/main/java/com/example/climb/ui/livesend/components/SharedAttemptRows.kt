package com.example.climb.ui.livesend.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.SharedAttemptEntity
import com.example.climb.ui.livesend.SharedAttemptRow

/**
 * Builds one [SharedAttemptRow] per [SharedAttemptEntity] with its own live like count/state —
 * each item is wrapped in [key] since [ClubRepository.observeLikesForSharedAttempt]'s collection
 * is itself a composable call made inside this loop, and the list's size/order can change as
 * Firestore pushes updates; without a stable key, Compose's slot table would mismatch likes to the
 * wrong item when one is added, removed, or reordered. Shared by RouteDetail's per-route feed and
 * the club-wide Social feed so both build rows the exact same way.
 */
@Composable
fun rememberSharedAttemptRows(
    clubRepository: ClubRepository,
    sharedAttempts: List<SharedAttemptEntity>,
    currentUid: String,
): List<SharedAttemptRow> = sharedAttempts.map { shared ->
    key(shared.id) {
        val likes by clubRepository.observeLikesForSharedAttempt(shared.id).collectAsStateWithLifecycle(initialValue = emptyList())
        SharedAttemptRow(
            id = shared.id,
            userDisplayName = shared.userDisplayName,
            videoUrl = shared.videoUrl,
            completed = shared.completed,
            flash = shared.flash,
            likeCount = likes.size,
            likedByViewer = likes.any { it.userId == currentUid },
            routeName = shared.routeName,
        )
    }
}
