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
import com.gesturelink.app.network.ConnectionState
import com.gesturelink.app.network.GestureLinkClient
import com.gesturelink.app.ui.DashboardScreen
import com.gesturelink.app.ui.PairingScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore
    private val client = GestureLinkClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(applicationContext)
        val saved = pairingStore.load()

        setContent {
            var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var pairedToken by remember { mutableStateOf(saved?.token.orEmpty()) }

            // We don't have a "current state" query action yet (see docs/ARCHITECTURE.md
            // planned actions), so these start optimistically enabled rather than reflecting
            // the PC's real radio state until that's added.
            var wifiEnabled by remember { mutableStateOf(true) }
            var bluetoothEnabled by remember { mutableStateOf(true) }

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

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (connectionState == ConnectionState.CONNECTED) {
                        DashboardScreen(
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
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("App browser is coming in the next commit")
                                }
                            },
                            onDisconnect = {
                                client.disconnect()
                                connectionState = ConnectionState.DISCONNECTED
                            },
                        )
                    } else {
                        PairingScreen(
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
                                            ConnectionState.CONNECTED ->
                                                pairingStore.save(PairingInfo(host, PairingStore.DEFAULT_PORT, token))
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
