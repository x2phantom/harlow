package dev.harlow.core.player

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Gives the audio focus back once the queue has played to its end (a media app releases focus when playback ends).
 * Media3 keeps the focus in [Player.STATE_ENDED] and only abandons it in [Player.STATE_IDLE], so the player goes back
 * to the start of the queue, paused, and stops. Play in the media center then prepares the queue and starts over. A
 * user's pause keeps the focus, as Android's media guidance does.
 */
internal class ReleaseFocusAtEnd(
    private val player: Player,
    private val handler: Handler,
) : Player.Listener {

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        // Posted: the other listeners (playback reporting, queue persistence) still see the ended state first.
        handler.post {
            if (player.playbackState != Player.STATE_ENDED) return@post
            player.pause()
            val first = player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled)
            if (first != C.INDEX_UNSET) player.seekToDefaultPosition(first)
            player.stop()
        }
    }
}
