package com.example.climb

import android.app.Application
import androidx.work.Configuration
import com.example.climb.analysis.AnalysisWorkerFactory

class ClimbApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // No Hilt in this app (manual AppContainer DI throughout), so PoseAnalysisWorker needs its
    // dependencies from a custom WorkerFactory instead of @HiltWorker — this requires disabling
    // WorkManager's default auto-init (see the <provider> override in AndroidManifest.xml) and
    // relying on WorkManager's on-demand initialization, which calls this property lazily.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AnalysisWorkerFactory(container))
            .build()
}
