package com.gesturelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gesturelink.app.data.PairingInfo
import com.gesturelink.app.data.PairingStore
import com.gesturelink.app.network.AppInfo
import com.gesturelink.app.network.AppsListResult
import com.gesturelink.app.network.ConnectionState
import com.gesturelink.app.network.GestureLinkClient
import com.gesturelink.app.ui.AppsScreen
import com.gesturelink.app.ui.DashboardScreen
import com.gesturelink.app.ui.PairingScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private sealed class Screen {
    object Pairing : Screen()
    object Dashboard : Screen()
    object Apps : Screen()
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

            // We don't have a "current state" query action yet (see docs/ARCHITECTURE.md
            // planned actions), so these start optimistically enabled rather than reflecting
            // the PC's real radio state until that's added.
            var wifiEnabled by remember { mutableStateOf(true) }
            var bluetoothEnabled by remember { mutableStateOf(true) }

            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var appsLoading by remember { mutableStateOf(false) }

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

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Dashboard -> DashboardScreen(
                            snackbarHostState = snackbarHostState,
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
                            onDisconnect = {
                                client.disconnect()
                                connectionState = ConnectionState.DISCONNECTED
                                screen = Screen.Pairing
                            },
                        )

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
