package com.gesturelink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gesturelink.app.network.SystemStats
import kotlin.math.roundToInt

private enum class ConfirmAction { SHUTDOWN, RESTART }

@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState,
    stats: SystemStats?,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    onShutdown: () -> Unit,
    onRestart: () -> Unit,
    onCancelShutdown: () -> Unit,
    onSleep: () -> Unit,
    onLock: () -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onBluetoothToggle: (Boolean) -> Unit,
    onOpenApps: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTouchpad: () -> Unit,
    onDisconnect: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeMuteToggle: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
) {
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "GestureLink", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "System", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (stats == null) {
                        Text("Loading...")
                    } else {
                        Text("CPU ${stats.cpuPercent.roundToInt()}%  ·  RAM ${stats.memoryPercent.roundToInt()}%")
                        if (stats.batteryPercent != null) {
                            val chargingLabel = if (stats.batteryPlugged == true) "charging" else "on battery"
                            Text("Battery ${stats.batteryPercent.roundToInt()}% ($chargingLabel)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Power", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { pendingConfirm = ConfirmAction.SHUTDOWN },
                            modifier = Modifier.weight(1f),
                        ) { Text("Shut down") }
                        Button(
                            onClick = { pendingConfirm = ConfirmAction.RESTART },
                            modifier = Modifier.weight(1f),
                        ) { Text("Restart") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onSleep, modifier = Modifier.weight(1f)) { Text("Sleep") }
                        OutlinedButton(onClick = onLock, modifier = Modifier.weight(1f)) { Text("Lock") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onCancelShutdown) { Text("Cancel pending shutdown/restart") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Connectivity", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Wifi")
                        Switch(checked = wifiEnabled, onCheckedChange = onWifiToggle)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Bluetooth")
                        Switch(checked = bluetoothEnabled, onCheckedChange = onBluetoothToggle)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Media", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onVolumeDown, modifier = Modifier.weight(1f)) { Text("Vol -") }
                        OutlinedButton(onClick = onVolumeMuteToggle, modifier = Modifier.weight(1f)) { Text("Mute") }
                        OutlinedButton(onClick = onVolumeUp, modifier = Modifier.weight(1f)) { Text("Vol +") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onMediaPrevious, modifier = Modifier.weight(1f)) { Text("<<") }
                        OutlinedButton(onClick = onMediaPlayPause, modifier = Modifier.weight(1f)) { Text("Play/Pause") }
                        OutlinedButton(onClick = onMediaNext, modifier = Modifier.weight(1f)) { Text(">>") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Apps", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                        Text("Browse & launch apps")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Files", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenFiles, modifier = Modifier.fillMaxWidth()) {
                        Text("Browse files")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Touchpad", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenTouchpad, modifier = Modifier.fillMaxWidth()) {
                        Text("Open touchpad")
                    }
                }
            }
        }
    }

    val confirmAction = pendingConfirm
    if (confirmAction != null) {
        val (title, message, onConfirm) = when (confirmAction) {
            ConfirmAction.SHUTDOWN -> Triple("Shut down PC?", "This will shut down the PC right now.", onShutdown)
            ConfirmAction.RESTART -> Triple("Restart PC?", "This will restart the PC right now.", onRestart)
        }
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirm = null
                    onConfirm()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            },
        )
    }
}
