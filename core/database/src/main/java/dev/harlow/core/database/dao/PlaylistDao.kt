package dev.harlow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.PlaylistTrackCrossRef
import dev.harlow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY sortName ASC")
    fun observePlaylists(serverId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 20): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoritePlaylists(serverId: String): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlists WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id FROM playlists WHERE serverId = :serverId")
    suspend fun getIdsByServer(serverId: String): List<String>

    /** Deletes the playlists and, through the foreign key, their track lists. */
    @Query("DELETE FROM playlists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC",
    )
    fun getPlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    /** Replaces the track list of a playlist in one go, so the media center never sees it half written. */
    @Transaction
    suspend fun replacePlaylistTracks(playlistId: String, refs: List<PlaylistTrackCrossRef>) {
        clearPlaylistTracks(playlistId)
        if (refs.isNotEmpty()) insertPlaylistTracks(refs)
    }

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getPlaylistsByServer(serverId: String): List<PlaylistEntity>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC",
    )
    suspend fun getPlaylistTracksSync(playlistId: String): List<TrackEntity>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT id, imageTag FROM playlists WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getImageTags(serverId: String): List<ItemImageTag>
}
