package dev.harlow.core.common

/**
 * Authority of the ArtworkProvider, "${packageName}.artwork". ArtworkProvider.onCreate() sets it at process start, so
 * it follows the applicationId (e.g. a debug suffix); the default is the release app's.
 */
@Volatile
var artworkAuthority: String = "eu.hwld.harlow.artwork"

fun artworkUri(itemId: String): String = "content://$artworkAuthority/$itemId"

// Server URLs carry no token: the shared HTTP client (JellyfinClientWrapper.httpClient) signs requests to the server
// with the Authorization header, so the token stays out of server and proxy logs.

fun jellyfinImageUrl(
    serverUrl: String,
    itemId: String,
    maxWidth: Int = 600,
    quality: Int = 90,
): String = "$serverUrl/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=$quality&format=Webp"

fun jellyfinStreamUrl(
    serverUrl: String,
    itemId: String,
    quality: String = "original",
): String {
    val base = "$serverUrl/Audio/$itemId/stream"
    return when (quality) {
        "high" -> "$base?static=false&audioCodec=mp3&audioBitRate=320000"
        "medium" -> "$base?static=false&audioCodec=opus&audioBitRate=128000&container=ogg"
        else -> "$base?static=true"
    }
}

/**
 * HLS, as only this can seek in a transcoded track: the server answers progressive transcoding (`/stream?static=false`)
 * without `Content-Length` and ignores `Range`. The URL stays the same per track and bitrate, so the player's cache
 * finds the segments again. `mediaSourceId` is the ID in the server's own form, without dashes. The server only accepts
 * a report of transcoding with the `playSessionId` of a running transcode (see [PlaybackReporter]).
 */
fun jellyfinTranscodedStreamUrl(
    serverUrl: String,
    itemId: String,
    bitrate: Int,
): String {
    val id = itemId.replace("-", "")
    return "$serverUrl/Audio/$itemId/main.m3u8?segmentContainer=mp4&transcodingContainer=mp4&audioCodec=aac" +
        "&audioBitRate=$bitrate&mediaSourceId=$id&$PLAY_SESSION_ID_PARAM=$id-$bitrate"
}

const val PLAY_SESSION_ID_PARAM = "playSessionId"
