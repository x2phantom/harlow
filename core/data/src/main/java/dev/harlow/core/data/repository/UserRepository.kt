package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.model.Server

interface UserRepository {
    suspend fun authenticate(serverUrl: String, username: String, password: String): HarlowResult<Server>
    suspend fun getServers(): HarlowResult<List<Server>>
    suspend fun setFavorite(itemId: String, isFavorite: Boolean): HarlowResult<Unit>
    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long): HarlowResult<Unit>
    suspend fun reportPlaybackStarted(itemId: String): HarlowResult<Unit>
    suspend fun reportPlaybackStopped(itemId: String, positionMs: Long): HarlowResult<Unit>
    suspend fun logout(): HarlowResult<Unit>
}
