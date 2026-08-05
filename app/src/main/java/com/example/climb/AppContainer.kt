package com.example.climb

import android.content.Context
import android.os.Environment
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.data.ClimbDatabase
import com.example.climb.data.ClimbRepository
import com.example.climb.data.social.AuthRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.pose.MediaPipePoseEstimator
import com.example.climb.pose.PoseEstimator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    val moviesDir: File by lazy {
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).apply {
            mkdirs()
        }
    }

    fun moviesDirFor(uid: String): File = File(moviesDir, uid).apply { mkdirs() }
}
