package com.example.climb.ui.livesend

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.GradeBadge
import com.example.climb.ui.livesend.components.LiveIndicatorDot
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendBadge
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendFab
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * "Live Send" (Alternative UI Concept 2) home feed — Figma node 5:304. The member-shell landing
 * screen: a greeting header, a For You / Clubs feed switch, one featured send video card, a
 * weekly-stats pill, and the floating record FAB + bottom nav shared by the other member-shell
 * screens in this concept.
 *
 * This is a self-contained screen in the design-exploration package (`ui/livesend/`) — it does
 * not touch, extend, or share a nav host with the shipped `MemberClubNavHost`.
 */
@Composable
fun HomeFeedScreen(
    onProfileClick: () -> Unit,
    onForYouTabClick: () -> Unit,
    onClubsTabClick: () -> Unit,
    onPlayVideo: () -> Unit,
    onRecordClick: () -> Unit,
    onFeedTabClick: () -> Unit,
    onProgressTabClick: () -> Unit,
    onRanksTabClick: () -> Unit,
    onClubTabClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 140.dp),
        ) {
            HomeFeedHeader(onProfileClick = onProfileClick)

            Spacer(Modifier.height(20.dp))

            FeedTabsRow(onForYouTabClick = onForYouTabClick, onClubsTabClick = onClubsTabClick)

            Spacer(Modifier.height(20.dp))

            FeaturedSendVideoCard(onPlayVideo = onPlayVideo)

            Spacer(Modifier.height(16.dp))

            WeeklyStatsPill()
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(icon = Icons.Filled.Home, label = "Feed", selected = true, onClick = onFeedTabClick),
                    LiveSendNavTab(icon = Icons.Filled.QueryStats, label = "Progress", selected = false, onClick = onProgressTabClick),
                    LiveSendNavTab(icon = Icons.Filled.EmojiEvents, label = "Ranks", selected = false, onClick = onRanksTabClick),
                    LiveSendNavTab(icon = Icons.Filled.Group, label = "Club", selected = false, onClick = onClubTabClick),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            LiveSendFab(
                onClick = onRecordClick,
                icon = Icons.Filled.FiberManualRecord,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
            )
        }
    }
}

/** "Hey Amit 👋" greeting + the profile avatar, top of the feed. */
@Composable
private fun HomeFeedHeader(onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Hey Amit 👋",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick, role = Role.Button, onClickLabel = "Open profile"),
            contentAlignment = Alignment.Center,
        ) {
            LiveSendAvatar(initial = "A", size = 32, ringed = true)
        }
    }
}

/** "For You" (active, underlined) / "Clubs" feed switch. */
@Composable
private fun FeedTabsRow(onForYouTabClick: () -> Unit, onClubsTabClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onForYouTabClick, role = Role.Button, onClickLabel = "Show For You feed"),
        ) {
            Text(text = "For You", color = ClimbPalette.liveSendAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ClimbPalette.liveSendAccent),
            )
        }
        Spacer(Modifier.width(20.dp))
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onClubsTabClick, role = Role.Button, onClickLabel = "Show Clubs feed"),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(text = "Clubs", color = ClimbPalette.liveSendTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/** The 335x420 featured send video card, with its grade badge, location, streak ribbon, play
 * control, and caption overlays. The whole card is one tap target — it opens the video. */
@Composable
private fun FeaturedSendVideoCard(onPlayVideo: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ClimbPalette.liveSendSurface)
            .clickable(onClick = onPlayVideo, role = Role.Button, onClickLabel = "Play video"),
    ) {
        // Top row: grade badge + location dot on the left, streak ribbon on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                GradeBadge(grade = "V7")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveIndicatorDot(color = ClimbPalette.liveSendInfo)
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Blue Wall · Main", color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp)
                }
            }
            LiveSendBadge(
                text = "🔥 3-day streak",
                containerColor = ClimbPalette.liveSendAccent,
                contentColor = ClimbPalette.liveSendAccentText,
                fontSize = 11,
            )
        }

        // Center play control.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp)
                .clip(CircleShape)
                .background(ClimbPalette.mediaScrim),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = ">", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        }

        // Bottom caption + engagement summary.
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                text = "\"Sent it first try\" — @amit",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "42 likes · 8 comments · V7 Blue",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/** The "3 sends this week" / "1 day streak" summary pill under the video card. */
@Composable
private fun WeeklyStatsPill() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ClimbPalette.liveSendSurfaceRaised)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "3 sends this week", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = "1 day streak", color = ClimbPalette.liveSendAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
