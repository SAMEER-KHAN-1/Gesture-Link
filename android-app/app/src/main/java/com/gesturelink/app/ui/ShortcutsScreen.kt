package com.gesturelink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A labelled key combo. `keys` uses the key names the PC's `keyboard_hotkey` action accepts. */
data class Shortcut(val label: String, val keys: List<String>)

val DEFAULT_SHORTCUTS = listOf(
    Shortcut("Copy", listOf("ctrl", "c")),
    Shortcut("Paste", listOf("ctrl", "v")),
    Shortcut("Cut", listOf("ctrl", "x")),
    Shortcut("Undo", listOf("ctrl", "z")),
    Shortcut("Redo", listOf("ctrl", "y")),
    Shortcut("Select all", listOf("ctrl", "a")),
    Shortcut("Save", listOf("ctrl", "s")),
    Shortcut("Find", listOf("ctrl", "f")),
    Shortcut("New tab", listOf("ctrl", "t")),
    Shortcut("Close tab", listOf("ctrl", "w")),
    Shortcut("Refresh", listOf("f5")),
    Shortcut("Fullscreen", listOf("f11")),
    Shortcut("Switch window", listOf("alt", "tab")),
    Shortcut("Close window", listOf("alt", "f4")),
    Shortcut("Show desktop", listOf("win", "d")),
    Shortcut("Task Manager", listOf("ctrl", "shift", "escape")),
    Shortcut("Screenshot tool", listOf("win", "shift", "s")),
)

@Composable
fun ShortcutsScreen(
    snackbarHostState: SnackbarHostState,
    shortcuts: List<Shortcut> = DEFAULT_SHORTCUTS,
    onShortcut: (Shortcut) -> Unit,
    onBack: () -> Unit,
) {
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
                Text(text = "Shortcuts", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shortcuts, key = { it.label }) { shortcut ->
                OutlinedButton(
                    onClick = { onShortcut(shortcut) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(shortcut.label) }
            }
        }
    }
}
