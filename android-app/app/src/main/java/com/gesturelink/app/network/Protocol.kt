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
    @SerialName("disk_percent") val diskPercent: Double? = null,
    @SerialName("disk_free_gb") val diskFreeGb: Double? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long? = null,
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

/** Shape of the `result` payload for the `radio_status` action. */
@Serializable
data class RadioStatusResult(
    @SerialName("wifi_enabled") val wifiEnabled: Boolean,
    @SerialName("bluetooth_enabled") val bluetoothEnabled: Boolean,
)

/** Shape of the `result` payload for the `download_file` action. */
@Serializable
data class DownloadFileResult(
    val name: String,
    val size: Long,
    @SerialName("data_base64") val dataBase64: String,
)

/** Shape of the `result` payload for the `clipboard_get` action. */
@Serializable
data class ClipboardGetResult(
    val text: String = "",
    val truncated: Boolean = false,
)

/** Shape of the `result` payload for the `screenshot` action. */
@Serializable
data class ScreenshotResult(
    val width: Int,
    val height: Int,
    @SerialName("data_base64") val dataBase64: String,
)

/** Shape of the `result` payload for the `brightness_get` action. */
@Serializable
data class BrightnessResult(val brightness: Int)

/** One running process in the `result` payload for the `process_list` action. */
@Serializable
data class ProcessInfo(
    val pid: Int,
    val name: String,
    @SerialName("memory_mb") val memoryMb: Double,
)

@Serializable
data class ProcessListResult(val processes: List<ProcessInfo> = emptyList())

/**
 * A message the PC sends unprompted, not in reply to a request - told apart
 * from a CommandResponse by having no `id` field. See the "Push messages"
 * section of docs/ARCHITECTURE.md.
 */
@Serializable
data class PushMessage(
    val push: String,
    val data: JsonObject = JsonObject(emptyMap()),
)
