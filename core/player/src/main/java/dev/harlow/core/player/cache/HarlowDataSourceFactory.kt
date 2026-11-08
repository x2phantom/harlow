package dev.harlow.core.player.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dev.harlow.core.network.JellyfinClientWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HarlowDataSourceFactory @Inject constructor(
    private val harlowCache: HarlowCache,
    jellyfinClient: JellyfinClientWrapper,
) {
    // The shared client signs requests to the server with the Authorization header: stream URLs carry no token.
    private val networkDataSourceFactory: DataSource.Factory by lazy {
        OkHttpDataSource.Factory(jellyfinClient.httpClient)
    }

    /**
     * Creates a DataSource.Factory for streaming playback.
     *
     * Resolution order:
     * 1. Check downloadCache (read-only, for completed downloads)
     * 2. Check streamingCache (read/write, for recently streamed content)
     * 3. Fetch from network (writes to streamingCache)
     */
    fun createPlaybackDataSourceFactory(): DataSource.Factory {
        val streamingCacheFactory = CacheDataSource.Factory()
            .setCache(harlowCache.streamingCache)
            .setUpstreamDataSourceFactory(networkDataSourceFactory)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

        return CacheDataSource.Factory()
            .setCache(harlowCache.downloadCache)
            .setUpstreamDataSourceFactory(streamingCacheFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createDownloadDataSourceFactory(): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(harlowCache.downloadCache)
            .setUpstreamDataSourceFactory(networkDataSourceFactory)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createNetworkDataSourceFactory(): DataSource.Factory = networkDataSourceFactory
}
