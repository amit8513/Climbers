package com.example.climb.ui.livesend

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.ui.livesend.components.GradeBadge
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
 * button) when one exists, a 3-up stats row, a "Log Attempt" CTA, and this concept's floating
 * bottom nav + record FAB.
 *
 * Real-data note ([com.example.climb.ui.livesend.real.LiveSendClubExploreHost]): the original mock
 * showed 3 hardcoded *personal* stats (send rate / peak grade / sessions for the viewer alone).
 * The real backing data ([com.example.climb.clubs.RouteStatsEntity], via
 * [com.example.climb.clubs.ClubRepository.observeRouteStats]) is club-wide, not per-viewer, so the
 * stats row here shows real club-wide send rate/attempts/sends instead of fabricating personal
 * numbers — see [sendRatePercent]/[totalAttempts]/[totalSends]. The mock's fake "live" indicator
 * and fake "0:41" duration on the beta-video card are dropped for the same reason: neither has a
 * real backing value, so [betaVideoAvailable] just gates whether the card (a plain play affordance)
 * shows at all.
 */
@Composable
fun RouteDetailScreen(
    onBack: () -> Unit,
    onPlayVideo: () -> Unit,
    onLogAttempt: () -> Unit,
    onRecordAttempt: () -> Unit,
    // Matches ExploreScreen's showRecordFab — real Club Mode never shows the record-a-climb FAB
    // (staff aren't logging their own climbs from here), the mock preview and any future non-club
    // caller keep the FAB by default.
    showRecordFab: Boolean = true,
    onFeedTab: () -> Unit,
    onProgressTab: () -> Unit,
    onRanksTab: () -> Unit,
    onClubTab: () -> Unit,
    // Defaults reproduce the original mock content exactly, so
    // com.example.climb.ui.livesend.LiveSendNavHost (the untouched preview) keeps compiling and
    // rendering identically without passing any of these explicitly.
    routeName: String = "Blue Route",
    vGrade: Int? = 7,
    // Real route color lives on its latest RouteVersionEntity (colorHex), looked up live by the
    // caller (com.example.climb.ui.livesend.real.LiveSendClubExploreHost) — null (this default)
    // falls back to GradeBadge's own default accent, same as before color existed.
    routeColorHex: Long? = null,
    sendRatePercent: Int? = 100,
    totalAttempts: Int = 2,
    totalSends: Int = 2,
    betaVideoAvailable: Boolean = true,
    // The real playable URL — null means "play inline" isn't possible, so tapping play falls back
    // to onPlayVideo (preserves prior behavior for the mock preview and any caller not yet passing
    // a real URL).
    betaVideoUrl: String? = null,
    // Staff-only "set/replace beta video" action — real upload mechanics (media picker + upload +
    // attaching the URL to the route) live in the caller
    // (com.example.climb.ui.livesend.real.LiveSendClubExploreHost), since launching a picker needs
    // its own @Composable call site; this screen just renders the affordance. Defaults preserve
    // the mock preview and the member-facing context (isStaff=false there) unchanged.
    isStaff: Boolean = false,
    onUploadBeta: () -> Unit = {},
    // Real users who've sent this route (com.example.climb.clubs.ClubRepository.observeRouteCompletions),
    // most-recent-first — see RouteCompletionRow's doc comment for why this is a plain chronological
    // list rather than a fabricated ranking score. Empty default keeps the mock preview unaffected.
    completions: List<RouteCompletionRow> = emptyList(),
    // Real member-shared attempt videos for this route (com.example.climb.clubs.ClubRepository.observeSharedAttemptsForRoute),
    // shown below the staff beta video so a member can watch how other real members climbed it,
    // not just the one official beta take — see SharedAttemptRow. Empty default keeps the mock
    // preview unaffected.
    sharedAttempts: List<SharedAttemptRow> = emptyList(),
    onToggleLike: (SharedAttemptRow) -> Unit = {},
    // False in the member shell, where the outer MemberClubNavHost's own shared floating island
    // already shows for this tab (per user request that every floating island in Club Mode stay
    // consistent, rather than this screen's own distinct Home/Progress/Ranks/Club bar). Staff
    // Club Mode has no such shared chrome, so it keeps rendering its own bar (default true, also
    // preserving the untouched mock preview).
    showOwnBottomBar: Boolean = true,
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

            RouteTitleRow(routeName = routeName, vGrade = vGrade, routeColorHex = routeColorHex)

            Spacer(modifier = Modifier.height(20.dp))

            if (betaVideoAvailable) {
                BetaVideoCard(onPlayVideo = onPlayVideo, videoUrl = betaVideoUrl)
                if (isStaff) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Replace beta video",
                        color = ClimbPalette.liveSendAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onUploadBeta)
                            .semantics { role = Role.Button },
                    )
                }
            } else if (isStaff) {
                LiveSendCard(cornerRadius = 16, padding = 16) {
                    Column {
                        Text(
                            text = "No beta video yet. Show members how to climb this route.",
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveSendPrimaryButton(text = "Set Beta Video", onClick = onUploadBeta, height = 44)
                    }
                }
            } else {
                LiveSendCard(cornerRadius = 16, padding = 16) {
                    Text(text = "No beta video yet.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explicit "N of M succeeded" line — spelled out plainly rather than relying on the
            // "Send Rate %" stat tile alone, which is climbing jargon a non-climber (or a staffer
            // just glancing at this) could misread.
            if (totalAttempts > 0) {
                Text(
                    text = "$totalSends of $totalAttempts logged attempts succeeded",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            RouteStatsRow(sendRatePercent = sendRatePercent, totalAttempts = totalAttempts, totalSends = totalSends)

            Spacer(modifier = Modifier.height(20.dp))

            SentByRow(completions = completions)

            Spacer(modifier = Modifier.height(20.dp))

            SharedAttemptsSection(attempts = sharedAttempts, onToggleLike = onToggleLike)

            Spacer(modifier = Modifier.height(20.dp))

            LiveSendPrimaryButton(
                text = "Log Attempt",
                onClick = onLogAttempt,
                height = 50,
                modifier = Modifier.semantics { contentDescription = "Log attempt" },
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showOwnBottomBar) {
                LiveSendBottomBar(
                    tabs = listOf(
                        // Was "Feed" (no real feed screen exists in club mode) — real Home now.
                        LiveSendNavTab(icon = Icons.Filled.Home, label = "Home", selected = false, onClick = onFeedTab),
                        LiveSendNavTab(icon = Icons.Filled.ShowChart, label = "Progress", selected = false, onClick = onProgressTab),
                        LiveSendNavTab(icon = Icons.Filled.EmojiEvents, label = "Ranks", selected = false, onClick = onRanksTab),
                        LiveSendNavTab(icon = Icons.Filled.Groups, label = "Club", selected = true, onClick = onClubTab),
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (showRecordFab) {
                LiveSendFab(
                    onClick = onRecordAttempt,
                    icon = Icons.Filled.Videocam,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
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

/** Route name + V-grade badge (5:428–5:430), colored to the route's real set color when it has
 * one (falls back to GradeBadge's own default accent otherwise). */
@Composable
private fun RouteTitleRow(routeName: String, vGrade: Int?, routeColorHex: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = routeName,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
        )
        if (vGrade != null) {
            if (routeColorHex != null) {
                val bg = Color(routeColorHex)
                GradeBadge(grade = "V$vGrade", cornerRadius = 14.dp, containerColor = bg, contentColor = if (bg.luminance() > 0.5f) Color.Black else Color.White)
            } else {
                GradeBadge(grade = "V$vGrade", cornerRadius = 14.dp)
            }
        }
    }
}

/**
 * The beta video card (5:431–5:433, 38:638–38:640). When a real [videoUrl] is available, tapping
 * play switches this card to a real inline player instead of just firing [onPlayVideo] — same
 * ExoPlayer setup/lifecycle as [com.example.climb.ui.clubs.ClubRouteDetailScreen]'s
 * `BetaVideoPlayer` (`ExoPlayer.Builder` + `MediaItem.fromUri` + `PlayerView` via `AndroidView`,
 * released in `onDispose`), reused rather than reinvented. Without a real URL (the mock preview,
 * or any caller not yet wired to one), tapping play just calls [onPlayVideo] as before.
 */
@Composable
private fun BetaVideoCard(onPlayVideo: () -> Unit, videoUrl: String?) {
    var isPlaying by remember(videoUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)

    if (isPlaying && videoUrl != null) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(335f / 420f).clip(shape)) {
            BetaVideoPlayer(videoUrl = videoUrl)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(335f / 420f)
            .clip(shape)
            .background(ClimbPalette.liveSendSurface),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(70.dp)
                .clip(CircleShape)
                .background(ClimbPalette.mediaScrim)
                .clickable(onClick = { if (videoUrl != null) isPlaying = true else onPlayVideo() })
                .semantics {
                    contentDescription = "Play video"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = ClimbPalette.liveSendTextPrimary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** Real ExoPlayer playback — same mechanism as
 * [com.example.climb.ui.clubs.ClubRouteDetailScreen]'s `BetaVideoPlayer`, copied here since that
 * one is `private` in its own file. */
@Composable
private fun BetaVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxSize(),
    )
}

// TODO(live-send-real): a per-route "top rank by fastest completion time" leaderboard, requested
// alongside the stats above, is NOT buildable from existing data. ClimbAttemptEntity/
// ClimbAnalysisEntity (which carry the real routeId link and real climbStartMs/climbEndMs timing)
// live entirely in the local Room database — AnalysisRepository's own doc comment says outright
// "this never needed to move to Firestore." Every climber's attempt/analysis history is private to
// their own phone; there is no cross-user query surface at all to rank against. Building this for
// real would mean adding a new sync path (uploading a per-attempt route+duration summary to
// Firestore, keyed by route, similar to RouteStatsEntity) — a genuine new backend feature, not a
// small additive query, so it's left undone here rather than faked with placeholder rankings.

/**
 * Real club-wide send-rate / attempts / sends stat row (5:434–5:439 slots, repurposed — see the
 * file doc comment for why these are club-wide rather than the mock's personal numbers). Unlike
 * ClubDashboard's stat cards, these three are each a single uniform Bold 13sp two-line text block
 * with no giant number and no uppercase caption — so this uses the plain [LiveSendCard] surface
 * directly rather than the big-number/caption [LiveSendStatCard].
 */
@Composable
private fun RouteStatsRow(sendRatePercent: Int?, totalAttempts: Int, totalSends: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        StatBlock(text = "${sendRatePercent?.let { "$it%" } ?: "—"}\nSend Rate", modifier = Modifier.weight(1f))
        StatBlock(text = "$totalAttempts\nAttempts", modifier = Modifier.weight(1f))
        StatBlock(text = "$totalSends\nSends", modifier = Modifier.weight(1f))
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

/** One real user who has sent this route — [completedAt] is a real timestamp, not a rank; there's
 * no real per-user tiebreak metric (see the TODO above) to rank sends by, so this is presented as
 * a plain most-recent-first list rather than a fabricated "#1/#2/#3" ranking. */
data class RouteCompletionRow(val userDisplayName: String, val completedAt: Long)

/** "Sent by" — who has actually completed this route, most-recent-first
 * (com.example.climb.clubs.ClubRepository.observeRouteCompletions), documented via the one real
 * flow that both logs a climb AND links it to this route (ClimbDetailsInputScreen's route picker +
 * "Sent this climb" switch — plain in-app tagging alone has no route picker, so it can't produce
 * one of these). Bounded-height internal scroll past ~2 rows, matching every other growing real
 * list in this package (ClubDashboardScreen's activity feed, etc.) rather than stretching the page. */
@Composable
private fun SentByRow(completions: List<RouteCompletionRow>) {
    Column {
        Text(
            text = "Sent by (${completions.size})",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (completions.isEmpty()) {
            Text(
                text = "No one has logged a send for this route yet.",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )
        } else {
            Column(
                modifier = Modifier.heightIn(max = 138.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                completions.forEach { completion ->
                    LiveSendCard(cornerRadius = 14, padding = 14) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = completion.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = formatRelativeTime(completion.completedAt), color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** One member-shared attempt video plus its live like state — see
 * [com.example.climb.clubs.ClubRepository.observeSharedAttemptsForRoute]/[com.example.climb.clubs.ClubRepository.observeLikesForSharedAttempt].
 * [likeCount]/[likedByViewer] are derived from a real observed list of likes, not a denormalized
 * counter (see [com.example.climb.clubs.SharedAttemptLikeEntity]'s doc comment for why). */
data class SharedAttemptRow(
    val id: Long,
    val userDisplayName: String,
    val videoUrl: String,
    val completed: Boolean,
    val flash: Boolean,
    val likeCount: Int,
    val likedByViewer: Boolean,
)

/** "Member sends" — real videos other members shared of their own attempts on this exact route,
 * alongside (not instead of) the staff beta video above. Same bounded-height internal scroll
 * convention as [SentByRow]. Each card plays inline on tap (same [BetaVideoPlayer] reuse as
 * [BetaVideoCard]) rather than a separate screen, and carries its own like button. */
@Composable
private fun SharedAttemptsSection(attempts: List<SharedAttemptRow>, onToggleLike: (SharedAttemptRow) -> Unit) {
    Column {
        Text(
            text = "Member sends (${attempts.size})",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (attempts.isEmpty()) {
            Text(
                text = "No member videos shared for this route yet.",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
            )
        } else {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                attempts.forEach { row -> SharedAttemptCard(row = row, onToggleLike = { onToggleLike(row) }) }
            }
        }
    }
}

@Composable
private fun SharedAttemptCard(row: SharedAttemptRow, onToggleLike: () -> Unit) {
    var isPlaying by remember(row.id) { mutableStateOf(false) }
    LiveSendCard(cornerRadius = 16, padding = 14) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = row.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = if (row.flash) "Flash" else if (row.completed) "Sent" else "Fell",
                        color = if (row.completed) ClimbPalette.sent else ClimbPalette.fell,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onToggleLike)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (row.likedByViewer) "Unlike" else "Like"
                        },
                ) {
                    Icon(
                        imageVector = if (row.likedByViewer) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (row.likedByViewer) ClimbPalette.liveSendCta else ClimbPalette.liveSendTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "${row.likeCount}", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))) {
                    BetaVideoPlayer(videoUrl = row.videoUrl)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClimbPalette.liveSendSurface)
                        .clickable { isPlaying = true }
                        .semantics { role = Role.Button; contentDescription = "Watch video" },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ClimbPalette.liveSendTextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Watch", color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
