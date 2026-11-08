package dev.harlow.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Server URLs carry no token: the shared HTTP client sends it in the Authorization header. */
class StreamUrlTest {

    @Test
    fun `stream and image URLs contain no api_key`() {
        listOf("original", "high", "medium").forEach { quality ->
            assertFalse(jellyfinStreamUrl("https://jelly.example", "abc", quality).contains("api_key"))
        }
        assertFalse(jellyfinImageUrl("https://jelly.example", "abc").contains("api_key"))
    }

    @Test
    fun `original quality streams the file as is`() {
        val url = jellyfinStreamUrl("https://jelly.example", "abc")

        assertEquals("https://jelly.example/Audio/abc/stream?static=true", url)
    }

    @Test
    fun `transcoded stream is HLS with the bitrate and a media source ID without dashes`() {
        val url = jellyfinTranscodedStreamUrl("https://jelly.example", "ab-cd", 160_000)

        assertEquals(
            "https://jelly.example/Audio/ab-cd/main.m3u8?segmentContainer=mp4&transcodingContainer=mp4" +
                "&audioCodec=aac&audioBitRate=160000&mediaSourceId=abcd&playSessionId=abcd-160000",
            url,
        )
        assertFalse(url.contains("api_key"))
    }
}
