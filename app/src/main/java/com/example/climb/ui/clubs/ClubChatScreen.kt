package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubMessageEntity
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

/**
 * The club's single group chat thread — every member, staff or not, can read and post here
 * (unlike the staff-only "Updates" broadcast). Real-time via
 * [ClubRepository.observeMessagesForOrganization]'s Firestore snapshot listener, so a message
 * from any member's phone appears on everyone else's without a manual refresh.
 */
@Composable
fun ClubChatScreen(
    currentUid: String,
    currentUsername: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    // Null in the member shell, which already has a shared Scaffold "← Back"; non-null in the
    // staff Club Mode shell, which has no shared chrome at all and needs this screen to provide
    // its own way back.
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val messages by clubRepository.observeMessagesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var draft by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val canSend = draft.isNotBlank() && !sending
    fun send() {
        val trimmed = draft.trim()
        if (trimmed.isEmpty() || sending) return
        sending = true
        scope.launch {
            val result = clubRepository.sendMessage(organization.id, currentUid, currentUsername, trimmed)
            sending = false
            result.onSuccess { draft = "" }
            result.onFailure { errorMessage = it.message ?: "Couldn't send message" }
        }
    }

    Column(modifier = modifier.fillMaxSize().wallTexture()) {
        if (onBack != null) {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, start = 20.dp, bottom = 4.dp).clickable(onClick = onBack),
            )
        }
        Text(
            text = "${organization.name} chat",
            color = ClimbPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No messages yet — say hi to the club.",
                    color = ClimbPalette.textMuted,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message, isOwnMessage = message.senderUid == currentUid)
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                color = ClimbPalette.fell,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; errorMessage = null },
                placeholder = { Text("Message the club…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Send",
                color = if (canSend) ClimbPalette.chalkText else ClimbPalette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (canSend) ClimbPalette.chalk else ClimbPalette.surfaceRaised)
                    .clickable(enabled = canSend) { send() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ClubMessageEntity, isOwnMessage: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isOwnMessage) ClimbPalette.chalk else ClimbPalette.surfaceRaised)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!isOwnMessage) {
                Text(
                    text = message.senderDisplayName,
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.text,
                color = if (isOwnMessage) ClimbPalette.chalkText else ClimbPalette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRelativeTime(message.sentAt),
                color = if (isOwnMessage) ClimbPalette.chalkText.copy(alpha = 0.6f) else ClimbPalette.textMuted,
                fontSize = 10.sp,
            )
        }
    }
}
