package dev.wardar.scrollapp.settings

import android.content.Context
import androidx.core.content.edit
import dev.wardar.scrollapp.Platform

enum class MixMode(val label: String) {
    ROTATE("Take turns"),
    SHUFFLE("Shuffle"),
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(platform: Platform) = prefs.getBoolean("enabled_${platform.name}", true)
    fun setEnabled(platform: Platform, on: Boolean) = prefs.edit { putBoolean("enabled_${platform.name}", on) }

    /** Load the desktop site, for when the mobile site refuses to show the feed. */
    fun isDesktop(platform: Platform) = prefs.getBoolean("desktop_${platform.name}", false)
    fun setDesktop(platform: Platform, on: Boolean) = prefs.edit { putBoolean("desktop_${platform.name}", on) }

    var autoscroll: Boolean
        get() = prefs.getBoolean("autoscroll", true)
        set(value) = prefs.edit { putBoolean("autoscroll", value) }

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = prefs.edit { putBoolean("sound", value) }

    var skipAds: Boolean
        get() = prefs.getBoolean("skip_ads", true)
        set(value) = prefs.edit { putBoolean("skip_ads", value) }

    var mixMode: MixMode
        get() = MixMode.entries.firstOrNull { it.name == prefs.getString("mix_mode", null) } ?: MixMode.ROTATE
        set(value) = prefs.edit { putString("mix_mode", value.name) }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit { putBoolean("onboarded", value) }
}
