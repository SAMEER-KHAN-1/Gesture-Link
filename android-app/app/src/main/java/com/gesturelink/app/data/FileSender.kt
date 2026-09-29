package com.gesturelink.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64

/**
 * Reads a file the user picked via the system document picker into memory as
 * base64, along with its display name, ready to send in an `upload_file`
 * command - the counterpart to FileSaver on the download side.
 */
object FileSender {

    data class PickedFile(val name: String, val base64Data: String)

    fun read(context: Context, uri: Uri): Result<PickedFile> = runCatching {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment ?: "upload"

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("couldn't read the picked file")

        PickedFile(name = name, base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}
