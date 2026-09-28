package com.gesturelink.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64

/**
 * Saves a downloaded file's bytes into the device's public Downloads folder via
 * MediaStore, so no storage permission is needed. Only available on API 29+
 * (scoped storage); older versions would need a runtime permission + legacy
 * file path instead, which isn't worth the extra code for a v1.
 */
object FileSaver {

    fun saveToDownloads(context: Context, fileName: String, base64Data: String): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(UnsupportedOperationException("saving files needs Android 10 or newer"))
        }

        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("couldn't create '$fileName' in Downloads")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("couldn't open '$fileName' for writing")
        }
    }
}
