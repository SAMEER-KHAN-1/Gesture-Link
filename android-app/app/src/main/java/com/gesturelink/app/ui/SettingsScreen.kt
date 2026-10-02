package com.gesturelink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gesturelink.app.data.ThemeMode

/**
 * App-level settings. Starts with the connection controls that used to crowd the
 * dashboard header; later settings (sensitivity, theme) get their own cards here.
 */
@Composable
fun SettingsScreen(
    pairedAddress: String,
    appVersion: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Connection", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Paired PC: $pairedAddress")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Forget this PC")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.values().forEach { mode ->
                            // The active choice is the filled button; the others are outlined.
                            if (mode == themeMode) {
                                Button(onClick = { onThemeModeChange(mode) }, modifier = Modifier.weight(1f)) { Text(mode.label) }
                            } else {
                                OutlinedButton(onClick = { onThemeModeChange(mode) }, modifier = Modifier.weight(1f)) { Text(mode.label) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "About", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "GestureLink v$appVersion")
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this PC?") },
            text = { Text("You'll need to re-enter the IP and token (or re-scan its QR code) to pair again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForget()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}
