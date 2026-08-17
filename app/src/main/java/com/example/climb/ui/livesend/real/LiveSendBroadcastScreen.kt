package com.example.climb.ui.livesend.real

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.clubs.ClubChatContent
import com.example.climb.ui.components.EmptyState
import com.example.climb.ui.components.PhotoAnnotationDialog
import com.example.climb.ui.livesend.ActivityItem
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendPrimaryButton
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTextField
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch

private enum class ManageSocialTab(val label: String) {
    UPDATES("Updates"),
    CHAT("Chat"),
}

/**
 * Real, Live-Send-styled replacement for [com.example.climb.ui.clubs.ClubUpdatesScreen] at the
 * `club_updates`/`member_club_updates` destinations — renamed from "Broadcast" to "Manage Social"
 * and restructured into an Updates/Chat tabbed layout matching [LiveSendSocialScreen]'s member-
 * facing "Social" tab UX pattern exactly (same [SocialTabBar]/[SocialTabSpec] segmented pill row,
 * same [rememberSaveableStateHolder]-backed tab switching so each tab keeps its own scroll
 * position/chat draft across switches instead of disposing it). Folding Chat in as a tab (rather
 * than a separate pushed screen) means staff no longer need to navigate away to
 * [com.example.climb.ui.clubs.ClubChatScreen] for it — see
 * [com.example.climb.navigation.ClubNavHost], which no longer registers a standalone chat route.
 *
 * Both tabs still use the exact same real data/actions as before this restructure
 * ([ClubRepository.observeUpdatesForOrganization]/[ClubRepository.postUpdate]/[ClubRepository.deleteUpdate]
 * for Updates, [ClubChatContent] for Chat) and the same staff-vs-member distinction ([isStaff]
 * gates the posting form and now also the chat delete affordance) — just reached via one tabbed
 * page instead of a separate screen per concept. Unlike [LiveSendSocialScreen] there is
 * deliberately no third "Shared videos" tab here — staff already has a separate, dedicated videos-
 * management screen elsewhere, so a redundant third tab would just be unscoped duplication.
 *
 * The outer header/floating "island" bottom bar shell is UNCHANGED by this restructure — the staff
 * shell still has no shared Scaffold chrome (unlike [com.example.climb.navigation.MemberClubNavHost]),
 * so this screen still renders both itself; only the content area between them became tabbed.
 * [onGoHome] navigates to the real app's Home screen as a real, poppable destination inside the
 * staff shell's own back stack (see [com.example.climb.navigation.ClubNavHost]'s
 * `club_home_preview`) — not a permanent exit; member callers pass their real "back" callback
 * instead, which was always real back-navigation. [onExitClub] is the genuinely separate,
 * permanent "leave Club Mode" action the staff "Exit" tab uses — unused/defaulted no-op in the
 * member context, which has no such tab.
 */
@Composable
fun LiveSendBroadcastScreen(
    currentUid: String,
    currentUsername: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    onGoHome: () -> Unit,
    onExitClub: () -> Unit = {},
    // Staff-only cross-navigation to the sibling Members screen (and back to this one) — unused,
    // defaulted no-op in the member context, which has no members-management concept.
    onNavBroadcast: () -> Unit = {},
    onNavMembers: () -> Unit = {},
    // Member-only — this screen is a pushed destination reached from the Social tab, not a tab
    // itself, so it needs a real "back to Social" affordance instead of the header's usual Home
    // icon. Null (default) for staff, whose Manage Social is still a real top-level tab.
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(ManageSocialTab.UPDATES) }
    // Same reasoning as LiveSendSocialScreen: switching selectedTab would otherwise fully dispose
    // the previous tab's subtree (losing an in-progress chat draft or scroll position) every time
    // — SaveableStateProvider keyed on the tab saves/restores each tab's own rememberSaveable
    // state across that dispose/recompose.
    val saveableStateHolder = rememberSaveableStateHolder()

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No statusBarsPadding here — the enclosing Scaffold (staff or member) already
                // reserves top system-bar inset space; applying it again pushed this headline
                // visibly lower than Overview/Videos/Chat, which never had one either.
                .padding(horizontal = 20.dp, vertical = 20.dp)
                // A bit taller than the island's own footprint (56dp bar + 14dp*2 vertical margin)
                // so it never overlaps this page's content even with a larger system navigation
                // bar inset on top of that — was 90dp, which could clip the last visible row.
                .padding(bottom = 104.dp),
        ) {
            LiveSendPageHeader(
                title = "Manage Social",
                onGoHome = onGoHome,
                onBack = onBack,
            )
            Spacer(Modifier.height(16.dp))

            SocialTabBar(
                tabs = ManageSocialTab.entries.map { tab ->
                    SocialTabSpec(label = tab.label, selected = tab == selectedTab, onClick = { selectedTab = tab })
                },
            )
            Spacer(Modifier.height(16.dp))

            saveableStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    ManageSocialTab.UPDATES -> ManageSocialUpdatesTab(
                        currentUid = currentUid,
                        clubRepository = clubRepository,
                        organization = organization,
                        isStaff = isStaff,
                        modifier = Modifier.weight(1f),
                    )
                    ManageSocialTab.CHAT -> ClubChatContent(
                        currentUid = currentUid,
                        currentUsername = currentUsername,
                        clubRepository = clubRepository,
                        organization = organization,
                        isStaff = isStaff,
                        onDeleteMessage = if (isStaff) {
                            { message -> scope.launch { clubRepository.deleteMessage(organization.id, currentUid, message) } }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }

        // Member context relies on MemberClubNavHost's own shared floating island instead (per
        // user request that every floating island in Club Mode stay consistent) — only staff,
        // which has no such shared chrome, renders its own bar here.
        if (isStaff) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                    LiveSendNavTab(Icons.Filled.Campaign, "Social", selected = true, onClick = onNavBroadcast),
                    LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                    LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExitClub),
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The "Updates" tab's content — the staff-only posting composer (unchanged) plus the real update
 * feed, including the already-working staff delete-post affordance ([ClubRepository.deleteUpdate]
 * via [LiveSendActivityRow]'s `onDelete`), now under the same centered "LABEL (N)" section-header
 * pattern [LiveSendSocialScreen]'s own Updates tab uses, for visual consistency between the two. */
@Composable
private fun ManageSocialUpdatesTab(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    modifier: Modifier = Modifier,
) {
    val updates by clubRepository.observeUpdatesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val orgInitial = organization.name.firstOrNull()?.uppercase() ?: "?"

    Column(modifier = modifier) {
        if (isStaff) {
            val context = LocalContext.current
            var text by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var posting by remember { mutableStateOf(false) }
            // The picked photo before annotation (shows the markup dialog); the annotated
            // result after "Done" (shown as a small preview, attached on Post).
            var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

            val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    pickedBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }

            LiveSendSectionLabel(text = "Post an update")
            Spacer(Modifier.height(8.dp))
            LiveSendTextField(value = text, onValueChange = { text = it; errorMessage = null }, placeholder = "What's new at the gym?")
            Spacer(Modifier.height(8.dp))

            val currentAnnotated = annotatedBitmap
            if (currentAnnotated != null) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(currentAnnotated.width.toFloat() / currentAnnotated.height.toFloat())) {
                    Image(
                        bitmap = currentAnnotated.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        text = "Remove photo",
                        color = ClimbPalette.liveSendCta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clickable { annotatedBitmap = null },
                    )
                }
            } else {
                Text(
                    text = "+ Add a photo (circle a hold, draw an arrow, highlight the wall)",
                    color = ClimbPalette.liveSendAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            errorMessage?.let { Text(text = it, color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            LiveSendPrimaryButton(
                text = if (posting) "Posting…" else "Post",
                enabled = text.isNotBlank() && !posting,
                onClick = {
                    posting = true
                    scope.launch {
                        val photoToUpload = annotatedBitmap
                        val photoUrlResult = if (photoToUpload != null) {
                            clubRepository.uploadUpdatePhoto(organization.id, currentUid, photoToUpload)
                        } else {
                            Result.success(null)
                        }
                        photoUrlResult.onFailure {
                            posting = false
                            errorMessage = it.message ?: "Couldn't upload photo"
                        }
                        photoUrlResult.onSuccess { photoUrl ->
                            val result = clubRepository.postUpdate(organization.id, currentUid, text, photoUrl)
                            posting = false
                            result.onSuccess { text = ""; annotatedBitmap = null }
                            result.onFailure { errorMessage = it.message ?: "Something went wrong" }
                        }
                    }
                },
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(16.dp))

            val bitmapToAnnotate = pickedBitmap
            if (bitmapToAnnotate != null) {
                PhotoAnnotationDialog(
                    bitmap = bitmapToAnnotate,
                    onCancel = { pickedBitmap = null },
                    onDone = { result ->
                        annotatedBitmap = result
                        pickedBitmap = null
                    },
                )
            }
        }

        Text(
            text = "UPDATES (${updates.size})",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        if (updates.isEmpty()) {
            EmptyState(title = "No updates yet.", message = "New sets, maintenance notices, and events will show up here.")
        } else {
            // Fills the rest of this tab's content (rather than a small fixed max height) with its
            // own internal scroll, so a growing real update list scrolls in place in a large,
            // clearly-bordered frame instead of a cramped little box.
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
                    updates.forEach { update ->
                        LiveSendActivityRow(
                            activity = ActivityItem(initial = orgInitial, text = update.text, timeAgo = formatRelativeTime(update.createdAt), photoUrl = update.photoUrl),
                            onDelete = if (isStaff) {
                                {
                                    scope.launch { clubRepository.deleteUpdate(organization.id, currentUid, update) }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Shared page header for the Live-Send-styled real club screens (Manage Social/Members/Cameras) —
 * a title plus an explicit icon button, matching [com.example.climb.ui.livesend.ClubDashboardScreen]'s
 * header row so all of Club Mode reads as one consistent surface. That button is Home by default
 * ([onGoHome]); [onBack] — highest priority — swaps it for a back arrow instead, for a screen that
 * isn't a tab at all but a pushed destination (member Manage Social, reached from the Social tab).
 */
@Composable
internal fun LiveSendPageHeader(title: String, onGoHome: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ClimbPalette.liveSendSurface)
                .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(20.dp))
                .clickable(onClick = onBack ?: onGoHome)
                .semantics {
                    role = Role.Button
                    contentDescription = if (onBack != null) "Back" else "Go to Home"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Home,
                contentDescription = null,
                tint = ClimbPalette.liveSendTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Same row visual as [com.example.climb.ui.livesend.ClubDashboardScreen]'s private `ActivityRow`
 * — promoted here as an internal helper so Manage Social doesn't duplicate the look. [onDelete] is
 * staff-only moderation (null hides the affordance entirely — the member-facing call site never
 * passes one, since members can't delete posts). */
@Composable
internal fun LiveSendActivityRow(activity: ActivityItem, onDelete: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveSendAvatar(initial = activity.initial, size = 32)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = activity.text, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(text = activity.timeAgo, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (onDelete != null) {
                Text(
                    text = "Delete",
                    color = ClimbPalette.liveSendCta,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onDelete)
                        .semantics { role = Role.Button; contentDescription = "Delete post" },
                )
            }
        }
        if (activity.photoUrl != null) {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = activity.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}
