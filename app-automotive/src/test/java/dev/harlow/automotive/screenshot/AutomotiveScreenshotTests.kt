package dev.harlow.automotive.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.harlow.automotive.R
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.designsystem.login.LoginScreen
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.network.ConnectionState
import dev.harlow.feature.settings.LicensesScreen
import dev.harlow.feature.settings.SettingsScreen
import org.junit.Test

/**
 * The app's own screens on AAOS: sign-in and settings (browsing and playback are the car's media center's). One
 * subclass per head unit, see AutomotiveScreenshotDevices.kt.
 */
abstract class AutomotiveScreenshotTests : ScreenshotCapture() {

    @Test
    fun loginDefault() = capture("login-default") {
        LoginScreen(logo = painterResource(R.drawable.ic_launcher_foreground), onClose = {})
    }

    @Test
    fun loginLoading() = capture("login-loading") {
        LoginScreen(logo = painterResource(R.drawable.ic_launcher_foreground), isLoading = true, onClose = {})
    }

    @Test
    fun loginError() = capture("login-error") {
        LoginScreen(logo = painterResource(R.drawable.ic_launcher_foreground), error = "Invalid username or password", onClose = {})
    }

    @Test
    fun settingsDefault() = capture("settings-default") {
        Settings()
    }

    @Test
    fun settingsSyncing() = capture("settings-syncing") {
        Settings(isSyncing = true)
    }

    @Test
    fun licensesEmpty() = capture("licenses-empty") {
        LicensesScreen(communityLibraries = emptyList(), platformLibraries = emptyList(), onBack = {})
    }

    @Test
    fun settingsOffline() = capture("settings-offline") {
        Settings(connectionState = ConnectionState.ServerUnreachable)
    }

    @Composable
    private fun Settings(
        isSyncing: Boolean = false,
        connectionState: ConnectionState = ConnectionState.Connected,
    ) {
        SettingsScreen(
            appVersion = "1.0.0",
            serverUrl = "https://jellyfin.example.com",
            connectionState = connectionState,
            lastSyncTimestamp = 0L,
            isSyncing = isSyncing,
            syncProgress = if (isSyncing) SyncProgress(phase = "albums", current = 120, total = 480) else null,
            isCleaningUp = false,
            isForceOffline = false,
            autoSyncIntervalHours = 6,
            streamQuality = StreamQuality.DEFAULT,
            autoResume = false,
            listView = false,
            onBack = {},
            onSyncNow = {},
            onCleanup = {},
            onForceOfflineChange = {},
            onAutoSyncIntervalChange = {},
            onStreamQualityChange = {},
            onAutoResumeChange = {},
            onListViewChange = {},
            onLicensesClick = {},
            onLogout = {},
        )
    }
}
