package com.example.climb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.Configuration

class ClimbApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
    }

    /** Required on Android O+ before any notification can show at all — must exist before
     * [com.example.climb.notifications.ClimbMessagingService] ever receives a push, so this runs
     * unconditionally at app startup rather than lazily on first use. Re-creating an
     * already-existing channel (matching id) is a documented no-op, so this is safe to call every
     * launch. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            getString(R.string.default_notification_channel_id),
            "Climb",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Club updates and chat messages" }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
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
