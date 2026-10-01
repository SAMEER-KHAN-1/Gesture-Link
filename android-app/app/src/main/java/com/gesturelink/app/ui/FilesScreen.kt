package com.gesturelink.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gesturelink.app.network.FileEntry
import com.gesturelink.app.util.folderLabel
import com.gesturelink.app.util.formatFileSize
import com.gesturelink.app.util.isValidFileName

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
    currentPath: String,
    entries: List<FileEntry>,
    canGoUp: Boolean,
    downloadingPath: String?,
    /** Folders the user has bookmarked (full paths), sorted. */
    bookmarks: List<String>,
    onOpenEntry: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmark: (String) -> Unit,
    onUploadFile: (Uri) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameEntry: (FileEntry, String) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onBack: () -> Unit,
) {
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onUploadFile)
    }
    var bookmarksMenuOpen by remember { mutableStateOf(false) }
    val isBookmarked = currentPath in bookmarks

    // Which row's long-press menu is open, and which dialog (if any) is showing.
    var menuEntry by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text(text = "Files", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onNavigateUp, enabled = canGoUp) { Text("Up") }
                Text(
                    text = currentPath.ifEmpty { "This PC" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    enabled = currentPath.isNotEmpty(),
                ) { Text("Upload here") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onToggleBookmark, enabled = currentPath.isNotEmpty()) {
                    Text(if (isBookmarked) "★ Bookmarked" else "☆ Bookmark")
                }
                Box {
                    TextButton(onClick = { bookmarksMenuOpen = true }, enabled = bookmarks.isNotEmpty()) {
                        Text("Bookmarks (${bookmarks.size})")
                    }
                    DropdownMenu(expanded = bookmarksMenuOpen, onDismissRequest = { bookmarksMenuOpen = false }) {
                        bookmarks.forEach { path ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(folderLabel(path))
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                onClick = {
                                    bookmarksMenuOpen = false
                                    onOpenBookmark(path)
                                },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { creatingFolder = true },
                    enabled = currentPath.isNotEmpty(),
                ) { Text("New folder") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                entries.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty")
                }
                else -> LazyColumn {
                    items(entries, key = { it.path }) { entry ->
                        val isDownloading = entry.path == downloadingPath
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = !isDownloading,
                                        onClick = { onOpenEntry(entry) },
                                        onLongClick = { menuEntry = entry },
                                    )
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = if (entry.isDir) "${entry.name}/" else entry.name)
                                when {
                                    isDownloading -> Text(
                                        text = "Downloading...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    !entry.isDir && entry.size != null -> Text(
                                        text = formatFileSize(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DropdownMenu(expanded = menuEntry == entry, onDismissRequest = { menuEntry = null }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuEntry = null
                                        renameTarget = entry
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuEntry = null
                                        deleteTarget = entry
                                    },
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (creatingFolder) {
        NameDialog(
            title = "New folder",
            confirmLabel = "Create",
            initialName = "",
            onConfirm = { name ->
                creatingFolder = false
                onCreateFolder(name)
            },
            onDismiss = { creatingFolder = false },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename ${target.name}",
            confirmLabel = "Rename",
            initialName = target.name,
            onConfirm = { name ->
                renameTarget = null
                if (name != target.name) onRenameEntry(target, name)
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    if (target.isDir) "The folder and everything in it will be moved to the PC's Recycle Bin."
                    else "It will be moved to the PC's Recycle Bin.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDeleteEntry(target)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/** A dialog with one text field for a file/folder name; Confirm stays disabled until the name is usable. */
@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val valid = isValidFileName(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isNotEmpty() && !valid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
