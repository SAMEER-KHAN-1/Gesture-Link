package com.gesturelink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun TouchpadScreen(
    onMove: (dx: Int, dy: Int) -> Unit,
    onClick: (button: String) -> Unit,
    onDoubleClick: () -> Unit,
    onButtonState: (button: String, down: Boolean) -> Unit,
    onScroll: (ticks: Int) -> Unit,
    onTypeText: (text: String) -> Unit,
    onKeyPress: (key: String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var sensitivity by remember { mutableStateOf(1f) }
    // While on, the PC's left button is held down so dragging the pad drags on the PC.
    var dragLock by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)

    // Leaving the screen with the button still held would leave it stuck down on the PC.
    DisposableEffect(Unit) {
        onDispose { if (dragLock) onButtonState("left", false) }
    }

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
                Text(text = "Touchpad", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            tick()
                            onDoubleClick()
                        })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove((dragAmount.x * sensitivity).roundToInt(), (dragAmount.y * sensitivity).roundToInt())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (dragLock) "Dragging - move to drag, switch off to drop" else "Drag to move the pointer, double-tap to double-click",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Sensitivity", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.5f..3f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1fx".format(sensitivity),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        tick()
                        onClick("left")
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Left click") }
                OutlinedButton(
                    onClick = {
                        tick()
                        onClick("right")
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Right click") }
                OutlinedButton(
                    onClick = {
                        tick()
                        onDoubleClick()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Double click") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Drag lock (hold left button)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = dragLock,
                    onCheckedChange = { locked ->
                        tick()
                        dragLock = locked
                        onButtonState("left", locked)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onScroll(-1) }, modifier = Modifier.weight(1f)) { Text("Scroll up") }
                OutlinedButton(onClick = { onScroll(1) }, modifier = Modifier.weight(1f)) { Text("Scroll down") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Type on PC") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (text.isNotEmpty()) {
                            onTypeText(text)
                            text = ""
                        }
                    },
                ) { Text("Send") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onKeyPress("enter") }, modifier = Modifier.weight(1f)) { Text("Enter") }
                OutlinedButton(onClick = { onKeyPress("backspace") }, modifier = Modifier.weight(1f)) { Text("Backspace") }
                OutlinedButton(onClick = { onKeyPress("escape") }, modifier = Modifier.weight(1f)) { Text("Esc") }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
