package com.gesturelink.app.util

private val DRIVE_PATH = Regex("""^[A-Za-z]:\\.*""")

/**
 * Every folder from the drive root down to [path], e.g. `C:\Users\sam` ->
 * [`C:\`, `C:\Users`, `C:\Users\sam`]. The file browser's "Up" just pops this stack, so
 * jumping straight to a deep folder (a bookmark) still lets the user walk back up.
 * Anything that isn't a plain drive path (e.g. a network share) is just [path] on its own.
 */
fun pathAncestors(path: String): List<String> {
    if (!DRIVE_PATH.matches(path)) return listOf(path)

    val parts = path.split('\\').filter { it.isNotEmpty() } // "C:", "Users", "sam"
    val ancestors = mutableListOf(parts[0] + "\\")
    var current = parts[0]
    for (part in parts.drop(1)) {
        current = "$current\\$part"
        ancestors.add(current)
    }
    return ancestors
}

/** Short name for a folder in lists: its last segment, or the drive letter for a drive root. */
fun folderLabel(path: String): String {
    val trimmed = path.trimEnd('\\')
    return trimmed.substringAfterLast('\\').ifEmpty { path } // "" only for odd input like a bare "\"
}

/** [path] added to [bookmarks] if it isn't there, removed if it is. Result is sorted. */
fun toggleBookmark(bookmarks: List<String>, path: String): List<String> =
    (if (path in bookmarks) bookmarks - path else bookmarks + path).sorted()

private val INVALID_NAME_CHARS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')

/**
 * Whether [name] is usable as a single Windows file/folder name. The PC re-checks (and refuses a
 * path), this just saves a round trip and gives the dialog something to disable its button on.
 */
fun isValidFileName(name: String): Boolean {
    if (name.isBlank() || name == "." || name == "..") return false
    if (name.endsWith('.') || name.endsWith(' ')) return false // Windows silently strips these
    return name.none { it in INVALID_NAME_CHARS || it.code < 32 }
}

private fun isSameOrInside(path: String, folder: String): Boolean {
    val prefix = folder.trimEnd('\\')
    return path.equals(folder, ignoreCase = true) ||
        path.startsWith("$prefix\\", ignoreCase = true)
}

/** [bookmarks] without [path] or any bookmark inside it - used after that folder is deleted. */
fun removeBookmarksUnder(bookmarks: List<String>, path: String): List<String> =
    bookmarks.filterNot { isSameOrInside(it, path) }

/**
 * [bookmarks] after the folder at [oldPath] was renamed to [newPath]: a bookmark on it, or on
 * anything inside it, follows the rename instead of going stale. Result is sorted.
 */
fun renameBookmarksUnder(bookmarks: List<String>, oldPath: String, newPath: String): List<String> =
    bookmarks.map { bookmark ->
        if (isSameOrInside(bookmark, oldPath)) newPath + bookmark.substring(oldPath.length) else bookmark
    }.sorted()
