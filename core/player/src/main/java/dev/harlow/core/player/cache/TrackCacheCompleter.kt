package dev.harlow.core.player.cache

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Loads the rest of a track that was played only partly into the streaming cache, so it plays offline as well: the
 * player buffers less than a minute ahead, and offline only tracks complete in the cache are offered ([CachedTracks]).
 *
 * Fills the same cache keys as the player (HLS playlist and segments by URL, the original under the track's ID) and
 * fetches only what is missing. One track at a time, in the background; a failure (offline) leaves the track as it is.
 */
internal class TrackCacheCompleter(
    private val cache: Cache,
    private val upstream: DataSource.Factory,
) {
    private val cachedTracks = CachedTracks(cache)
    private val pending: MutableSet<StreamSource> = Collections.synchronizedSet(HashSet())
    private val jobs: MutableSet<Job> = Collections.synchronizedSet(HashSet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)

    /** Completes [source] in [scope] after what was queued before; a source already queued isn't queued again. */
    fun complete(scope: CoroutineScope, source: StreamSource) {
        if (!pending.add(source)) return
        val job = scope.launch(worker) { runInterruptible { completeNow(source) } }
        jobs += job
        job.invokeOnCompletion {
            jobs -= job
            pending -= source
        }
    }

    /** Stops all completions (signing out: nothing may be written to the cache afterwards). */
    fun cancelAll() {
        synchronized(jobs) { jobs.toList() }.forEach { it.cancel() }
    }

    /** Blocking: completes [source] now. */
    fun completeNow(source: StreamSource) {
        if (cachedTracks.isComplete(source)) return
        try {
            downloader(source).download(null)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't complete a track in the cache: ${e.javaClass.simpleName}")
        }
    }

    private fun downloader(source: StreamSource): Downloader {
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        val item = MediaItem.Builder()
            .setUri(source.uri)
            .setCustomCacheKey(source.customCacheKey)
            .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        return if (source.isHls) {
            HlsDownloader(item, factory, Runnable::run)
        } else {
            ProgressiveDownloader(item, factory, Runnable::run)
        }
    }

    private companion object {
        const val TAG = "TrackCacheCompleter"
    }
}
