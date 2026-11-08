package dev.harlow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.harlow.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
        WHERE a.serverId = :serverId
        GROUP BY aa.canonicalArtistId
        ORDER BY a.sortName ASC
    """)
    fun getArtistsByServer(serverId: String): PagingSource<Int, ArtistEntity>

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
        WHERE a.serverId = :serverId
        GROUP BY aa.canonicalArtistId
        ORDER BY a.sortName ASC
    """)
    fun observeArtistsByServer(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtistById(id: String): Flow<ArtistEntity?>

    @Query("SELECT isFavorite FROM artists WHERE id = :id")
    fun observeIsFavorite(id: String): Flow<Boolean?>

    @Query("SELECT * FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    fun getFavoriteArtists(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 10): List<ArtistEntity>

    /**
     * Artists whose name — or an alias's — matches [query], each once as its canonical artist (the one with the
     * albums and tracks): the same artist under several spellings is one search result. An artist not yet grouped
     * into aliases stands for itself.
     */
    @Query("""
        SELECT a.* FROM artists raw
        LEFT JOIN artist_aliases aa ON aa.serverId = raw.serverId AND aa.rawArtistId = raw.id
        INNER JOIN artists a ON a.serverId = raw.serverId AND a.id = COALESCE(aa.canonicalArtistId, raw.id)
        WHERE raw.serverId = :serverId AND raw.name LIKE '%' || :query || '%'
        GROUP BY a.id
        ORDER BY a.sortName ASC
        LIMIT :limit
    """)
    suspend fun searchCanonical(serverId: String, query: String, limit: Int = 10): List<ArtistEntity>

    /**
     * Saves artists from the server without clearing a stored favorite: Jellyfin's `/Artists` reports
     * `IsFavorite = false` even for favorite artists, so favorites are set and removed from the server's favorites
     * list instead (see [setFavoriteByIds]).
     */
    @Transaction
    suspend fun upsertArtists(artists: List<ArtistEntity>) {
        val favorites = artists.map { it.id }.chunked(BIND_LIMIT).flatMap { getFavoriteIdsAmong(it) }.toSet()
        upsertArtistRows(artists.map { it.copy(isFavorite = it.isFavorite || it.id in favorites) })
    }

    @Upsert
    suspend fun upsertArtistRows(artists: List<ArtistEntity>)

    @Query("SELECT id FROM artists WHERE isFavorite = 1 AND id IN (:ids)")
    suspend fun getFavoriteIdsAmong(ids: List<String>): List<String>

    @Query("UPDATE artists SET cleanName = :cleanName WHERE id = :id")
    suspend fun updateCleanName(id: String, cleanName: String)

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id = :artistId")
    suspend fun setFavorite(artistId: String, isFavorite: Boolean)

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getAllArtistsByServer(serverId: String): List<ArtistEntity>

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
        WHERE a.serverId = :serverId
        GROUP BY aa.canonicalArtistId
        ORDER BY a.sortName ASC
    """)
    suspend fun getCanonicalArtistsByServer(serverId: String): List<ArtistEntity>

    @Query("SELECT id FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteArtistIds(serverId: String): List<String>

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("SELECT id FROM artists WHERE serverId = :serverId")
    suspend fun getAllArtistIdsByServer(serverId: String): List<String>

    @Query("DELETE FROM artists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM artists WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id, imageTag FROM artists WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getImageTags(serverId: String): List<ItemImageTag>

    @Query("SELECT * FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteArtistsSync(serverId: String): List<ArtistEntity>
}
