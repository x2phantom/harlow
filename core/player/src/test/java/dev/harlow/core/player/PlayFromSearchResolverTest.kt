package dev.harlow.core.player

import androidx.room.Room
import dev.harlow.core.database.HarlowDatabase
import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.ArtistEntity
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.PlaylistTrackCrossRef
import dev.harlow.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Voice requests ("Spiele das Album All Is Dust", "… den Titel …", "… die Playlist …", "… Musik von …") against a
 * small library in Room: what gets played, and from where.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayFromSearchResolverTest {

    private lateinit var db: HarlowDatabase
    private lateinit var resolver: PlayFromSearchResolver

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HarlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resolver = PlayFromSearchResolver(db.artistDao(), db.artistAliasDao(), db.albumDao(), db.trackDao(), db.playlistDao())

        db.artistDao().upsertArtists(listOf(artist(HARBOR, "Harbor Lights"), artist(ECHO, "Echo Valley")))
        db.albumDao().upsertAlbums(
            listOf(
                album(DUST, "All Is Dust", HARBOR, "Harbor Lights"),
                album(DUST_COVER, "All Is Dust", ECHO, "Echo Valley"),
                album(TIDES, "Tides", ECHO, "Echo Valley"),
            ),
        )
        db.trackDao().upsertTracks(
            listOf(
                // Out of order on purpose: an album plays by disc and track number.
                track("dust-2", "Ashes", DUST, 2, HARBOR, "Harbor Lights"),
                track("dust-1", "Prelude", DUST, 1, HARBOR, "Harbor Lights"),
                track("dust-3", "Lanterns", DUST, 3, HARBOR, "Harbor Lights"),
                track("cover-1", "Ashes", DUST_COVER, 1, ECHO, "Echo Valley"),
                track("tides-1", "Low Water", TIDES, 1, ECHO, "Echo Valley"),
                track("tides-2", "All Is Dust", TIDES, 2, ECHO, "Echo Valley"),
                track("tides-3", "High Water", TIDES, 3, ECHO, "Echo Valley"),
            ),
        )
        db.playlistDao().upsertPlaylists(listOf(playlist(ROADTRIP, "Roadtrip"), playlist(ROADTRIP_2, "Roadtrip 2")))
        db.playlistDao().insertPlaylistTracks(
            listOf(
                PlaylistTrackCrossRef(ROADTRIP, "tides-3", position = 0, addedAt = 0),
                PlaylistTrackCrossRef(ROADTRIP, "dust-1", position = 1, addedAt = 0),
                PlaylistTrackCrossRef(ROADTRIP, "cover-1", position = 2, addedAt = 0),
                PlaylistTrackCrossRef(ROADTRIP_2, "tides-1", position = 0, addedAt = 0),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    // Album

    @Test
    fun `album request plays the whole album in order`() = runTest {
        val playback = resolve("All Is Dust", FOCUS_ALBUM, ALBUM to "all is dust", ARTIST to "Harbor Lights")

        assertEquals(listOf("dust-1", "dust-2", "dust-3"), playback.ids())
        assertEquals(0, playback!!.startIndex)
    }

    @Test
    fun `album request picks the album of the named artist`() = runTest {
        val playback = resolve("All Is Dust", FOCUS_ALBUM, ALBUM to "All Is Dust", ARTIST to "Echo Valley")

        assertEquals(listOf("cover-1"), playback.ids())
    }

    @Test
    fun `album request without extras uses the query`() = runTest {
        val playback = resolve("Tides", FOCUS_ALBUM)

        assertEquals(listOf("tides-1", "tides-2", "tides-3"), playback.ids())
    }

    @Test
    fun `free text album name plays the album, not the track of that name`() = runTest {
        val playback = resolve("all is dust")

        assertEquals(DUST, playback!!.tracks.first().albumId)
    }

    // Track

    @Test
    fun `track request starts at the track and plays the rest of its album`() = runTest {
        val playback = resolve("Lanterns", FOCUS_TRACK, TITLE to "Lanterns")

        assertEquals(listOf("dust-1", "dust-2", "dust-3"), playback.ids())
        assertEquals(2, playback!!.startIndex)
    }

    @Test
    fun `track request picks the track of the named artist`() = runTest {
        val playback = resolve("Ashes von Echo Valley", FOCUS_TRACK, TITLE to "Ashes", ARTIST to "Echo Valley")

        assertEquals("cover-1", playback!!.tracks[playback.startIndex].id)
    }

    @Test
    fun `free text track name starts at that track`() = runTest {
        val playback = resolve("High Water")

        assertEquals("tides-3", playback!!.tracks[playback.startIndex].id)
    }

    // Artist

    @Test
    fun `artist request plays only that artist`() = runTest {
        val playback = resolve("Harbor Lights", FOCUS_ARTIST, ARTIST to "harbor lights")

        assertEquals(setOf("dust-1", "dust-2", "dust-3"), playback.ids().toSet())
    }

    @Test
    fun `free text artist name plays that artist`() = runTest {
        val playback = resolve("Echo Valley")

        assertEquals(setOf("cover-1", "tides-1", "tides-2", "tides-3"), playback.ids().toSet())
    }

    // Playlist

    @Test
    fun `playlist request plays the playlist in its order`() = runTest {
        val playback = resolve("Roadtrip", FOCUS_PLAYLIST, PLAYLIST to "roadtrip")

        assertEquals(listOf("tides-3", "dust-1", "cover-1"), playback.ids())
        assertEquals(0, playback!!.startIndex)
    }

    @Test
    fun `playlist request without extras uses the query`() = runTest {
        val playback = resolve("Roadtrip 2", FOCUS_PLAYLIST)

        assertEquals(listOf("tides-1"), playback.ids())
    }

    @Test
    fun `free text playlist name plays the playlist`() = runTest {
        val playback = resolve("roadtrip")

        assertEquals(listOf("tides-3", "dust-1", "cover-1"), playback.ids())
    }

    // Offline and misses

    @Test
    fun `offline only downloaded tracks play, starting at the requested one if it is there`() = runTest {
        val downloaded = setOf("dust-1", "dust-3")
        val playback = resolver.resolve(SERVER, request("Lanterns", FOCUS_TRACK, TITLE to "Lanterns")) {
            it.id in downloaded
        }

        assertEquals(listOf("dust-1", "dust-3"), playback.ids())
        assertEquals(1, playback!!.startIndex)
    }

    @Test
    fun `nothing downloaded offline plays nothing`() = runTest {
        val playback = resolver.resolve(SERVER, request("Roadtrip", FOCUS_PLAYLIST)) { false }

        assertNull(playback)
    }

    @Test
    fun `unknown names play nothing`() = runTest {
        assertNull(resolve("Nonexistent", FOCUS_ALBUM))
        assertNull(resolve("Nonexistent", FOCUS_TRACK))
        assertNull(resolve("Nonexistent", FOCUS_ARTIST))
        assertNull(resolve("Nonexistent", FOCUS_PLAYLIST))
        assertNull(resolve("Nonexistent"))
    }

    @Test
    fun `another server's library is not searched`() = runTest {
        assertNull(resolver.resolve("other-server", request("All Is Dust", FOCUS_ALBUM)) { true })
    }

    private suspend fun resolve(query: String, focus: String? = null, vararg extras: Pair<String, String>) =
        resolver.resolve(SERVER, request(query, focus, *extras)) { true }

    private fun request(query: String, focus: String? = null, vararg extras: Pair<String, String>): PlaySearchRequest {
        val all = extras.toMap() + listOfNotNull(focus?.let { FOCUS to it })
        return playSearchRequest(query, all::get)
    }

    private fun SearchPlayback?.ids() = this?.tracks.orEmpty().map { it.id }

    private companion object {
        const val SERVER = "server"
        const val HARBOR = "artist-harbor"
        const val ECHO = "artist-echo"
        const val DUST = "album-dust"
        const val DUST_COVER = "album-dust-cover"
        const val TIDES = "album-tides"
        const val ROADTRIP = "playlist-roadtrip"
        const val ROADTRIP_2 = "playlist-roadtrip-2"

        // android.provider.MediaStore's play-from-search extras.
        const val FOCUS = "android.intent.extra.focus"
        const val TITLE = "android.intent.extra.title"
        const val ALBUM = "android.intent.extra.album"
        const val ARTIST = "android.intent.extra.artist"
        const val PLAYLIST = "android.intent.extra.playlist"
        const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
        const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
        const val FOCUS_TRACK = "vnd.android.cursor.item/audio"
        const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"

        fun artist(id: String, name: String) = ArtistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
            isFavorite = false, overview = null, genres = emptyList(), cleanName = name, musicBrainzId = null,
            lastSynced = 0,
        )

        fun album(id: String, name: String, artistId: String, artistName: String) = AlbumEntity(
            id = id, serverId = SERVER, name = name, sortName = name, artistId = artistId, artistName = artistName,
            year = null, trackCount = 0, genres = emptyList(), imageTag = null, isFavorite = false,
            resolvedArtistId = artistId, dateAdded = 0, lastSynced = 0,
        )

        fun track(id: String, name: String, albumId: String, number: Int, artistId: String, artistName: String) =
            TrackEntity(
                id = id, serverId = SERVER, name = name, sortName = name, albumId = albumId, albumName = null,
                artistId = artistId, artistName = artistName, trackNumber = number, discNumber = 1, durationMs = 0,
                genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
                normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
                channels = null, resolvedArtistId = artistId, dateAdded = 0, lastSynced = 0,
            )

        fun playlist(id: String, name: String) = PlaylistEntity(
            id = id, serverId = SERVER, name = name, sortName = name, trackCount = 0, durationMs = 0,
            imageTag = null, isFavorite = false, isLocal = false, lastSynced = 0,
        )
    }
}
