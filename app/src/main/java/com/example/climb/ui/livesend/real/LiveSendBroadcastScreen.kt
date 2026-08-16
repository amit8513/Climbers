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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
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

/**
 * Real, Live-Send-styled replacement for [com.example.climb.ui.clubs.ClubUpdatesScreen] at the
 * `club_updates`/`member_club_updates` destinations — same real data
 * ([ClubRepository.observeUpdatesForOrganization]/[ClubRepository.postUpdate]) and the same
 * staff-vs-member distinction ([isStaff] gates the posting form), just matching
 * [com.example.climb.ui.livesend.ClubDashboardScreen]'s dark/neon-lime look instead of the old
 * Material page style, per the user's request to make Broadcast/Members visually consistent with
 * the dashboard — including the dashboard's own floating "island" bottom bar ([isStaff]:
 * Home/Broadcast/Members/Exit, mirroring [com.example.climb.ui.livesend.ClubDashboardScreen]'s bar
 * exactly, since Dashboard itself isn't one of those tabs either — same as Explore isn't reachable
 * from Dashboard's bar, only its Manage grid; member context has no members-management concept, so
 * just a Home tab). The whole page is a fixed, non-scrolling layout — only "Recent" scrolls
 * internally within its own bounded height — per user request. [onGoHome] navigates to the real
 * app's Home screen as a real, poppable destination inside the staff shell's own back stack (see
 * [com.example.climb.navigation.ClubNavHost]'s `club_home_preview`) — not a permanent exit; member
 * callers pass their real "back" callback instead, which was always real back-navigation. [onExitClub]
 * is the genuinely separate, permanent "leave Club Mode" action the staff "Exit" tab uses — unused/
 * defaulted no-op in the member context, which has no such tab.
 */
@Composable
fun LiveSendBroadcastScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    isStaff: Boolean,
    onGoHome: () -> Unit,
    onExitClub: () -> Unit = {},
    // Staff-only cross-navigation to the sibling Members screen (and back to this one) — unused,
    // defaulted no-op in the member context, which has no members-management concept.
    onNavBroadcast: () -> Unit = {},
    onNavMembers: () -> Unit = {},
) {
    val updates by clubRepository.observeUpdatesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val orgInitial = organization.name.firstOrNull()?.uppercase() ?: "?"

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 90.dp),
        ) {
            LiveSendPageHeader(title = "Broadcast", onGoHome = onGoHome)
            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(10.dp))
                LiveSendTextField(value = text, onValueChange = { text = it; errorMessage = null }, placeholder = "What's new at the gym?")
                Spacer(Modifier.height(10.dp))

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
                Spacer(Modifier.height(10.dp))

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
                Spacer(Modifier.height(24.dp))

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

            LiveSendSectionLabel(text = "Recent")
            Spacer(Modifier.height(10.dp))
            if (updates.isEmpty()) {
                EmptyState(title = "No updates yet.", message = "New sets, maintenance notices, and events will show up here.")
            } else {
                // Fills the rest of this fixed, non-scrolling page (rather than a small fixed max
                // height) with its own internal scroll, so a growing real update list scrolls in
                // place in a large, clearly-bordered frame instead of a cramped little box.
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

        val tabs = if (isStaff) {
            listOf(
                LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                LiveSendNavTab(Icons.Filled.Campaign, "Broadcast", selected = true, onClick = onNavBroadcast),
                LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExitClub),
            )
        } else {
            listOf(LiveSendNavTab(Icons.Filled.Home, "Home", selected = true, onClick = onGoHome))
        }
        LiveSendBottomBar(tabs = tabs, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Shared page header for the Live-Send-styled real club screens (Broadcast/Members) — a title
 * plus an explicit Home icon button, matching [com.example.climb.ui.livesend.ClubDashboardScreen]'s
 * header row so all of Club Mode reads as one consistent surface. */
@Composable
internal fun LiveSendPageHeader(title: String, onGoHome: () -> Unit) {
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
                .clickable(onClick = onGoHome)
                .semantics {
                    role = Role.Button
                    contentDescription = "Go to Home"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = ClimbPalette.liveSendTextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Same row visual as [com.example.climb.ui.livesend.ClubDashboardScreen]'s private `ActivityRow`
 * — promoted here as an internal helper so Broadcast doesn't duplicate the look. [onDelete] is
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
