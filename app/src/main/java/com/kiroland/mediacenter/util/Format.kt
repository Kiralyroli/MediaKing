package com.kiroland.mediacenter.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val HU = Locale.forLanguageTag("hu-HU")

/** Human-readable size with binary units and Hungarian decimal comma, e.g. "1,7 TB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(HU, pattern, value, units[unit])
}

/** Playback time: "45:12" or "1:23:45". */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, HU).format(Date(millis))
