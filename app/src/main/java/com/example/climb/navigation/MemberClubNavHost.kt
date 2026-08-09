package com.example.climb.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.climb.AppContainer
import com.example.climb.ui.clubs.ClubLeaderboardScreen
import com.example.climb.ui.clubs.ClubRoutesScreen
import com.example.climb.ui.clubs.ClubUpdatesScreen
import com.example.climb.ui.clubs.ClubVideosScreen
import com.example.climb.ui.nav.ClubBarTab
import com.example.climb.ui.nav.ClubBottomBar
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private object MemberClubRoutes {
    const val ROUTES = "member_club_routes"
    const val UPDATES = "member_club_updates"
    const val VIDEOS = "member_club_videos"
    const val LEADERBOARD = "member_club_leaderboard"
}

private val MEMBER_CLUB_TAB_ROUTES = setOf(
    MemberClubRoutes.ROUTES, MemberClubRoutes.UPDATES, MemberClubRoutes.VIDEOS, MemberClubRoutes.LEADERBOARD,
)

private fun navigateToMemberClubTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(MemberClubRoutes.ROUTES) { inclusive = false }
    }
}

/**
 * What an approved member sees once they open a club they belong to — a dedicated floating bar
 * (Routes / Updates / My club videos / Club leaderboard), separate from both the normal climber
 * shell and the staff Club Mode shell. Reached from "Your gyms" in [com.example.climb.ui.clubs.ClubsScreen].
 */
@Composable
fun MemberClubNavHost(container: AppContainer, currentUid: String, organizationId: Long, onBack: () -> Unit) {
    val organization by container.clubRepository.observeOrganization(organizationId).collectAsStateWithLifecycle(initialValue = null)
    val org = organization

    if (org == null) {
        Box(modifier = Modifier.fillMaxSize().wallTexture(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ClimbPalette.textSecondary)
        }
        return
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = ClimbPalette.bg,
        topBar = {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp).clickable(onClick = onBack),
            )
        },
        bottomBar = {
            if (currentRoute in MEMBER_CLUB_TAB_ROUTES) {
                ClubBottomBar(
                    tabs = listOf(
                        ClubBarTab(Icons.Filled.Terrain, "Routes", currentRoute == MemberClubRoutes.ROUTES) { navigateToMemberClubTab(navController, MemberClubRoutes.ROUTES) },
                        ClubBarTab(Icons.Filled.Campaign, "Updates", currentRoute == MemberClubRoutes.UPDATES) { navigateToMemberClubTab(navController, MemberClubRoutes.UPDATES) },
                        ClubBarTab(Icons.Filled.VideoLibrary, "My club videos", currentRoute == MemberClubRoutes.VIDEOS) { navigateToMemberClubTab(navController, MemberClubRoutes.VIDEOS) },
                        ClubBarTab(Icons.Filled.EmojiEvents, "Club leaderboard", currentRoute == MemberClubRoutes.LEADERBOARD) { navigateToMemberClubTab(navController, MemberClubRoutes.LEADERBOARD) },
                    ),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MemberClubRoutes.ROUTES,
            modifier = Modifier.padding(padding),
        ) {
            composable(MemberClubRoutes.ROUTES) {
                ClubRoutesScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = org, isStaff = false)
            }
            composable(MemberClubRoutes.UPDATES) {
                ClubUpdatesScreen(currentUid = currentUid, clubRepository = container.clubRepository, organization = org, isStaff = false)
            }
            composable(MemberClubRoutes.VIDEOS) {
                ClubVideosScreen(currentUid = currentUid, analysisRepository = container.analysisRepository, organization = org)
            }
            composable(MemberClubRoutes.LEADERBOARD) {
                ClubLeaderboardScreen(clubRepository = container.clubRepository, organization = org)
            }
        }
    }
}
