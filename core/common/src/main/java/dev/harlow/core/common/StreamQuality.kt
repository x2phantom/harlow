package dev.harlow.core.common

/** What the server streams: the file as it is, or transcoded to at most [bitrate] (bits per second). */
enum class StreamQuality(val bitrate: Int?) {
    ORIGINAL(null),
    KBPS_320(320_000),
    KBPS_160(160_000),
    KBPS_128(128_000),
    ;

    /**
     * The bitrate to transcode a file of [sourceBitrate] to, or `null` to play the file as it is: a file that is
     * already small enough would only lose quality. A file of unknown bitrate is transcoded.
     */
    fun transcodeBitrate(sourceBitrate: Int?): Int? =
        bitrate?.takeIf { sourceBitrate == null || sourceBitrate > it }

    companion object {
        /** Until the user picks one: little mobile data and cache, no audible loss in the car. */
        val DEFAULT = KBPS_320
    }
}
