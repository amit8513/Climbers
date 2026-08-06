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
import com.example.climb.ui.analysis.AnalysisProgressScreen
import com.example.climb.ui.analysis.AnalysisResultScreen
import com.example.climb.ui.analysis.ClimbDetailsInputScreen
import com.example.climb.ui.analysis.VideoSourceScreen
import com.example.climb.ui.auth.AuthScreen
import com.example.climb.ui.auth.ProfileSetupScreen
import com.example.climb.ui.detail.DetailScreen
import com.example.climb.ui.friends.FriendClimbsScreen
import com.example.climb.ui.friends.FriendsScreen
import com.example.climb.ui.home.HomeScreen
import com.example.climb.ui.leaderboard.LeaderboardScreen
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
    const val LEADERBOARD = "leaderboard"
    const val RECORD = "record"
    const val TAG = "tag/{videoPath}/{durationMs}"
    const val DETAIL = "detail/{climbId}"
    const val VIDEO_SOURCE = "video_source"
    const val ANALYSIS_RECORD = "analysis_record"
    const val CLIMB_DETAILS_INPUT = "climb_details_input/{videoPath}/{durationMs}/{sourceClimbId}"
    const val ANALYSIS_PROGRESS = "analysis_progress/{attemptId}"
    const val ANALYSIS_RESULT = "analysis_result/{analysisId}"
    const val FRIEND_CLIMBS = "friend_climbs/{friendUid}/{friendUsername}"

    fun tag(videoPath: String, durationMs: Long) = "tag/${Uri.encode(videoPath)}/$durationMs"
    fun detail(climbId: Long) = "detail/$climbId"
    fun climbDetailsInput(videoPath: String, durationMs: Long, sourceClimbId: Long = -1L) =
        "climb_details_input/${Uri.encode(videoPath)}/$durationMs/$sourceClimbId"
    fun analysisProgress(attemptId: Long) = "analysis_progress/$attemptId"
    fun analysisResult(analysisId: Long) = "analysis_result/$analysisId"
    fun friendClimbs(friendUid: String, friendUsername: String) = "friend_climbs/$friendUid/${Uri.encode(friendUsername)}"
}

/** Destinations that are bottom-bar tabs, and so keep the bar visible. */
private val TAB_ROUTES = setOf(Routes.HOME, Routes.PROGRESS, Routes.LEADERBOARD, Routes.FRIENDS)

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
            if (currentRoute in TAB_ROUTES) {
                ClimbBottomBar(
                    selectedRoute = currentRoute,
                    onHomeClick = { navigateToTab(navController, Routes.HOME) },
                    onProgressClick = { navigateToTab(navController, Routes.PROGRESS) },
                    onFriendsClick = { navigateToTab(navController, Routes.FRIENDS) },
                    onLeaderboardClick = { navigateToTab(navController, Routes.LEADERBOARD) },
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
                    currentUid = currentUid,
                    onClimbClick = { id -> navController.navigate(Routes.detail(id)) },
                    onAnalyzeClick = { navController.navigate(Routes.VIDEO_SOURCE) },
                )
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(repository = container.climbRepository, currentUid = currentUid)
            }

            composable(Routes.FRIENDS) {
                FriendsScreen(
                    currentUid = currentUid,
                    currentUsername = profile.username,
                    socialRepository = container.socialRepository,
                    authRepository = container.authRepository,
                    onFriendClick = { friend -> navController.navigate(Routes.friendClimbs(friend.uid, friend.username)) },
                )
            }

            composable(
                route = Routes.FRIEND_CLIMBS,
                arguments = listOf(
                    navArgument("friendUid") { type = NavType.StringType },
                    navArgument("friendUsername") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val friendUid = backStackEntry.arguments?.getString("friendUid").orEmpty()
                val friendUsername = Uri.decode(backStackEntry.arguments?.getString("friendUsername").orEmpty())
                FriendClimbsScreen(
                    friendUsername = friendUsername,
                    friendUid = friendUid,
                    friendClimbsRepository = container.friendClimbsRepository,
                    firebaseStorage = container.firebaseStorage,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.LEADERBOARD) {
                LeaderboardScreen(
                    currentUid = currentUid,
                    leaderboardRepository = container.leaderboardRepositoryFor(currentUid, profile.username),
                )
            }

            composable(Routes.RECORD) {
                RecordScreen(
                    moviesDir = container.moviesDirFor(currentUid),
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
                    currentUid = currentUid,
                    currentUsername = profile.username,
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
                    currentUid = currentUid,
                    currentUsername = profile.username,
                    analysisRepository = container.analysisRepository,
                    onDeleted = { navController.popBackStack(Routes.HOME, false) },
                    onStartAnalysis = { path, duration, sourceClimbId ->
                        navController.navigate(Routes.climbDetailsInput(path, duration, sourceClimbId))
                    },
                    onViewAnalysisProgress = { attemptId -> navController.navigate(Routes.analysisProgress(attemptId)) },
                    onViewAnalysisResult = { analysisId -> navController.navigate(Routes.analysisResult(analysisId)) },
                )
            }

            composable(Routes.VIDEO_SOURCE) {
                VideoSourceScreen(
                    repository = container.climbRepository,
                    currentUid = currentUid,
                    onRecordNew = { navController.navigate(Routes.ANALYSIS_RECORD) },
                    onExistingVideoSelected = { path, duration, sourceClimbId ->
                        navController.navigate(Routes.climbDetailsInput(path, duration, sourceClimbId))
                    },
                )
            }

            composable(Routes.ANALYSIS_RECORD) {
                RecordScreen(
                    moviesDir = container.moviesDirFor(currentUid),
                    onRecorded = { path, duration ->
                        navController.navigate(Routes.climbDetailsInput(path, duration)) {
                            popUpTo(Routes.ANALYSIS_RECORD) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.CLIMB_DETAILS_INPUT,
                arguments = listOf(
                    navArgument("videoPath") { type = NavType.StringType },
                    navArgument("durationMs") { type = NavType.LongType },
                    navArgument("sourceClimbId") { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val videoPath = Uri.decode(backStackEntry.arguments?.getString("videoPath").orEmpty())
                val durationMs = backStackEntry.arguments?.getLong("durationMs") ?: 0L
                val sourceClimbIdArg = backStackEntry.arguments?.getLong("sourceClimbId") ?: -1L
                ClimbDetailsInputScreen(
                    videoPath = videoPath,
                    durationMs = durationMs,
                    currentUid = currentUid,
                    sourceClimbId = sourceClimbIdArg.takeIf { it > 0 },
                    analysisRepository = container.analysisRepository,
                    onAnalyzeStarted = { attemptId ->
                        navController.navigate(Routes.analysisProgress(attemptId)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                )
            }

            composable(
                route = Routes.ANALYSIS_PROGRESS,
                arguments = listOf(navArgument("attemptId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val attemptId = backStackEntry.arguments?.getLong("attemptId") ?: 0L
                AnalysisProgressScreen(
                    attemptId = attemptId,
                    onComplete = { analysisId ->
                        navController.navigate(Routes.analysisResult(analysisId)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onGiveUp = { navController.popBackStack(Routes.HOME, false) },
                )
            }

            composable(
                route = Routes.ANALYSIS_RESULT,
                arguments = listOf(navArgument("analysisId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val analysisId = backStackEntry.arguments?.getLong("analysisId") ?: 0L
                AnalysisResultScreen(
                    analysisId = analysisId,
                    analysisRepository = container.analysisRepository,
                )
            }
        }
    }
}
