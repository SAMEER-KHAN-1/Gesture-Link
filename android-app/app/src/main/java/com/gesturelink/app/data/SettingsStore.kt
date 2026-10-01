package com.gesturelink.app.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

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

    var themeMode: ThemeMode
        // An unknown/missing stored value (e.g. from a future version) falls back to following the system.
        get() = ThemeMode.values().firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) } ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /** Folders bookmarked in the file browser, sorted. */
    var bookmarks: List<String>
        get() = (prefs.getStringSet(KEY_BOOKMARKS, null) ?: emptySet()).sorted()
        set(value) {
            prefs.edit().putStringSet(KEY_BOOKMARKS, value.toSet()).apply()
        }

    companion object {
        private const val PREFS_NAME = "gesturelink_settings"
        private const val KEY_BOOKMARKS = "file_bookmarks"
        private const val KEY_MOUSE_SENSITIVITY = "mouse_sensitivity"
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_MOUSE_SENSITIVITY = 1f
    }
}
