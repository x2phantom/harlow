package dev.harlow.core.player.cache

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import java.io.IOException

/**
 * Which tracks play without the server: those whose stream lies completely in the streaming cache, so offline the
 * media center can still offer what was played before.
 *
 * A transcoded track is complete when its HLS playlist and every segment it lists are; the original file when all of
 * it is. Only tracks with any data in the cache are [candidates][candidateTrackIds], which keeps the check cheap for a
 * large library. Blocking (reads the playlist from disk): call it off the main thread.
 */
internal class CachedTracks(private val cache: Cache) {

    /** IDs of the tracks with any data in the cache, complete or not. */
    fun candidateTrackIds(): Set<String> = cache.keys.mapNotNullTo(HashSet()) { key ->
        STREAM_URL_TRACK_ID.find(key)?.groupValues?.get(1) ?: key.takeIf { TRACK_ID.matches(it) }
    }

    fun isComplete(source: StreamSource): Boolean {
        val key = source.customCacheKey ?: source.uri.toString()
        if (!isFullyCached(key)) return false
        if (!source.isHls) return true
        val playlist = readPlaylist(source.uri) ?: return false
        if (!playlist.hasEndTag || playlist.segments.isEmpty()) return false
        val initSegments = playlist.segments.mapNotNull { it.initializationSegment }
            .distinctBy { it.url to it.byteRangeOffset }
        val parts = initSegments + playlist.segments
        return parts.all { part ->
            val partKey = UriUtil.resolve(playlist.baseUri, part.url)
            if (part.byteRangeLength != C.LENGTH_UNSET.toLong()) {
                cache.isCached(partKey, part.byteRangeOffset, part.byteRangeLength)
            } else {
                isFullyCached(partKey)
            }
        }
    }

    /** Whether the cache holds [key] from start to end (its length is only known once it was read to the end). */
    private fun isFullyCached(key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, length)
    }

    /** The media playlist at [uri], read from the cache only (no upstream: nothing is fetched). */
    private fun readPlaylist(uri: Uri): HlsMediaPlaylist? = try {
        val source = CacheDataSource(cache, PlaceholderDataSource.INSTANCE)
        DataSourceInputStream(source, DataSpec(uri)).use { input ->
            HlsPlaylistParser().parse(uri, input) as? HlsMediaPlaylist
        }
    } catch (_: IOException) {
        null
    }

    private companion object {
        /** Stream URLs (playlist, segments, original file) name the track: `…/Audio/<id>/…`. */
        val STREAM_URL_TRACK_ID = Regex("/Audio/([0-9a-fA-F-]{32,36})/")

        /** Originals and downloads are cached under the bare track ID. */
        val TRACK_ID = Regex("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}")
    }
}
