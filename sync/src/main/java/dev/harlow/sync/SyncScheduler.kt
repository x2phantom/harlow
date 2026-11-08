package dev.harlow.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.data.preferences.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferences,
) : SignOutCleanup {
    private val workManager = WorkManager.getInstance(context)

    fun syncNow(serverId: String): UUID {
        val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_SYNC)
            .build()
        workManager.beginUniqueWork(
            SYNC_CHAIN_NAME,
            ExistingWorkPolicy.KEEP,
            syncRequest,
        ).enqueue()
        return syncRequest.id
    }

    fun cleanupNow(serverId: String): UUID {
        val cleanupRequest = OneTimeWorkRequestBuilder<LibraryCleanupWorker>()
            .setInputData(workDataOf(LibraryCleanupWorker.KEY_SERVER_ID to serverId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_CLEANUP)
            .build()
        workManager.beginUniqueWork(
            SYNC_CHAIN_NAME,
            ExistingWorkPolicy.APPEND,
            cleanupRequest,
        ).enqueue()
        return cleanupRequest.id
    }

    suspend fun schedulePeriodicSync(serverId: String) {
        val intervalHours = syncPreferences.autoSyncIntervalHours.first()
        if (intervalHours == 0) {
            cancelPeriodicSync()
            return
        }
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncRequest(serverId, intervalHours),
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    /**
     * Signing out: cancels every sync and cleanup and waits (up to [STOP_TIMEOUT_MS]) until none runs anymore, so no
     * worker writes into the database after it was cleared. Workers also check the session before writing anything.
     */
    override suspend fun stopBeforeSignOut() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        workManager.cancelUniqueWork(SYNC_CHAIN_NAME)
        workManager.cancelAllWorkByTag(TAG_SYNC)
        workManager.cancelAllWorkByTag(TAG_CLEANUP)
        val stopped = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            combine(
                workManager.getWorkInfosByTagFlow(TAG_SYNC),
                workManager.getWorkInfosByTagFlow(TAG_CLEANUP),
            ) { sync, cleanup -> (sync + cleanup).none { it.state == WorkInfo.State.RUNNING } }
                .first { it }
        }
        if (stopped == null) Log.w(TAG, "Sync still running after cancelling it")
    }

    /** Signing out: drops the finished work records (they hold the server ID). */
    override suspend fun clearOnSignOut() {
        workManager.pruneWork()
    }

    fun observeSyncState(): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow(TAG_SYNC)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }

    fun observeSyncProgress(): Flow<SyncProgress?> {
        return workManager.getWorkInfosByTagFlow(TAG_SYNC)
            .map { workInfos ->
                val running = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                running?.progress?.let { data ->
                    val phase = data.getString(LibrarySyncWorker.KEY_PHASE) ?: return@let null
                    SyncProgress(
                        phase,
                        data.getInt(LibrarySyncWorker.KEY_CURRENT, 0),
                        data.getInt(LibrarySyncWorker.KEY_TOTAL, 0),
                    )
                }
            }
    }

    fun observeCleanupState(): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow(TAG_CLEANUP)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }

    companion object {
        internal fun periodicSyncRequest(serverId: String, intervalHours: Int): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            // The first run waits one interval: whoever schedules this after signing in also syncs right away
            // (syncNow), and a periodic request runs at once otherwise — the first sync ran twice, in parallel.
            return PeriodicWorkRequestBuilder<LibrarySyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setInitialDelay(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
                .addTag(TAG_SYNC)
                .build()
        }

        private const val TAG = "SyncScheduler"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SYNC_CHAIN_NAME = "harlow_sync_chain"
        private const val PERIODIC_SYNC_WORK_NAME = "harlow_periodic_sync"
        private const val TAG_SYNC = "harlow_sync"
        private const val TAG_CLEANUP = "harlow_cleanup"
    }
}
