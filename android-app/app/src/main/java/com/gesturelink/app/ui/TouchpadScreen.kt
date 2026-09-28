package com.gesturelink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun TouchpadScreen(
    onMove: (dx: Int, dy: Int) -> Unit,
    onClick: (button: String) -> Unit,
    onScroll: (ticks: Int) -> Unit,
    onTypeText: (text: String) -> Unit,
    onKeyPress: (key: String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
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
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Drag to move the pointer",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onClick("left") }, modifier = Modifier.weight(1f)) { Text("Left click") }
                OutlinedButton(onClick = { onClick("right") }, modifier = Modifier.weight(1f)) { Text("Right click") }
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
