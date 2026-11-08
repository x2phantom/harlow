package dev.harlow.core.player

import android.os.SystemClock
import androidx.media3.common.Player

/**
 * Notices a play request the system denies audio focus for (a call, navigation or another source has priority). Media3 then quietly stays paused; [onDenied] lets the service tell the driver why.
 *
 * Media3 answers a denied request by turning `playWhenReady` off again, with
 * [Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS], right after the request. (It reports playing in between:
 * the request is masked as granted until the playback thread asked for focus.) The same event later is an ordinary
 * loss of focus to another source and passes silently, as does a transient loss (playback is only suppressed then and
 * resumes by itself).
 */
internal class AudioFocusDenialListener(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onDenied: () -> Unit,
) : Player.Listener {

    /** When the user last asked to play, while that request may still be denied. */
    private var playRequestedAt: Long? = null

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            playRequestedAt = if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) clock() else null
            return
        }
        val requestedAt = playRequestedAt
        playRequestedAt = null
        if (requestedAt != null &&
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
            clock() - requestedAt <= DENIAL_WINDOW_MS
        ) {
            onDenied()
        }
    }

    private companion object {
        /** The focus request follows the play request within milliseconds; later losses are other sources'. */
        const val DENIAL_WINDOW_MS = 2_000L
    }
}
