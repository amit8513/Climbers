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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.OrganizationJoinRequestEntity
import com.example.climb.clubs.OrganizationMembershipEntity
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

/**
 * Real, Live-Send-styled replacement for [com.example.climb.ui.clubs.ClubMembersScreen] at the
 * `club_members` destination — same real data/actions
 * ([ClubRepository.observeMembersForOrganization], [ClubRepository.observePendingJoinRequests],
 * [ClubRepository.approveJoinRequest]/[ClubRepository.denyJoinRequest]), just matching
 * [com.example.climb.ui.livesend.ClubDashboardScreen]'s dark/neon-lime look — including its own
 * floating "island" bottom bar (Home/Broadcast/Members/Exit, mirroring the Dashboard's bar
 * exactly — this screen is staff-only, unlike Broadcast, so there's no member-context variant to
 * consider here). The whole page is a fixed, non-scrolling layout — "Join requests" and "Members"
 * each scroll internally within their own bounded height — per user request. [onGoHome] navigates
 * to the real app's Home screen as a real, poppable destination inside the staff shell's own back
 * stack (see [com.example.climb.navigation.ClubNavHost]'s `club_home_preview`) — not a permanent
 * exit. [onExitClub] is the genuinely separate, permanent "leave Club Mode" action the "Exit" tab
 * uses.
 */
@Composable
fun LiveSendMembersScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    onGoHome: () -> Unit,
    onExitClub: () -> Unit,
    onNavBroadcast: () -> Unit,
) {
    val members by clubRepository.observeMembersForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingRequests by clubRepository.observePendingJoinRequests(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No statusBarsPadding here — the enclosing staff Scaffold already reserves top
                // system-bar inset space; applying it again pushed this headline visibly lower
                // than the rest of Club Mode.
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 90.dp),
        ) {
            LiveSendPageHeader(title = "Members", onGoHome = onGoHome)
            Spacer(Modifier.height(20.dp))

            LiveSendSectionLabel(text = "Join requests (${pendingRequests.size})")
            Spacer(Modifier.height(10.dp))
            if (pendingRequests.isEmpty()) {
                Text(text = "No pending requests.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
            } else {
                // Fixed-height + its own scroll (~3 rows) so a growing real request list scrolls
                // in place rather than stretching the now-fixed page.
                Column(
                    modifier = Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pendingRequests.forEach { request ->
                        JoinRequestCard(
                            request = request,
                            onApprove = { scope.launch { clubRepository.approveJoinRequest(organization.id, currentUid, request) } },
                            onDeny = { scope.launch { clubRepository.denyJoinRequest(organization.id, currentUid, request) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            LiveSendSectionLabel(text = "Members (${members.size})")
            Spacer(Modifier.height(10.dp))
            if (members.isEmpty()) {
                Text(text = "No members yet.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
            } else {
                // Same bordered, fills-the-rest-of-the-page treatment as Broadcast's "Recent" box,
                // per user request to make this section match — a large, clearly-edged frame
                // instead of a small fixed-height list.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        members.forEach { member -> MemberCard(member) }
                    }
                }
            }
        }

        LiveSendBottomBar(
            tabs = listOf(
                LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                LiveSendNavTab(Icons.Filled.Campaign, "Broadcast", selected = false, onClick = onNavBroadcast),
                LiveSendNavTab(Icons.Filled.Group, "Members", selected = true, onClick = {}),
                LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExitClub),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MemberCard(member: OrganizationMembershipEntity) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = member.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        LiveSendRoleBadge(member.role.name)
    }
}

@Composable
private fun JoinRequestCard(request: OrganizationJoinRequestEntity, onApprove: () -> Unit, onDeny: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = request.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row {
            Text(
                text = "Approve",
                color = ClimbPalette.liveSendAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onApprove),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Deny",
                color = ClimbPalette.liveSendCta,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onDeny),
            )
        }
    }
}

@Composable
private fun LiveSendRoleBadge(roleName: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = roleName, color = ClimbPalette.liveSendTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
