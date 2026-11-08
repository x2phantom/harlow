package dev.harlow.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only Jellyfin item IDs reach the artwork cache's file names and the server. */
class ArtworkItemIdTest {

    @Test
    fun `Jellyfin IDs with and without dashes are accepted`() {
        assertTrue(ArtworkPreCacher.isItemId("a5e2c4b1d0f94e3a8b7c6d5e4f3a2b1c"))
        assertTrue(ArtworkPreCacher.isItemId("a5e2c4b1-d0f9-4e3a-8b7c-6d5e4f3a2b1c"))
    }

    @Test
    fun `paths and anything else are rejected`() {
        assertFalse(ArtworkPreCacher.isItemId("../../databases/harlow.db"))
        assertFalse(ArtworkPreCacher.isItemId("a5e2c4b1d0f94e3a8b7c6d5e4f3a2b1c/../x"))
        assertFalse(ArtworkPreCacher.isItemId("placeholder"))
        assertFalse(ArtworkPreCacher.isItemId(""))
    }
}
