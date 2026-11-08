package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.data.ActiveSession
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.core.database.DatabaseWiper
import dev.harlow.core.database.TokenCipher
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.model.Server
import dev.harlow.core.network.DeviceIdentity
import dev.harlow.core.network.JellyfinClientWrapper
import dev.harlow.core.network.datasource.JellyfinDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

/** Signing out leaves the app as after a fresh install, even when parts of it fail. */
class SignOutTest {

    private val jellyfinClient = mockk<JellyfinClientWrapper>(relaxed = true)
    private val dataSource = mockk<JellyfinDataSource>(relaxed = true)
    private val activeSession = mockk<ActiveSession>(relaxed = true)
    private val databaseWiper = mockk<DatabaseWiper>(relaxed = true)
    private val tokenCipher = mockk<TokenCipher>(relaxed = true)
    private val deviceIdentity = mockk<DeviceIdentity>(relaxed = true)
    private val serverDao = mockk<ServerDao>(relaxed = true)
    private val sync = mockk<SignOutCleanup>(relaxed = true)
    private val caches = mockk<SignOutCleanup>(relaxed = true)

    private val repository = UserRepositoryImpl(
        jellyfinClient = jellyfinClient,
        jellyfinDataSource = dataSource,
        activeSession = activeSession,
        databaseWiper = databaseWiper,
        tokenCipher = tokenCipher,
        deviceIdentity = deviceIdentity,
        signOutCleanups = Provider { setOf(sync, caches) },
        serverDao = serverDao,
        trackDao = mockk(relaxed = true),
        albumDao = mockk(relaxed = true),
        artistDao = mockk(relaxed = true),
    )

    private val entity = ServerEntity("s1", "Jellyfin", "https://jelly.example", "u1", "v1:x:y", true, 0)

    @Test
    fun `stops work, revokes the token, then deletes everything`() = runTest {
        coEvery { serverDao.getActiveServer() } returns entity
        coEvery { dataSource.endSession(any()) } returns true

        val result = repository.logout()

        assertTrue(result is HarlowResult.Success)
        coVerifyOrder {
            sync.stopBeforeSignOut()
            dataSource.endSession(any())
            jellyfinClient.disconnect()
            sync.clearOnSignOut()
            databaseWiper.clearAllTables()
        }
        coVerify { caches.clearOnSignOut() }
        verify { tokenCipher.deleteKey() }
        coVerify { deviceIdentity.reset() }
    }

    @Test
    fun `offline, the local data is deleted anyway`() = runTest {
        coEvery { serverDao.getActiveServer() } returns entity
        coEvery { dataSource.endSession(any()) } returns false

        repository.logout()

        coVerify { caches.clearOnSignOut() }
        coVerify { databaseWiper.clearAllTables() }
    }

    @Test
    fun `a failing cleanup doesn't stop the others`() = runTest {
        coEvery { sync.clearOnSignOut() } throws IllegalStateException("broken")
        coEvery { activeSession.ensureConnected() } throws IllegalStateException("broken")
        coEvery { serverDao.getActiveServer() } returns entity

        val result = repository.logout()

        assertTrue(result is HarlowResult.Success)
        coVerify { caches.clearOnSignOut() }
        coVerify { databaseWiper.clearAllTables() }
        coVerify { deviceIdentity.reset() }
    }

    @Test
    fun `signing in over a previous session signs that one out but keeps the device ID`() = runTest {
        coEvery { dataSource.authenticate(any(), any(), any()) } returns
            JellyfinDataSource.AuthResult(userId = "u2", accessToken = "new", serverId = "s2", serverName = "Other")
        coEvery { serverDao.getServers() } returns listOf(entity)
        coEvery { serverDao.getActiveServer() } returns entity

        val result = repository.authenticate("jelly.example", "user", "pw")

        assertTrue(result is HarlowResult.Success<Server>)
        coVerify { dataSource.endSession(any()) }
        coVerify { databaseWiper.clearAllTables() }
        coVerify(exactly = 0) { deviceIdentity.reset() }
        coVerify { serverDao.upsert(match { it.id == "s2" && it.isActive }) }
        verify { tokenCipher.encrypt("new") }
    }

    @Test
    fun `a failed sign-in leaves the running session alone`() = runTest {
        coEvery { dataSource.authenticate(any(), any(), any()) } throws IllegalStateException("401")
        coEvery { serverDao.getServers() } returns listOf(entity)

        val result = repository.authenticate("jelly.example", "user", "wrong")

        assertTrue(result is HarlowResult.Error)
        coVerify(exactly = 0) { databaseWiper.clearAllTables() }
        coVerify(exactly = 0) { dataSource.endSession(any()) }
    }
}
