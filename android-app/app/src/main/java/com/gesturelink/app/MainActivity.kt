package com.gesturelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gesturelink.app.data.FileSaver
import com.gesturelink.app.data.PairingInfo
import com.gesturelink.app.data.PairingStore
import com.gesturelink.app.network.AppInfo
import com.gesturelink.app.network.AppsListResult
import com.gesturelink.app.network.ConnectionState
import com.gesturelink.app.network.DownloadFileResult
import com.gesturelink.app.network.FileEntry
import com.gesturelink.app.network.GestureLinkClient
import com.gesturelink.app.network.ListDirResult
import com.gesturelink.app.network.RadioStatusResult
import com.gesturelink.app.network.SystemStats
import com.gesturelink.app.ui.AppsScreen
import com.gesturelink.app.ui.DashboardScreen
import com.gesturelink.app.ui.FilesScreen
import com.gesturelink.app.ui.PairingScreen
import com.gesturelink.app.ui.TouchpadScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val STATS_POLL_INTERVAL_MS = 5000L

private sealed class Screen {
    object Pairing : Screen()
    object Dashboard : Screen()
    object Apps : Screen()
    object Files : Screen()
    object Touchpad : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore
    private val client = GestureLinkClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(applicationContext)
        val saved = pairingStore.load()

        setContent {
            var screen by remember { mutableStateOf<Screen>(Screen.Pairing) }
            var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var pairedToken by remember { mutableStateOf(saved?.token.orEmpty()) }

            // Start optimistically enabled; corrected by a radio_status query as soon as
            // the dashboard loads (see the LaunchedEffect below).
            var wifiEnabled by remember { mutableStateOf(true) }
            var bluetoothEnabled by remember { mutableStateOf(true) }

            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var appsLoading by remember { mutableStateOf(false) }
            var stats by remember { mutableStateOf<SystemStats?>(null) }

            // Empty string = drives ("This PC"). Each push is a directory the user opened,
            // so "Up" just pops the stack instead of asking the server for a parent path.
            var filesPathStack by remember { mutableStateOf(listOf<String>()) }
            var fileEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
            var filesLoading by remember { mutableStateOf(false) }
            var downloadingPath by remember { mutableStateOf<String?>(null) }

            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            fun runCommand(
                action: String,
                params: JsonObject = JsonObject(emptyMap()),
                onFailureRevert: (() -> Unit)? = null,
            ) {
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, action, params) }
                        .onSuccess { response ->
                            if (!response.ok) {
                                onFailureRevert?.invoke()
                                snackbarHostState.showSnackbar(response.error ?: "'$action' failed")
                            }
                        }
                        .onFailure { throwable ->
                            onFailureRevert?.invoke()
                            snackbarHostState.showSnackbar(throwable.message ?: "'$action' failed")
                        }
                }
            }

            fun loadApps() {
                appsLoading = true
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "apps_list") }
                        .onSuccess { response ->
                            appsLoading = false
                            if (response.ok) {
                                apps = Json.decodeFromJsonElement(AppsListResult.serializer(), response.result).apps
                            } else {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't load apps")
                            }
                        }
                        .onFailure { throwable ->
                            appsLoading = false
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't load apps")
                        }
                }
            }

            fun loadDir(path: String) {
                filesLoading = true
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "list_dir", buildJsonObject { put("path", path) }) }
                        .onSuccess { response ->
                            filesLoading = false
                            if (response.ok) {
                                fileEntries = Json.decodeFromJsonElement(ListDirResult.serializer(), response.result).entries
                            } else {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't list '$path'")
                            }
                        }
                        .onFailure { throwable ->
                            filesLoading = false
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't list '$path'")
                        }
                }
            }

            fun downloadFile(entry: FileEntry) {
                downloadingPath = entry.path
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "download_file", buildJsonObject { put("path", entry.path) }) }
                        .onSuccess { response ->
                            downloadingPath = null
                            if (response.ok) {
                                val result = Json.decodeFromJsonElement(DownloadFileResult.serializer(), response.result)
                                FileSaver.saveToDownloads(applicationContext, result.name, result.dataBase64)
                                    .onSuccess { snackbarHostState.showSnackbar("Saved ${result.name} to Downloads") }
                                    .onFailure { throwable ->
                                        snackbarHostState.showSnackbar(throwable.message ?: "couldn't save ${result.name}")
                                    }
                            } else {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't download '${entry.name}'")
                            }
                        }
                        .onFailure { throwable ->
                            downloadingPath = null
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't download '${entry.name}'")
                        }
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Dashboard -> {
                            LaunchedEffect(Unit) {
                                runCatching { client.sendCommand(pairedToken, "radio_status") }
                                    .onSuccess { response ->
                                        if (response.ok) {
                                            val status = Json.decodeFromJsonElement(
                                                RadioStatusResult.serializer(),
                                                response.result,
                                            )
                                            wifiEnabled = status.wifiEnabled
                                            bluetoothEnabled = status.bluetoothEnabled
                                        }
                                    }
                                while (isActive) {
                                    runCatching { client.sendCommand(pairedToken, "system_stats") }
                                        .onSuccess { response ->
                                            if (response.ok) {
                                                stats = Json.decodeFromJsonElement(
                                                    SystemStats.serializer(),
                                                    response.result,
                                                )
                                            }
                                        }
                                    delay(STATS_POLL_INTERVAL_MS)
                                }
                            }
                            DashboardScreen(
                                snackbarHostState = snackbarHostState,
                                stats = stats,
                                wifiEnabled = wifiEnabled,
                                bluetoothEnabled = bluetoothEnabled,
                                onShutdown = { runCommand("shutdown") },
                                onRestart = { runCommand("restart") },
                                onCancelShutdown = { runCommand("cancel_shutdown") },
                                onSleep = { runCommand("sleep") },
                                onLock = { runCommand("lock") },
                                onWifiToggle = { enabled ->
                                    val previous = wifiEnabled
                                    wifiEnabled = enabled
                                    runCommand(
                                        action = "wifi_set",
                                        params = buildJsonObject { put("enabled", enabled) },
                                        onFailureRevert = { wifiEnabled = previous },
                                    )
                                },
                                onBluetoothToggle = { enabled ->
                                    val previous = bluetoothEnabled
                                    bluetoothEnabled = enabled
                                    runCommand(
                                        action = "bluetooth_set",
                                        params = buildJsonObject { put("enabled", enabled) },
                                        onFailureRevert = { bluetoothEnabled = previous },
                                    )
                                },
                                onOpenApps = {
                                    screen = Screen.Apps
                                    loadApps()
                                },
                                onOpenFiles = {
                                    screen = Screen.Files
                                    filesPathStack = emptyList()
                                    loadDir("")
                                },
                                onOpenTouchpad = { screen = Screen.Touchpad },
                                onDisconnect = {
                                    client.disconnect()
                                    connectionState = ConnectionState.DISCONNECTED
                                    screen = Screen.Pairing
                                },
                                onVolumeUp = { runCommand("volume_up") },
                                onVolumeDown = { runCommand("volume_down") },
                                onVolumeMuteToggle = { runCommand("volume_mute_toggle") },
                                onMediaPrevious = { runCommand("media_previous") },
                                onMediaPlayPause = { runCommand("media_play_pause") },
                                onMediaNext = { runCommand("media_next") },
                            )
                        }

                        Screen.Apps -> AppsScreen(
                            snackbarHostState = snackbarHostState,
                            isLoading = appsLoading,
                            apps = apps,
                            onLaunch = { app ->
                                runCommand("app_launch", buildJsonObject { put("app_id", app.appId) })
                                coroutineScope.launch { snackbarHostState.showSnackbar("Launching ${app.name}") }
                            },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Files -> FilesScreen(
                            snackbarHostState = snackbarHostState,
                            isLoading = filesLoading,
                            currentPath = filesPathStack.lastOrNull() ?: "",
                            entries = fileEntries,
                            canGoUp = filesPathStack.isNotEmpty(),
                            downloadingPath = downloadingPath,
                            onOpenEntry = { entry ->
                                if (entry.isDir) {
                                    filesPathStack = filesPathStack + entry.path
                                    loadDir(entry.path)
                                } else {
                                    downloadFile(entry)
                                }
                            },
                            onNavigateUp = {
                                filesPathStack = filesPathStack.dropLast(1)
                                loadDir(filesPathStack.lastOrNull() ?: "")
                            },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Touchpad -> TouchpadScreen(
                            onMove = { dx, dy ->
                                runCommand("mouse_move", buildJsonObject { put("dx", dx); put("dy", dy) })
                            },
                            onClick = { button ->
                                runCommand("mouse_click", buildJsonObject { put("button", button) })
                            },
                            onScroll = { ticks ->
                                runCommand("mouse_scroll", buildJsonObject { put("ticks", ticks) })
                            },
                            onTypeText = { text ->
                                runCommand("keyboard_type", buildJsonObject { put("text", text) })
                            },
                            onKeyPress = { key ->
                                runCommand("keyboard_key", buildJsonObject { put("key", key) })
                            },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Pairing -> PairingScreen(
                            initialHost = saved?.host.orEmpty(),
                            initialToken = saved?.token.orEmpty(),
                            isConnecting = connectionState == ConnectionState.CONNECTING,
                            errorMessage = errorMessage,
                            onConnect = { host, token ->
                                errorMessage = null
                                pairedToken = token
                                client.connect(host, PairingStore.DEFAULT_PORT) { state ->
                                    // Callback fires on OkHttp's thread, not the main thread.
                                    runOnUiThread {
                                        connectionState = state
                                        when (state) {
                                            ConnectionState.CONNECTED -> {
                                                pairingStore.save(PairingInfo(host, PairingStore.DEFAULT_PORT, token))
                                                screen = Screen.Dashboard
                                            }
                                            ConnectionState.DISCONNECTED ->
                                                errorMessage = "Couldn't reach the PC - check the IP and that the server is running."
                                            else -> Unit
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }
}
