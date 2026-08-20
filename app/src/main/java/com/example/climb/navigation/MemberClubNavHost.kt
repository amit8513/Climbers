package com.example.climb.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.climb.ui.clubs.ClubLeaderboardScreen
import com.example.climb.ui.clubs.ClubOverviewScreen
import com.example.climb.ui.clubs.ClubVideosScreen
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.real.LiveSendClubExploreHost
import com.example.climb.ui.livesend.real.LiveSendSocialScreen
import com.example.climb.ui.livesend.real.LiveSendUserProfileScreen
import com.example.climb.ui.progress.ProgressScreen
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private object MemberClubRoutes {
    const val OVERVIEW = "member_club_overview"
    const val ROUTES = "member_club_routes"
    // Social's own landing tab — Updates/Shared videos/Chat are an in-place segmented bar inside
    // this one screen (see LiveSendSocialScreen), not separate pushed destinations, per user
    // request that navigating between them never leave this screen.
    const val SOCIAL = "member_club_social"
    const val VIDEOS = "member_club_videos"
    const val LEADERBOARD = "member_club_leaderboard"
    // Same real push/pop destination pattern as the staff shell's Home/Progress previews — the
    // Routes tab's "Progress" bottom-bar tab had no real destination before.
    const val PROGRESS_PREVIEW = "member_club_progress_preview"
    const val ATTEMPT_VIDEO = "member_club_attempt_video/{attemptId}"
    fun attemptVideo(attemptId: Long) = "member_club_attempt_video/$attemptId"
    // Reached by tapping a sharer's name/avatar on a shared-video card in Social.
    const val USER_PROFILE = "member_club_user_profile/{targetUid}"
    fun userProfile(targetUid: String) = "member_club_user_profile/$targetUid"
}

// Every top-level member tab shows the same shared floating island (per user request that all
// of Club Mode's floating islands stay consistent) — ROUTES used to render its own distinct
// internal bottom bar instead (a leftover from before it was a member-shell tab); that internal
// bar is now suppressed for the member (non-staff) context so this shared one shows through
// instead, matching Overview/Social/Videos/Leaderboard.
private val MEMBER_CLUB_TAB_ROUTES = setOf(
    MemberClubRoutes.OVERVIEW, MemberClubRoutes.ROUTES, MemberClubRoutes.SOCIAL,
    MemberClubRoutes.VIDEOS, MemberClubRoutes.LEADERBOARD,
)

// Pushed (non-tab) destinations that render their own back control, so the shared topBar's
// "← Back" text is hidden for them. ROUTES is handled separately (see routesAtRoot below) since
// it's a tab on Browse but has its own back control once a sub-destination is pushed.
private val ROUTES_WITH_OWN_BACK = setOf(
    MemberClubRoutes.ATTEMPT_VIDEO,
    MemberClubRoutes.USER_PROFILE,
)

private fun navigateToMemberClubTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(MemberClubRoutes.OVERVIEW) { inclusive = false }
    }
}

/**
 * What an approved member sees once they open a club they belong to — a dedicated floating bar
 * (Overview / Routes / Social / My club videos / Club leaderboard), separate from both the normal
 * climber shell and the staff Club Mode shell. Reached from "Your gyms" in
 * [com.example.climb.ui.clubs.ClubsScreen]. Overview is the landing tab — "what's happening at my
 * gym today". Social replaced separate Updates/Chat tabs with one hub (see
 * [com.example.climb.ui.livesend.real.LiveSendSocialScreen]) — an in-place segmented bar
 * (Updates/Shared videos/Chat) within that same screen, not separate pushed destinations.
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

    // Once per real visit to this club's member shell — the real signal behind the staff
    // Statistics screen's active-member/churn-risk numbers. Fire-and-forget: a failure here (e.g.
    // offline) must never block entering Club Mode itself.
    LaunchedEffect(organizationId, currentUid) {
        container.clubRepository.recordMemberActivity(organizationId, currentUid)
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // Tracks whether the Routes tab's OWN nested NavHost (see LiveSendClubExploreHost) is sitting
    // at its root (Browse) or has pushed a sub-destination (RouteDetail/AddVenue/AddRoute steps,
    // which each render their own back control) — this outer NavHost only ever sees "ROUTES" as
    // currentRoute regardless of which inner screen is showing, so this is the only way to tell
    // the two apart and hide the shared topBar's back link solely when it would actually double up.
    var routesAtRoot by remember { mutableStateOf(true) }

    // containerColor matches whichever palette the current route actually shows — every route here
    // is liveSend-styled EXCEPT PROGRESS_PREVIEW, which intentionally shows the real app's own
    // theme-reactive ProgressScreen chrome — so nothing mismatched ever peeks through behind the
    // system bar insets.
    Scaffold(
        containerColor = if (currentRoute == MemberClubRoutes.PROGRESS_PREVIEW) ClimbPalette.bg else ClimbPalette.liveSendBg,
        topBar = {
            // Hidden on ATTEMPT_VIDEO, and on ROUTES specifically once its inner nav has pushed a
            // sub-destination with its own back control (routesAtRoot false) — showing both at
            // once was a real reported bug (two back rows stacked at once). Browse (ROUTES at its
            // root) and PROGRESS_PREVIEW have no back control of their own, so they keep this one.
            val hideTopBar = currentRoute in ROUTES_WITH_OWN_BACK || (currentRoute == MemberClubRoutes.ROUTES && !routesAtRoot)
            if (!hideTopBar) {
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
                        LiveSendNavTab(Icons.Filled.Groups, "Social", currentRoute == MemberClubRoutes.SOCIAL) { navigateToMemberClubTab(navController, MemberClubRoutes.SOCIAL) },
                        LiveSendNavTab(Icons.Filled.VideoLibrary, "Videos", currentRoute == MemberClubRoutes.VIDEOS) { navigateToMemberClubTab(navController, MemberClubRoutes.VIDEOS) },
                        LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", currentRoute == MemberClubRoutes.LEADERBOARD) { navigateToMemberClubTab(navController, MemberClubRoutes.LEADERBOARD) },
                    ),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MemberClubRoutes.OVERVIEW,
            // Consumes this Scaffold's own padding so ClubChatContent's .imePadding() (three
            // levels down) doesn't see an already-inflated ime inset stacked on top of this one.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(MemberClubRoutes.OVERVIEW) {
                ClubOverviewScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = org)
            }
            composable(MemberClubRoutes.ROUTES) {
                LiveSendClubExploreHost(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = org,
                    onGoHome = onBack,
                    onHistoryTab = { navController.navigate(MemberClubRoutes.PROGRESS_PREVIEW) },
                    onRanksTab = { navigateToMemberClubTab(navController, MemberClubRoutes.LEADERBOARD) },
                    onAtRootChanged = { routesAtRoot = it },
                    onOpenUserProfile = { targetUid -> navController.navigate(MemberClubRoutes.userProfile(targetUid)) },
                )
            }
            composable(MemberClubRoutes.PROGRESS_PREVIEW) {
                ProgressScreen(repository = container.climbRepository, currentUid = currentUid)
            }
            composable(MemberClubRoutes.SOCIAL) {
                LiveSendSocialScreen(
                    currentUid = currentUid,
                    currentUsername = currentUsername,
                    clubRepository = container.clubRepository,
                    organization = org,
                    onOpenUserProfile = { targetUid -> navController.navigate(MemberClubRoutes.userProfile(targetUid)) },
                )
            }
            composable(
                route = MemberClubRoutes.USER_PROFILE,
                arguments = listOf(navArgument("targetUid") { type = NavType.StringType }),
            ) { backStackEntry ->
                val targetUid = backStackEntry.arguments?.getString("targetUid") ?: return@composable
                LiveSendUserProfileScreen(
                    currentUid = currentUid,
                    currentUsername = currentUsername,
                    targetUid = targetUid,
                    socialRepository = container.socialRepository,
                    clubRepository = container.clubRepository,
                    onBack = { navController.popBackStack() },
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
                    ClubAttemptVideoScreen(
                        attempt = currentAttempt,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(MemberClubRoutes.LEADERBOARD) {
                ClubLeaderboardScreen(clubRepository = container.clubRepository, organization = org)
            }
        }
    }
}
