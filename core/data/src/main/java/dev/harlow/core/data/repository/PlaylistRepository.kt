package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.model.Playlist
import dev.harlow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(serverId: String): Flow<HarlowResult<List<Playlist>>>
    fun observePlaylistTracks(playlistId: String): Flow<HarlowResult<List<Track>>>
    suspend fun getPlaylistById(id: String): HarlowResult<Playlist?>
    suspend fun syncPlaylists(serverId: String): HarlowResult<Unit>
    suspend fun syncPlaylistTracks(playlistId: String, serverId: String): HarlowResult<Unit>
    suspend fun createPlaylist(name: String, serverId: String): HarlowResult<String>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, serverId: String): HarlowResult<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): HarlowResult<Unit>
}
