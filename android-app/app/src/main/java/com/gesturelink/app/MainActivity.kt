package com.gesturelink.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gesturelink.app.data.ClipboardHelper
import com.gesturelink.app.data.FileSaver
import com.gesturelink.app.data.FileSender
import com.gesturelink.app.data.PairingInfo
import com.gesturelink.app.data.PairingStore
import com.gesturelink.app.data.SettingsStore
import com.gesturelink.app.data.ThemeMode
import com.gesturelink.app.network.AppInfo
import com.gesturelink.app.network.AppsListResult
import com.gesturelink.app.network.BrightnessResult
import com.gesturelink.app.network.ClipboardGetResult
import com.gesturelink.app.network.ConnectionState
import com.gesturelink.app.network.DownloadFileResult
import com.gesturelink.app.network.FileEntry
import com.gesturelink.app.network.GestureLinkClient
import com.gesturelink.app.network.ListDirResult
import com.gesturelink.app.network.MacAddressResult
import com.gesturelink.app.network.ProcessInfo
import com.gesturelink.app.network.ProcessListResult
import com.gesturelink.app.network.RadioStatusResult
import com.gesturelink.app.network.ScreenshotResult
import com.gesturelink.app.network.SystemStats
import com.gesturelink.app.ui.AppsScreen
import com.gesturelink.app.ui.DashboardScreen
import com.gesturelink.app.ui.FilesScreen
import com.gesturelink.app.ui.PairingScreen
import com.gesturelink.app.ui.NotificationsScreen
import com.gesturelink.app.ui.PresentationScreen
import com.gesturelink.app.ui.ProcessesScreen
import com.gesturelink.app.ui.ScreenshotScreen
import com.gesturelink.app.ui.SettingsScreen
import com.gesturelink.app.ui.ShortcutsScreen
import com.gesturelink.app.ui.TouchpadScreen
import com.gesturelink.app.util.NotificationEntry
import com.gesturelink.app.util.formatHostPort
import com.gesturelink.app.util.parseHostPort
import com.gesturelink.app.util.sendMagicPacket
import com.gesturelink.app.util.withNewest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val STATS_POLL_INTERVAL_MS = 5000L

// A phone screen can't show more than this anyway, and it keeps each frame small.
private const val SCREENSHOT_MAX_WIDTH = 1280

private sealed class Screen {
    object Pairing : Screen()
    object Dashboard : Screen()
    object Apps : Screen()
    object Files : Screen()
    object Touchpad : Screen()
    object Screenshot : Screen()
    object Processes : Screen()
    object Shortcuts : Screen()
    object Presentation : Screen()
    object Settings : Screen()
    object Notifications : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore
    private lateinit var settingsStore: SettingsStore
    private val client = GestureLinkClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(applicationContext)
        settingsStore = SettingsStore(applicationContext)
        val saved = pairingStore.load()

        setContent {
            var screen by remember { mutableStateOf<Screen>(Screen.Pairing) }
            var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var pairedToken by remember { mutableStateOf(saved?.token.orEmpty()) }
            var themeMode by remember { mutableStateOf(settingsStore.themeMode) }

            // Mirrors what's in PairingStore, but as compose state so forgetting a PC
            // clears the pairing screen's fields immediately instead of showing stale ones.
            var savedInfo by remember { mutableStateOf(saved) }

            // Start optimistically enabled; corrected by a radio_status query as soon as
            // the dashboard loads (see the LaunchedEffect below).
            var wifiEnabled by remember { mutableStateOf(true) }
            var bluetoothEnabled by remember { mutableStateOf(true) }

            // Null until the PC reports it - and stays null for displays that don't support
            // brightness control, which hides the dashboard's brightness card.
            var brightness by remember { mutableStateOf<Int?>(null) }

            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var appsLoading by remember { mutableStateOf(false) }
            var stats by remember { mutableStateOf<SystemStats?>(null) }
            var latencyMs by remember { mutableStateOf<Long?>(null) }
            var notifications by remember { mutableStateOf<List<NotificationEntry>>(emptyList()) }

            // Empty string = drives ("This PC"). Each push is a directory the user opened,
            // so "Up" just pops the stack instead of asking the server for a parent path.
            var filesPathStack by remember { mutableStateOf(listOf<String>()) }
            var fileEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
            var filesLoading by remember { mutableStateOf(false) }
            var downloadingPath by remember { mutableStateOf<String?>(null) }

            var screenshot by remember { mutableStateOf<Bitmap?>(null) }
            var screenshotLoading by remember { mutableStateOf(false) }

            var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
            var processesLoading by remember { mutableStateOf(false) }

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

            fun runHotkey(keys: List<String>) {
                runCommand("keyboard_hotkey", buildJsonObject { put("keys", buildJsonArray { keys.forEach { add(it) } }) })
            }

            fun sendClipboardToPc() {
                val text = ClipboardHelper.readText(applicationContext)
                if (text.isNullOrEmpty()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Phone clipboard is empty") }
                    return
                }
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "clipboard_set", buildJsonObject { put("text", text) }) }
                        .onSuccess { response ->
                            snackbarHostState.showSnackbar(
                                if (response.ok) "Sent clipboard to PC" else response.error ?: "couldn't send clipboard",
                            )
                        }
                        .onFailure { throwable ->
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't send clipboard")
                        }
                }
            }

            fun fetchPcClipboard() {
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "clipboard_get") }
                        .onSuccess { response ->
                            if (!response.ok) {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't read the PC clipboard")
                                return@onSuccess
                            }
                            val result = Json.decodeFromJsonElement(ClipboardGetResult.serializer(), response.result)
                            if (result.text.isEmpty()) {
                                snackbarHostState.showSnackbar("PC clipboard has no text")
                            } else {
                                ClipboardHelper.writeText(applicationContext, result.text)
                                snackbarHostState.showSnackbar(
                                    if (result.truncated) "Copied from PC (text was cut off - too long)" else "Copied from PC",
                                )
                            }
                        }
                        .onFailure { throwable ->
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't read the PC clipboard")
                        }
                }
            }

            /** Returns whether the capture worked, so the screen can stop live mode on a failure. */
            suspend fun refreshScreenshot(): Boolean {
                screenshotLoading = true
                try {
                    val bitmap = runCatching {
                        val response = client.sendCommand(
                            pairedToken,
                            "screenshot",
                            buildJsonObject { put("max_width", SCREENSHOT_MAX_WIDTH) },
                        )
                        if (!response.ok) throw IllegalStateException(response.error ?: "couldn't capture the screen")
                        val result = Json.decodeFromJsonElement(ScreenshotResult.serializer(), response.result)
                        withContext(Dispatchers.Default) {
                            val bytes = Base64.decode(result.dataBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: throw IllegalStateException("couldn't decode the screenshot")
                        }
                    }.onFailure { throwable ->
                        // Leaving the screen cancels a capture in flight - that's not an error to show.
                        if (throwable is CancellationException) throw throwable
                        snackbarHostState.showSnackbar(throwable.message ?: "couldn't capture the screen")
                    }.getOrNull()

                    if (bitmap != null) screenshot = bitmap
                    return bitmap != null
                } finally {
                    screenshotLoading = false
                }
            }

            fun loadProcesses() {
                processesLoading = true
                coroutineScope.launch {
                    runCatching { client.sendCommand(pairedToken, "process_list") }
                        .onSuccess { response ->
                            processesLoading = false
                            if (response.ok) {
                                processes = Json.decodeFromJsonElement(ProcessListResult.serializer(), response.result).processes
                            } else {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't load processes")
                            }
                        }
                        .onFailure { throwable ->
                            processesLoading = false
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't load processes")
                        }
                }
            }

            fun killProcess(process: ProcessInfo) {
                coroutineScope.launch {
                    runCatching {
                        client.sendCommand(
                            pairedToken,
                            "process_kill",
                            buildJsonObject {
                                put("pid", process.pid)
                                put("name", process.name)
                            },
                        )
                    }
                        .onSuccess { response ->
                            if (response.ok) {
                                snackbarHostState.showSnackbar("Ended ${process.name}")
                            } else {
                                snackbarHostState.showSnackbar(response.error ?: "couldn't end '${process.name}'")
                            }
                            loadProcesses()
                        }
                        .onFailure { throwable ->
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't end '${process.name}'")
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

            // Learned while connected (the PC is off by the time we need it), so Wake-on-LAN
            // works the next time the PC is shut down.
            fun refreshMacAddress() {
                coroutineScope.launch {
                    val mac = runCatching { client.sendCommand(pairedToken, "mac_address") }
                        .getOrNull()
                        ?.takeIf { it.ok }
                        ?.let { Json.decodeFromJsonElement(MacAddressResult.serializer(), it.result).mac }
                        ?: return@launch
                    val current = savedInfo ?: return@launch
                    if (current.mac != mac) {
                        val updated = current.copy(mac = mac)
                        pairingStore.save(updated)
                        savedInfo = updated
                    }
                }
            }

            fun connectTo(host: String, port: Int, token: String) {
                errorMessage = null
                pairedToken = token
                client.connect(
                    host = host,
                    port = port,
                    onStateChanged = { state ->
                        // Callback fires on OkHttp's thread, not the main thread.
                        runOnUiThread {
                            connectionState = state
                            when (state) {
                                ConnectionState.CONNECTED -> {
                                    // Keep the MAC we already know for this PC; refreshMacAddress() below
                                    // updates it in case the PC's network adapter changed.
                                    val knownMac = savedInfo?.takeIf { it.host == host }?.mac
                                    val info = PairingInfo(host, port, token, knownMac)
                                    pairingStore.save(info)
                                    savedInfo = info
                                    screen = Screen.Dashboard
                                    refreshMacAddress()
                                }
                                ConnectionState.DISCONNECTED -> {
                                    // Also covers a connection dropping mid-use (not just a failed
                                    // pairing attempt) - GestureLinkClient only calls this for a real
                                    // loss, never for a deliberate disconnect() (see its isStale check).
                                    errorMessage = "Couldn't reach the PC - check the IP and that the server is running."
                                    screen = Screen.Pairing
                                }
                                else -> Unit
                            }
                        }
                    },
                    onPush = { push ->
                        if (push.push == "battery_low") {
                            val percent = push.data["battery_percent"]?.jsonPrimitive?.intOrNull
                            val message = if (percent != null) "PC battery low ($percent%)" else "PC battery low"
                            runOnUiThread {
                                notifications = notifications.withNewest(NotificationEntry(message, System.currentTimeMillis()))
                                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        }
                    },
                )
            }

            // Try the last PC we paired with automatically, so re-opening the app doesn't
            // always mean re-entering (or re-scanning) the IP and token by hand.
            LaunchedEffect(Unit) {
                if (saved != null) {
                    connectTo(saved.host, saved.port, saved.token)
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

            fun uploadFile(uri: Uri) {
                val targetDir = filesPathStack.lastOrNull()
                if (targetDir.isNullOrEmpty()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Open a folder first") }
                    return
                }

                FileSender.read(applicationContext, uri)
                    .onSuccess { picked ->
                        coroutineScope.launch {
                            runCatching {
                                client.sendCommand(
                                    pairedToken,
                                    "upload_file",
                                    buildJsonObject {
                                        put("dir", targetDir)
                                        put("name", picked.name)
                                        put("data_base64", picked.base64Data)
                                    },
                                )
                            }
                                .onSuccess { response ->
                                    if (response.ok) {
                                        snackbarHostState.showSnackbar("Uploaded ${picked.name}")
                                        loadDir(targetDir)
                                    } else {
                                        snackbarHostState.showSnackbar(response.error ?: "couldn't upload '${picked.name}'")
                                    }
                                }
                                .onFailure { throwable ->
                                    snackbarHostState.showSnackbar(throwable.message ?: "couldn't upload '${picked.name}'")
                                }
                        }
                    }
                    .onFailure { throwable ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(throwable.message ?: "couldn't read the picked file")
                        }
                    }
            }

            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()) {
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
                                brightness = runCatching { client.sendCommand(pairedToken, "brightness_get") }
                                    .getOrNull()
                                    ?.takeIf { it.ok }
                                    ?.let { Json.decodeFromJsonElement(BrightnessResult.serializer(), it.result).brightness }
                                while (isActive) {
                                    // Round-trip time of a no-op command; null if the PC didn't answer.
                                    val pingStartedAt = SystemClock.elapsedRealtime()
                                    latencyMs = runCatching { client.sendCommand(pairedToken, "ping") }
                                        .getOrNull()
                                        ?.takeIf { it.ok }
                                        ?.let { SystemClock.elapsedRealtime() - pingStartedAt }
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
                                latencyMs = latencyMs,
                                wifiEnabled = wifiEnabled,
                                bluetoothEnabled = bluetoothEnabled,
                                brightness = brightness,
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
                                onOpenShortcuts = { screen = Screen.Shortcuts },
                                onOpenPresentation = { screen = Screen.Presentation },
                                onOpenScreen = {
                                    screenshot = null // don't flash the previous session's frame
                                    screen = Screen.Screenshot
                                },
                                onOpenProcesses = {
                                    processes = emptyList()
                                    screen = Screen.Processes
                                    loadProcesses()
                                },
                                onOpenNotifications = { screen = Screen.Notifications },
                                onOpenSettings = { screen = Screen.Settings },
                                onVolumeUp = { runCommand("volume_up") },
                                onVolumeDown = { runCommand("volume_down") },
                                onVolumeMuteToggle = { runCommand("volume_mute_toggle") },
                                onMediaPrevious = { runCommand("media_previous") },
                                onMediaPlayPause = { runCommand("media_play_pause") },
                                onMediaNext = { runCommand("media_next") },
                                onSendClipboardToPc = { sendClipboardToPc() },
                                onFetchPcClipboard = { fetchPcClipboard() },
                                onBrightnessChange = { level ->
                                    val previous = brightness
                                    brightness = level
                                    runCommand(
                                        action = "brightness_set",
                                        params = buildJsonObject { put("level", level) },
                                        onFailureRevert = { brightness = previous },
                                    )
                                },
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
                            onUploadFile = { uri -> uploadFile(uri) },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Touchpad -> TouchpadScreen(
                            initialSensitivity = settingsStore.mouseSensitivity,
                            onSensitivityChangeFinished = { settingsStore.mouseSensitivity = it },
                            onMove = { dx, dy ->
                                runCommand("mouse_move", buildJsonObject { put("dx", dx); put("dy", dy) })
                            },
                            onClick = { button ->
                                runCommand("mouse_click", buildJsonObject { put("button", button) })
                            },
                            onDoubleClick = {
                                runCommand("mouse_click", buildJsonObject { put("button", "left"); put("count", 2) })
                            },
                            onButtonState = { button, down ->
                                runCommand("mouse_button", buildJsonObject { put("button", button); put("state", if (down) "down" else "up") })
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

                        Screen.Screenshot -> ScreenshotScreen(
                            snackbarHostState = snackbarHostState,
                            screenshot = screenshot,
                            isLoading = screenshotLoading,
                            onRefresh = { refreshScreenshot() },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Processes -> ProcessesScreen(
                            snackbarHostState = snackbarHostState,
                            isLoading = processesLoading,
                            processes = processes,
                            onRefresh = { loadProcesses() },
                            onKill = { process -> killProcess(process) },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Shortcuts -> ShortcutsScreen(
                            snackbarHostState = snackbarHostState,
                            onShortcut = { shortcut -> runHotkey(shortcut.keys) },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Presentation -> PresentationScreen(
                            snackbarHostState = snackbarHostState,
                            onKey = { key -> runHotkey(listOf(key)) },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Notifications -> NotificationsScreen(
                            notifications = notifications,
                            onClear = { notifications = emptyList() },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Settings -> SettingsScreen(
                            pairedAddress = savedInfo?.let { "${it.host}:${it.port}" } ?: "unknown",
                            appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "?",
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                settingsStore.themeMode = mode
                            },
                            onDisconnect = {
                                client.disconnect()
                                connectionState = ConnectionState.DISCONNECTED
                                screen = Screen.Pairing
                            },
                            onForget = {
                                client.disconnect()
                                pairingStore.clear()
                                savedInfo = null
                                connectionState = ConnectionState.DISCONNECTED
                                screen = Screen.Pairing
                            },
                            onBack = { screen = Screen.Dashboard },
                        )

                        Screen.Pairing -> PairingScreen(
                            initialHost = savedInfo?.let { formatHostPort(it.host, it.port, PairingStore.DEFAULT_PORT) }.orEmpty(),
                            initialToken = savedInfo?.token.orEmpty(),
                            isConnecting = connectionState == ConnectionState.CONNECTING,
                            errorMessage = errorMessage,
                            canWake = savedInfo?.mac != null,
                            onWake = wake@{
                                val info = savedInfo ?: return@wake
                                val mac = info.mac ?: return@wake
                                coroutineScope.launch {
                                    val sent = sendMagicPacket(mac, info.host)
                                    val message = if (sent) {
                                        "Wake signal sent - give the PC a minute to start, then tap Connect"
                                    } else {
                                        "Couldn't send the wake signal"
                                    }
                                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            onConnect = { address, token ->
                                val target = parseHostPort(address, PairingStore.DEFAULT_PORT)
                                if (target == null) {
                                    errorMessage = "That address isn't valid - use e.g. 192.168.1.5 or 192.168.1.5:9000."
                                } else {
                                    connectTo(target.host, target.port, token)
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
