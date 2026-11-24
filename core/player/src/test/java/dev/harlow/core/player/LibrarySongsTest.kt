package dev.harlow.core.player

import androidx.room.Room
import dev.harlow.core.database.HarlowDatabase
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
class LibrarySongsTest {

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
    fun `offline lists the available tracks even beyond the first page of the library`() = runTest {
        db.trackDao().upsertTracks((0 until 600).map { track("t$it", "Track %03d".format(it)) })

        val songs = db.trackDao().librarySongs(SERVER, available = setOf("t550", "t3"), limit = 500)

        assertEquals(listOf("Track 003", "Track 550"), songs.map { it.name })
    }

    @Test
    fun `offline lists only tracks of the active server`() = runTest {
        db.trackDao().upsertTracks(listOf(track("a", "A"), track("b", "B", serverId = "other")))

        val songs = db.trackDao().librarySongs(SERVER, available = setOf("a", "b"), limit = 500)

        assertEquals(listOf("a"), songs.map { it.id })
    }

    @Test
    fun `online lists the library by sort name up to the limit`() = runTest {
        db.trackDao().upsertTracks((0 until 600).map { track("t$it", "Track %03d".format(it)) })

        val songs = db.trackDao().librarySongs(SERVER, available = null, limit = 500)

        assertEquals(500, songs.size)
        assertEquals("Track 000", songs.first().name)
    }

    private fun track(id: String, name: String, serverId: String = SERVER) = TrackEntity(
        id = id, serverId = serverId, name = name, sortName = name, albumId = null, albumName = null,
        artistId = null, artistName = null, trackNumber = 1, discNumber = 1, durationMs = 0,
        genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
        normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
        channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
    )

    private companion object {
        const val SERVER = "server"
    }
}
