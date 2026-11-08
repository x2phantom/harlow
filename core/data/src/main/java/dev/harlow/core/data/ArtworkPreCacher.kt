package dev.harlow.core.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.network.ImageFetchResult
import dev.harlow.core.network.ImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkPreCacher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val activeSession: ActiveSession,
    private val imageFetcher: ImageFetcher,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
) : SignOutCleanup {
    private val cacheDir = File(context.cacheDir, "artwork")

    suspend fun preCacheArtwork(
        serverId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val server = activeSession.ensureConnected() ?: return@withContext
        cacheDir.mkdirs()

        cleanupLegacyJpeg()

        val all = albumDao.getImageTags(serverId) +
            artistDao.getImageTags(serverId) +
            playlistDao.getImageTags(serverId) +
            trackDao.getOrphanTrackImageTags(serverId)
        val missing = all.filter { !cacheFile(it.id, it.imageTag).isUsable() }

        if (missing.isEmpty()) {
            Log.d(TAG, "Artwork cache up to date (${all.size} items)")
            return@withContext
        }

        Log.d(TAG, "Pre-caching ${missing.size} artwork images (${all.size} total)")
        // Listed once: what an item had cached before (an older image) goes once its current image is there.
        val cachedByItem = cacheDir.listFiles().orEmpty().groupBy { itemIdOf(it.name) }
        var cached = 0
        var failed = 0
        var negCached = 0

        missing.forEachIndexed { index, item ->
            if (index % 50 == 0) {
                onProgress(SyncProgress("artwork", index, missing.size))
            }
            val result = downloadArtwork(server.url, item.id, item.imageTag)
            if (result != DownloadResult.ERROR) removeOutdated(item.id, item.imageTag, cachedByItem[item.id])
            when (result) {
                DownloadResult.SUCCESS -> cached++
                DownloadResult.NOT_FOUND -> negCached++
                DownloadResult.ERROR -> failed++
            }
        }

        onProgress(SyncProgress("artwork", missing.size, missing.size))
        Log.d(TAG, "Artwork pre-cache complete: $cached cached, $negCached not found, $failed failed")
    }

    /**
     * The cached artwork of [itemId], downloaded first if needed. Only IDs of the signed-in server's library are
     * downloaded: the artwork provider is open to every app on the head unit, and it shouldn't fetch arbitrary items
     * with the user's token. `null` if there is none (the caller shows a placeholder).
     *
     * Cached per image tag: a cover replaced on the server gets a new tag and is downloaded again. Until that
     * succeeds (offline) the older image stays.
     */
    suspend fun resolveArtwork(itemId: String): File? = withContext(Dispatchers.IO) {
        if (!isItemId(itemId)) return@withContext null
        val serverId = serverDao.getActiveServer()?.id ?: return@withContext null
        if (!serverDao.isKnownItem(serverId, itemId)) return@withContext null
        val imageTag = serverDao.imageTagOf(serverId, itemId)
        val cacheFile = cacheFile(itemId, imageTag)
        if (cacheFile.isUsable()) return@withContext cacheFile
        if (noArtMarker(itemId, imageTag).exists()) return@withContext null
        val server = activeSession.ensureConnected()
        cacheDir.mkdirs()
        val result = server?.let { downloadArtwork(it.url, itemId, imageTag) } ?: DownloadResult.ERROR
        val cachedBefore = cacheDir.listFiles { f -> itemIdOf(f.name) == itemId }.orEmpty().toList()
        when (result) {
            DownloadResult.SUCCESS -> cacheFile.also { removeOutdated(itemId, imageTag, cachedBefore) }
            DownloadResult.NOT_FOUND -> null.also { removeOutdated(itemId, imageTag, cachedBefore) }
            DownloadResult.ERROR -> cachedBefore.firstOrNull { it.extension == IMAGE_EXTENSION && it.isUsable() }
        }
    }

    private fun downloadArtwork(serverUrl: String, itemId: String, imageTag: String?): DownloadResult {
        val cacheFile = cacheFile(itemId, imageTag)
        if (cacheFile.isUsable()) return DownloadResult.SUCCESS

        val noArtMarker = noArtMarker(itemId, imageTag)
        return when (imageFetcher.fetchPrimaryImage(serverUrl, itemId, cacheFile)) {
            ImageFetchResult.SUCCESS -> {
                noArtMarker.delete()
                DownloadResult.SUCCESS
            }
            ImageFetchResult.NOT_FOUND -> {
                noArtMarker.createNewFile()
                DownloadResult.NOT_FOUND
            }
            ImageFetchResult.ERROR -> DownloadResult.ERROR
        }
    }

    suspend fun preCacheIds(
        itemIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val server = activeSession.ensureConnected() ?: return@withContext
        cacheDir.mkdirs()
        val missing = itemIds.filter { isItemId(it) }
            .map { id -> id to serverDao.imageTagOf(server.id, id) }
            .filter { (id, tag) -> !cacheFile(id, tag).isUsable() && !noArtMarker(id, tag).exists() }
        if (missing.isEmpty()) return@withContext
        Log.d(TAG, "Pre-caching ${missing.size} priority artwork images")
        missing.forEach { (id, tag) -> downloadArtwork(server.url, id, tag) }
    }

    /** The files of [itemId] cached for an image other than [imageTag]'s go: they show a cover that was replaced. */
    private fun removeOutdated(itemId: String, imageTag: String?, cached: List<File>?) {
        val current = cacheKey(itemId, imageTag)
        cached?.forEach { file -> if (file.name.substringBefore('.') != current) file.delete() }
    }

    private fun cacheFile(itemId: String, imageTag: String?) =
        File(cacheDir, "${cacheKey(itemId, imageTag)}.$IMAGE_EXTENSION")

    private fun noArtMarker(itemId: String, imageTag: String?) = File(cacheDir, "${cacheKey(itemId, imageTag)}.noart")

    private fun File.isUsable() = exists() && length() > 0

    private fun cleanupLegacyJpeg() {
        val jpegFiles = cacheDir.listFiles { f -> f.extension == "jpg" } ?: return
        if (jpegFiles.isEmpty()) return
        Log.d(TAG, "Cleaning up ${jpegFiles.size} legacy JPEG files")
        jpegFiles.forEach { it.delete() }
    }

    /** Signing out: the cached covers show what the user listens to. */
    override suspend fun clearOnSignOut() {
        withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }
    }

    private enum class DownloadResult { SUCCESS, NOT_FOUND, ERROR }

    companion object {
        private const val TAG = "ArtworkPreCacher"

        /** Jellyfin item IDs: 32 hex digits, with or without the GUID's dashes. Nothing else becomes a file name. */
        private val ITEM_ID = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

        /** Jellyfin image tags are hex digests; anything else isn't put into a file name. */
        private val IMAGE_TAG = Regex("[0-9a-zA-Z]{1,64}")

        private const val IMAGE_EXTENSION = "webp"

        fun isItemId(value: String): Boolean = ITEM_ID.matches(value)

        /** `<itemId>-<imageTag>`, or only the item id if there is no (usable) tag. */
        internal fun cacheKey(itemId: String, imageTag: String?): String =
            if (imageTag != null && IMAGE_TAG.matches(imageTag)) "$itemId-$imageTag" else itemId

        /** The item a cache file belongs to (`<key>.webp`, `<key>.noart`, `<key>.webp.tmp`), null for other files. */
        internal fun itemIdOf(fileName: String): String? {
            val key = fileName.substringBefore('.')
            return when {
                ITEM_ID.matches(key) -> key
                ITEM_ID.matches(key.substringBeforeLast('-')) -> key.substringBeforeLast('-')
                else -> null
            }
        }
    }
}
