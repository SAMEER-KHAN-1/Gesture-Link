package com.gesturelink.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PairingScreen(
    initialHost: String,
    initialToken: String,
    isConnecting: Boolean,
    errorMessage: String?,
    onConnect: (host: String, token: String) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var token by remember { mutableStateOf(initialToken) }

    // The PC's tray icon shows a "gesturelink://<ip>:<port>?token=<token>" QR code -
    // scanning it fills these fields in instead of typing them by hand.
    val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val uri = Uri.parse(raw)
        uri.host?.let { host = if (uri.port != -1) "$it:${uri.port}" else it }
        uri.getQueryParameter("token")?.let { token = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Pair with your PC", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Run the GestureLink server on your PC, then scan the QR code from its tray icon - or enter the IP and token by hand.")
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("PC IP address (add :port if not 8765)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Pairing token") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scanLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the QR code shown on your PC")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan QR code")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { onConnect(host.trim(), token.trim()) },
            enabled = !isConnecting && host.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PairingScreenPreview() {
    MaterialTheme {
        PairingScreen(
            initialHost = "192.168.1.42",
            initialToken = "",
            isConnecting = false,
            errorMessage = null,
            onConnect = { _, _ -> },
        )
    }
}
