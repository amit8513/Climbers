package com.example.climb.sharing

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.climb.data.ClimbRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first

private const val TAG = "ClimbSyncWorker"

/**
 * Syncs one climb to (or removes it from) the cloud after a local save/update/delete. Requires
 * network (any type — expedited scheduling matters more here than saving mobile data, see below).
 * Survives navigating away and process death, same as [com.example.climb.analysis.PoseAnalysisWorker].
 *
 * This device can hold more than one signed-in account's local climbs (see the per-user climb
 * scoping this app already does), but Firebase Auth only has one *active* session at a time — the
 * security rules correctly check against whoever is signed in when this worker actually runs, not
 * whoever was signed in when it was enqueued. If the account was switched away from in between,
 * every write is (correctly) rejected. Scheduled expedited to shrink that window, and this worker
 * bails out without retrying if the wrong account (or no account) is active when it runs — retrying
 * won't help until the right one signs back in, so it'd just be futile noise.
 */
class ClimbSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val climbRepository: ClimbRepository,
    private val syncRepository: ClimbSyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val username = inputData.getString(KEY_USERNAME) ?: return Result.failure()
        val climbId = inputData.getLong(KEY_CLIMB_ID, -1L)
        if (climbId <= 0L) return Result.failure()

        val activeUid = FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != userId) {
            Log.w(TAG, "Skipping sync for climb $climbId: $userId isn't the active signed-in account (active=$activeUid)")
            return Result.success()
        }

        val climb = climbRepository.observeById(climbId, userId).first()
        return runCatching {
            if (climb == null) {
                syncRepository.deleteSyncedClimb(userId, climbId)
            } else {
                syncRepository.sync(climb, username)
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val KEY_USER_ID = "userId"
        private const val KEY_USERNAME = "username"
        private const val KEY_CLIMB_ID = "climbId"

        fun uniqueWorkName(userId: String, climbId: Long) = "climb_sync_${userId}_$climbId"

        fun buildRequest(userId: String, username: String, climbId: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ClimbSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_USER_ID to userId,
                        KEY_USERNAME to username,
                        KEY_CLIMB_ID to climbId,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        /** Enqueues (or supersedes an in-flight sync of stale data for) this climb. */
        fun enqueue(workManager: androidx.work.WorkManager, userId: String, username: String, climbId: Long) {
            workManager.enqueueUniqueWork(
                uniqueWorkName(userId, climbId),
                ExistingWorkPolicy.REPLACE,
                buildRequest(userId, username, climbId),
            )
        }
    }
}
