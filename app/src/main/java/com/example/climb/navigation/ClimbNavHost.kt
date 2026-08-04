package com.example.climb.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.AppContainer
import com.example.climb.data.social.UserProfile
import com.example.climb.ui.auth.AuthScreen
import com.example.climb.ui.auth.ProfileSetupScreen
import com.example.climb.ui.detail.DetailScreen
import com.example.climb.ui.friends.FriendsScreen
import com.example.climb.ui.home.HomeScreen
import com.example.climb.ui.nav.ClimbBottomBar
import com.example.climb.ui.progress.ProgressScreen
import com.example.climb.ui.record.RecordScreen
import com.example.climb.ui.tag.TagScreen
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.flow.map

private object Routes {
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val FRIENDS = "friends"
    const val RECORD = "record"
    const val TAG = "tag/{videoPath}/{durationMs}"
    const val DETAIL = "detail/{climbId}"

    fun tag(videoPath: String, durationMs: Long) = "tag/${Uri.encode(videoPath)}/$durationMs"
    fun detail(climbId: Long) = "detail/$climbId"
}

private sealed interface ProfileLoadState {
    object Loading : ProfileLoadState
    data class Loaded(val profile: UserProfile?) : ProfileLoadState
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(Routes.HOME) { inclusive = false }
    }
}

@Composable
fun ClimbNavHost(container: AppContainer) {
    val uid by container.authRepository.currentUserFlow.collectAsStateWithLifecycle(
        initialValue = container.authRepository.currentUid,
    )

    val currentUid = uid
    if (currentUid == null) {
        AuthScreen(authRepository = container.authRepository)
        return
    }

    val profileLoadState by container.socialRepository.observeProfile(currentUid)
        .map<UserProfile?, ProfileLoadState> { ProfileLoadState.Loaded(it) }
        .collectAsStateWithLifecycle(initialValue = ProfileLoadState.Loading)

    when (val state = profileLoadState) {
        is ProfileLoadState.Loading -> LoadingScreen()
        is ProfileLoadState.Loaded -> {
            val profile = state.profile
            if (profile == null) {
                ProfileSetupScreen(uid = currentUid, socialRepository = container.socialRepository)
            } else {
                MainNavHost(container = container, currentUid = currentUid, profile = profile)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().wallTexture(), contentAlignment = Alignment.Center) {
        Text("Loading…", color = ClimbPalette.textSecondary)
    }
}

@Composable
private fun MainNavHost(container: AppContainer, currentUid: String, profile: UserProfile) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = ClimbPalette.bg,
        bottomBar = {
            if (currentRoute == Routes.HOME || currentRoute == Routes.PROGRESS || currentRoute == Routes.FRIENDS) {
                ClimbBottomBar(
                    selectedRoute = currentRoute,
                    onHomeClick = { navigateToTab(navController, Routes.HOME) },
                    onProgressClick = { navigateToTab(navController, Routes.PROGRESS) },
                    onFriendsClick = { navigateToTab(navController, Routes.FRIENDS) },
                    onRecordClick = { navController.navigate(Routes.RECORD) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    repository = container.climbRepository,
                    onClimbClick = { id -> navController.navigate(Routes.detail(id)) },
                )
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(repository = container.climbRepository)
            }

            composable(Routes.FRIENDS) {
                FriendsScreen(
                    currentUid = currentUid,
                    currentUsername = profile.username,
                    socialRepository = container.socialRepository,
                    authRepository = container.authRepository,
                )
            }

            composable(Routes.RECORD) {
                RecordScreen(
                    moviesDir = container.moviesDir,
                    onRecorded = { path, duration ->
                        navController.navigate(Routes.tag(path, duration)) {
                            popUpTo(Routes.RECORD) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.TAG,
                arguments = listOf(
                    navArgument("videoPath") { type = NavType.StringType },
                    navArgument("durationMs") { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val videoPath = Uri.decode(backStackEntry.arguments?.getString("videoPath").orEmpty())
                val durationMs = backStackEntry.arguments?.getLong("durationMs") ?: 0L
                TagScreen(
                    videoPath = videoPath,
                    durationMs = durationMs,
                    repository = container.climbRepository,
                    onSaved = { navController.popBackStack(Routes.HOME, false) },
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("climbId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val climbId = backStackEntry.arguments?.getLong("climbId") ?: 0L
                DetailScreen(
                    climbId = climbId,
                    repository = container.climbRepository,
                    onDeleted = { navController.popBackStack(Routes.HOME, false) },
                )
            }
        }
    }
}
