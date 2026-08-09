package com.example.climb.ui.clubs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.OrganizationJoinRequestEntity
import com.example.climb.clubs.OrganizationMembershipEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

/**
 * The "Members" tab of Club Mode — current members plus pending join requests, with
 * approve/deny. [ClubRepository.approveJoinRequest]/[ClubRepository.denyJoinRequest] re-check
 * staff access server-side, so this is safe even though every viewer here is already staff.
 * Display names come straight off each record ([OrganizationMembershipEntity.userDisplayName] /
 * [OrganizationJoinRequestEntity.userDisplayName]) rather than a separate profile lookup — the
 * same denormalize-at-write-time pattern [com.example.climb.data.social.FriendRequest] already
 * uses, so a member never shows as a raw uid because some unrelated lookup happened to fail.
 */
@Composable
fun ClubMembersScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val members by clubRepository.observeMembersForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingRequests by clubRepository.observePendingJoinRequests(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "Members",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            SectionCard(title = "Join requests (${pendingRequests.size})") {
                if (pendingRequests.isEmpty()) {
                    Text(text = "No pending requests.", color = ClimbPalette.textMuted, fontSize = 13.sp)
                } else {
                    Column {
                        pendingRequests.forEachIndexed { index, request ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            JoinRequestRow(
                                request = request,
                                displayName = request.userDisplayName,
                                onApprove = { scope.launch { clubRepository.approveJoinRequest(organization.id, currentUid, request) } },
                                onDeny = { scope.launch { clubRepository.denyJoinRequest(organization.id, currentUid, request) } },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Members (${members.size})") {
                if (members.isEmpty()) {
                    Text(text = "No members yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
                } else {
                    Column {
                        members.forEachIndexed { index, member ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            MemberRow(member = member, displayName = member.userDisplayName)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun MemberRow(member: OrganizationMembershipEntity, displayName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = displayName, color = ClimbPalette.textPrimary, fontSize = 14.sp)
        RoleBadge(member.role)
    }
}

@Composable
private fun JoinRequestRow(request: OrganizationJoinRequestEntity, displayName: String, onApprove: () -> Unit, onDeny: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = displayName, color = ClimbPalette.textPrimary, fontSize = 14.sp)
        Row {
            Text(
                text = "Approve",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onApprove),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Deny",
                color = ClimbPalette.fell,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onDeny),
            )
        }
    }
}
