package com.gesturelink.app.data

import android.content.Context
import android.content.SharedPreferences

data class PairingInfo(val host: String, val port: Int, val token: String)

/** Remembers the last PC we successfully paired with, so we can auto-reconnect. */
class PairingStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(info: PairingInfo) {
        prefs.edit()
            .putString(KEY_HOST, info.host)
            .putInt(KEY_PORT, info.port)
            .putString(KEY_TOKEN, info.token)
            .apply()
    }

    fun load(): PairingInfo? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        return PairingInfo(host, port, token)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "gesturelink_pairing"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        const val DEFAULT_PORT = 8765
    }
}
