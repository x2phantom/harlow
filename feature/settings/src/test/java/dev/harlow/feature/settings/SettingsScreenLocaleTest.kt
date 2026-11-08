package dev.harlow.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.network.ConnectionState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** German is the default language: everything but an English system language shows it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenLocaleTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen() {
        compose.setContent {
            SettingsScreen(
                appVersion = "1.2.3",
                serverUrl = "https://jellyfin.example",
                connectionState = ConnectionState.Connected,
                lastSyncTimestamp = 0L,
                isSyncing = false,
                syncProgress = null,
                isCleaningUp = false,
                isForceOffline = false,
                autoSyncIntervalHours = 6,
                streamQuality = StreamQuality.ORIGINAL,
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

    private fun assertGerman() {
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithText("Jetzt synchronisieren").assertIsDisplayed()
        compose.onNodeWithText("Alle 6 Stunden").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-w1280dp-h800dp-land")
    fun `a German system shows German`() {
        setScreen()
        assertGerman()
    }

    @Test
    @Config(qualifiers = "fr-w1280dp-h800dp-land")
    fun `a system language without translation falls back to German`() {
        setScreen()
        assertGerman()
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-land")
    fun `an English system shows English`() {
        setScreen()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Every 6 hours").assertIsDisplayed()
    }
}
