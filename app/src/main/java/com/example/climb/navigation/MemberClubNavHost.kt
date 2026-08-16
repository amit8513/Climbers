package com.example.climb.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.climb.AppContainer
import com.example.climb.ui.clubs.ClubAttemptVideoScreen
import com.example.climb.ui.clubs.ClubChatScreen
import com.example.climb.ui.clubs.ClubLeaderboardScreen
import com.example.climb.ui.clubs.ClubOverviewScreen
import com.example.climb.ui.clubs.ClubVideosScreen
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.real.LiveSendBroadcastScreen
import com.example.climb.ui.livesend.real.LiveSendClubExploreHost
import com.example.climb.ui.progress.ProgressScreen
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private object MemberClubRoutes {
    const val OVERVIEW = "member_club_overview"
    const val ROUTES = "member_club_routes"
    const val UPDATES = "member_club_updates"
    const val VIDEOS = "member_club_videos"
    const val CHAT = "member_club_chat"
    const val LEADERBOARD = "member_club_leaderboard"
    // Same real push/pop destination pattern as the staff shell's Home/Progress previews — the
    // Routes tab's "Progress" bottom-bar tab had no real destination before.
    const val PROGRESS_PREVIEW = "member_club_progress_preview"
    const val ATTEMPT_VIDEO = "member_club_attempt_video/{attemptId}"
    fun attemptVideo(attemptId: Long) = "member_club_attempt_video/$attemptId"
}

// Every top-level member tab shows the same shared floating island (per user request that all
// of Club Mode's floating islands stay consistent) — ROUTES and UPDATES used to render their own
// distinct internal bottom bar instead (a leftover from before they were member-shell tabs); that
// internal bar is now suppressed for the member (non-staff) context so this shared one shows
// through instead, matching Overview/Videos/Chat/Leaderboard.
private val MEMBER_CLUB_TAB_ROUTES = setOf(
    MemberClubRoutes.OVERVIEW, MemberClubRoutes.ROUTES, MemberClubRoutes.UPDATES,
    MemberClubRoutes.VIDEOS, MemberClubRoutes.CHAT, MemberClubRoutes.LEADERBOARD,
)

private fun navigateToMemberClubTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(MemberClubRoutes.OVERVIEW) { inclusive = false }
    }
}

/**
 * What an approved member sees once they open a club they belong to — a dedicated floating bar
 * (Overview / Routes / Updates / My club videos / Club leaderboard), separate from both the
 * normal climber shell and the staff Club Mode shell. Reached from "Your gyms" in
 * [com.example.climb.ui.clubs.ClubsScreen]. Overview is the landing tab — "what's happening at my
 * gym today" — added as the smallest addition to this bar's existing four tabs.
 */
@Composable
fun MemberClubNavHost(container: AppContainer, currentUid: String, currentUsername: String, organizationId: Long, onBack: () -> Unit) {
    val organization by container.clubRepository.observeOrganization(organizationId).collectAsStateWithLifecycle(initialValue = null)
    val org = organization

    if (org == null) {
        Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ClimbPalette.liveSendTextMuted)
        }
        return
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // containerColor matches whichever palette the current route actually shows — every route here
    // is liveSend-styled EXCEPT PROGRESS_PREVIEW, which intentionally shows the real app's own
    // theme-reactive ProgressScreen chrome — so nothing mismatched ever peeks through behind the
    // system bar insets.
    Scaffold(
        containerColor = if (currentRoute == MemberClubRoutes.PROGRESS_PREVIEW) ClimbPalette.bg else ClimbPalette.liveSendBg,
        topBar = {
            // Hidden only on ATTEMPT_VIDEO — that screen renders its own "← Back" control, and
            // showing both at once was a real reported bug (two back rows stacked on the video
            // screen). PROGRESS_PREVIEW has no back control of its own, so it keeps this one.
            if (currentRoute != MemberClubRoutes.ATTEMPT_VIDEO) {
                // Every screen's back goes to Overview, the shell's own landing tab — only
                // Overview's back exits the shell entirely, back to the Clubs list.
                val backTarget = if (currentRoute == MemberClubRoutes.OVERVIEW) {
                    onBack
                } else {
                    { navigateToMemberClubTab(navController, MemberClubRoutes.OVERVIEW) }
                }
                Text(
                    text = "← Back",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp).clickable(onClick = backTarget),
                )
            }
        },
        bottomBar = {
            if (currentRoute in MEMBER_CLUB_TAB_ROUTES) {
                LiveSendBottomBar(
                    tabs = listOf(
                        LiveSendNavTab(Icons.Filled.Home, "Overview", currentRoute == MemberClubRoutes.OVERVIEW) { navigateToMemberClubTab(navController, MemberClubRoutes.OVERVIEW) },
                        LiveSendNavTab(Icons.Filled.Terrain, "Routes", currentRoute == MemberClubRoutes.ROUTES) { navigateToMemberClubTab(navController, MemberClubRoutes.ROUTES) },
                        LiveSendNavTab(Icons.Filled.Campaign, "Updates", currentRoute == MemberClubRoutes.UPDATES) { navigateToMemberClubTab(navController, MemberClubRoutes.UPDATES) },
                        LiveSendNavTab(Icons.Filled.VideoLibrary, "Videos", currentRoute == MemberClubRoutes.VIDEOS) { navigateToMemberClubTab(navController, MemberClubRoutes.VIDEOS) },
                        LiveSendNavTab(Icons.AutoMirrored.Filled.Chat, "Chat", currentRoute == MemberClubRoutes.CHAT) { navigateToMemberClubTab(navController, MemberClubRoutes.CHAT) },
                        LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", currentRoute == MemberClubRoutes.LEADERBOARD) { navigateToMemberClubTab(navController, MemberClubRoutes.LEADERBOARD) },
                    ),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MemberClubRoutes.OVERVIEW,
            modifier = Modifier.padding(padding),
        ) {
            composable(MemberClubRoutes.OVERVIEW) {
                ClubOverviewScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = org)
            }
            composable(MemberClubRoutes.ROUTES) {
                LiveSendClubExploreHost(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = org,
                    onClubTab = { navigateToMemberClubTab(navController, MemberClubRoutes.OVERVIEW) },
                    onGoHome = onBack,
                    onProgressTab = { navController.navigate(MemberClubRoutes.PROGRESS_PREVIEW) },
                    onRanksTab = { navigateToMemberClubTab(navController, MemberClubRoutes.LEADERBOARD) },
                )
            }
            composable(MemberClubRoutes.PROGRESS_PREVIEW) {
                ProgressScreen(repository = container.climbRepository, currentUid = currentUid)
            }
            composable(MemberClubRoutes.UPDATES) {
                LiveSendBroadcastScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = org,
                    isStaff = false,
                    onGoHome = onBack,
                )
            }
            composable(MemberClubRoutes.VIDEOS) {
                ClubVideosScreen(
                    currentUid = currentUid,
                    analysisRepository = container.analysisRepository,
                    organization = org,
                    onAttemptClick = { attempt -> navController.navigate(MemberClubRoutes.attemptVideo(attempt.id)) },
                )
            }
            composable(
                route = MemberClubRoutes.ATTEMPT_VIDEO,
                arguments = listOf(navArgument("attemptId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val attemptId = backStackEntry.arguments?.getLong("attemptId") ?: return@composable
                val attempt by container.analysisRepository.observeAttempt(attemptId).collectAsStateWithLifecycle(initialValue = null)
                val currentAttempt = attempt
                if (currentAttempt == null) {
                    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                        Text("Loading…", color = ClimbPalette.liveSendTextMuted)
                    }
                } else {
                    ClubAttemptVideoScreen(attempt = currentAttempt, onBack = { navController.popBackStack() })
                }
            }
            composable(MemberClubRoutes.CHAT) {
                ClubChatScreen(
                    currentUid = currentUid,
                    currentUsername = currentUsername,
                    clubRepository = container.clubRepository,
                    organization = org,
                )
            }
            composable(MemberClubRoutes.LEADERBOARD) {
                ClubLeaderboardScreen(clubRepository = container.clubRepository, organization = org)
            }
        }
    }
}
