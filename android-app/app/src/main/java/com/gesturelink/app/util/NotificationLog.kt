package com.gesturelink.app.util

const val MAX_NOTIFICATIONS = 50

/** One alert pushed from the PC (e.g. "PC battery low"), kept so it can be looked at after the snackbar is gone. */
data class NotificationEntry(val message: String, val timestampMs: Long)

/** Newest first, dropping the oldest once there are more than [limit]. */
fun List<NotificationEntry>.withNewest(entry: NotificationEntry, limit: Int = MAX_NOTIFICATIONS): List<NotificationEntry> =
    (listOf(entry) + this).take(limit)
