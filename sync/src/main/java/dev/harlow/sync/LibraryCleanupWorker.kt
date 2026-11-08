package dev.harlow.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.harlow.core.data.ActiveSession
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.data.repository.LibraryRepository

@HiltWorker
class LibraryCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val activeSession: ActiveSession,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        // Signed out or signed in elsewhere since this was queued: nothing to do, and nothing may be written.
        if (activeSession.ensureConnected()?.id != serverId) return Result.success()
        return try {
            setForeground(createForegroundInfo(applicationContext.getString(R.string.cleanup_notification_text), 0, 0))
            libraryRepository.cleanupOrphans(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
                setProgressAsync(
                    workDataOf(
                        KEY_PHASE to progress.phase,
                        KEY_CURRENT to progress.current,
                        KEY_TOTAL to progress.total,
                    ),
                )
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(applicationContext.getString(R.string.cleanup_notification_text), 0, 0)

    private fun createForegroundInfo(progress: SyncProgress): ForegroundInfo {
        val phase = progress.phaseLabel(applicationContext)
        val text = if (progress.total > 0) {
            applicationContext.getString(R.string.cleanup_progress_counted, phase, progress.current, progress.total)
        } else {
            applicationContext.getString(R.string.cleanup_progress, phase)
        }
        return createForegroundInfo(text, progress.current, progress.total)
    }

    private fun createForegroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.cleanup_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(applicationContext.getString(R.string.cleanup_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)

        if (total > 0) {
            builder.setProgress(total, current, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "harlow_cleanup"
        private const val NOTIFICATION_ID = 43
    }
}
