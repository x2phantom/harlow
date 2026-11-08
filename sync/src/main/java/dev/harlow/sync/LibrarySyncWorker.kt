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
import dev.harlow.core.common.HarlowResult
import dev.harlow.core.data.ArtworkPreCacher
import dev.harlow.core.data.ActiveSession
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.data.repository.LibraryRepository
import dev.harlow.core.data.repository.PlaylistRepository

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val activeSession: ActiveSession,
    private val syncPreferences: SyncPreferences,
    private val artworkPreCacher: ArtworkPreCacher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        // Signed out or signed in elsewhere since this was queued: nothing to do, and nothing may be written.
        if (activeSession.ensureConnected()?.id != serverId) return Result.success()
        return try {
            setForeground(createForegroundInfo(applicationContext.getString(R.string.sync_notification_text), 0, 0))

            val homeImageIdsResult = libraryRepository.syncHomeScreenPriority(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
            }
            val homeImageIds = (homeImageIdsResult as? HarlowResult.Success)?.data ?: emptySet()
            prefetchImages(homeImageIds)

            libraryRepository.syncLibrary(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
                setProgressAsync(
                    workDataOf(
                        KEY_PHASE to progress.phase,
                        KEY_CURRENT to progress.current,
                        KEY_TOTAL to progress.total,
                    ),
                )
            }
            // After the library, so the playlists' tracks are already there; also removes playlists deleted in Jellyfin.
            setForegroundAsync(createForegroundInfo(SyncProgress(phase = "playlists", current = 0, total = 0)))
            playlistRepository.syncPlaylists(serverId)
            artworkPreCacher.preCacheArtwork(serverId) { progress ->
                setForegroundAsync(createForegroundInfo(progress))
                setProgressAsync(
                    workDataOf(
                        KEY_PHASE to progress.phase,
                        KEY_CURRENT to progress.current,
                        KEY_TOTAL to progress.total,
                    ),
                )
            }
            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(applicationContext.getString(R.string.sync_notification_text), 0, 0)

    private fun createForegroundInfo(progress: SyncProgress): ForegroundInfo {
        val phase = progress.phaseLabel(applicationContext)
        val text = if (progress.total > 0) {
            applicationContext.getString(R.string.sync_progress_counted, phase, progress.current, progress.total)
        } else {
            applicationContext.getString(R.string.sync_progress, phase)
        }
        return createForegroundInfo(text, progress.current, progress.total)
    }

    private fun createForegroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
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

    private suspend fun prefetchImages(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        artworkPreCacher.preCacheIds(itemIds)
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "harlow_sync"
        private const val NOTIFICATION_ID = 42
    }
}
