package dev.harlow.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.harlow.core.common.PLAY_SESSION_ID_PARAM
import dev.harlow.core.common.PlaybackReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reports playback of [player] to the Jellyfin server: a track as started once it actually plays, its progress every
 * [PROGRESS_INTERVAL_MS] while playing, and stopped when the queue ends or the service goes away.
 *
 * Lives in the service, as on AAOS the car's media center is the only UI and no in-app controller is around to
 * report. A queue that is merely restored (paused) isn't reported until it plays.
 *
 * [onTrackLeft] runs once the server no longer has a track as playing: after the next track was reported as started,
 * or the track as stopped.
 */
internal class PlaybackReporting(
    private val player: Player,
    private val reporter: PlaybackReporter,
    private val scope: CoroutineScope,
    private val onTrackLeft: suspend (trackId: String) -> Unit = {},
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())

    /** The track last reported as started, so a pause/resume doesn't report it again. */
    private var reportedTrackId: String? = null
    private var reportedPlaySessionId: String? = null

    private val reportProgress = object : Runnable {
        override fun run() {
            val trackId = reportedTrackId
            if (trackId != null && player.isPlaying) {
                val positionMs = player.currentPosition
                val playSessionId = reportedPlaySessionId
                scope.launch { reporter.reportProgress(trackId, positionMs, playSessionId) }
            }
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    fun attach() {
        player.addListener(this)
    }

    /** Reports the current track as stopped and detaches; call before the player is released. */
    fun release() {
        handler.removeCallbacks(reportProgress)
        player.removeListener(this)
        reportedTrackId?.let { trackId -> reportStopped(trackId, player.currentPosition) }
        reportedTrackId = null
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(reportProgress)
        if (!isPlaying) return
        val current = player.currentMediaItem?.mediaId
        if (current != null && current != reportedTrackId) reportStarted(current)
        handler.postDelayed(reportProgress, PROGRESS_INTERVAL_MS)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A new current item is only reported once it plays: a queue set while paused may never be played.
        if (mediaItem != null && player.isPlaying && mediaItem.mediaId != reportedTrackId) {
            reportStarted(mediaItem.mediaId)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        val trackId = reportedTrackId ?: return
        reportedTrackId = null
        reportStopped(trackId, player.currentPosition)
    }

    private fun reportStarted(trackId: String) {
        val previous = reportedTrackId
        reportedTrackId = trackId
        val playSessionId = player.currentMediaItem?.localConfiguration?.uri?.getQueryParameter(PLAY_SESSION_ID_PARAM)
        reportedPlaySessionId = playSessionId
        scope.launch {
            reporter.reportStarted(trackId, playSessionId)
            previous?.let { onTrackLeft(it) }
        }
    }

    private fun reportStopped(trackId: String, positionMs: Long) {
        val playSessionId = reportedPlaySessionId
        scope.launch {
            reporter.reportStopped(trackId, positionMs, playSessionId)
            onTrackLeft(trackId)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
    }
}
