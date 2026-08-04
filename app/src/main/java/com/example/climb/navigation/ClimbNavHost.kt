package com.example.climb.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.climb.AppContainer
import com.example.climb.ui.detail.DetailScreen
import com.example.climb.ui.home.HomeScreen
import com.example.climb.ui.record.RecordScreen
import com.example.climb.ui.tag.TagScreen

private object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val TAG = "tag/{videoPath}/{durationMs}"
    const val DETAIL = "detail/{climbId}"

    fun tag(videoPath: String, durationMs: Long) = "tag/${Uri.encode(videoPath)}/$durationMs"
    fun detail(climbId: Long) = "detail/$climbId"
}

@Composable
fun ClimbNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = container.climbRepository,
                onRecordClick = { navController.navigate(Routes.RECORD) },
                onClimbClick = { id -> navController.navigate(Routes.detail(id)) },
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
