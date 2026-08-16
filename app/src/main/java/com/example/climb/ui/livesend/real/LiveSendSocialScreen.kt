package com.example.climb.ui.livesend.real

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.clubs.ClubChatContent
import com.example.climb.ui.components.EmptyState
import com.example.climb.ui.livesend.ActivityItem
import com.example.climb.ui.livesend.SharedAttemptRow
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.livesend.components.rememberSharedAttemptRows
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

private enum class SocialTab(val label: String) {
    UPDATES("Updates"),
    SHARED("Shared videos"),
    CHAT("Chat"),
}

/**
 * The member club shell's "Social" tab — one page with an always-visible segmented bar for
 * Updates / Shared videos / Chat, switching which content shows in place rather than navigating to
 * a separate screen for each (per user request: "the user will navigate through there not through
 * redirect into another window"). Updates and Chat reuse the exact same real data/actions as the
 * standalone Broadcast/Chat screens ([ClubRepository.observeUpdatesForOrganization]/
 * [com.example.climb.ui.clubs.ClubChatContent]) — nothing about those two data paths changed, just
 * how they're reached. Shared videos is a club-wide feed of every member-shared attempt video
 * (see [ClubRepository.observeSharedAttemptsForOrganization]) — distinct from
 * [com.example.climb.ui.livesend.RouteDetailScreen]'s per-route feed, this one spans every route in
 * the club, each row naming which route it's from.
 */
@Composable
fun LiveSendSocialScreen(
    currentUid: String,
    currentUsername: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SocialTab.UPDATES) }
    // Switching selectedTab swaps which composable the `when` below calls, which would otherwise
    // fully dispose the previous tab's subtree (losing an in-progress chat draft, scroll
    // position, or a playing shared video) every time — SaveableStateProvider keyed on the tab
    // saves/restores each tab's own rememberSaveable state across that dispose/recompose, so
    // switching tabs behaves like a real in-place tab bar rather than a disguised navigation.
    val saveableStateHolder = rememberSaveableStateHolder()

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 100.dp)) {
            Text(
                text = "Social",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SocialTabBar(selected = selectedTab, onSelect = { selectedTab = it })

            Spacer(Modifier.height(16.dp))

            saveableStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    SocialTab.UPDATES -> UpdatesTabContent(clubRepository = clubRepository, organization = organization, modifier = Modifier.weight(1f))
                    SocialTab.SHARED -> SharedVideosTabContent(currentUid = currentUid, clubRepository = clubRepository, organization = organization, modifier = Modifier.weight(1f))
                    SocialTab.CHAT -> ClubChatContent(
                        currentUid = currentUid,
                        currentUsername = currentUsername,
                        clubRepository = clubRepository,
                        organization = organization,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialTabBar(selected: SocialTab, onSelect: (SocialTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialTab.entries.forEach { tab ->
            SocialTabButton(
                label = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SocialTabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) ClimbPalette.liveSendAccentText else ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Read-only here — the member context never showed the staff-only posting composer (see
 * [LiveSendBroadcastScreen]'s own [isStaff] gate), so this is just the same real update feed,
 * reusing the exact same row look ([LiveSendActivityRow]) as the standalone Broadcast screen. */
@Composable
private fun UpdatesTabContent(clubRepository: ClubRepository, organization: OrganizationEntity, modifier: Modifier = Modifier) {
    val updates by clubRepository.observeUpdatesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val orgInitial = organization.name.firstOrNull()?.uppercase() ?: "?"

    Column(modifier = modifier) {
        Text(
            text = "UPDATES (${updates.size})",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (updates.isEmpty()) {
            EmptyState(title = "No updates yet.", message = "New sets, maintenance notices, and events will show up here.")
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    updates.forEach { update ->
                        LiveSendActivityRow(
                            activity = ActivityItem(initial = orgInitial, text = update.text, timeAgo = formatRelativeTime(update.createdAt), photoUrl = update.photoUrl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedVideosTabContent(currentUid: String, clubRepository: ClubRepository, organization: OrganizationEntity, modifier: Modifier = Modifier) {
    val sharedAttempts by clubRepository.observeSharedAttemptsForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val rows = rememberSharedAttemptRows(clubRepository, sharedAttempts, currentUid)
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Text(
            text = "SHARED VIDEOS (${rows.size})",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (rows.isEmpty()) {
            EmptyState(title = "No shared videos yet.", message = "When a member shares a sent attempt publicly, it shows up here.")
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rows.forEach { row ->
                        SocialSharedVideoCard(
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

@Composable
private fun SocialSharedVideoCard(row: SharedAttemptRow, onToggleLike: () -> Unit) {
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
            Column {
                Text(text = row.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    text = listOfNotNull(
                        row.routeName?.let { "on $it" },
                        if (row.flash) "Flash" else if (row.completed) "Sent" else "Fell",
                    ).joinToString(" · "),
                    color = if (row.completed) ClimbPalette.sent else ClimbPalette.fell,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                    modifier = Modifier.width(20.dp).height(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "${row.likeCount}", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (isPlaying) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))) {
                SocialInlineVideoPlayer(videoUrl = row.videoUrl)
            }
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

/** Same minimal inline-player shape as every other Storage-hosted video in Club Mode (see
 * [com.example.climb.ui.livesend.RouteDetailScreen]'s private BetaVideoPlayer) — plays a remote
 * Storage URL directly, no download step. */
@Composable
private fun SocialInlineVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxSize(),
    )
}
