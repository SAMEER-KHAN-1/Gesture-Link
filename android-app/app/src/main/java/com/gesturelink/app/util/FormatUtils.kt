package com.gesturelink.app.util

import kotlin.math.log10
import kotlin.math.pow

private val SIZE_UNITS = arrayOf("KB", "MB", "GB", "TB")

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(1, SIZE_UNITS.size)
    val value = bytes / 1024.0.pow(unit.toDouble())
    return "%.1f %s".format(value, SIZE_UNITS[unit - 1])
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86400L

/** Compact uptime for the dashboard: "3d 4h", "5h 12m", or "7m" - only the two
 * biggest units, since seconds-level precision on a long uptime is just noise. */
fun formatUptime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val days = seconds / SECONDS_PER_DAY
    val hours = seconds % SECONDS_PER_DAY / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
