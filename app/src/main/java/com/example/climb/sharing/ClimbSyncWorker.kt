package com.example.climb.sharing

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.climb.data.ClimbRepository
import kotlinx.coroutines.flow.first

/**
 * Syncs one climb to (or removes it from) the cloud after a local save/update/delete. Runs on
 * Wi-Fi only by default — climb videos can be large and this shouldn't eat mobile data without
 * the user noticing. Survives navigating away and process death, same as [com.example.climb.analysis.PoseAnalysisWorker].
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
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
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
