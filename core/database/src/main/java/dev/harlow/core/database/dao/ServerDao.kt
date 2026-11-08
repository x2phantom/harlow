package dev.harlow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.harlow.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    suspend fun getServers(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun observeActiveServer(): Flow<ServerEntity?>

    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE servers SET accessToken = :encryptedAccessToken WHERE id = :serverId")
    suspend fun updateEncryptedAccessToken(serverId: String, encryptedAccessToken: String)

    /** Whether [itemId] is an album, artist, track or playlist of [serverId]: only their artwork is fetched. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM albums WHERE id = :itemId AND serverId = :serverId) " +
            "OR EXISTS(SELECT 1 FROM artists WHERE id = :itemId AND serverId = :serverId) " +
            "OR EXISTS(SELECT 1 FROM tracks WHERE id = :itemId AND serverId = :serverId) " +
            "OR EXISTS(SELECT 1 FROM playlists WHERE id = :itemId AND serverId = :serverId)",
    )
    suspend fun isKnownItem(serverId: String, itemId: String): Boolean

    /**
     * The image tag of [itemId] (album, artist, track or playlist of [serverId]): it changes with the image, so the
     * artwork cache knows when a cover was replaced on the server. Null if the item has no image or isn't known.
     */
    @Query(
        "SELECT imageTag FROM albums WHERE id = :itemId AND serverId = :serverId " +
            "UNION ALL SELECT imageTag FROM artists WHERE id = :itemId AND serverId = :serverId " +
            "UNION ALL SELECT imageTag FROM tracks WHERE id = :itemId AND serverId = :serverId " +
            "UNION ALL SELECT imageTag FROM playlists WHERE id = :itemId AND serverId = :serverId " +
            "LIMIT 1",
    )
    suspend fun imageTagOf(serverId: String, itemId: String): String?

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun delete(serverId: String)
}
