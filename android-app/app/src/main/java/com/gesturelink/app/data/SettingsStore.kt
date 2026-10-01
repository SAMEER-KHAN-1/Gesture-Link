package com.gesturelink.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App preferences that should survive restarts. Kept in its own file, separate from
 * [PairingStore], so "Forget PC" (which clears the pairing) doesn't also reset these.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mouseSensitivity: Float
        get() = prefs.getFloat(KEY_MOUSE_SENSITIVITY, DEFAULT_MOUSE_SENSITIVITY)
        set(value) {
            prefs.edit().putFloat(KEY_MOUSE_SENSITIVITY, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "gesturelink_settings"
        private const val KEY_MOUSE_SENSITIVITY = "mouse_sensitivity"
        const val DEFAULT_MOUSE_SENSITIVITY = 1f
    }
}
