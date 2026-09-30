package com.gesturelink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.gesturelink.app.network.ProcessInfo
import kotlin.math.roundToInt

@Composable
fun ProcessesScreen(
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
    processes: List<ProcessInfo>,
    onRefresh: () -> Unit,
    onKill: (ProcessInfo) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingKill by remember { mutableStateOf<ProcessInfo?>(null) }
    val filtered = remember(processes, query) {
        if (query.isBlank()) processes else processes.filter { it.name.contains(query, ignoreCase = true) }
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
                Text(
                    text = "Processes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )

            when {
                isLoading && processes.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                filtered.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No processes found")
                }

                else -> LazyColumn {
                    items(filtered, key = { it.pid }) { process ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingKill = process }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = process.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "PID ${process.pid}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(text = "${process.memoryMb.roundToInt()} MB")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val target = pendingKill
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text("End ${target.name}?") },
            text = { Text("This force-closes the process (PID ${target.pid}) immediately. Unsaved work in it is lost.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingKill = null
                    onKill(target)
                }) { Text("End process") }
            },
            dismissButton = {
                TextButton(onClick = { pendingKill = null }) { Text("Cancel") }
            },
        )
    }
}
