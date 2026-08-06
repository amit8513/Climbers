package com.example.climb

import android.content.Context
import android.os.Environment
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.data.ClimbDatabase
import com.example.climb.data.ClimbRepository
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.leaderboard.data.LeaderboardRepository
import com.example.climb.leaderboard.data.LocalLeaderboardRepository
import com.example.climb.pose.MediaPipePoseEstimator
import com.example.climb.pose.PoseEstimator
import com.example.climb.sharing.ClimbSyncRepository
import com.example.climb.sharing.FriendClimbsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class AppContainer(context: Context) {
    val climbRepository: ClimbRepository by lazy {
        ClimbRepository(ClimbDatabase.getInstance(context).climbDao())
    }

    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepository(ClimbDatabase.getInstance(context).analysisDao())
    }

    val poseEstimator: PoseEstimator by lazy {
        MediaPipePoseEstimator(context)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(FirebaseAuth.getInstance())
    }

    val socialRepository: SocialRepository by lazy {
        SocialRepository(FirebaseFirestore.getInstance())
    }

    val climbSyncRepository: ClimbSyncRepository by lazy {
        ClimbSyncRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
    }

    val friendClimbsRepository: FriendClimbsRepository by lazy {
        FriendClimbsRepository(FirebaseFirestore.getInstance())
    }

    val firebaseStorage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val moviesDir: File by lazy {
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).apply {
            mkdirs()
        }
    }

    fun moviesDirFor(uid: String): File = File(moviesDir, uid).apply { mkdirs() }

    private val leaderboardRepositories = mutableMapOf<String, LeaderboardRepository>()

    /** Keyed by uid so the in-memory cache inside [LocalLeaderboardRepository] survives
     * navigating away from the leaderboard and back, rather than refetching every time. */
    fun leaderboardRepositoryFor(uid: String, displayName: String): LeaderboardRepository =
        leaderboardRepositories.getOrPut(uid) { LocalLeaderboardRepository(climbRepository, socialRepository, friendClimbsRepository, uid, displayName) }
}
