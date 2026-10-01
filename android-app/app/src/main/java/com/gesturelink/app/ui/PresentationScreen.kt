package com.gesturelink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * A slideshow remote: big previous/next buttons so it can be used without looking,
 * plus start/end. `onKey` gets a key name the PC's `keyboard_hotkey` action accepts -
 * PowerPoint, Google Slides, PDF viewers and most slide tools share these keys.
 */
@Composable
fun PresentationScreen(
    snackbarHostState: SnackbarHostState,
    onKey: (String) -> Unit,
    onBack: () -> Unit,
) {
    // A buzz on each press lets the presenter feel the slide change without looking down.
    val haptics = LocalHapticFeedback.current
    fun press(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onKey(key)
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
                Text(text = "Presentation", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { press("f5") }, modifier = Modifier.weight(1f)) { Text("Start (F5)") }
                OutlinedButton(onClick = { press("escape") }, modifier = Modifier.weight(1f)) { Text("End (Esc)") }
                OutlinedButton(onClick = { press("b") }, modifier = Modifier.weight(1f)) { Text("Blank (B)") }
            }

            Button(
                onClick = { press("right") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f),
            ) { Text("Next", style = MaterialTheme.typography.headlineMedium) }

            OutlinedButton(
                onClick = { press("left") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { Text("Previous", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
