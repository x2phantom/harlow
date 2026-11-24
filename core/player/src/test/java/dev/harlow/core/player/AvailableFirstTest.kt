package dev.harlow.core.player

import androidx.room.Room
import dev.harlow.core.database.HarlowDatabase
import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvailableFirstTest {

    private lateinit var db: HarlowDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HarlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `offline a search finds available tracks beyond the online limit`() = runTest {
        db.trackDao().upsertTracks((0 until 30).map { track("t$it", "Love %02d".format(it)) })
        val available = setOf("t25", "t2")

        val tracks = availableFirst(limit = 20, isAvailable = { it.id in available }) { limit ->
            db.trackDao().search(SERVER, "Love", limit)
        }

        assertEquals(listOf("Love 02", "Love 25"), tracks.map { it.name })
    }

    @Test
    fun `offline a home row lists available albums beyond the online limit, newest first`() = runTest {
        db.albumDao().upsertAlbums((0 until 20).map { album("a$it", dateAdded = it.toLong()) })
        val available = setOf("a1", "a3", "a0")

        val albums = availableFirst(limit = 2, isAvailable = { it.id in available }) { limit ->
            db.albumDao().getRecentlyAddedAlbums(SERVER, limit)
        }

        assertEquals(listOf("a3", "a1"), albums.map { it.id })
    }

    @Test
    fun `online the query keeps its limit`() = runTest {
        db.trackDao().upsertTracks((0 until 30).map { track("t$it", "Love %02d".format(it)) })

        val tracks = availableFirst<TrackEntity>(limit = 20, isAvailable = null) { limit ->
            db.trackDao().search(SERVER, "Love", limit)
        }

        assertEquals(20, tracks.size)
    }

    private fun track(id: String, name: String) = TrackEntity(
        id = id, serverId = SERVER, name = name, sortName = name, albumId = null, albumName = null,
        artistId = null, artistName = null, trackNumber = 1, discNumber = 1, durationMs = 0,
        genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
        normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
        channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
    )

    private fun album(id: String, dateAdded: Long) = AlbumEntity(
        id = id, serverId = SERVER, name = id, sortName = id, artistId = null, artistName = null, year = null,
        trackCount = 0, genres = emptyList(), imageTag = null, isFavorite = false, resolvedArtistId = null,
        dateAdded = dateAdded, lastSynced = 0,
    )

    private companion object {
        const val SERVER = "server"
    }
}
