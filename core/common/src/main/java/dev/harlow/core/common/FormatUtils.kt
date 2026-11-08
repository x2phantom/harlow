package dev.harlow.core.common

import java.text.NumberFormat
import java.util.Locale
import java.time.Duration

fun formatTrackDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = minOf(digitGroups, units.size - 1)
    return NumberFormat.getInstance(Locale.US).format(bytes / Math.pow(1024.0, index.toDouble())) + " " + units[index]
}
