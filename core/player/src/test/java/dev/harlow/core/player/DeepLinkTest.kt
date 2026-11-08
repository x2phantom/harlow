package dev.harlow.core.player

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkTest {

    private fun parse(link: String) = DeepLink.parse(Uri.parse(link))

    @Test
    fun `browses tabs and items`() {
        assertEquals(DeepLink.Browse(HarlowMediaService.TAB_FAVORITES), parse("harlow://browse/favorites"))
        assertEquals(DeepLink.Browse("album:$ID"), parse("harlow://browse/album/$ID"))
        assertEquals(DeepLink.Browse("artist:$ID"), parse("harlow://browse/artist/$ID"))
        assertEquals(DeepLink.Browse("playlist:$ID"), parse("harlow://browse/playlist/$ID"))
        assertEquals(DeepLink.Browse("genre:Hip Hop"), parse("harlow://browse/genre/Hip%20Hop"))
    }

    @Test
    fun `plays items, with ids as Room stores them`() {
        assertEquals(DeepLink.Play("album:$ID"), parse("harlow://play/album/$ID"))
        assertEquals(DeepLink.Play(ID), parse("harlow://play/track/$ID"))
        // Jellyfin's own URLs use ids without dashes
        assertEquals(DeepLink.Play("playlist:$ID"), parse("harlow://play/playlist/${ID.replace("-", "").uppercase()}"))
    }

    @Test
    fun `searches and opens`() {
        assertEquals(DeepLink.Search("daft punk", playFirst = false), parse("harlow://search?q=daft+punk"))
        assertEquals(DeepLink.Search("daft punk", playFirst = true), parse("harlow://search?q=daft%20punk&play=1"))
        assertEquals(DeepLink.Open, parse("harlow://open"))
    }

    @Test
    fun `rejects anything else`() {
        listOf(
            "https://browse/album/$ID",
            "harlow://browse/album/not-an-id",
            "harlow://browse/album/$ID/extra",
            "harlow://browse/unknown",
            "harlow://play/genre/$ID",
            "harlow://play/album",
            "harlow://search",
            "harlow://search?q=%20",
            "harlow://search?q=${"x".repeat(201)}",
            "harlow://settings",
            "harlow://unknown",
        ).forEach { assertNull(it, parse(it)) }
    }

    private companion object {
        const val ID = "0b5ac0e1-4f2c-6d3e-9a7b-8c1d2e3f4a5b"
    }
}
