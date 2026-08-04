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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.FriendRequest
import com.example.climb.data.social.SocialRepository
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(
    currentUid: String,
    currentUsername: String,
    socialRepository: SocialRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val friends by socialRepository.observeFriends(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val incoming by socialRepository.observeIncomingRequests(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val outgoing by socialRepository.observeOutgoingRequests(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "CLIMB",
                color = ClimbPalette.textMuted,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Friends",
                        color = ClimbPalette.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                    )
                    Text(
                        text = "@$currentUsername",
                        color = ClimbPalette.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = "Sign out",
                    color = ClimbPalette.fell,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { authRepository.signOut() },
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Add friend") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; statusMessage = null },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !searching && query.isNotBlank(),
                        onClick = {
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
                        },
                    ) {
                        if (searching) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Add", color = ClimbPalette.chalk, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (statusMessage != null) {
                    Text(
                        text = statusMessage.orEmpty(),
                        color = ClimbPalette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Requests") {
                if (incoming.isEmpty() && outgoing.isEmpty()) {
                    EmptyHint("No pending requests.")
                } else {
                    incoming.forEach { request ->
                        IncomingRequestRow(
                            request = request,
                            onAccept = { scope.launch { socialRepository.acceptFriendRequest(request) } },
                            onDecline = { scope.launch { socialRepository.declineFriendRequest(request.id) } },
                        )
                    }
                    outgoing.forEach { request -> OutgoingRequestRow(request) }
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Friends") {
                if (friends.isEmpty()) {
                    EmptyHint("No friends yet — search a username above.")
                } else {
                    friends.forEach { friend ->
                        Text(
                            text = friend.username,
                            color = ClimbPalette.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, color = ClimbPalette.textSecondary, fontSize = 13.sp)
}

@Composable
private fun IncomingRequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(request.fromUsername, color = ClimbPalette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RequestActionChip(text = "Accept", color = ClimbPalette.sent, onClick = onAccept)
            RequestActionChip(text = "Decline", color = ClimbPalette.fell, onClick = onDecline)
        }
    }
}

@Composable
private fun OutgoingRequestRow(request: FriendRequest) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(request.toUsername, color = ClimbPalette.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = "PENDING",
            color = ClimbPalette.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .border(1.dp, ClimbPalette.border, RoundedCornerShape(50))
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
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
