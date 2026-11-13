package com.pindou.studio.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 设置存储（SharedPreferences），主题等关键项用 Compose 状态，改完即时生效。 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("pindou_settings", Context.MODE_PRIVATE)

    /** 主题配色 id，见 PindouThemes。 */
    var themeId: String by mutableStateOf(prefs.getString("themeId", "sea") ?: "sea")
        private set

    /** 0 跟随系统，1 浅色，2 深色 */
    var darkMode: Int by mutableStateOf(prefs.getInt("darkMode", 0))
        private set

    /** 是否看过新手指引。 */
    var introShown: Boolean by mutableStateOf(prefs.getBoolean("introShown", false))
        private set

    fun updateTheme(id: String) {
        themeId = id
        prefs.edit().putString("themeId", id).apply()
    }

    fun updateDarkMode(mode: Int) {
        darkMode = mode
        prefs.edit().putInt("darkMode", mode).apply()
    }

    fun updateIntroShown(shown: Boolean) {
        introShown = shown
        prefs.edit().putBoolean("introShown", shown).apply()
    }

    var lastPaletteId: String
        get() = prefs.getString("lastPaletteId", "") ?: ""
        set(value) = prefs.edit().putString("lastPaletteId", value).apply()

    var showCodes: Boolean
        get() = prefs.getBoolean("showCodes", false)
        set(value) = prefs.edit().putBoolean("showCodes", value).apply()

    var showGrid: Boolean
        get() = prefs.getBoolean("showGrid", true)
        set(value) = prefs.edit().putBoolean("showGrid", value).apply()

    var showMajorGrid: Boolean
        get() = prefs.getBoolean("showMajorGrid", true)
        set(value) = prefs.edit().putBoolean("showMajorGrid", value).apply()

    var displayMode: String
        get() = prefs.getString("displayMode", "standard") ?: "standard"
        set(value) = prefs.edit().putString("displayMode", value).apply()

    var roundBeads: Boolean
        get() = prefs.getBoolean("roundBeads", false)
        set(value) = prefs.edit().putBoolean("roundBeads", value).apply()

    var showRuler: Boolean
        get() = prefs.getBoolean("showRuler", true)
        set(value) = prefs.edit().putBoolean("showRuler", value).apply()

    var lastProjectId: String
        get() = prefs.getString("lastProjectId", "") ?: ""
        set(value) = prefs.edit().putString("lastProjectId", value).apply()
}
