package dev.harlow.automotive

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.harlow.core.appcommon.HarlowAccounts
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.data.repository.UserRepositoryImpl
import dev.harlow.core.network.NetworkStateObserver
import dev.harlow.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * The process usually starts because the car's media center binds HarlowMediaService, not because of an activity, so
 * session-wide setup happens here: keep the periodic library sync scheduled, apply "force offline", keep the Android
 * account in line with the session and restore the Jellyfin session.
 */
@HiltAndroidApp
class AutoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var userRepository: UserRepositoryImpl
    @Inject lateinit var networkStateObserver: NetworkStateObserver
    @Inject lateinit var syncPreferences: SyncPreferences
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var accounts: HarlowAccounts

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            syncPreferences.isForceOffline.distinctUntilChanged().collect { offline ->
                networkStateObserver.setOfflineMode(offline)
            }
        }
        appScope.launch { setUpSession() }
    }

    private suspend fun setUpSession() {
        try {
            deleteLegacyNetworkPreferences()
            userRepository.secureStoredSession()
            accounts.reconcile()
            // Started for the settings or a sync rather than the media service: connect anyway, or the server check
            // has no URL and the session looks offline.
            val server = userRepository.getActiveServer() ?: return
            userRepository.restoreSession()
            networkStateObserver.refresh()
            syncScheduler.schedulePeriodicSync(server.id)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't set up the session", e)
        }
    }

    /**
     * Versions before 2026-10 stored "trust self-signed certificates" here. The option is gone (only certificates the
     * system trusts are accepted), so is its file.
     */
    private fun deleteLegacyNetworkPreferences() {
        File(filesDir, "datastore/network_preferences.preferences_pb").delete()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private companion object {
        const val TAG = "AutoApplication"
    }
}
