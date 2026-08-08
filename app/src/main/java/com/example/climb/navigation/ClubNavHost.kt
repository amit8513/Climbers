package com.example.climb.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.climb.AppContainer
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.clubs.ClubMembersScreen
import com.example.climb.ui.clubs.ClubRoutesScreen
import com.example.climb.ui.clubs.ClubUpdatesScreen
import com.example.climb.ui.nav.ClubBarTab
import com.example.climb.ui.nav.ClubBottomBar
import com.example.climb.ui.theme.ClimbPalette

private object ClubRoutes {
    const val MANAGE = "club_manage"
    const val UPDATES = "club_updates"
    const val MEMBERS = "club_members"
}

private val CLUB_TAB_ROUTES = setOf(ClubRoutes.MANAGE, ClubRoutes.UPDATES, ClubRoutes.MEMBERS)

private fun navigateToClubTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(ClubRoutes.MANAGE) { inclusive = false }
    }
}

/**
 * The dedicated Club Mode shell — a separate NavHost/bottom bar from the normal climber
 * experience, entered only via the post-login mode switcher or Settings' "Club Mode" section, and
 * only ever reachable by a STAFF/ADMIN member of [organization]. [onExitClub] is the one required
 * way back to Normal Mode (the bar's "Exit" tab).
 */
@Composable
fun ClubNavHost(container: AppContainer, currentUid: String, organization: OrganizationEntity, onExitClub: () -> Unit) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = ClimbPalette.bg,
        bottomBar = {
            if (currentRoute in CLUB_TAB_ROUTES) {
                ClubBottomBar(
                    tabs = listOf(
                        ClubBarTab(Icons.Filled.Handyman, "Manage", currentRoute == ClubRoutes.MANAGE) { navigateToClubTab(navController, ClubRoutes.MANAGE) },
                        ClubBarTab(Icons.Filled.Campaign, "Updates", currentRoute == ClubRoutes.UPDATES) { navigateToClubTab(navController, ClubRoutes.UPDATES) },
                        ClubBarTab(Icons.Filled.Group, "Members", currentRoute == ClubRoutes.MEMBERS) { navigateToClubTab(navController, ClubRoutes.MEMBERS) },
                        ClubBarTab(Icons.AutoMirrored.Filled.Logout, "Exit Club Mode", false, onExitClub),
                    ),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ClubRoutes.MANAGE,
            modifier = Modifier.padding(padding),
        ) {
            composable(ClubRoutes.MANAGE) {
                ClubRoutesScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = organization, isStaff = true)
            }
            composable(ClubRoutes.UPDATES) {
                ClubUpdatesScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = organization, isStaff = true)
            }
            composable(ClubRoutes.MEMBERS) {
                ClubMembersScreen(
                    currentUid = currentUid,
                    clubRepository = container.clubRepository,
                    socialRepository = container.socialRepository,
                    organization = organization,
                )
            }
        }
    }
}
