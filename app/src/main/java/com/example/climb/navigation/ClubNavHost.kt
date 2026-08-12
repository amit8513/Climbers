package com.example.climb.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.climb.AppContainer
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.data.social.UserProfile
import com.example.climb.ui.home.HomeScreen
import com.example.climb.ui.livesend.ActivityItem
import com.example.climb.ui.livesend.ClubDashboardScreen
import com.example.climb.ui.livesend.ExploreSection
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.livesend.real.LiveSendBroadcastScreen
import com.example.climb.ui.livesend.real.LiveSendCamerasScreen
import com.example.climb.ui.livesend.real.LiveSendClubExploreHost
import com.example.climb.ui.livesend.real.LiveSendMembersScreen
import com.example.climb.ui.settings.SettingsScreen
import com.example.climb.ui.theme.ClimbPalette

private object ClubRoutes {
    const val MANAGE = "club_manage"
    const val UPDATES = "club_updates"
    const val MEMBERS = "club_members"
    const val EXPLORE = "club_explore/{section}"
    fun explore(section: String) = "club_explore/$section"
    const val CAMERAS = "club_cameras"
    // A real destination inside THIS NavHost's own back stack (not an AppMode switch) — see the
    // doc comment below for why that distinction matters.
    const val HOME_PREVIEW = "club_home_preview"
    const val SETTINGS_PREVIEW = "club_settings_preview"
}

private fun navigateToClubTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(ClubRoutes.MANAGE) { inclusive = false }
    }
}

/**
 * The dedicated Club Mode shell — a separate NavHost from the normal climber experience, entered
 * only via the post-login mode switcher or Settings' "Club Mode" section, and only ever reachable
 * by a STAFF/ADMIN member of [organization]. [onExitClub] is a real `AppMode` switch back to
 * Normal Mode — a one-way jump with no back stack connecting the two, since Club/Normal are two
 * separate top-level NavHost compositions, not part of one shared NavController. That's the right
 * behavior for an explicit "Exit Club Mode"/"Switch" action, but every plain "Home" tab/icon in
 * this shell used to call it too, which meant tapping Home was ALSO a one-way exit — there was no
 * way to see Home and come back to the Dashboard. Home now instead navigates to [ClubRoutes.HOME_PREVIEW],
 * a real composable destination inside THIS NavHost's own back stack, so it behaves like normal
 * in-app navigation: showing the real Home screen, poppable straight back to the Dashboard by
 * system Back (since [ClubRoutes.MANAGE] is the start destination, popping HOME_PREVIEW lands
 * there automatically) — [onExitClub] itself stays wired only to the screens' explicit
 * "Exit"/"Switch" affordances, which remain genuine, permanent exits.
 *
 * All 6 destinations render their own full-screen "Live Send" chrome (their own floating bottom
 * bar baked in per screen, except the two real-screen previews below, which use the real app's own
 * chrome) rather than a shared Scaffold bottomBar — per user request, no Club Mode screen shows the
 * old-style bottom bar anymore, so there's no outer Scaffold bottomBar slot left to gate by route.
 */
@Composable
fun ClubNavHost(container: AppContainer, currentUid: String, profile: UserProfile, organization: OrganizationEntity, onExitClub: () -> Unit) {
    val navController = rememberNavController()
    val goHome = { navController.navigate(ClubRoutes.HOME_PREVIEW) }

    Scaffold(containerColor = ClimbPalette.bg) { padding ->
        NavHost(
            navController = navController,
            startDestination = ClubRoutes.MANAGE,
            modifier = Modifier.padding(padding),
        ) {
            composable(ClubRoutes.MANAGE) {
                val members by container.clubRepository.observeMembersForOrganization(organization.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val pendingRequests by container.clubRepository.observePendingJoinRequests(organization.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val clubStats by container.clubRepository.observeClubLeaderboard(organization.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val updates by container.clubRepository.observeUpdatesForOrganization(organization.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val orgInitial = organization.name.firstOrNull()?.uppercase() ?: "?"

                ClubDashboardScreen(
                    clubName = organization.name,
                    memberCount = members.size,
                    pendingJoinRequestCount = pendingRequests.size,
                    totalSends = clubStats.sumOf { it.totalSends },
                    recentUpdates = updates.take(5).map { update ->
                        ActivityItem(initial = orgInitial, text = update.text, timeAgo = formatRelativeTime(update.createdAt))
                    },
                    onSwitchToNormalMode = onExitClub,
                    onGoHome = goHome,
                    onManageRoutes = { navController.navigate(ClubRoutes.explore("routes")) },
                    onManageMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                    onManageVenues = { navController.navigate(ClubRoutes.CAMERAS) },
                    onManageBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onNavRoutes = { navController.navigate(ClubRoutes.explore("routes")) },
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onNavMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                    onExit = onExitClub,
                )
            }
            composable(
                route = ClubRoutes.EXPLORE,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { backStackEntry ->
                val section = backStackEntry.arguments?.getString("section")
                LiveSendClubExploreHost(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    onClubTab = { navController.popBackStack(ClubRoutes.MANAGE, inclusive = false) },
                    onGoHome = goHome,
                    isStaff = true,
                    initialSection = if (section == "venues") ExploreSection.VENUES else ExploreSection.ROUTES,
                )
            }
            composable(ClubRoutes.UPDATES) {
                LiveSendBroadcastScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    isStaff = true,
                    onGoHome = goHome,
                    onExitClub = onExitClub,
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onNavMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                )
            }
            composable(ClubRoutes.MEMBERS) {
                LiveSendMembersScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    onGoHome = goHome,
                    onExitClub = onExitClub,
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                )
            }
            composable(ClubRoutes.CAMERAS) {
                LiveSendCamerasScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    onGoHome = goHome,
                    onExitClub = onExitClub,
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onNavMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                )
            }
            composable(ClubRoutes.HOME_PREVIEW) {
                HomeScreen(
                    repository = container.climbRepository,
                    currentUid = currentUid,
                    profile = profile,
                    settingsStore = container.settingsStore,
                    // Opening a clicked climb's real DetailScreen (ExoPlayer playback, color-
                    // isolation editing, sharing, the pose-analysis section) needs its own
                    // destination + several more container dependencies — a bigger addition than
                    // this fix's scope (making Home itself reachable-and-poppable). Left a no-op
                    // rather than a shallow partial implementation.
                    onClimbClick = { /* TODO(live-send-real): real climb detail from inside Club Mode's Home preview */ },
                    onSettingsClick = { navController.navigate(ClubRoutes.SETTINGS_PREVIEW) },
                )
            }
            composable(ClubRoutes.SETTINGS_PREVIEW) {
                SettingsScreen(
                    uid = currentUid,
                    profile = profile,
                    socialRepository = container.socialRepository,
                    authRepository = container.authRepository,
                    settingsStore = container.settingsStore,
                    onBack = { navController.popBackStack() },
                    // "Open Clubs" (browsing/joining other orgs) is ClimbNavHost's own `clubs`
                    // route, a completely different NavHost not reachable from inside this shell —
                    // left a no-op rather than fabricating a destination that doesn't exist here.
                    onOpenClubs = { },
                    // Real, not fabricated — we already know this staffer belongs to exactly this
                    // one org, since we're rendering their Club Mode shell for it right now.
                    staffOrganizations = listOf(organization),
                    // No-op: selecting this org here would mean "enter Club Mode for it," which is
                    // already exactly where we are.
                    onEnterClubMode = { },
                )
            }
        }
    }
}
