package dev.harlow.core.network.datasource

import dev.harlow.core.network.JellyfinClientWrapper
import dev.harlow.core.network.di.IoDispatcher
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode

import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PagedItems(
    val items: List<BaseItemDto>,
    val totalRecordCount: Int,
)

@Singleton
class JellyfinDataSource @Inject constructor(
    private val client: JellyfinClientWrapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // The Jellyfin SDK reads response bodies on the caller's dispatcher. Running every request on IO
    // keeps this class main-safe and avoids NetworkOnMainThreadException.
    private suspend fun <T> io(block: suspend CoroutineScope.() -> T): T = withContext(ioDispatcher, block)

    /**
     * Signs in with a client of its own, so a failed attempt leaves a running session alone. The caller switches the
     * session over ([JellyfinClientWrapper.restoreSession]) once it succeeded.
     */
    suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult = io {
        val api = client.createApi(serverUrl)
        val response by api.userApi.authenticateUserByName(
            data = AuthenticateUserByName(username = username, pw = password),
        )
        AuthResult(
            userId = response.user!!.id.toString(),
            accessToken = response.accessToken!!,
            serverId = response.serverId!!,
            serverName = response.user!!.serverName.orEmpty(),
        )
    }

    /**
     * Revokes the session's access token on the server (`POST /Sessions/Logout`), so it stops working even if a copy
     * survived somewhere. Best effort: offline or after [timeoutMs] the token stays valid until removed in the
     * server's dashboard. Returns whether the server confirmed.
     */
    suspend fun endSession(timeoutMs: Long = 5_000): Boolean = io {
        if (!client.isConnected) return@io false
        try {
            withTimeoutOrNull(timeoutMs) { client.api.sessionApi.reportSessionEnded() } != null
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't end the session on the server: ${e.javaClass.simpleName}")
            false
        }
    }

    suspend fun getRecentlyAddedAlbums(userId: UUID, limit: Int = 50): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
            limit = limit,
        )
        response.items.orEmpty()
    }

    suspend fun getAlbums(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 200,
        minDateLastSaved: LocalDateTime? = null,
    ): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
            minDateLastSaved = minDateLastSaved,
        )
        response.items.orEmpty()
    }

    suspend fun getAlbumsByIds(userId: UUID, ids: List<UUID>): List<BaseItemDto> = io {
        if (ids.isEmpty()) return@io emptyList()
        val response by client.api.itemsApi.getItems(
            userId = userId,
            ids = ids,
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun getAlbumsPaged(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 200,
    ): PagedItems = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        PagedItems(response.items.orEmpty(), response.totalRecordCount ?: 0)
    }

    suspend fun getArtists(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 200,
    ): List<BaseItemDto> = io {
        val response by client.api.artistsApi.getArtists(
            userId = userId,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.OVERVIEW, ItemFields.PROVIDER_IDS),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        response.items.orEmpty()
    }

    suspend fun getArtistsPaged(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 200,
    ): PagedItems = io {
        val response by client.api.artistsApi.getArtists(
            userId = userId,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.OVERVIEW, ItemFields.PROVIDER_IDS),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        PagedItems(response.items.orEmpty(), response.totalRecordCount ?: 0)
    }

    suspend fun getTracks(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 500,
        minDateLastSaved: LocalDateTime? = null,
    ): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
            minDateLastSaved = minDateLastSaved,
        )
        response.items.orEmpty()
    }

    suspend fun getTracksPaged(
        userId: UUID,
        startIndex: Int = 0,
        limit: Int = 500,
    ): PagedItems = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS, ItemFields.DATE_CREATED),
            enableUserData = true,
            startIndex = startIndex,
            limit = limit,
        )
        PagedItems(response.items.orEmpty(), response.totalRecordCount ?: 0)
    }

    suspend fun getRecentlyPlayedItems(userId: UUID, limit: Int = 200): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            filters = listOf(ItemFilter.IS_PLAYED),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS, ItemFields.DATE_CREATED),
            enableUserData = true,
            limit = limit,
        )
        response.items.orEmpty()
    }

    suspend fun getAlbumTracks(userId: UUID, albumId: UUID): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            parentId = albumId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = listOf(ItemFields.MEDIA_STREAMS),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun setFavorite(userId: UUID, itemId: UUID, isFavorite: Boolean): Unit = io {
        if (isFavorite) {
            client.api.userLibraryApi.markFavoriteItem(userId = userId, itemId = itemId)
        } else {
            client.api.userLibraryApi.unmarkFavoriteItem(userId = userId, itemId = itemId)
        }
    }

    suspend fun getInstantMixFromSong(
        itemId: UUID,
        userId: UUID,
        limit: Int = 50,
    ): List<BaseItemDto> = io {
        val response by client.api.instantMixApi.getInstantMixFromSong(
            itemId = itemId,
            userId = userId,
            limit = limit,
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS),
            enableUserData = true,
            enableImages = true,
        )
        response.items.orEmpty()
    }

    private fun playMethod(playSessionId: String?) = if (playSessionId != null) PlayMethod.TRANSCODE else PlayMethod.DIRECT_PLAY

    suspend fun reportPlaybackStarted(itemId: UUID, playSessionId: String? = null): Unit = io {
        try {
            client.api.playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = playMethod(playSessionId),
                    playSessionId = playSessionId,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback started for $itemId", e)
        }
    }

    suspend fun reportPlaybackProgress(itemId: UUID, positionTicks: Long, playSessionId: String? = null): Unit = io {
        try {
            client.api.playStateApi.reportPlaybackProgress(
                PlaybackProgressInfo(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = playMethod(playSessionId),
                    playSessionId = playSessionId,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback progress for $itemId", e)
        }
    }

    suspend fun reportPlaybackStopped(itemId: UUID, positionTicks: Long, playSessionId: String? = null): Unit = io {
        try {
            client.api.playStateApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    failed = false,
                    playSessionId = playSessionId,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback stopped for $itemId", e)
        }
    }

    companion object {
        private const val TAG = "JellyfinDataSource"
    }

    suspend fun getFavoriteAlbums(userId: UUID): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.DATE_CREATED),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun getFavoriteArtists(userId: UUID): List<BaseItemDto> = io {
        val response by client.api.artistsApi.getArtists(
            userId = userId,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.OVERVIEW, ItemFields.PROVIDER_IDS),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun getFavoriteTracks(userId: UUID): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun getPlaylists(userId: UUID): List<BaseItemDto> = io {
        val response by client.api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(ItemFields.DATE_CREATED),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun getPlaylistItems(playlistId: UUID, userId: UUID): List<BaseItemDto> = io {
        val response by client.api.playlistsApi.getPlaylistItems(
            playlistId = playlistId,
            userId = userId,
            fields = listOf(ItemFields.GENRES, ItemFields.MEDIA_STREAMS),
            enableUserData = true,
        )
        response.items.orEmpty()
    }

    suspend fun createPlaylist(name: String, userId: UUID): String? = io {
        val response by client.api.playlistsApi.createPlaylist(
            CreatePlaylistDto(
                name = name,
                ids = emptyList(),
                userId = userId,
                mediaType = MediaType.AUDIO,
                users = emptyList(),
                isPublic = false,
            ),
        )
        response.id
    }

    suspend fun addToPlaylist(playlistId: UUID, trackIds: List<UUID>, userId: UUID): Unit = io {
        client.api.playlistsApi.addItemToPlaylist(
            playlistId = playlistId,
            ids = trackIds,
            userId = userId,
        )
    }

    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>): Unit = io {
        client.api.playlistsApi.removeItemFromPlaylist(
            playlistId = playlistId,
            entryIds = entryIds,
        )
    }

    data class AuthResult(
        val userId: String,
        val accessToken: String,
        val serverId: String,
        val serverName: String,
    )
}
