package com.gesturelink.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gesturelink.app.network.FileEntry
import kotlin.math.log10
import kotlin.math.pow

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(3)
    val value = bytes / 1024.0.pow(unit.toDouble())
    return "%.1f %s".format(value, arrayOf("KB", "MB", "GB", "TB")[unit - 1])
}

@Composable
fun FilesScreen(
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
    currentPath: String,
    entries: List<FileEntry>,
    canGoUp: Boolean,
    downloadingPath: String?,
    onOpenEntry: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onUploadFile: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onUploadFile)
    }

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isDownloading) { onOpenEntry(entry) }
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
                                    text = formatSize(entry.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
