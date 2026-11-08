package dev.harlow.core.player.cache

import android.net.Uri
import dev.harlow.core.common.jellyfinStreamUrl
import dev.harlow.core.common.jellyfinTranscodedStreamUrl

/**
 * Where the player fetches a track from, and so where it lies in the cache: a transcoded track is an HLS playlist
 * whose segments are cached under their URLs (which include the bitrate); the original file is cached under the
 * track's ID, as are downloads.
 */
internal data class StreamSource(val uri: Uri, val customCacheKey: String?, val isHls: Boolean)

/** The stream of [trackId]: transcoded at [transcodeBitrate], or the original file if that's `null` or [original]. */
internal fun streamSource(
    serverUrl: String,
    trackId: String,
    transcodeBitrate: Int?,
    original: Boolean = false,
): StreamSource =
    if (transcodeBitrate != null && !original) {
        StreamSource(Uri.parse(jellyfinTranscodedStreamUrl(serverUrl, trackId, transcodeBitrate)), null, isHls = true)
    } else {
        StreamSource(Uri.parse(jellyfinStreamUrl(serverUrl, trackId)), trackId, isHls = false)
    }
