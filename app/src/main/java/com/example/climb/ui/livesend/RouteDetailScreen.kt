package com.example.climb.ui.livesend

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.climb.ui.livesend.components.GradeBadge
import com.example.climb.ui.livesend.components.LiveIndicatorDot
import com.example.climb.ui.livesend.components.LiveSendBadge
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendFab
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Live Send (Alternative UI Concept 2) — RouteDetailScreen (Figma node 5:306).
 *
 * A single route's detail view: back affordance, route name + grade, a beta-video card (play
 * button, route-color dot, duration tag), a 3-up personal-stat row (send rate / peak grade /
 * sessions), a "Log Attempt" CTA, and this concept's floating bottom nav + record FAB. All content
 * mirrors the Figma spec's literal text; only navigation/interaction is left to the caller via the
 * lambda parameters below, matching this project's convention of leaf screens knowing nothing
 * about route strings or NavController.
 */
@Composable
fun RouteDetailScreen(
    onBack: () -> Unit,
    onPlayVideo: () -> Unit,
    onLogAttempt: () -> Unit,
    onRecordAttempt: () -> Unit,
    onFeedTab: () -> Unit,
    onProgressTab: () -> Unit,
    onRanksTab: () -> Unit,
    onClubTab: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 132.dp),
        ) {
            BackRow(onBack = onBack)

            Spacer(modifier = Modifier.height(20.dp))

            RouteTitleRow()

            Spacer(modifier = Modifier.height(20.dp))

            BetaVideoCard(onPlayVideo = onPlayVideo)

            Spacer(modifier = Modifier.height(16.dp))

            PersonalStatsRow()

            Spacer(modifier = Modifier.height(20.dp))

            LiveSendPrimaryButton(
                text = "Log Attempt",
                onClick = onLogAttempt,
                height = 50,
                modifier = Modifier.semantics { contentDescription = "Log attempt" },
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(icon = Icons.Filled.Home, label = "Feed", selected = false, onClick = onFeedTab),
                    LiveSendNavTab(icon = Icons.Filled.ShowChart, label = "Progress", selected = false, onClick = onProgressTab),
                    LiveSendNavTab(icon = Icons.Filled.EmojiEvents, label = "Ranks", selected = false, onClick = onRanksTab),
                    LiveSendNavTab(icon = Icons.Filled.Groups, label = "Club", selected = true, onClick = onClubTab),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            LiveSendFab(
                onClick = onRecordAttempt,
                icon = Icons.Filled.Videocam,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** "← Back" affordance, following this project's literal per-screen inline pattern. Padded to a
 * 44dp-tall touch target since the glyph+label alone (5:427) is only 17dp tall. */
@Composable
private fun BackRow(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onBack)
            .semantics {
                contentDescription = "Back"
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "← Back",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

/** Route name + V-grade badge (5:428–5:430). */
@Composable
private fun RouteTitleRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Blue Route",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
        )
        GradeBadge(grade = "V7", cornerRadius = 14.dp)
    }
}

/** The beta video card (5:431–5:433, 38:638–38:640): play button, route-color dot, duration tag. */
@Composable
private fun BetaVideoCard(onPlayVideo: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(335f / 420f)
            .clip(shape)
            .background(ClimbPalette.liveSendSurface),
    ) {
        LiveIndicatorDot(
            color = ClimbPalette.liveSendInfo,
            size = 14,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )

        LiveSendBadge(
            text = "0:41",
            containerColor = ClimbPalette.mediaScrim,
            contentColor = ClimbPalette.liveSendTextPrimary,
            fontSize = 11,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(70.dp)
                .clip(CircleShape)
                .background(ClimbPalette.mediaScrim)
                .clickable(onClick = onPlayVideo)
                .semantics {
                    contentDescription = "Play video"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ">",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
        }
    }
}

/**
 * Send-rate / peak-grade / sessions stat row (5:434–5:439). Unlike ClubDashboard's stat cards,
 * these three (5:435/5:437/5:439) are each a single uniform Bold 13sp two-line text block with no
 * giant number and no uppercase caption — so this uses the plain [LiveSendCard] surface directly
 * rather than the big-number/caption [LiveSendStatCard].
 */
@Composable
private fun PersonalStatsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        StatBlock(text = "100%\nSend Rate", modifier = Modifier.weight(1f))
        StatBlock(text = "V7\nPeak Grade", modifier = Modifier.weight(1f))
        StatBlock(text = "2\nSessions", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatBlock(text: String, modifier: Modifier = Modifier) {
    LiveSendCard(modifier = modifier, cornerRadius = 16, padding = 12) {
        Text(
            text = text,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
