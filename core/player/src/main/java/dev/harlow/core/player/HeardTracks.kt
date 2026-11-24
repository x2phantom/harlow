package dev.harlow.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Notices when playback leaves a track that was listened to (skipped, ended, or its queue replaced), so its rest can
 * be cached for offline use ([dev.harlow.core.player.cache.TrackCacheCompleter]).
 */
internal class HeardTracks(private val onLeft: (MediaItem) -> Unit) : Player.Listener {

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        heardTrackLeft(oldPosition, newPosition)?.let(onLeft)
    }
}

/** Played at least this far, a track counts as listened to rather than skipped through. */
internal const val MIN_HEARD_MS = 5_000L

/** The track playback left, if it got [MIN_HEARD_MS] into it; `null` within a track (seek, repeat of one track). */
internal fun heardTrackLeft(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo): MediaItem? {
    val left = oldPosition.mediaItem ?: return null
    if (left.mediaId == newPosition.mediaItem?.mediaId) return null
    if (left.localConfiguration == null || oldPosition.positionMs < MIN_HEARD_MS) return null
    return left
}
