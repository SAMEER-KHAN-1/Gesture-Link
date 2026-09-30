package com.gesturelink.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIVE_REFRESH_INTERVAL_MS = 1500L
private const val MAX_ZOOM = 5f

@Composable
fun ScreenshotScreen(
    snackbarHostState: SnackbarHostState,
    screenshot: Bitmap?,
    isLoading: Boolean,
    /** Fetches a fresh screenshot; returns false if it failed. Suspends until done so
     * live mode never has more than one capture in flight. */
    onRefresh: suspend () -> Boolean,
    onBack: () -> Unit,
) {
    var live by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { onRefresh() }

    // Restarts whenever Live is flipped; leaving the screen cancels it. A failed
    // capture turns Live back off instead of retrying (and erroring) forever.
    LaunchedEffect(live) {
        while (live) {
            delay(LIVE_REFRESH_INTERVAL_MS)
            if (!onRefresh()) live = false
        }
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
                    text = "Screen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text("Live")
                Switch(checked = live, onCheckedChange = { live = it })
                TextButton(onClick = { coroutineScope.launch { onRefresh() } }) { Text("Refresh") }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (screenshot == null) {
                if (isLoading) CircularProgressIndicator() else Text("No screenshot yet")
            } else {
                Image(
                    bitmap = screenshot.asImageBitmap(),
                    contentDescription = "The PC's screen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                // Panning only makes sense once zoomed in; at 1x snap back to centred.
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }

            if (scale > 1f) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { Text("Reset zoom") }
            }
        }
    }
}
