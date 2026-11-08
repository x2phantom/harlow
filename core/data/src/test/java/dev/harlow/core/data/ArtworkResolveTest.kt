package dev.harlow.core.data

import android.content.Context
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.model.Server
import dev.harlow.core.network.ImageFetchResult
import dev.harlow.core.network.ImageFetcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The artwork provider is open to every app: it fetches only items of the signed-in library. */
class ArtworkResolveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val serverDao = mockk<ServerDao>(relaxed = true)
    private val activeSession = mockk<ActiveSession>()
    private val imageFetcher = mockk<ImageFetcher>()

    private fun cacher() = ArtworkPreCacher(
        context = mockk<Context> { every { cacheDir } returns folder.root },
        serverDao = serverDao,
        activeSession = activeSession,
        imageFetcher = imageFetcher,
        albumDao = mockk(),
        artistDao = mockk(),
        playlistDao = mockk(),
        trackDao = mockk(),
    )

    @Test
    fun `an ID that isn't in the library is not fetched`() = runTest {
        coEvery { serverDao.getActiveServer() } returns ENTITY
        coEvery { serverDao.isKnownItem("s1", ITEM_ID) } returns false

        assertNull(cacher().resolveArtwork(ITEM_ID))

        verify(exactly = 0) { imageFetcher.fetchPrimaryImage(any(), any(), any()) }
    }

    @Test
    fun `an invalid ID doesn't even reach the database`() = runTest {
        assertNull(cacher().resolveArtwork("..%2F..%2Fdatabases%2Fharlow.db"))

        coVerify(exactly = 0) { serverDao.isKnownItem(any(), any()) }
    }

    @Test
    fun `an item of the library is fetched and cached`() = runTest {
        coEvery { serverDao.getActiveServer() } returns ENTITY
        coEvery { serverDao.isKnownItem("s1", ITEM_ID) } returns true
        coEvery { activeSession.ensureConnected() } returns Server("s1", "Jellyfin", URL, "u1", "token")
        every { imageFetcher.fetchPrimaryImage(URL, ITEM_ID, any()) } answers {
            thirdArg<File>().writeText("image")
            ImageFetchResult.SUCCESS
        }

        val file = cacher().resolveArtwork(ITEM_ID)

        assertEquals("image", file?.readText())
    }

    @Test
    fun `a cover replaced on the server is fetched again and the old one goes`() = runTest {
        libraryItem(imageTag = "cover")
        val old = File(artworkDir(), "$ITEM_ID-waveform.webp").apply { writeText("waveform") }
        val legacy = File(artworkDir(), "$ITEM_ID.webp").apply { writeText("waveform") }
        every { imageFetcher.fetchPrimaryImage(URL, ITEM_ID, any()) } answers {
            thirdArg<File>().writeText("cover")
            ImageFetchResult.SUCCESS
        }

        val file = cacher().resolveArtwork(ITEM_ID)

        assertEquals("cover", file?.readText())
        assertEquals("$ITEM_ID-cover.webp", file?.name)
        assertFalse(old.exists())
        assertFalse(legacy.exists())
    }

    @Test
    fun `the current cover is not fetched again`() = runTest {
        libraryItem(imageTag = "cover")
        File(artworkDir(), "$ITEM_ID-cover.webp").writeText("cover")

        assertEquals("cover", cacher().resolveArtwork(ITEM_ID)?.readText())

        verify(exactly = 0) { imageFetcher.fetchPrimaryImage(any(), any(), any()) }
    }

    @Test
    fun `offline the older cover stays until the new one can be fetched`() = runTest {
        libraryItem(imageTag = "cover")
        val old = File(artworkDir(), "$ITEM_ID-waveform.webp").apply { writeText("waveform") }
        every { imageFetcher.fetchPrimaryImage(URL, ITEM_ID, any()) } returns ImageFetchResult.ERROR

        assertEquals("waveform", cacher().resolveArtwork(ITEM_ID)?.readText())
        assertTrue(old.exists())
    }

    @Test
    fun `cache file names map back to their item`() {
        val dashed = "0f8c2a7e-6b1d-4c3e-9a5f-1d2e3f4a5b6c"
        assertEquals("$ITEM_ID-abc123", ArtworkPreCacher.cacheKey(ITEM_ID, "abc123"))
        assertEquals(ITEM_ID, ArtworkPreCacher.cacheKey(ITEM_ID, "../x"))
        assertEquals(ITEM_ID, ArtworkPreCacher.itemIdOf("$ITEM_ID-abc123.webp"))
        assertEquals(ITEM_ID, ArtworkPreCacher.itemIdOf("$ITEM_ID.noart"))
        assertEquals(dashed, ArtworkPreCacher.itemIdOf("$dashed-abc123.webp.tmp"))
        assertEquals(dashed, ArtworkPreCacher.itemIdOf("$dashed.webp"))
        assertNull(ArtworkPreCacher.itemIdOf("placeholder-album.png"))
    }

    private fun artworkDir() = File(folder.root, "artwork").apply { mkdirs() }

    private fun libraryItem(imageTag: String?) {
        coEvery { serverDao.getActiveServer() } returns ENTITY
        coEvery { serverDao.isKnownItem("s1", ITEM_ID) } returns true
        coEvery { serverDao.imageTagOf("s1", ITEM_ID) } returns imageTag
        coEvery { activeSession.ensureConnected() } returns Server("s1", "Jellyfin", URL, "u1", "token")
    }

    private companion object {
        const val ITEM_ID = "a5e2c4b1d0f94e3a8b7c6d5e4f3a2b1c"
        const val URL = "https://jelly.example"
        val ENTITY = ServerEntity("s1", "Jellyfin", URL, "u1", "v1:x:y", true, 0)
    }
}
