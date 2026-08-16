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
import com.example.climb.ui.clubs.ClubChatScreen
import com.example.climb.ui.leaderboard.LeaderboardScreen
import com.example.climb.ui.livesend.ActivityItem
import com.example.climb.ui.livesend.ClubDashboardScreen
import com.example.climb.ui.livesend.ExploreSection
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.livesend.real.LiveSendBroadcastScreen
import com.example.climb.ui.livesend.real.LiveSendCamerasScreen
import com.example.climb.ui.livesend.real.LiveSendClubExploreHost
import com.example.climb.ui.livesend.real.LiveSendMembersScreen
import com.example.climb.ui.progress.ProgressScreen
import com.example.climb.ui.settings.SettingsScreen
import com.example.climb.ui.theme.ClimbPalette

private object ClubRoutes {
    const val MANAGE = "club_manage"
    const val UPDATES = "club_updates"
    const val MEMBERS = "club_members"
    const val EXPLORE = "club_explore/{section}"
    fun explore(section: String) = "club_explore/$section"
    const val CAMERAS = "club_cameras"
    const val CHAT = "club_chat"
    // Real destinations inside THIS NavHost's own back stack (not an AppMode switch) — see the
    // doc comment below for why that distinction matters.
    const val SETTINGS_PREVIEW = "club_settings_preview"
    const val PROGRESS_PREVIEW = "club_progress_preview"
    const val RANKS_PREVIEW = "club_ranks_preview"
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
 * separate top-level NavHost compositions, not part of one shared NavController. That stays wired
 * only to each screen's explicit "Exit"/"Switch" affordance.
 *
 * Every plain "Home" tab/icon anywhere in this shell — including the Dashboard's own — means
 * "Club Home," i.e. [ClubRoutes.MANAGE] itself (real in-app back/tab navigation via
 * [backToClubHome], not an `AppMode` switch): tapping it from the Dashboard is a harmless
 * self-navigate, and from anywhere else it's a real pop/tab-switch back to the Dashboard. The one
 * real screen still worth reaching from inside this shell without leaving it — Settings — has its
 * own dedicated icon on the Dashboard (there's no more "visit personal Home" detour to hang it
 * off of), navigating straight to [ClubRoutes.SETTINGS_PREVIEW].
 *
 * Most destinations render their own full-screen "Live Send" chrome (their own floating bottom
 * bar baked in per screen); the real-screen previews (Settings/Progress/Ranks) use the real app's
 * own chrome instead — there's no shared Scaffold bottomBar here at all anymore, per user request
 * that no Club Mode screen show the old-style bottom bar.
 */
@Composable
fun ClubNavHost(container: AppContainer, currentUid: String, profile: UserProfile, organization: OrganizationEntity, onExitClub: () -> Unit) {
    val navController = rememberNavController()
    // Every "Home" control in this shell means Club Home — the Dashboard itself.
    val backToClubHome = { navigateToClubTab(navController, ClubRoutes.MANAGE) }
    // Same real push/pop pattern as HOME_PREVIEW — Explore/RouteDetail's Progress/Ranks tabs had
    // no real destination at all before (a TODO no-op), now they show the app's own real,
    // club-palette-restyled Progress/Leaderboard screens, poppable straight back.
    val goProgress = { navController.navigate(ClubRoutes.PROGRESS_PREVIEW) }
    val goRanks = { navController.navigate(ClubRoutes.RANKS_PREVIEW) }

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
                        ActivityItem(initial = orgInitial, text = update.text, timeAgo = formatRelativeTime(update.createdAt), photoUrl = update.photoUrl)
                    },
                    onSwitchToNormalMode = onExitClub,
                    // A real no-op, not a self-navigate — this screen already IS Club Home, and
                    // navigating to the destination you're already on still visibly flashed a
                    // transition for going nowhere.
                    onGoHome = {},
                    onOpenSettings = { navController.navigate(ClubRoutes.SETTINGS_PREVIEW) },
                    onManageRoutes = { navController.navigate(ClubRoutes.explore("routes")) },
                    onManageMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                    onManageVenues = { navController.navigate(ClubRoutes.CAMERAS) },
                    onManageBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onManageChat = { navController.navigate(ClubRoutes.CHAT) },
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
                    onGoHome = backToClubHome,
                    onProgressTab = goProgress,
                    onRanksTab = goRanks,
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
                    onGoHome = backToClubHome,
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
                    onGoHome = backToClubHome,
                    onExitClub = onExitClub,
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                )
            }
            composable(ClubRoutes.CAMERAS) {
                LiveSendCamerasScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    onGoHome = backToClubHome,
                    onExitClub = onExitClub,
                    onNavBroadcast = { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                    onNavMembers = { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                )
            }
            composable(ClubRoutes.CHAT) {
                ClubChatScreen(
                    currentUid = currentUid,
                    currentUsername = profile.username,
                    clubRepository = container.clubRepository,
                    organization = organization,
                    onBack = backToClubHome,
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
            composable(ClubRoutes.PROGRESS_PREVIEW) {
                ProgressScreen(repository = container.climbRepository, currentUid = currentUid)
            }
            composable(ClubRoutes.RANKS_PREVIEW) {
                LeaderboardScreen(
                    currentUid = currentUid,
                    leaderboardRepository = container.leaderboardRepositoryFor(currentUid, profile.username),
                    // Opening a friend's shared-climb player from inside Club Mode needs its own
                    // destination + FirebaseStorage dependency — same "don't go deeper than this
                    // fix's scope" call as Home preview's onClimbClick no-op above.
                    onOpenFriendClimbs = { /* TODO(live-send-real): friend-climb playback from inside Club Mode */ },
                )
            }
        }
    }
}
