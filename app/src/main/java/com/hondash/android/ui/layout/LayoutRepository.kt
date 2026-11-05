package com.hondash.android.ui.layout

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Portable layout and device-local alerts share the MVP's preference file, not its key. */
class LayoutRepository internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("hondash_tiles", Context.MODE_PRIVATE)
    )

    fun load(): DashboardLayout {
        prefs.getString("layout_v2", null)?.let { text ->
            runCatching { LayoutCodec.decode(text).layout }.getOrNull()?.let { return it }
        }
        // Never edit tiles_v1: downgrading to the MVP can still recover the original tiles.
        val migrated = prefs.getString("tiles_v1", null)?.let { text ->
            runCatching { LayoutCodec.decode(text).layout }.getOrNull()
        } ?: DashboardLayout()
        save(migrated)
        return migrated
    }

    fun save(layout: DashboardLayout) {
        prefs.edit().putString("layout_v2", LayoutCodec.encode(layout)).apply()
    }

    fun loadAlertPreferences(): AlertPreferences {
        val text = prefs.getString("alerts_v1", null) ?: return AlertPreferences()
        return runCatching {
            val json = JSONObject(text)
            AlertPreferences(
                sound = json.opt("sound") as? Boolean ?: true,
                vibration = json.opt("vibration") as? Boolean ?: true,
                demoAlerts = json.opt("demoAlerts") as? Boolean ?: false,
            )
        }.getOrDefault(AlertPreferences())
    }

    fun saveAlertPreferences(preferences: AlertPreferences) {
        val json = JSONObject().apply {
            put("sound", preferences.sound)
            put("vibration", preferences.vibration)
            put("demoAlerts", preferences.demoAlerts)
        }
        prefs.edit().putString("alerts_v1", json.toString()).apply()
    }
}
