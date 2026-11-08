package dev.harlow.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFallbackTest {

    @Test
    fun `falls back when the server refuses or fails to transcode`() {
        assertTrue(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertTrue(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 500))
    }

    @Test
    fun `falls back when the result can't be read`() {
        assertTrue(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, null))
        assertTrue(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_DECODING_FAILED, null))
    }

    @Test
    fun `stays with the error when signed out or the track is gone`() {
        assertFalse(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 401))
        assertFalse(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
    }

    @Test
    fun `stays with the error when the server can't be reached`() {
        assertFalse(shouldFallBackToOriginal(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
    }
}
