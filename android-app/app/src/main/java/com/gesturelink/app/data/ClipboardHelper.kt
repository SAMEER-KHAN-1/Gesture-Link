package com.gesturelink.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Reads and writes the device's plain-text clipboard. */
object ClipboardHelper {

    private const val LABEL = "GestureLink"

    /** Null when the clipboard is empty or holds something that isn't text. */
    fun readText(context: Context): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    fun writeText(context: Context, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(LABEL, text))
    }
}
