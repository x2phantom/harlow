package dev.harlow.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One device ID per sign-in: stable across connections, new after signing out. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceIdentityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.identity() = DeviceIdentity(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("device_identity.preferences_pb")
        },
    )

    @Test
    fun `the device ID stays the same across connections`() = runTest(UnconfinedTestDispatcher()) {
        val identity = identity()

        assertEquals(identity.deviceInfo().id, identity.deviceInfo().id)
        assertEquals("Harlow", identity.deviceInfo().name)
    }

    @Test
    fun `after a reset the device ID is new`() = runTest(UnconfinedTestDispatcher()) {
        val identity = identity()
        val before = identity.deviceInfo().id

        identity.reset()

        assertNotEquals(before, identity.deviceInfo().id)
    }
}
