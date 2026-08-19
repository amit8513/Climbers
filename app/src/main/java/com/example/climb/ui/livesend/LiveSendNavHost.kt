package com.example.climb.ui.livesend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object LiveSendRoutes {
    const val ONBOARDING = "live_send_onboarding"
    const val LOGIN = "live_send_login"
    const val SIGNUP = "live_send_signup"
    const val HOME_FEED = "live_send_home_feed"
    const val EXPLORE = "live_send_explore"
    const val PROGRESS = "live_send_progress"
    const val COMMUNITY = "live_send_community"
    const val CLUB_DASHBOARD = "live_send_club_dashboard"
    const val ROUTE_DETAIL = "live_send_route_detail"
    const val PROFILE = "live_send_profile"
}

/**
 * HomeFeed / Explore / Progress / Community act as 4 peer "tabs". Unlike
 * [com.example.climb.navigation.MemberClubNavHost], these screens each render their own
 * bottom-bar chrome internally (it's part of the screen's own composable, not a Scaffold-level
 * slot owned by this NavHost) — so this helper's only job is the `popUpTo` anchor when hopping
 * between them, always relative to HomeFeed, the first tab reached after auth.
 */
private fun navigateToLiveSendTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(LiveSendRoutes.HOME_FEED) { inclusive = false }
    }
}

/** Successful auth (login, social login, or signup) drops the whole onboarding/auth back stack. */
private fun completeAuth(navController: NavHostController) {
    navController.navigate(LiveSendRoutes.HOME_FEED) {
        popUpTo(LiveSendRoutes.ONBOARDING) { inclusive = true }
    }
}

/**
 * Standalone Navigation Compose graph wiring together the 10 "Live Send" (Alternative UI Concept
 * 2) design-exploration screens under this package. This mirrors the structural conventions of
 * [com.example.climb.navigation.MemberClubNavHost] (a private `object <X>Routes` of snake_case,
 * area-prefixed route constants; a tab-routes set; a `navigateTo<X>Tab` helper centralizing
 * `launchSingleTop` + `popUpTo`) but is otherwise entirely self-contained: it is NOT wired into
 * the app's real navigation graph, takes no [com.example.climb.AppContainer] or other production
 * dependency, and every one of the 10 screens is a pure, self-contained UI mockup (no repository
 * data, no ViewModels) — so, unlike `MemberClubNavHost`, there is nothing to load up front and no
 * shared Scaffold chrome to own here; each screen already renders its own full-screen bottom bar.
 *
 * Flow:
 * - Onboarding is the start destination; "Get Started" -> Signup, "Log in" -> Login.
 * - Login/Signup can navigate to each other; successful auth (incl. Google/Apple) lands on
 *   HomeFeed with the whole onboarding/auth back stack cleared ([completeAuth]).
 * - HomeFeed, Explore, Progress and Community act as the 4 peer "tabs" — each one's own bottom
 *   bar can jump directly to any of the others via [navigateToLiveSendTab]. The bottom bar's
 *   "Club" tab lands on Explore (browsing venues/routes is the climber-facing "club" content);
 *   full staff Club Mode is a separate, deliberately-entered destination (see below).
 * - Explore's route/venue rows push RouteDetail (the screen renders static content and takes no
 *   route/venue id, so both row-kinds land on the same destination); RouteDetail's own bottom bar
 *   also participates in the 4-way tab hop, and its "← Back" pops back to Explore.
 * - HomeFeed's avatar pushes Profile; Profile's "Enter Club Mode →" pushes ClubDashboard (staff
 *   Club Mode), and ClubDashboard's "Switch"/"Exit" both leave Club Mode back to HomeFeed. Since
 *   ClubDashboard has no matching Members/Venues/Broadcast screens in this batch, only its
 *   Routes tile/tab is wired (to Explore, the closest fit); the rest are intentionally inert.
 * - Callbacks with no matching destination in this 10-screen batch (record/video FABs, search,
 *   forgot-password, change-photo, the in-page app-mode/appearance/feed-filter toggles that only
 *   report local UI state) are intentionally left as no-ops rather than guessing at a screen that
 *   doesn't exist yet.
 */
@Composable
fun LiveSendNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LiveSendRoutes.ONBOARDING,
        modifier = modifier,
    ) {
        composable(LiveSendRoutes.ONBOARDING) {
            OnboardingScreen(
                onGetStarted = { navController.navigate(LiveSendRoutes.SIGNUP) },
                onLogin = { navController.navigate(LiveSendRoutes.LOGIN) },
            )
        }
        composable(LiveSendRoutes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLogin = { _, _ -> completeAuth(navController) },
                onForgotPassword = { /* no forgot-password screen in this batch */ },
                onGoogleLogin = { completeAuth(navController) },
                onAppleLogin = { completeAuth(navController) },
                onCreateAccount = { navController.navigate(LiveSendRoutes.SIGNUP) { launchSingleTop = true } },
            )
        }
        composable(LiveSendRoutes.SIGNUP) {
            SignupScreen(
                onBack = { navController.popBackStack() },
                onLogin = { navController.navigate(LiveSendRoutes.LOGIN) { launchSingleTop = true } },
                onCreateAccount = { _, _, _, _ -> completeAuth(navController) },
            )
        }
        composable(LiveSendRoutes.HOME_FEED) {
            HomeFeedScreen(
                onProfileClick = { navController.navigate(LiveSendRoutes.PROFILE) },
                onForYouTabClick = { /* in-page feed filter, already selected */ },
                onClubsTabClick = { /* in-page feed filter */ },
                onPlayVideo = { /* no video player screen in this batch */ },
                onRecordClick = { /* no record/log-attempt screen in this batch */ },
                onFeedTabClick = { navigateToLiveSendTab(navController, LiveSendRoutes.HOME_FEED) },
                onProgressTabClick = { navigateToLiveSendTab(navController, LiveSendRoutes.PROGRESS) },
                onRanksTabClick = { navigateToLiveSendTab(navController, LiveSendRoutes.COMMUNITY) },
                onClubTabClick = { navigateToLiveSendTab(navController, LiveSendRoutes.EXPLORE) },
            )
        }
        composable(LiveSendRoutes.EXPLORE) {
            ExploreScreen(
                onSearchClick = { /* no search screen in this batch */ },
                onRouteClick = { navController.navigate(LiveSendRoutes.ROUTE_DETAIL) },
                onZoneClick = { navController.navigate(LiveSendRoutes.ROUTE_DETAIL) },
                onNavigateFeed = { navigateToLiveSendTab(navController, LiveSendRoutes.HOME_FEED) },
                onNavigateHistory = { navigateToLiveSendTab(navController, LiveSendRoutes.PROGRESS) },
                onNavigateRanks = { navigateToLiveSendTab(navController, LiveSendRoutes.COMMUNITY) },
                onFabClick = { /* no record screen in this batch */ },
            )
        }
        composable(LiveSendRoutes.PROGRESS) {
            ProgressScreen(
                onFeedClick = { navigateToLiveSendTab(navController, LiveSendRoutes.HOME_FEED) },
                onProgressClick = { navigateToLiveSendTab(navController, LiveSendRoutes.PROGRESS) },
                onRanksClick = { navigateToLiveSendTab(navController, LiveSendRoutes.COMMUNITY) },
                onClubClick = { navigateToLiveSendTab(navController, LiveSendRoutes.EXPLORE) },
                onLogAttempt = { /* no log-attempt screen in this batch */ },
            )
        }
        composable(LiveSendRoutes.COMMUNITY) {
            CommunityScreen(
                onNavigateFeed = { navigateToLiveSendTab(navController, LiveSendRoutes.HOME_FEED) },
                onNavigateProgress = { navigateToLiveSendTab(navController, LiveSendRoutes.PROGRESS) },
                onNavigateRanks = { navigateToLiveSendTab(navController, LiveSendRoutes.COMMUNITY) },
                onNavigateClub = { navigateToLiveSendTab(navController, LiveSendRoutes.EXPLORE) },
                onFabClick = { /* no record screen in this batch */ },
            )
        }
        composable(LiveSendRoutes.ROUTE_DETAIL) {
            RouteDetailScreen(
                onBack = { navController.popBackStack() },
                onPlayVideo = { /* no video player screen in this batch */ },
                onLogAttempt = { /* no log-attempt screen in this batch */ },
                onRecordAttempt = { /* no record screen in this batch */ },
                onFeedTab = { navigateToLiveSendTab(navController, LiveSendRoutes.HOME_FEED) },
                onHistoryTab = { navigateToLiveSendTab(navController, LiveSendRoutes.PROGRESS) },
                onRanksTab = { navigateToLiveSendTab(navController, LiveSendRoutes.COMMUNITY) },
            )
        }
        composable(LiveSendRoutes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onChangePhoto = { /* no photo-picker screen in this batch */ },
                onAppModeSelected = { /* local UI state only, screen tracks its own selection */ },
                onAppearanceSelected = { /* local UI state only, screen tracks its own selection */ },
                onEnterClubMode = { navController.navigate(LiveSendRoutes.CLUB_DASHBOARD) },
            )
        }
        composable(LiveSendRoutes.CLUB_DASHBOARD) {
            ClubDashboardScreen(
                onSwitchToNormalMode = {
                    navController.navigate(LiveSendRoutes.HOME_FEED) {
                        popUpTo(LiveSendRoutes.CLUB_DASHBOARD) { inclusive = true }
                    }
                },
                onManageRoutes = { navController.navigate(LiveSendRoutes.EXPLORE) },
                onManageMembers = { /* no members-management screen in this batch */ },
                onManageVenues = { /* no venues-management screen in this batch */ },
                onManageBroadcast = { /* no broadcast screen in this batch */ },
                onNavRoutes = { navController.navigate(LiveSendRoutes.EXPLORE) },
                onNavBroadcast = { /* no broadcast screen in this batch */ },
                onNavMembers = { /* no members-management screen in this batch */ },
                onExit = {
                    navController.navigate(LiveSendRoutes.HOME_FEED) {
                        popUpTo(LiveSendRoutes.CLUB_DASHBOARD) { inclusive = true }
                    }
                },
            )
        }
    }
}
