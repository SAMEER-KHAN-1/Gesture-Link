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
