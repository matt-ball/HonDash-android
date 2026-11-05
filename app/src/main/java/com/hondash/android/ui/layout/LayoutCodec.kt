package com.hondash.android.ui.layout

import com.hondash.android.ui.tiles.TileSource
import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileStyle
import com.hondash.android.ui.tiles.TileUnits
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.CharacterCodingException

/** Successful reads retain recoverable problems for the future import preview. */
data class DecodedLayout(
    val layout: DashboardLayout,
    val warnings: List<String>,
    val droppedTiles: Int,
)

open class LayoutCodecException(message: String) : IllegalArgumentException(message)

class NewerLayoutVersionException : LayoutCodecException(LayoutCodec.NEWER_VERSION_MESSAGE)

/** The same versioned document is used for preferences and portable files. */
object LayoutCodec {
    const val FORMAT = "hondash-layout"
    const val VERSION = 1
    const val MAX_BYTES = 256 * 1024
    const val MAX_TILES = 200
    const val NEWER_VERSION_MESSAGE = "Created by a newer HonDash — please update"

    fun encode(layout: DashboardLayout): String {
        checkDocument(layout.tiles.size <= MAX_TILES, "Layouts may contain at most $MAX_TILES tiles")
        val text = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("defaultGrid", layout.defaultGrid.name)
            put("pageGrids", JSONArray().apply { layout.pageGrids.forEach { put(it.name) } })
            put("tiles", JSONArray().apply { layout.tiles.forEach { put(encodeTile(it)) } })
            put("shiftLight", JSONObject().apply {
                put("enabled", layout.shiftLight.enabled)
                put("startRpm", layout.shiftLight.startRpm)
                put("shiftRpm", layout.shiftLight.shiftRpm)
            })
            put("theme", layout.theme.name)
        }.toString()
        checkSize(text.toByteArray(Charsets.UTF_8).size)
        return text
    }

    fun decode(bytes: ByteArray): DecodedLayout {
        checkSize(bytes.size)
        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw LayoutCodecException("Layout must be valid UTF-8 JSON")
        }
        return decode(text)
    }

    /** A legacy tiles_v1 array is version 0 and upgrades to the default layout settings. */
    fun decode(text: String): DecodedLayout {
        checkSize(text.toByteArray(Charsets.UTF_8).size)
        try {
            val root = LayoutJson.parse(text)
            val warnings = mutableListOf<String>()
            if (root is JSONArray) {
                val tiles = decodeTiles(root, warnings)
                return DecodedLayout(DashboardLayout(tiles = tiles), warnings, root.length() - tiles.size)
            }
            checkDocument(root is JSONObject, "Layout must be a JSON object or legacy tile array")
            val json = root as JSONObject
            checkDocument(json.opt("format") == FORMAT, "Not a HonDash layout")
            val version = json.intOrNull("version")
                ?: throw LayoutCodecException("Layout version must be an integer")
            if (version > VERSION) throw NewerLayoutVersionException()
            checkDocument(version == VERSION, "Unsupported layout version: $version")
            val defaultGrid = enumValue(json, "defaultGrid", GridPreset.G2X2, warnings)
            val grids = json.optionalArray("pageGrids") ?: JSONArray()
            val pageGrids = (0 until grids.length()).map { index ->
                enumValue<GridPreset>(grids.opt(index), defaultGrid, "pageGrids[$index]", warnings)
            }
            val tilesJson = json.optJSONArray("tiles")
                ?: throw LayoutCodecException("Layout tiles must be an array")
            val tiles = decodeTiles(tilesJson, warnings)
            val layout = DashboardLayout(
                defaultGrid = defaultGrid,
                pageGrids = pageGrids,
                tiles = tiles,
                shiftLight = decodeShiftLight(json.optionalObject("shiftLight"), warnings),
                theme = enumValue(json, "theme", ThemeMode.AUTO, warnings),
            )
            return DecodedLayout(layout, warnings, tilesJson.length() - tiles.size)
        } catch (error: JSONException) {
            throw LayoutCodecException("Invalid layout JSON: ${error.message}")
        }
    }

    private fun encodeTile(tile: TileSpec): JSONObject = JSONObject().apply {
        put("source", tile.source.name)
        put("unit", tile.unit.name)
        tile.label?.let { put("label", it) }
        put("style", tile.style.name)
        put("alarmSilenced", tile.alarmSilenced)
        mapOf(
            "warnHigh" to tile.warnHigh, "alarmHigh" to tile.alarmHigh,
            "warnLow" to tile.warnLow, "alarmLow" to tile.alarmLow,
            "inputMinV" to tile.inputMinV, "inputMaxV" to tile.inputMaxV,
            "outputMin" to tile.outputMin, "outputMax" to tile.outputMax,
            "gaugeMin" to tile.gaugeMin, "gaugeMax" to tile.gaugeMax,
        ).forEach { (key, value) -> value?.let { put(key, it.toDouble()) } }
    }

    private fun decodeTiles(json: JSONArray, warnings: MutableList<String>): List<TileSpec> {
        checkDocument(json.length() <= MAX_TILES, "Layouts may contain at most $MAX_TILES tiles")
        return buildList {
            for (index in 0 until json.length()) {
                val tile = json.optJSONObject(index)
                    ?: throw LayoutCodecException("tiles[$index] must be an object")
                val path = "tiles[$index]"
                val source = TileSource.entries.firstOrNull { it.name == tile.opt("source") }
                if (source == null) {
                    warnings += "$path.source: unknown source; tile dropped"
                    continue
                }
                var gaugeMin = tile.floatOrNull("gaugeMin", path, warnings)
                var gaugeMax = tile.floatOrNull("gaugeMax", path, warnings)
                if (gaugeMin != null && gaugeMax != null && gaugeMin >= gaugeMax) {
                    warnings += "$path: invalid gauge range; using source defaults"
                    gaugeMin = null
                    gaugeMax = null
                }
                add(TileSpec(
                    source = source,
                    unit = enumValue(tile, "unit", TileUnits.defaultFor(source), warnings, path),
                    label = (tile.opt("label") as? String)?.takeIf { it.isNotBlank() },
                    warnHigh = tile.floatOrNull("warnHigh", path, warnings),
                    alarmHigh = tile.floatOrNull("alarmHigh", path, warnings),
                    warnLow = tile.floatOrNull("warnLow", path, warnings),
                    alarmLow = tile.floatOrNull("alarmLow", path, warnings),
                    inputMinV = tile.floatOrNull("inputMinV", path, warnings),
                    inputMaxV = tile.floatOrNull("inputMaxV", path, warnings),
                    outputMin = tile.floatOrNull("outputMin", path, warnings),
                    outputMax = tile.floatOrNull("outputMax", path, warnings),
                    style = enumValue(tile, "style", TileStyle.NUMERIC, warnings, path),
                    gaugeMin = gaugeMin,
                    gaugeMax = gaugeMax,
                    alarmSilenced = tile.boolean("alarmSilenced", false, path, warnings),
                ))
            }
        }
    }

    private fun decodeShiftLight(json: JSONObject?, warnings: MutableList<String>): ShiftLightConfig {
        val defaults = ShiftLightConfig()
        if (json == null) return defaults
        val enabled = json.boolean("enabled", defaults.enabled, "shiftLight", warnings)
        val start = if (json.has("startRpm")) json.intOrNull("startRpm") else defaults.startRpm
        val shift = if (json.has("shiftRpm")) json.intOrNull("shiftRpm") else defaults.shiftRpm
        if (start == null || shift == null || start <= 0 || start >= shift || shift > 12000) {
            warnings += "shiftLight: invalid RPM range; using defaults"
            return defaults.copy(enabled = enabled)
        }
        return ShiftLightConfig(enabled, start, shift)
    }

    private fun JSONObject.floatOrNull(key: String, path: String, warnings: MutableList<String>): Float? {
        if (!has(key) || isNull(key)) return null
        // The MVP accepted numeric strings, so retain them when reading legacy fixtures too.
        val raw = opt(key)
        val value = when (raw) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull()
            else -> null
        }
        if (value != null && value.isFinite()) return value
        warnings += "$path.$key: invalid number; using default"
        return null
    }

    private fun JSONObject.intOrNull(key: String): Int? {
        val value = opt(key) as? Number ?: return null
        val number = value.toDouble()
        return number.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }
            ?.toInt()?.takeIf { it.toDouble() == number }
    }

    private fun JSONObject.boolean(
        key: String, default: Boolean, path: String, warnings: MutableList<String>,
    ): Boolean {
        if (!has(key)) return default
        return (opt(key) as? Boolean) ?: default.also {
            warnings += "$path.$key: invalid boolean; using default"
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(
        json: JSONObject, key: String, default: T, warnings: MutableList<String>, path: String = "layout",
    ): T = if (!json.has(key)) default else enumValue(json.opt(key), default, "$path.$key", warnings)

    private inline fun <reified T : Enum<T>> enumValue(
        value: Any?, default: T, path: String, warnings: MutableList<String>,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: default.also {
        warnings += "$path: unknown value; using ${default.name}"
    }

    private fun JSONObject.optionalArray(key: String): JSONArray? {
        if (!has(key)) return null
        return optJSONArray(key) ?: throw LayoutCodecException("$key must be an array")
    }

    private fun JSONObject.optionalObject(key: String): JSONObject? {
        if (!has(key)) return null
        return optJSONObject(key) ?: throw LayoutCodecException("$key must be an object")
    }

    private fun checkSize(size: Int) = checkDocument(size <= MAX_BYTES, "Layout exceeds 256 KB")

    private fun checkDocument(valid: Boolean, message: String) {
        if (!valid) throw LayoutCodecException(message)
    }
}
