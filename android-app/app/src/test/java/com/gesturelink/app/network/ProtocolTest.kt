package com.gesturelink.app.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON below is shaped like what the PC server (server/protocol.py + the actions) really
 * sends - see docs/ARCHITECTURE.md. Two decoders are in play in the app, so both are used here:
 * GestureLinkClient reads whole messages leniently (ignoreUnknownKeys), while MainActivity
 * decodes each action's `result` with the default, strict Json.
 */
class ProtocolTest {

    private val lenient = Json { ignoreUnknownKeys = true }

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // --- CommandRequest ---

    @Test
    fun `a request carries id, token, action and params`() {
        val request = CommandRequest(
            id = "ab12cd34",
            token = "secret",
            action = "list_dir",
            params = buildJsonObject { put("path", "C:\\Users") },
        )

        val sent = obj(Json.encodeToString(CommandRequest.serializer(), request))

        assertEquals("ab12cd34", sent["id"]!!.jsonPrimitive.content)
        assertEquals("secret", sent["token"]!!.jsonPrimitive.content)
        assertEquals("list_dir", sent["action"]!!.jsonPrimitive.content)
        assertEquals("C:\\Users", sent["params"]!!.jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a request without params round-trips with empty params`() {
        val request = CommandRequest(id = "1", token = "t", action = "ping")

        val text = Json.encodeToString(CommandRequest.serializer(), request)

        // The PC treats a missing params as {} (Field(default_factory=dict)), so either form is fine.
        assertEquals(request, Json.decodeFromString(CommandRequest.serializer(), text))
    }

    // --- CommandResponse ---

    @Test
    fun `an ok response keeps its result`() {
        val response = lenient.decodeFromString(
            CommandResponse.serializer(),
            """{"id":"ab12cd34","ok":true,"action":"mac_address","result":{"mac":"AA:BB:CC:DD:EE:FF"},"error":null}""",
        )

        assertEquals("ab12cd34", response.id)
        assertTrue(response.ok)
        assertEquals("mac_address", response.action)
        assertEquals("AA:BB:CC:DD:EE:FF", response.result["mac"]!!.jsonPrimitive.content)
        assertNull(response.error)
    }

    @Test
    fun `an error response carries the message and an empty result`() {
        val response = lenient.decodeFromString(
            CommandResponse.serializer(),
            """{"id":"x","ok":false,"action":"download_file","result":{},"error":"'big.iso' is over the 15MB transfer limit"}""",
        )

        assertFalse(response.ok)
        assertEquals("'big.iso' is over the 15MB transfer limit", response.error)
        assertTrue(response.result.isEmpty())
    }

    @Test
    fun `a response with no result or error field uses the defaults`() {
        val response = lenient.decodeFromString(CommandResponse.serializer(), """{"id":"x","ok":true,"action":"ping"}""")

        assertTrue(response.result.isEmpty())
        assertNull(response.error)
    }

    @Test
    fun `unknown fields on a response are ignored so a newer server doesn't break an older app`() {
        val response = lenient.decodeFromString(
            CommandResponse.serializer(),
            """{"id":"x","ok":true,"action":"ping","result":{},"server_version":"2.0"}""",
        )

        assertEquals("x", response.id)
    }

    @Test
    fun `a response without an id is not a response`() {
        assertThrows(SerializationException::class.java) {
            lenient.decodeFromString(CommandResponse.serializer(), """{"ok":true,"action":"ping"}""")
        }
    }

    // --- PushMessage ---

    @Test
    fun `a push carries its kind and data`() {
        val push = lenient.decodeFromString(PushMessage.serializer(), """{"push":"battery_low","data":{"battery_percent":12}}""")

        assertEquals("battery_low", push.push)
        assertEquals(12, push.data["battery_percent"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a push with no data gets an empty object`() {
        val push = lenient.decodeFromString(PushMessage.serializer(), """{"push":"hello"}""")

        assertTrue(push.data.isEmpty())
    }

    @Test
    fun `pushes and responses can be told apart by the push key alone`() {
        // GestureLinkClient.onMessage relies on this: "push" present -> push, otherwise a response.
        val push = obj("""{"push":"battery_low","data":{}}""")
        val response = obj("""{"id":"x","ok":true,"action":"ping","result":{}}""")

        assertTrue("push" in push)
        assertFalse("id" in push)
        assertFalse("push" in response)
        assertTrue("id" in response)
    }

    // --- action result payloads ---

    @Test
    fun `apps_list result maps app_id`() {
        val result = Json.decodeFromJsonElement(
            AppsListResult.serializer(),
            obj("""{"apps":[{"app_id":"notepad","name":"Notepad"},{"app_id":"calc","name":"Calculator"}]}"""),
        )

        assertEquals(listOf(AppInfo("notepad", "Notepad"), AppInfo("calc", "Calculator")), result.apps)
    }

    @Test
    fun `apps_list result with no apps is empty`() {
        assertTrue(Json.decodeFromJsonElement(AppsListResult.serializer(), obj("{}")).apps.isEmpty())
    }

    @Test
    fun `system_stats result with every field`() {
        val stats = Json.decodeFromJsonElement(
            SystemStats.serializer(),
            obj(
                """{"cpu_percent":12.5,"memory_percent":63.0,"disk_percent":71.2,"disk_free_gb":140.3,
                   "uptime_seconds":93784,"battery_percent":88.0,"battery_plugged":true}""",
            ),
        )

        assertEquals(12.5, stats.cpuPercent, 0.0)
        assertEquals(63.0, stats.memoryPercent, 0.0)
        assertEquals(71.2, stats.diskPercent!!, 0.0)
        assertEquals(140.3, stats.diskFreeGb!!, 0.0)
        assertEquals(93784L, stats.uptimeSeconds)
        assertEquals(88.0, stats.batteryPercent!!, 0.0)
        assertEquals(true, stats.batteryPlugged)
    }

    @Test
    fun `system_stats result from a desktop has no battery and unreadable disk is null`() {
        val stats = Json.decodeFromJsonElement(
            SystemStats.serializer(),
            obj("""{"cpu_percent":3.0,"memory_percent":40.0,"disk_percent":null,"disk_free_gb":null,"uptime_seconds":null,"battery_percent":null,"battery_plugged":null}"""),
        )

        assertNull(stats.diskPercent)
        assertNull(stats.diskFreeGb)
        assertNull(stats.uptimeSeconds)
        assertNull(stats.batteryPercent)
        assertNull(stats.batteryPlugged)
    }

    @Test
    fun `system_stats result from an older server with only cpu and memory still decodes`() {
        val stats = Json.decodeFromJsonElement(SystemStats.serializer(), obj("""{"cpu_percent":1.0,"memory_percent":2.0}"""))

        assertNull(stats.diskPercent)
        assertNull(stats.uptimeSeconds)
    }

    @Test
    fun `list_dir at the top level lists drives, which have no size`() {
        val result = Json.decodeFromJsonElement(
            ListDirResult.serializer(),
            obj("""{"path":"","entries":[{"name":"C:","path":"C:\\","is_dir":true},{"name":"D:","path":"D:\\","is_dir":true}]}"""),
        )

        assertEquals("", result.path)
        assertEquals(listOf("C:\\", "D:\\"), result.entries.map { it.path })
        assertTrue(result.entries.all { it.isDir && it.size == null })
    }

    @Test
    fun `list_dir distinguishes files from folders and keeps file sizes`() {
        val result = Json.decodeFromJsonElement(
            ListDirResult.serializer(),
            obj(
                """{"path":"C:\\Users","entries":[
                   {"name":"sam","path":"C:\\Users\\sam","is_dir":true},
                   {"name":"notes.txt","path":"C:\\Users\\notes.txt","is_dir":false,"size":2048}]}""",
            ),
        )

        val (folder, file) = result.entries
        assertTrue(folder.isDir)
        assertFalse(file.isDir)
        assertEquals(2048L, file.size)
    }

    @Test
    fun `list_dir with no entries field is an empty folder`() {
        assertTrue(Json.decodeFromJsonElement(ListDirResult.serializer(), obj("""{"path":"C:\\Empty"}""")).entries.isEmpty())
    }

    @Test
    fun `list_dir entry without is_dir is rejected`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromJsonElement(FileEntry.serializer(), obj("""{"name":"x","path":"C:\\x"}"""))
        }
    }

    @Test
    fun `radio_status result`() {
        val result = Json.decodeFromJsonElement(
            RadioStatusResult.serializer(),
            obj("""{"wifi_enabled":true,"bluetooth_enabled":false}"""),
        )

        assertTrue(result.wifiEnabled)
        assertFalse(result.bluetoothEnabled)
    }

    @Test
    fun `download_file result`() {
        val result = Json.decodeFromJsonElement(
            DownloadFileResult.serializer(),
            obj("""{"name":"hello.txt","size":5,"data_base64":"aGVsbG8="}"""),
        )

        assertEquals(DownloadFileResult("hello.txt", 5, "aGVsbG8="), result)
    }

    @Test
    fun `download_file result missing its data is rejected`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromJsonElement(DownloadFileResult.serializer(), obj("""{"name":"hello.txt","size":5}"""))
        }
    }

    @Test
    fun `clipboard_get result defaults truncated to false`() {
        val result = Json.decodeFromJsonElement(ClipboardGetResult.serializer(), obj("""{"text":"copied"}"""))

        assertEquals("copied", result.text)
        assertFalse(result.truncated)
    }

    @Test
    fun `clipboard_get result flags truncation`() {
        val result = Json.decodeFromJsonElement(ClipboardGetResult.serializer(), obj("""{"text":"abc","truncated":true}"""))

        assertTrue(result.truncated)
    }

    @Test
    fun `clipboard_get result with an empty clipboard has empty text`() {
        assertEquals("", Json.decodeFromJsonElement(ClipboardGetResult.serializer(), obj("{}")).text)
    }

    @Test
    fun `screenshot result`() {
        val result = Json.decodeFromJsonElement(
            ScreenshotResult.serializer(),
            obj("""{"width":1280,"height":720,"data_base64":"/9j/4AAQ"}"""),
        )

        assertEquals(ScreenshotResult(1280, 720, "/9j/4AAQ"), result)
    }

    @Test
    fun `brightness_get result`() {
        assertEquals(40, Json.decodeFromJsonElement(BrightnessResult.serializer(), obj("""{"brightness":40}""")).brightness)
    }

    @Test
    fun `mac_address result`() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            Json.decodeFromJsonElement(MacAddressResult.serializer(), obj("""{"mac":"AA:BB:CC:DD:EE:FF"}""")).mac,
        )
    }

    @Test
    fun `process_list result maps memory_mb`() {
        val result = Json.decodeFromJsonElement(
            ProcessListResult.serializer(),
            obj("""{"processes":[{"pid":1234,"name":"chrome.exe","memory_mb":210.5},{"pid":4,"name":"System","memory_mb":0.1}]}"""),
        )

        assertEquals(listOf(ProcessInfo(1234, "chrome.exe", 210.5), ProcessInfo(4, "System", 0.1)), result.processes)
    }

    @Test
    fun `an empty process_list result has no processes`() {
        assertTrue(Json.decodeFromJsonElement(ProcessListResult.serializer(), obj("{}")).processes.isEmpty())
    }
}
