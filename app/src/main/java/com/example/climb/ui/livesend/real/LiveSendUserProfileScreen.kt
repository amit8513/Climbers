package com.example.climb.ui.livesend.real

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.SharedAttemptEntity
import com.example.climb.data.social.SocialRepository
import com.example.climb.ui.components.EmptyState
import com.example.climb.ui.livesend.SharedAttemptRow
import com.example.climb.ui.livesend.components.ExpandableVideoPlayer
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.rememberSharedAttemptRows
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * A club member's public profile — reached by tapping a sharer's name/avatar on a
 * [SocialSharedVideoCard] (see [LiveSendSocialScreen]). Shows their photo, friend count, an
 * "+ Add friend" action (reusing the same friends system as [com.example.climb.ui.social.FriendsScreen],
 * not a new one), which real clubs they belong to, and a gallery of every attempt video they've
 * shared publicly across those clubs.
 */
@Composable
fun LiveSendUserProfileScreen(
    currentUid: String,
    currentUsername: String,
    targetUid: String,
    socialRepository: SocialRepository,
    clubRepository: ClubRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by socialRepository.observeProfile(targetUid).collectAsStateWithLifecycle(initialValue = null)
    val myFriends by socialRepository.observeFriends(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val myOutgoing by socialRepository.observeOutgoingRequests(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val memberships by clubRepository.observeMembershipsForUser(targetUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val allOrgs by clubRepository.observeAllOrganizations().collectAsStateWithLifecycle(initialValue = emptyList())
    val orgs = remember(memberships, allOrgs) {
        val orgIds = memberships.map { it.organizationId }.toSet()
        allOrgs.filter { it.id in orgIds }
    }
    val sharedAttempts = rememberUserSharedAttempts(clubRepository = clubRepository, orgs = orgs, targetUid = targetUid)
    val galleryRows = rememberSharedAttemptRows(clubRepository, sharedAttempts, currentUid)

    val isOwnProfile = currentUid == targetUid
    val isFriend = myFriends.any { it.uid == targetUid }
    val hasPendingOutgoing = myOutgoing.any { it.toUid == targetUid }
    val scope = rememberCoroutineScope()
    var sendingRequest by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp).clickable(onClick = onBack),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveSendAvatar(
                    initial = profile?.username?.firstOrNull()?.toString() ?: "?",
                    photoUrl = profile?.photoUrl,
                    size = 72,
                    ringed = true,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile?.username ?: "…",
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    val friendCount = profile?.friendCount ?: 0
                    Text(
                        text = "$friendCount friend${if (friendCount == 1) "" else "s"}",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    text = bio,
                    color = ClimbPalette.liveSendTextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }

            if (!isOwnProfile) {
                Spacer(Modifier.height(16.dp))
                LiveSendPrimaryButton(
                    text = when {
                        isFriend -> "Already friends"
                        hasPendingOutgoing || requestSent -> "Request sent"
                        else -> "+ Add friend"
                    },
                    enabled = profile != null && !isFriend && !hasPendingOutgoing && !requestSent,
                    loading = sendingRequest,
                    height = 48,
                    onClick = {
                        val target = profile ?: return@LiveSendPrimaryButton
                        sendingRequest = true
                        requestError = null
                        scope.launch {
                            val result = socialRepository.sendFriendRequest(currentUid, currentUsername, target)
                            sendingRequest = false
                            result.onSuccess { requestSent = true }
                            result.onFailure { requestError = it.message ?: "Couldn't send request" }
                        }
                    },
                )
                requestError?.let {
                    Text(text = it, color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            LiveSendSectionLabel(text = "Clubs (${orgs.size})", centered = true, modifier = Modifier.padding(bottom = 10.dp))
            if (orgs.isEmpty()) {
                EmptyState(title = "Not in any clubs yet.", message = "Clubs this member belongs to will show up here.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    orgs.forEach { org ->
                        LiveSendCard(cornerRadius = 14, padding = 14) {
                            Text(text = org.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            LiveSendSectionLabel(text = "Shared videos (${galleryRows.size})", centered = true, modifier = Modifier.padding(bottom = 10.dp))
            if (galleryRows.isEmpty()) {
                EmptyState(title = "No shared videos yet.", message = "Videos this member shares publicly will show up here.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    galleryRows.forEach { row ->
                        ProfileSharedVideoCard(
                            row = row,
                            onToggleLike = {
                                scope.launch { clubRepository.setSharedAttemptLiked(row.id, currentUid, liked = !row.likedByViewer) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Combines every club [targetUid] belongs to into one feed of their shared attempts — there's no
 * single "shared attempts by user" query, so this fans out one Firestore listener per club (per
 * [ClubRepository.observeSharedAttemptsForOrganization]) and filters client-side, same pattern as
 * [ClubRepository.observeStaffOrganizationsForUser]'s membership resolution. */
@Composable
private fun rememberUserSharedAttempts(
    clubRepository: ClubRepository,
    orgs: List<OrganizationEntity>,
    targetUid: String,
): List<SharedAttemptEntity> {
    val flow = remember(orgs, targetUid) {
        if (orgs.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(orgs.map { clubRepository.observeSharedAttemptsForOrganization(it.id) }) { perOrg ->
                perOrg.toList().flatten().filter { it.userId == targetUid }.sortedByDescending { it.createdAt }
            }
        }
    }
    val attempts by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return attempts
}

/** Same card shape as [LiveSendSocialScreen]'s SocialSharedVideoCard minus the sharer row — this
 * screen already shows whose gallery it is once, up top. */
@Composable
private fun ProfileSharedVideoCard(row: SharedAttemptRow, onToggleLike: () -> Unit) {
    var isPlaying by rememberSaveable(row.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOfNotNull(
                    row.routeName,
                    if (row.flash) "Flash" else if (row.completed) "Sent" else "Fell",
                ).joinToString(" · "),
                color = if (row.completed) ClimbPalette.sent else ClimbPalette.fell,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                // While the video is open, tapping this header closes it back to just this
                // header row instead of the video staying open indefinitely.
                modifier = if (isPlaying) {
                    Modifier
                        .clickable(onClick = { isPlaying = false })
                        .semantics { role = Role.Button; contentDescription = "Close video" }
                } else {
                    Modifier
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onToggleLike)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (row.likedByViewer) "Unlike" else "Like"
                    },
            ) {
                Icon(
                    imageVector = if (row.likedByViewer) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (row.likedByViewer) ClimbPalette.liveSendCta else ClimbPalette.liveSendTextMuted,
                    modifier = Modifier.width(18.dp).height(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "${row.likeCount}", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (isPlaying) {
            ExpandableVideoPlayer(videoUrl = row.videoUrl)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClimbPalette.liveSendSurface)
                    .clickable { isPlaying = true }
                    .semantics { role = Role.Button; contentDescription = "Watch video" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ClimbPalette.liveSendTextPrimary, modifier = Modifier.width(18.dp).height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = "Watch", color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
