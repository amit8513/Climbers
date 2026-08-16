package com.example.climb.notifications

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.climb.R
import com.example.climb.data.social.SocialRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Real push notifications — a new club update or club chat message triggers a Cloud Function
 * (see `functions/src/index.ts`) that sends a push to every other member's stored token, and this
 * service is what actually shows it on the device / keeps that token current.
 *
 * Constructed outside [com.example.climb.AppContainer] deliberately: Android instantiates
 * [FirebaseMessagingService] subclasses itself (no constructor control, so no DI), matching how
 * every other Android-framework-owned class in this app (e.g. `PoseAnalysisWorker` via its own
 * `AppWorkerFactory`) already has to work without Hilt.
 *
 * Known, accepted limitation: a token is only ever added ([SocialRepository.updateFcmToken] uses
 * `arrayUnion`), never removed — an uninstalled app or a token FCM itself has invalidated stays in
 * `users/{uid}.fcmTokens` until [Firebase Cloud Messaging errors out sending to it — the Cloud
 * Function is expected to just ignore/log that per-token failure rather than treat it as fatal for
 * the rest of the send]. Pruning stale tokens would need either a scheduled Cloud Function or
 * handling the send-time error server-side; not built here since a slightly-too-large fanout list
 * only wastes a few no-op sends, it doesn't send a wrong or duplicate notification to anyone.
 */
class ClimbMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val socialRepository = SocialRepository(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
        CoroutineScope(Dispatchers.IO).launch { socialRepository.updateFcmToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Cloud Functions sends a plain "notification" payload (see functions/src/index.ts), but
        // data-only fallback keeps this working even if that ever changes to a data-only message
        // (e.g. to control exact display behavior when the app is in the foreground).
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Android 13+ requires this permission at runtime (see HomeScreen's request flow) —
            // silently skipping here, rather than crashing, is the correct behavior for a user who
            // denied it: no notification, not a broken one.
            return
        }
        val channelId = getString(R.string.default_notification_channel_id)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
