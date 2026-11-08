package dev.harlow.core.data.repository

import dev.harlow.core.common.HarlowResult
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.PlaylistTrackCrossRef
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.network.datasource.JellyfinDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

/** Playlists in Room mirror the user's playlists in Jellyfin: new ones appear, deleted ones disappear. */
class PlaylistSyncTest {

    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val serverDao = mockk<ServerDao>(relaxed = true)
    private val dataSource = mockk<JellyfinDataSource>(relaxed = true)

    private val repository = PlaylistRepositoryImpl(playlistDao, trackDao, serverDao, dataSource)

    private val userId = UUID.randomUUID()
    private val server = ServerEntity("s1", "Jellyfin", "https://jelly.example", userId.toString(), "v1:x:y", true, 0)

    private val metal = UUID.randomUUID()
    private val jazz = UUID.randomUUID()
    private val song1 = UUID.randomUUID()
    private val song2 = UUID.randomUUID()

    private fun playlist(id: UUID, name: String) = BaseItemDto(id = id, type = BaseItemKind.PLAYLIST, name = name)
    private fun track(id: UUID) = BaseItemDto(id = id, type = BaseItemKind.AUDIO, name = "Song")

    init {
        coEvery { serverDao.getActiveServer() } returns server
    }

    @Test
    fun `stores the playlists of the server`() = runTest {
        coEvery { dataSource.getPlaylists(userId) } returns listOf(playlist(metal, "Metal"))
        coEvery { playlistDao.getIdsByServer("s1") } returns emptyList()
        val stored = slot<List<PlaylistEntity>>()
        coEvery { playlistDao.upsertPlaylists(capture(stored)) } returns Unit

        val result = repository.syncPlaylists("s1")

        assertTrue(result is HarlowResult.Success)
        assertEquals(listOf("Metal"), stored.captured.map { it.name })
        assertTrue(stored.captured.all { it.serverId == "s1" })
    }

    @Test
    fun `deletes playlists that were deleted in Jellyfin`() = runTest {
        coEvery { dataSource.getPlaylists(userId) } returns listOf(playlist(metal, "Metal"))
        coEvery { playlistDao.getIdsByServer("s1") } returns listOf(metal.toString(), jazz.toString())

        repository.syncPlaylists("s1")

        coVerify { playlistDao.deleteByIds(listOf(jazz.toString())) }
    }

    @Test
    fun `deletes all playlists when the server has none left`() = runTest {
        coEvery { dataSource.getPlaylists(userId) } returns emptyList()
        coEvery { playlistDao.getIdsByServer("s1") } returns listOf(jazz.toString())

        repository.syncPlaylists("s1")

        coVerify { playlistDao.deleteByIds(listOf(jazz.toString())) }
    }

    @Test
    fun `keeps the playlists when the server cannot be reached`() = runTest {
        coEvery { dataSource.getPlaylists(userId) } throws IOException("offline")
        coEvery { playlistDao.getIdsByServer("s1") } returns listOf(jazz.toString())

        val result = repository.syncPlaylists("s1")

        assertTrue(result is HarlowResult.Error)
        coVerify(exactly = 0) { playlistDao.deleteByIds(any()) }
    }

    @Test
    fun `stores the tracks of every playlist in their order`() = runTest {
        coEvery { dataSource.getPlaylists(userId) } returns listOf(playlist(metal, "Metal"))
        coEvery { playlistDao.getIdsByServer("s1") } returns emptyList()
        coEvery { dataSource.getPlaylistItems(metal, userId) } returns listOf(track(song2), track(song1))
        val refs = slot<List<PlaylistTrackCrossRef>>()
        coEvery { playlistDao.replacePlaylistTracks(metal.toString(), capture(refs)) } returns Unit

        repository.syncPlaylists("s1")

        coVerify { trackDao.upsertTracks(match { tracks -> tracks.map { it.id } == listOf("$song2", "$song1") }) }
        assertEquals(listOf("$song2" to 0, "$song1" to 1), refs.captured.map { it.trackId to it.position })
    }

    @Test
    fun `empties a playlist that was emptied in Jellyfin`() = runTest {
        coEvery { dataSource.getPlaylistItems(metal, userId) } returns emptyList()

        repository.syncPlaylistTracks(metal.toString(), "s1")

        coVerify { playlistDao.replacePlaylistTracks(metal.toString(), emptyList()) }
    }
}
