package dev.harlow.core.data.repository

import android.util.Log
import dev.harlow.core.common.HarlowResult
import dev.harlow.core.common.normalizeServerUrl
import dev.harlow.core.data.ActiveSession
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.core.database.DatabaseWiper
import dev.harlow.core.database.TokenCipher
import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.model.Server
import dev.harlow.core.network.DeviceIdentity
import dev.harlow.core.network.JellyfinClientWrapper
import dev.harlow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val jellyfinClient: JellyfinClientWrapper,
    private val jellyfinDataSource: JellyfinDataSource,
    private val activeSession: ActiveSession,
    private val databaseWiper: DatabaseWiper,
    private val tokenCipher: TokenCipher,
    private val deviceIdentity: DeviceIdentity,
    // A provider: some cleanups (player, sync) depend on classes that depend on this repository.
    private val signOutCleanups: Provider<Set<@JvmSuppressWildcards SignOutCleanup>>,
    private val serverDao: ServerDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
) : UserRepository {

    private val signOutMutex = Mutex()

    /**
     * Signs in without touching a running session until the server accepted the credentials. A previous session is
     * then signed out completely ([logout]): one user per car, nothing of the previous one stays.
     */
    override suspend fun authenticate(serverUrl: String, username: String, password: String): HarlowResult<Server> {
        return try {
            val url = normalizeServerUrl(serverUrl)
            val result = jellyfinDataSource.authenticate(url, username, password)

            if (serverDao.getServers().isNotEmpty()) {
                // The new token belongs to the current device ID: keep it.
                signOut(newDevice = false)
            }

            val server = Server(
                id = result.serverId,
                name = result.serverName,
                url = url,
                userId = result.userId,
                accessToken = result.accessToken,
            )
            val encryptedToken = withContext(Dispatchers.IO) { tokenCipher.encrypt(server.accessToken) }
            serverDao.upsert(
                ServerEntity(
                    id = server.id,
                    name = server.name,
                    url = server.url,
                    userId = server.userId,
                    encryptedAccessToken = encryptedToken,
                    isActive = true,
                    lastConnected = System.currentTimeMillis(),
                )
            )
            jellyfinClient.restoreSession(server.url, server.accessToken)
            HarlowResult.Success(server)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun getServers(): HarlowResult<List<Server>> =
        try {
            HarlowResult.Success(listOfNotNull(activeSession.server()))
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    suspend fun getActiveServer(): Server? = activeSession.server()

    fun observeActiveServer(): Flow<Server?> =
        serverDao.observeActiveServer().map { if (it == null) null else activeSession.server() }

    suspend fun restoreSession(): Boolean = activeSession.ensureConnected() != null

    /**
     * Start-up housekeeping: encrypts tokens stored in plaintext by older versions; a session whose token can't be
     * decrypted anymore (Keystore key lost) is signed out, so the media center offers "Sign in".
     */
    suspend fun secureStoredSession() {
        val encrypted = activeSession.encryptLegacyTokens()
        if (encrypted > 0) Log.i(TAG, "Encrypted $encrypted stored access token(s)")
        if (activeSession.isUnreadable()) {
            Log.w(TAG, "Stored access token can't be decrypted, signing out")
            logout()
        }
    }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return HarlowResult.Error(IllegalStateException("No active server"))
            jellyfinDataSource.setFavorite(
                userId = UUID.fromString(server.userId),
                itemId = UUID.fromString(itemId),
                isFavorite = isFavorite,
            )
            trackDao.setFavorite(itemId, isFavorite)
            albumDao.setFavorite(itemId, isFavorite)
            artistDao.setFavorite(itemId, isFavorite)
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackStarted(itemId: String): HarlowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackStarted(UUID.fromString(itemId))
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long): HarlowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackProgress(UUID.fromString(itemId), positionMs * 10_000)
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun reportPlaybackStopped(itemId: String, positionMs: Long): HarlowResult<Unit> {
        return try {
            jellyfinDataSource.reportPlaybackStopped(UUID.fromString(itemId), positionMs * 10_000)
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    /**
     * Signs out and leaves the app as after a fresh install: revokes the token on the server (best effort), then
     * deletes the database, preferences, caches, downloads and queued work, the Keystore key and the device ID.
     */
    override suspend fun logout(): HarlowResult<Unit> = signOut(newDevice = true)

    private suspend fun signOut(newDevice: Boolean): HarlowResult<Unit> = signOutMutex.withLock {
        val cleanups = signOutCleanups.get()
        cleanups.forEach { cleanup -> bestEffort("stop ${cleanup.name}") { cleanup.stopBeforeSignOut() } }

        if (serverDao.getActiveServer() != null) {
            bestEffort("revoke the token") {
                activeSession.ensureConnected()
                if (!jellyfinDataSource.endSession()) Log.w(TAG, "The server didn't confirm the sign-out")
            }
        }
        jellyfinClient.disconnect()

        cleanups.forEach { cleanup -> bestEffort("clear ${cleanup.name}") { cleanup.clearOnSignOut() } }
        bestEffort("delete the token key") { withContext(Dispatchers.IO) { tokenCipher.deleteKey() } }
        if (newDevice) bestEffort("reset the device ID") { deviceIdentity.reset() }

        try {
            databaseWiper.clearAllTables()
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't clear the database", e)
            HarlowResult.Error(e)
        }
    }

    private suspend fun bestEffort(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Signing out: couldn't $what", e)
        }
    }

    private val SignOutCleanup.name: String get() = javaClass.simpleName

    private companion object {
        const val TAG = "UserRepository"
    }
}
