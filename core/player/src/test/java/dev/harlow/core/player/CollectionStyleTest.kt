package dev.harlow.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionStyleTest {

    @Test
    fun `list view asks the media center for list items`() {
        assertEquals(1, HarlowMediaService.collectionStyleOf(listView = true))
    }

    @Test
    fun `without list view the library stays a cover grid`() {
        assertEquals(2, HarlowMediaService.collectionStyleOf(listView = false))
    }
}
