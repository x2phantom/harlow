package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.data.mapper.toModel
import dev.harlow.core.data.mapper.toPlaylistEntity
import dev.harlow.core.data.mapper.toTrackEntity
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.PlaylistTrackCrossRef
import dev.harlow.core.model.Playlist
import dev.harlow.core.model.Track
import dev.harlow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val serverDao: ServerDao,
    private val jellyfinDataSource: JellyfinDataSource,
) : PlaylistRepository {

    override fun observePlaylists(serverId: String): Flow<HarlowResult<List<Playlist>>> =
        playlistDao.observePlaylists(serverId).map { entities ->
            HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Playlist>>
        }.catch { emit(HarlowResult.Error(it)) }

    override fun observePlaylistTracks(playlistId: String): Flow<HarlowResult<List<Track>>> =
        playlistDao.getPlaylistTracks(playlistId).map { entities ->
            HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Track>>
        }.catch { emit(HarlowResult.Error(it)) }

    override suspend fun getPlaylistById(id: String): HarlowResult<Playlist?> =
        try {
            HarlowResult.Success(playlistDao.getPlaylistById(id)?.toModel())
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    /**
     * Mirrors the user's playlists in Jellyfin: stores new and changed ones with their tracks and deletes the ones
     * that no longer exist on the server. If the server can't be reached, Room stays as it is.
     */
    override suspend fun syncPlaylists(serverId: String): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)
            val items = jellyfinDataSource.getPlaylists(userId)
            if (items.isNotEmpty()) {
                playlistDao.upsertPlaylists(items.map { it.toPlaylistEntity(serverId) })
            }
            val serverIds = items.map { it.id.toString() }.toSet()
            val deletedIds = playlistDao.getIdsByServer(serverId).filterNot { it in serverIds }
            deletedIds.chunked(ROOM_BIND_LIMIT).forEach { playlistDao.deleteByIds(it) }

            var failure: HarlowResult.Error? = null
            items.forEach { playlist ->
                val result = syncPlaylistTracks(playlist.id.toString(), serverId)
                if (result is HarlowResult.Error && failure == null) failure = result
            }
            failure ?: HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun syncPlaylistTracks(playlistId: String, serverId: String): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)
            val items = jellyfinDataSource.getPlaylistItems(
                playlistId = UUID.fromString(playlistId),
                userId = userId,
            )
            if (items.isNotEmpty()) {
                trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
            }
            val now = System.currentTimeMillis()
            playlistDao.replacePlaylistTracks(
                playlistId,
                items.mapIndexed { index, dto ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = dto.id.toString(),
                        position = index,
                        addedAt = now,
                    )
                },
            )
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun createPlaylist(name: String, serverId: String): HarlowResult<String> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return HarlowResult.Error(IllegalStateException("No active server"))
            val userId = UUID.fromString(server.userId)
            val newId = jellyfinDataSource.createPlaylist(name, userId)
                ?: return HarlowResult.Error(IllegalStateException("Failed to create playlist"))

            playlistDao.upsert(
                PlaylistEntity(
                    id = newId,
                    serverId = serverId,
                    name = name,
                    sortName = name,
                    trackCount = 0,
                    durationMs = 0L,
                    imageTag = null,
                    isFavorite = false,
                    isLocal = false,
                    lastSynced = System.currentTimeMillis(),
                ),
            )
            HarlowResult.Success(newId)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun addTrackToPlaylist(
        playlistId: String,
        trackId: String,
        serverId: String,
    ): HarlowResult<Unit> {
        return try {
            val existingTracks = playlistDao.getPlaylistById(playlistId)?.trackCount ?: 0
            playlistDao.insertPlaylistTracks(
                listOf(
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = existingTracks,
                        addedAt = System.currentTimeMillis(),
                    ),
                ),
            )

            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)
            jellyfinDataSource.addToPlaylist(
                playlistId = UUID.fromString(playlistId),
                trackIds = listOf(UUID.fromString(trackId)),
                userId = userId,
            )
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun removeTrackFromPlaylist(
        playlistId: String,
        trackId: String,
    ): HarlowResult<Unit> {
        return try {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)

            jellyfinDataSource.removeFromPlaylist(
                playlistId = playlistId,
                entryIds = listOf(trackId),
            )
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    private companion object {
        /** SQLite allows at most 999 bound variables per statement. */
        const val ROOM_BIND_LIMIT = 900
    }
}
