package dev.harlow.feature.settings

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.network.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var licensesOpened = 0
    private var loggedOut = 0
    private var synced = 0
    private var offline: Boolean? = null
    private var quality: StreamQuality? = null
    private var autoResume: Boolean? = null
    private var listView: Boolean? = null

    private fun setScreen(connectionState: ConnectionState = ConnectionState.Connected) {
        compose.setContent {
            SettingsScreen(
                appVersion = "1.2.3",
                serverUrl = "https://jellyfin.example",
                connectionState = connectionState,
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
                onSyncNow = { synced++ },
                onCleanup = {},
                onForceOfflineChange = { offline = it },
                onAutoSyncIntervalChange = {},
                onStreamQualityChange = { quality = it },
                onAutoResumeChange = { autoResume = it },
                onListViewChange = { listView = it },
                onLicensesClick = { licensesOpened++ },
                onLogout = { loggedOut++ },
            )
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `choosing a streaming quality reports it`() {
        setScreen()
        scrollTo("Streaming quality")
        compose.onNodeWithText("Streaming quality").performClick()
        compose.onNodeWithText("160 kbps").performClick()

        assertEquals(StreamQuality.KBPS_160, quality)
    }

    @Test
    fun `original quality notes its data and storage use`() {
        setScreen()
        scrollTo("Streaming quality")
        compose.onNodeWithText("Streaming quality").performClick()

        // The note belongs to the "Original" option (the settings row shows the current choice, too).
        compose.onNodeWithText("Lossless", substring = true).performClick()
        assertEquals(StreamQuality.ORIGINAL, quality)
    }

    @Test
    fun `the resume switch reports its state`() {
        setScreen()
        scrollTo("Resume automatically")
        compose.onNodeWithText("Resume automatically").performClick()

        assertEquals(true, autoResume)
    }

    @Test
    fun `the list view switch reports its state`() {
        setScreen()
        scrollTo("List view")
        compose.onNodeWithText("List view").performClick()

        assertEquals(true, listView)
    }

    @Test
    fun `shows server, sync, offline, version and licenses`() {
        setScreen()
        compose.onNodeWithText("jellyfin.example").assertIsDisplayed()
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Sync now").assertIsDisplayed()
        compose.onNodeWithText("Work offline").assertIsDisplayed()
        scrollTo("1.2.3")
        compose.onNodeWithText("1.2.3").assertIsDisplayed()
        scrollTo("Open-source licenses")
        compose.onNodeWithText("Open-source licenses").assertIsDisplayed()
    }

    @Test
    fun `licenses row opens the licenses`() {
        setScreen()
        scrollTo("Open-source licenses")
        compose.onNodeWithText("Open-source licenses").performClick()
        assertEquals(1, licensesOpened)
    }

    @Test
    fun `sync now syncs while connected`() {
        setScreen()
        compose.onNodeWithText("Sync now").performClick()
        assertEquals(1, synced)
    }

    @Test
    fun `sync now is disabled without the server`() {
        setScreen(ConnectionState.ServerUnreachable)
        compose.onNodeWithText("Server not reachable").assertIsDisplayed()
        compose.onNodeWithText("Sync now").assertIsNotEnabled().performClick()
        assertEquals(0, synced)
    }

    @Test
    fun `work offline toggles`() {
        setScreen()
        compose.onNodeWithText("Work offline").performClick()
        assertEquals(true, offline)
    }

    @Test
    fun `sign out asks first`() {
        setScreen()
        scrollTo("Sign out")
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(0, loggedOut)
        // Row title and dialog title and button
        compose.onAllNodesWithText("Sign out")[2].performClick()
        assertEquals(1, loggedOut)
    }

    @Test
    fun `rows are big enough to tap in the car`() {
        setScreen()
        compose.onNodeWithText("Sync now").assertHeightIsAtLeast(76.dp)
    }
}
