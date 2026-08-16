package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
 * from any member's phone appears on everyone else's without a manual refresh. Styled with the
 * fixed liveSend palette to match the rest of the member club shell and the staff Club Mode shell.
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
    Column(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        if (onBack != null) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, start = 20.dp, bottom = 4.dp).clickable(onClick = onBack),
            )
        }
        Text(
            text = "${organization.name} chat",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        ClubChatContent(
            currentUid = currentUid,
            currentUsername = currentUsername,
            clubRepository = clubRepository,
            organization = organization,
            // ClubChatContent has neither horizontal padding nor navigationBarsPadding of its own
            // (so the Social tab — whose Scaffold already reserves real nav-bar-height space via
            // its own bottom island — can embed it flush, without a redundant second reservation
            // stacking on top and pushing the composer up too far). This standalone route has no
            // such Scaffold bottomBar, so it adds both back itself.
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
        )
    }
}

/** The message list + composer, with no outer chrome (background/header/back) of its own — split
 * out of [ClubChatScreen] so the Social tab's in-place "Chat" tab can embed exactly the same real
 * chat (same data, same send action) inline within its own page shell, instead of navigating to a
 * separate screen for it. [ClubChatScreen] itself is now a thin wrapper around this for the staff
 * shell's standalone Chat route, which still needs its own header/back. */
@Composable
fun ClubChatContent(
    currentUid: String,
    currentUsername: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val messages by clubRepository.observeMessagesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Saveable (not just remember) so an in-progress draft survives Social's tab-switch, which
    // fully disposes and later recreates this composable's subtree via SaveableStateHolder.
    var draft by rememberSaveable { mutableStateOf("") }
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

    Column(modifier = modifier) {
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No messages yet — say hi to the club.",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
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
                color = ClimbPalette.liveSendCta,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; errorMessage = null },
                placeholder = { Text("Message the club…", color = ClimbPalette.liveSendTextMuted) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = ClimbPalette.liveSendTextPrimary,
                    unfocusedTextColor = ClimbPalette.liveSendTextPrimary,
                    focusedContainerColor = ClimbPalette.liveSendSurface,
                    unfocusedContainerColor = ClimbPalette.liveSendSurface,
                    focusedIndicatorColor = ClimbPalette.liveSendAccent,
                    unfocusedIndicatorColor = ClimbPalette.liveSendBorder,
                    cursorColor = ClimbPalette.liveSendAccent,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Send",
                color = if (canSend) ClimbPalette.liveSendAccentText else ClimbPalette.liveSendTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (canSend) ClimbPalette.liveSendAccent else ClimbPalette.liveSendSurfaceRaised)
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
                .background(if (isOwnMessage) ClimbPalette.liveSendAccent else ClimbPalette.liveSendSurfaceRaised)
                .border(1.dp, if (isOwnMessage) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!isOwnMessage) {
                Text(
                    text = message.senderDisplayName,
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.text,
                color = if (isOwnMessage) ClimbPalette.liveSendAccentText else ClimbPalette.liveSendTextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRelativeTime(message.sentAt),
                color = if (isOwnMessage) ClimbPalette.liveSendAccentText.copy(alpha = 0.6f) else ClimbPalette.liveSendTextMuted,
                fontSize = 10.sp,
            )
        }
    }
}
