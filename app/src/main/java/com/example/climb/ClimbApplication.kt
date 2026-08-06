package com.example.climb

import android.app.Application
import androidx.work.Configuration

class ClimbApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // No Hilt in this app (manual AppContainer DI throughout), so background workers need their
    // dependencies from a custom WorkerFactory instead of @HiltWorker — this requires disabling
    // WorkManager's default auto-init (see the <provider> override in AndroidManifest.xml) and
    // relying on WorkManager's on-demand initialization, which calls this property lazily.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(container))
            .build()
}
