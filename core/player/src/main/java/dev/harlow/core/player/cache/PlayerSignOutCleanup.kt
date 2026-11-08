package dev.harlow.core.player.cache

import android.util.Log
import androidx.media3.datasource.cache.Cache
import dev.harlow.core.data.SignOutCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing out: removes downloaded and streamed audio, which show what the user listened to. The media service stops
 * the player once the session is gone and then clears the streaming cache again ([clearStreamingCache]), for the
 * track that was still playing (and locked) during the first pass.
 */
@Singleton
class PlayerSignOutCleanup @Inject constructor(
    private val harlowCache: HarlowCache,
) : SignOutCleanup {

    override suspend fun clearOnSignOut() {
        withContext(Dispatchers.IO) {
            harlowCache.downloadCache.removeAll()
            harlowCache.streamingCache.removeAll()
        }
    }

    suspend fun clearStreamingCache() = withContext(Dispatchers.IO) { harlowCache.streamingCache.removeAll() }

    private fun Cache.removeAll() {
        keys.toList().forEach { key ->
            try {
                removeResource(key)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove a cached resource", e)
            }
        }
    }

    private companion object {
        const val TAG = "PlayerSignOutCleanup"
    }
}
