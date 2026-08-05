package com.example.climb.analysis

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.climb.AppContainer

/**
 * The app has no Hilt (manual [AppContainer] DI throughout), so [PoseAnalysisWorker]'s
 * dependencies are supplied here instead of via `@HiltWorker`/`@AssistedInject`.
 */
class AnalysisWorkerFactory(private val container: AppContainer) : WorkerFactory() {
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
        else -> null
    }
}
