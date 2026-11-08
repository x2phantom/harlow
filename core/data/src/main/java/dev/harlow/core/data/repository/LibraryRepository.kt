package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.model.Album
import dev.harlow.core.model.Artist
import dev.harlow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getAlbums(serverId: String): Flow<HarlowResult<List<Album>>>
    fun getArtists(serverId: String): Flow<HarlowResult<List<Artist>>>
    fun getAlbumTracks(albumId: String): Flow<HarlowResult<List<Track>>>
    fun getArtistAlbumsById(artistId: String): Flow<HarlowResult<List<Album>>>
    suspend fun getAlbum(albumId: String): HarlowResult<Album?>
    fun observeAlbum(albumId: String): Flow<HarlowResult<Album?>>
    suspend fun getArtist(artistId: String): HarlowResult<Artist?>
    fun observeArtist(artistId: String): Flow<HarlowResult<Artist?>>
    fun getArtistTracksById(artistId: String): Flow<HarlowResult<List<Track>>>
    suspend fun countArtistAlbumsById(artistId: String): HarlowResult<Int>
    suspend fun countArtistTracksById(artistId: String): HarlowResult<Int>
    fun getRecentTracks(serverId: String): Flow<HarlowResult<List<Track>>>
    suspend fun search(serverId: String, query: String): HarlowResult<List<Track>>
    suspend fun searchAlbums(serverId: String, query: String): HarlowResult<List<Album>>
    suspend fun searchArtists(serverId: String, query: String): HarlowResult<List<Artist>>
    fun getFavoriteTracks(serverId: String): Flow<HarlowResult<List<Track>>>
    fun getFavoriteAlbums(serverId: String): Flow<HarlowResult<List<Album>>>
    fun getFavoriteArtists(serverId: String): Flow<HarlowResult<List<Artist>>>
    fun getRecentlyPlayedAlbums(serverId: String): Flow<HarlowResult<List<Album>>>
    fun getMostPlayedAlbums(serverId: String): Flow<HarlowResult<List<Album>>>
    suspend fun syncHomeScreenPriority(serverId: String, onProgress: (SyncProgress) -> Unit = {}): HarlowResult<Set<String>>
    suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit = {}): HarlowResult<Unit>
    suspend fun syncFavorites(serverId: String): HarlowResult<Unit>
    suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit = {}): HarlowResult<Unit>
    suspend fun getInstantMix(serverId: String, trackId: String): HarlowResult<List<Track>>
}
