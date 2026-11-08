package dev.harlow.core.database.dao

import androidx.room.Room
import dev.harlow.core.database.HarlowDatabase
import dev.harlow.core.database.entity.ArtistAliasEntity
import dev.harlow.core.database.entity.ArtistEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Search lists an artist once, however many spellings the server has for it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtistSearchTest {

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
    fun `spellings of one artist are one result, the canonical artist`() = runTest {
        db.artistDao().upsertArtists(
            listOf(
                artist("canonical", "All Hail The Yeti"),
                artist("alias", "All Hail the Yeti"),
                artist("other", "Yeti Hunters"),
            ),
        )
        db.artistAliasDao().upsertAliases(
            listOf(alias("canonical", "canonical"), alias("alias", "canonical"), alias("other", "other")),
        )

        val results = db.artistDao().searchCanonical(SERVER, "yeti")

        assertEquals(listOf("canonical", "other"), results.map { it.id })
    }

    @Test
    fun `a match on an alias's name finds the canonical artist`() = runTest {
        db.artistDao().upsertArtists(listOf(artist("canonical", "Beyoncé"), artist("alias", "Beyonce Knowles")))
        db.artistAliasDao().upsertAliases(listOf(alias("canonical", "canonical"), alias("alias", "canonical")))

        val results = db.artistDao().searchCanonical(SERVER, "Knowles")

        assertEquals(listOf("canonical"), results.map { it.id })
    }

    @Test
    fun `artists not yet grouped into aliases stand for themselves`() = runTest {
        db.artistDao().upsertArtists(listOf(artist("a", "Yeti"), artist("b", "The Yeti")))

        val results = db.artistDao().searchCanonical(SERVER, "yeti")

        assertEquals(setOf("a", "b"), results.map { it.id }.toSet())
    }

    @Test
    fun `the limit counts distinct artists`() = runTest {
        db.artistDao().upsertArtists(
            listOf(artist("c1", "Yeti A"), artist("c1-alias", "yeti a"), artist("c2", "Yeti B")),
        )
        db.artistAliasDao().upsertAliases(
            listOf(alias("c1", "c1"), alias("c1-alias", "c1"), alias("c2", "c2")),
        )

        val results = db.artistDao().searchCanonical(SERVER, "yeti", limit = 2)

        assertEquals(listOf("c1", "c2"), results.map { it.id })
    }

    private companion object {
        const val SERVER = "server"

        fun alias(raw: String, canonical: String) = ArtistAliasEntity(SERVER, raw, canonical, 0)

        fun artist(id: String, name: String) = ArtistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = name, musicBrainzId = null,
            lastSynced = 0,
        )
    }
}
