package dev.harlow.core.player

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkUrisTest {

    @Test
    fun `an item with artwork asks for it, naming its placeholder`() {
        val uri = ArtworkUris.forItem(AUTHORITY, ID, imageTag = "t1", placeholder = ArtworkPlaceholder.ARTIST)

        assertEquals("content://$AUTHORITY/$ID?placeholder=artist&v=t1", uri.toString())
        assertEquals(ID, ArtworkUris.itemId(uri))
        assertEquals(ArtworkPlaceholder.ARTIST, ArtworkUris.placeholderOf(uri))
    }

    @Test
    fun `an item without artwork gets the placeholder of its kind straight away`() {
        for (kind in ArtworkPlaceholder.entries) {
            val uri = ArtworkUris.forItem(AUTHORITY, ID, imageTag = null, placeholder = kind)

            assertEquals("content://$AUTHORITY/placeholder/${kind.pathName}", uri.toString())
            assertNull(ArtworkUris.itemId(uri))
            assertEquals(kind, ArtworkUris.placeholderOf(uri))
        }
    }

    @Test
    fun `a replaced cover gets a new URI, so the media center doesn't keep showing the old one`() {
        val before = ArtworkUris.item(AUTHORITY, ID, ArtworkPlaceholder.ALBUM, imageTag = "waveform")
        val after = ArtworkUris.item(AUTHORITY, ID, ArtworkPlaceholder.ALBUM, imageTag = "cover")

        assertNotEquals(before, after)
        assertEquals(ID, ArtworkUris.itemId(after))
        assertEquals(ArtworkPlaceholder.ALBUM, ArtworkUris.placeholderOf(after))
    }

    @Test
    fun `older and unknown URIs fall back to the music note`() {
        assertEquals(ArtworkPlaceholder.MUSIC, ArtworkUris.placeholderOf(Uri.parse("content://$AUTHORITY/$ID")))
        assertEquals(ID, ArtworkUris.itemId(Uri.parse("content://$AUTHORITY/$ID")))
        assertEquals(
            ArtworkPlaceholder.MUSIC,
            ArtworkUris.placeholderOf(Uri.parse("content://$AUTHORITY/placeholder/../../etc")),
        )
        assertEquals(
            ArtworkPlaceholder.MUSIC,
            ArtworkUris.placeholderOf(Uri.parse("content://$AUTHORITY/$ID?placeholder=unknown")),
        )
    }

    private companion object {
        const val AUTHORITY = "eu.hwld.harlow.artwork"
        const val ID = "0f8c2a7e-6b1d-4c3e-9a5f-1d2e3f4a5b6c"
    }
}
