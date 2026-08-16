package com.example.climb.ui.friends

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.social.Friend
import com.example.climb.data.social.FriendRequest
import com.example.climb.data.social.SocialRepository
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(
    currentUid: String,
    currentUsername: String,
    socialRepository: SocialRepository,
    onFriendClick: (Friend) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val friends by socialRepository.observeFriends(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val incoming by socialRepository.observeIncomingRequests(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val outgoing by socialRepository.observeOutgoingRequests(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "CLIMBERS",
                color = ClimbPalette.liveSendTextMuted,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = "Friends",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
            Text(
                text = "@$currentUsername",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(18.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Add friend")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveSendTextField(
                            value = query,
                            onValueChange = { query = it; statusMessage = null },
                            placeholder = "Username",
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable(enabled = !searching && query.isNotBlank()) {
                                    searching = true
                                    statusMessage = null
                                    val submittedQuery = query
                                    scope.launch {
                                        val profile = socialRepository.findUserByUsername(submittedQuery)
                                        statusMessage = if (profile == null) {
                                            "No user named \"$submittedQuery\""
                                        } else {
                                            socialRepository.sendFriendRequest(currentUid, currentUsername, profile).fold(
                                                onSuccess = { "Friend request sent to ${profile.username}" },
                                                onFailure = { it.message ?: "Couldn't send request" },
                                            )
                                        }
                                        searching = false
                                        query = ""
                                    }
                                }
                                .semantics { role = Role.Button; contentDescription = "Add friend" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = ClimbPalette.liveSendAccent)
                            } else {
                                Text(
                                    text = "Add",
                                    color = if (query.isNotBlank()) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    if (statusMessage != null) {
                        Text(
                            text = statusMessage.orEmpty(),
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Requests")
                    Spacer(Modifier.height(10.dp))
                    if (incoming.isEmpty() && outgoing.isEmpty()) {
                        EmptyHint("No pending requests.")
                    } else {
                        incoming.forEachIndexed { index, request ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            IncomingRequestRow(
                                request = request,
                                onAccept = { scope.launch { socialRepository.acceptFriendRequest(request) } },
                                onDecline = { scope.launch { socialRepository.declineFriendRequest(request.id) } },
                            )
                        }
                        outgoing.forEachIndexed { index, request ->
                            if (index > 0 || incoming.isNotEmpty()) Spacer(Modifier.height(10.dp))
                            OutgoingRequestRow(request)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            LiveSendCard {
                Column {
                    LiveSendSectionLabel(text = "Friends (${friends.size})")
                    Spacer(Modifier.height(10.dp))
                    if (friends.isEmpty()) {
                        EmptyHint("No friends yet — search a username above.")
                    } else {
                        friends.forEachIndexed { index, friend ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            FriendRow(friend = friend, onClick = { onFriendClick(friend) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveSendAvatar(initial = friend.username, size = 32)
            Text(text = friend.username, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(text = "View climbs →", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun IncomingRequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(request.fromUsername, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RequestActionChip(text = "Accept", color = ClimbPalette.sent, onClick = onAccept)
            RequestActionChip(text = "Decline", color = ClimbPalette.fell, onClick = onDecline)
        }
    }
}

@Composable
private fun OutgoingRequestRow(request: FriendRequest) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(request.toUsername, color = ClimbPalette.liveSendTextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = "PENDING",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RequestActionChip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
