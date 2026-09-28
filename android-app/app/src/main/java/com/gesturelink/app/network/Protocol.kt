package com.gesturelink.app.network

import kotlinx.serialization.SerialName
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

/** Shape of the `result` payload for the `apps_list` action. */
@Serializable
data class AppInfo(
    @SerialName("app_id") val appId: String,
    val name: String,
)

@Serializable
data class AppsListResult(val apps: List<AppInfo> = emptyList())

/** Shape of the `result` payload for the `system_stats` action. */
@Serializable
data class SystemStats(
    @SerialName("cpu_percent") val cpuPercent: Double,
    @SerialName("memory_percent") val memoryPercent: Double,
    @SerialName("battery_percent") val batteryPercent: Double? = null,
    @SerialName("battery_plugged") val batteryPlugged: Boolean? = null,
)

/** One drive/folder/file entry in the `result` payload for the `list_dir` action. */
@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    @SerialName("is_dir") val isDir: Boolean,
    val size: Long? = null,
)

@Serializable
data class ListDirResult(val path: String = "", val entries: List<FileEntry> = emptyList())
