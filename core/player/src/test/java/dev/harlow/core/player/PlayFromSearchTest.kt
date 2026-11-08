package dev.harlow.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayFromSearchTest {

    @Test
    fun `structured artist request keeps focus and artist`() {
        val extras = mapOf(
            "android.intent.extra.focus" to "vnd.android.cursor.item/artist",
            "android.intent.extra.artist" to " Radiohead ",
        )
        val request = playSearchRequest("radiohead", extras::get)
        assertEquals(PlaySearchRequest.Focus.Artist, request.focus)
        assertEquals("Radiohead", request.artist)
        assertEquals("radiohead", request.query)
    }

    @Test
    fun `track request with artist`() {
        val extras = mapOf(
            "android.intent.extra.focus" to "vnd.android.cursor.item/audio",
            "android.intent.extra.title" to "Karma Police",
            "android.intent.extra.artist" to "Radiohead",
        )
        val request = playSearchRequest("karma police by radiohead", extras::get)
        assertEquals(PlaySearchRequest.Focus.Track, request.focus)
        assertEquals("Karma Police", request.title)
        assertEquals("Radiohead", request.artist)
    }

    @Test
    fun `album request keeps focus and album`() {
        val extras = mapOf(
            "android.intent.extra.focus" to "vnd.android.cursor.item/album",
            "android.intent.extra.album" to "All Is Dust",
        )
        val request = playSearchRequest("all is dust", extras::get)
        assertEquals(PlaySearchRequest.Focus.Album, request.focus)
        assertEquals("All Is Dust", request.album)
        assertEquals(null, request.artist)
    }

    @Test
    fun `playlist request keeps focus and playlist`() {
        val extras = mapOf(
            "android.intent.extra.focus" to "vnd.android.cursor.item/playlist",
            "android.intent.extra.playlist" to "Roadtrip",
        )
        val request = playSearchRequest("roadtrip", extras::get)
        assertEquals(PlaySearchRequest.Focus.Playlist, request.focus)
        assertEquals("Roadtrip", request.playlist)
    }

    @Test
    fun `a playlist extra alone is not an empty request`() {
        val request = playSearchRequest(null, mapOf("android.intent.extra.playlist" to "Roadtrip")::get)
        assertEquals(false, request.isEmpty)
    }

    @Test
    fun `free text without extras is unstructured`() {
        val request = playSearchRequest("ok computer") { null }
        assertEquals(PlaySearchRequest.Focus.Unstructured, request.focus)
        assertEquals(false, request.isEmpty)
    }

    @Test
    fun `no query and blank extras ask for anything`() {
        val request = playSearchRequest(null, mapOf("android.intent.extra.artist" to "  ")::get)
        assertTrue(request.isEmpty)
    }

    private data class Item(val name: String, val artist: String?)

    @Test
    fun `best match prefers name and artist`() {
        val items = listOf(Item("Creep", "Stone Temple Pilots"), Item("Creep", "Radiohead"), Item("Creeping", "X"))
        assertEquals(Item("Creep", "Radiohead"), items.bestMatch("creep", "radiohead", Item::name, Item::artist))
    }

    @Test
    fun `best match without artist takes the exact name`() {
        val items = listOf(Item("Creeping", "X"), Item("Creep", "Radiohead"))
        assertEquals(Item("Creep", "Radiohead"), items.bestMatch("Creep", null, Item::name, Item::artist))
    }

    @Test
    fun `best match falls back to the first candidate`() {
        val items = listOf(Item("Creeping", "X"), Item("Creeper", "Y"))
        assertEquals(Item("Creeping", "X"), items.bestMatch("creep", "nobody", Item::name, Item::artist))
    }
}
