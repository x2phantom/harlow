package dev.harlow.core.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusDenialTest {

    private var now = 10_000L
    private var denials = 0
    private val listener = AudioFocusDenialListener(clock = { now }) { denials++ }

    private fun play() = listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

    private fun focusLoss() =
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)

    @Test
    fun `focus denied on a play request is reported once`() {
        play()
        // Media3 masks the request as playing until the playback thread asked for focus.
        listener.onIsPlayingChanged(true)
        now += 20
        focusLoss()
        focusLoss()

        assertEquals(1, denials)
    }

    @Test
    fun `losing focus to another source while playing is not a denial`() {
        play()
        now += 60_000
        focusLoss()

        assertEquals(0, denials)
    }

    @Test
    fun `pausing is not a denial`() {
        play()
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        focusLoss()

        assertEquals(0, denials)
    }

    @Test
    fun `resuming that wasn't the user's request is not watched`() {
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        focusLoss()

        assertEquals(0, denials)
    }

    @Test
    fun `a new play request after a denial is watched again`() {
        play()
        focusLoss()
        now += 5_000
        play()
        focusLoss()

        assertEquals(2, denials)
    }
}
