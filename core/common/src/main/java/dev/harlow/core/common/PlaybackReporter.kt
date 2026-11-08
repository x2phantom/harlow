package dev.harlow.core.common

/** [playSessionId] identifies the running transcode of a track; `null` when the file itself is played. */
interface PlaybackReporter {
    suspend fun reportStarted(itemId: String, playSessionId: String?)
    suspend fun reportProgress(itemId: String, positionMs: Long, playSessionId: String?)
    suspend fun reportStopped(itemId: String, positionMs: Long, playSessionId: String?)
}
