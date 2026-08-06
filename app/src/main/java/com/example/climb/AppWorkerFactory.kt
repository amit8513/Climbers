package com.example.climb

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.climb.analysis.PoseAnalysisWorker
import com.example.climb.sharing.ClimbSyncWorker

/**
 * The app has no Hilt (manual [AppContainer] DI throughout), so background workers' dependencies
 * are supplied here instead of via `@HiltWorker`/`@AssistedInject`. One factory for every worker
 * in the app — WorkManager only supports a single registered [WorkerFactory] per process.
 */
class AppWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        PoseAnalysisWorker::class.java.name -> PoseAnalysisWorker(
            appContext,
            workerParameters,
            container.analysisRepository,
            container.poseEstimator,
        )
        ClimbSyncWorker::class.java.name -> ClimbSyncWorker(
            appContext,
            workerParameters,
            container.climbRepository,
            container.climbSyncRepository,
        )
        else -> null
    }
}
