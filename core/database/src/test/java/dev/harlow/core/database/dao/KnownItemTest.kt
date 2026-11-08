package dev.harlow.core.database.dao

import androidx.room.Room
import dev.harlow.core.database.HarlowDatabase
import dev.harlow.core.database.entity.AlbumEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The artwork provider fetches only items of the signed-in server's library. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnownItemTest {

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
    fun `an album of the server is known, other IDs and servers are not`() = runTest {
        db.albumDao().upsertAlbums(listOf(album(id = ALBUM_ID, serverId = "s1")))

        assertTrue(db.serverDao().isKnownItem("s1", ALBUM_ID))
        assertFalse(db.serverDao().isKnownItem("s2", ALBUM_ID))
        assertFalse(db.serverDao().isKnownItem("s1", "0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `the image tag of a known item follows its image`() = runTest {
        db.albumDao().upsertAlbums(listOf(album(id = ALBUM_ID, serverId = "s1", imageTag = "waveform")))
        assertEquals("waveform", db.serverDao().imageTagOf("s1", ALBUM_ID))

        db.albumDao().upsertAlbums(listOf(album(id = ALBUM_ID, serverId = "s1", imageTag = "cover")))
        assertEquals("cover", db.serverDao().imageTagOf("s1", ALBUM_ID))
        assertEquals(listOf(ItemImageTag(ALBUM_ID, "cover")), db.albumDao().getImageTags("s1"))
        assertNull(db.serverDao().imageTagOf("s2", ALBUM_ID))
    }

    private fun album(id: String, serverId: String, imageTag: String? = null) = AlbumEntity(
        id = id,
        serverId = serverId,
        name = "Album",
        sortName = "Album",
        artistId = null,
        artistName = null,
        year = null,
        trackCount = 0,
        genres = emptyList(),
        imageTag = imageTag,
        isFavorite = false,
        resolvedArtistId = null,
        dateAdded = 0,
        lastSynced = 0,
    )

    private companion object {
        const val ALBUM_ID = "a5e2c4b1d0f94e3a8b7c6d5e4f3a2b1c"
    }
}
