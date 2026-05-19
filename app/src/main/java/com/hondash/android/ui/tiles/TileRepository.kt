package com.hondash.android.ui.tiles

import android.content.Context

/** Persists the user's tile layout in SharedPreferences as a JSON blob. */
class TileRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<TileSpec> {
        val text = prefs.getString(KEY_TILES, null) ?: return TileSpec.DEFAULTS
        val list = TileSpec.listFromJson(text)
        return list.ifEmpty { TileSpec.DEFAULTS }
    }

    fun save(tiles: List<TileSpec>) {
        prefs.edit().putString(KEY_TILES, TileSpec.listToJson(tiles)).apply()
    }

    private companion object {
        const val PREFS_NAME = "hondash_tiles"
        const val KEY_TILES = "tiles_v1"
    }
}
