package com.gesturelink.app.network

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Thin wrapper around an OkHttp WebSocket that speaks the GestureLink command
 * protocol: send a CommandRequest, suspend until the matching CommandResponse
 * (same id) comes back.
 */
class GestureLinkClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<CommandResponse>>()

    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    fun connect(
        host: String,
        port: Int,
        onStateChanged: (ConnectionState) -> Unit,
        onPush: (PushMessage) -> Unit = {},
    ) {
        state = ConnectionState.CONNECTING
        onStateChanged(state)

        val request = Request.Builder().url("ws://$host:$port/ws").build()
        val newWebSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                // A manual disconnect() nulls out our `webSocket` field immediately, but
                // OkHttp still delivers this listener's onClosed/onFailure asynchronously
                // afterward for the socket it was closing. Without this check, that stale
                // callback would fire onStateChanged(DISCONNECTED) a moment after a
                // deliberate disconnect and show a bogus "couldn't reach the PC" error.
                fun isStale(socket: WebSocket) = socket !== webSocket

                override fun onOpen(socket: WebSocket, response: Response) {
                    if (isStale(socket)) return
                    state = ConnectionState.CONNECTED
                    onStateChanged(state)
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    if (isStale(socket)) return
                    val element = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return

                    // Pushes have no matching request id - a CommandResponse always does -
                    // so the presence of a "push" key is what tells the two apart.
                    if ("push" in element) {
                        val push = runCatching { json.decodeFromJsonElement<PushMessage>(element) }.getOrNull()
                        push?.let(onPush)
                        return
                    }

                    val response = runCatching { json.decodeFromJsonElement<CommandResponse>(element) }.getOrNull()
                        ?: return
                    pendingRequests.remove(response.id)?.resume(response)
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    if (isStale(socket)) return
                    state = ConnectionState.DISCONNECTED
                    failPendingRequests()
                    onStateChanged(state)
                }

                override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                    if (isStale(socket)) return
                    state = ConnectionState.DISCONNECTED
                    failPendingRequests()
                    onStateChanged(state)
                }
            },
        )
        webSocket = newWebSocket
    }

    /** Without this, a request in flight when the connection drops would suspend
     * forever - nothing else is ever going to resume it. */
    private fun failPendingRequests() {
        val stillWaiting = pendingRequests.values.toList()
        pendingRequests.clear()
        stillWaiting.forEach { it.resumeWithException(IllegalStateException("connection lost")) }
    }

    suspend fun sendCommand(
        token: String,
        action: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): CommandResponse {
        val ws = webSocket ?: throw IllegalStateException("not connected to a PC yet")
        val id = UUID.randomUUID().toString().take(8)
        val request = CommandRequest(id = id, token = token, action = action, params = params)
        val payload = json.encodeToString(CommandRequest.serializer(), request)

        return suspendCancellableCoroutine { continuation ->
            pendingRequests[id] = continuation
            val enqueued = ws.send(payload)
            if (!enqueued) {
                pendingRequests.remove(id)
                continuation.resumeWithException(IllegalStateException("failed to send '$action' - connection may be closed"))
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        state = ConnectionState.DISCONNECTED
    }
}
