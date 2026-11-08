package dev.harlow.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityTest {

    @Test
    fun `the default is 320 kbps`() {
        assertEquals(StreamQuality.KBPS_320, StreamQuality.DEFAULT)
    }

    @Test
    fun `original is never transcoded`() {
        assertNull(StreamQuality.ORIGINAL.transcodeBitrate(1_100_000))
        assertNull(StreamQuality.ORIGINAL.transcodeBitrate(null))
    }

    @Test
    fun `a file above the bitrate is transcoded`() {
        assertEquals(160_000, StreamQuality.KBPS_160.transcodeBitrate(1_100_000))
    }

    @Test
    fun `a file at or below the bitrate plays as it is`() {
        assertNull(StreamQuality.KBPS_320.transcodeBitrate(320_000))
        assertNull(StreamQuality.KBPS_320.transcodeBitrate(128_000))
    }

    @Test
    fun `a file of unknown bitrate is transcoded`() {
        assertEquals(128_000, StreamQuality.KBPS_128.transcodeBitrate(null))
    }
}
