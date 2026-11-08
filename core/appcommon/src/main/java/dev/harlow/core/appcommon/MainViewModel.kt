package dev.harlow.core.appcommon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.data.preferences.DisplayPreferences
import dev.harlow.core.data.preferences.PlaybackPreferences
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.data.repository.UserRepositoryImpl
import dev.harlow.core.network.ConnectionState
import dev.harlow.core.network.NetworkStateObserver
import dev.harlow.sync.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether a Jellyfin server is signed in. */
enum class AuthState { CHECKING, LOGGED_IN, LOGGED_OUT }

/**
 * Session and library sync for the app's own screens. On AAOS those are only sign-in and settings: browsing and
 * playback happen in the car's media center, through the media service.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userRepository: UserRepositoryImpl,
    private val accounts: HarlowAccounts,
    private val networkStateObserver: NetworkStateObserver,
    private val syncPreferences: SyncPreferences,
    private val playbackPreferences: PlaybackPreferences,
    private val displayPreferences: DisplayPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val activeServer = userRepository.observeActiveServer()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val authState: StateFlow<AuthState> = userRepository.observeActiveServer()
        .map { server -> if (server != null) AuthState.LOGGED_IN else AuthState.LOGGED_OUT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.CHECKING)

    val serverUrl: StateFlow<String?> = activeServer
        .map { it?.url }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connectionState: StateFlow<ConnectionState> = networkStateObserver.connectionState

    val isSyncing: StateFlow<Boolean> = syncScheduler.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncProgress: StateFlow<SyncProgress?> = syncScheduler.observeSyncProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isCleaningUp: StateFlow<Boolean> = syncScheduler.observeCleanupState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lastSyncTimestamp: StateFlow<Long> = syncPreferences.lastSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val isForceOffline: StateFlow<Boolean> = syncPreferences.isForceOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoSyncIntervalHours: StateFlow<Int> = syncPreferences.autoSyncIntervalHours
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncPreferences.DEFAULT_SYNC_INTERVAL_HOURS,
        )

    val streamQuality: StateFlow<StreamQuality> = playbackPreferences.streamQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamQuality.DEFAULT)

    val autoResume: StateFlow<Boolean> = playbackPreferences.autoResume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val listView: StateFlow<Boolean> = displayPreferences.listView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Signs out and removes the account; the media service notices and asks the media center to sign in again. */
    fun logout() {
        viewModelScope.launch { accounts.signOut() }
    }

    fun syncNow() {
        val id = activeServer.value?.id ?: return
        syncScheduler.syncNow(id)
    }

    fun cleanupLibrary() {
        val id = activeServer.value?.id ?: return
        syncScheduler.cleanupNow(id)
    }

    fun setForceOffline(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setForceOffline(enabled)
            networkStateObserver.setOfflineMode(enabled)
        }
    }

    fun setStreamQuality(quality: StreamQuality) {
        viewModelScope.launch { playbackPreferences.setStreamQuality(quality) }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch { playbackPreferences.setAutoResume(enabled) }
    }

    fun setListView(enabled: Boolean) {
        viewModelScope.launch { displayPreferences.setListView(enabled) }
    }

    fun setAutoSyncInterval(hours: Int) {
        viewModelScope.launch {
            syncPreferences.setAutoSyncIntervalHours(hours)
            val id = activeServer.value?.id ?: return@launch
            syncScheduler.schedulePeriodicSync(id)
        }
    }
}
