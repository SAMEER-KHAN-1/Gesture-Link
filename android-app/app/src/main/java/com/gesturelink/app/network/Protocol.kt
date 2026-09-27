package com.gesturelink.app.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Mirrors server/protocol.py on the PC side - keep both in sync when adding fields.
 * See docs/ARCHITECTURE.md for the full protocol spec.
 */

@Serializable
data class CommandRequest(
    val id: String,
    val token: String,
    val action: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class CommandResponse(
    val id: String,
    val ok: Boolean,
    val action: String,
    val result: JsonObject = JsonObject(emptyMap()),
    val error: String? = null,
)
