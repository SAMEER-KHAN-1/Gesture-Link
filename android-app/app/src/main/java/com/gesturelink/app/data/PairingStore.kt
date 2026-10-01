package com.gesturelink.app.data

import android.content.Context
import android.content.SharedPreferences

/** [mac] is the PC's MAC address, learned while connected, so it can be woken with Wake-on-LAN later. */
data class PairingInfo(val host: String, val port: Int, val token: String, val mac: String? = null)

/** Remembers the last PC we successfully paired with, so we can auto-reconnect. */
class PairingStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(info: PairingInfo) {
        prefs.edit()
            .putString(KEY_HOST, info.host)
            .putInt(KEY_PORT, info.port)
            .putString(KEY_TOKEN, info.token)
            .putString(KEY_MAC, info.mac) // null removes the key
            .apply()
    }

    fun load(): PairingInfo? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        return PairingInfo(host, port, token, prefs.getString(KEY_MAC, null))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "gesturelink_pairing"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_MAC = "mac"
        const val DEFAULT_PORT = 8765
    }
}
