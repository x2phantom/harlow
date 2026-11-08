package dev.harlow.core.network

import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ImageFetchResult { SUCCESS, NOT_FOUND, ERROR }

/**
 * Downloads an item's primary image from the signed-in server, through the shared client (system trust, token in
 * the Authorization header, not in the URL). Blocking: call on an IO dispatcher.
 */
@Singleton
class ImageFetcher @Inject constructor(private val jellyfinClient: JellyfinClientWrapper) {

    private val client by lazy {
        jellyfinClient.httpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Writes the image to [target] (via a temporary file next to it, so a broken download leaves nothing). */
    fun fetchPrimaryImage(serverUrl: String, itemId: String, target: File): ImageFetchResult {
        val request = Request.Builder()
            .url("$serverUrl/Items/$itemId/Images/Primary?maxWidth=600&quality=90&format=Webp")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_NOT_FOUND -> ImageFetchResult.NOT_FOUND
                    !response.isSuccessful -> ImageFetchResult.ERROR
                    else -> {
                        val tmp = File(target.parentFile, "${target.name}.tmp")
                        tmp.outputStream().use { response.body.byteStream().copyTo(it) }
                        if (tmp.renameTo(target)) ImageFetchResult.SUCCESS else ImageFetchResult.ERROR
                    }
                }
            }
        } catch (_: IOException) {
            ImageFetchResult.ERROR
        } catch (_: IllegalArgumentException) {
            ImageFetchResult.ERROR
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val HTTP_NOT_FOUND = 404
    }
}
