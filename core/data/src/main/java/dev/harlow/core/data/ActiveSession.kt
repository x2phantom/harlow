package dev.harlow.core.data

import dev.harlow.core.database.TokenCipher
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.model.Server
import dev.harlow.core.network.JellyfinClientWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in server with its decrypted access token. The only place that decrypts the token stored in Room;
 * everything that talks to the server connects through [ensureConnected].
 */
@Singleton
class ActiveSession @Inject constructor(
    private val serverDao: ServerDao,
    private val tokenCipher: TokenCipher,
    private val jellyfinClient: JellyfinClientWrapper,
) {
    /** The active server, or `null` when signed out or when its token can't be decrypted anymore. */
    suspend fun server(): Server? {
        val entity = serverDao.getActiveServer() ?: return null
        val token = withContext(Dispatchers.IO) { tokenCipher.decrypt(entity.encryptedAccessToken) } ?: return null
        return entity.toModel(token)
    }

    /** Whether the active server's token can't be decrypted (Keystore key gone): the session is lost. */
    suspend fun isUnreadable(): Boolean {
        val entity = serverDao.getActiveServer() ?: return false
        return withContext(Dispatchers.IO) { tokenCipher.decrypt(entity.encryptedAccessToken) } == null
    }

    /** Connects the shared client to the active server (if it isn't already) and returns the server. */
    suspend fun ensureConnected(): Server? {
        val server = server() ?: return null
        jellyfinClient.restoreSession(server.url, server.accessToken)
        return server
    }

    /** Encrypts tokens stored in plaintext by versions before encryption. Returns how many were encrypted. */
    suspend fun encryptLegacyTokens(): Int = withContext(Dispatchers.IO) {
        serverDao.getServers()
            .filter { tokenCipher.isLegacyPlaintext(it.encryptedAccessToken) }
            .onEach { serverDao.updateEncryptedAccessToken(it.id, tokenCipher.encrypt(it.encryptedAccessToken)) }
            .size
    }

    private fun ServerEntity.toModel(token: String) = Server(
        id = id,
        name = name,
        url = url,
        userId = userId,
        accessToken = token,
    )
}
