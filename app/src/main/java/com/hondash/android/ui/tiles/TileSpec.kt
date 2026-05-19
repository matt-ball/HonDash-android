package com.hondash.android.ui.tiles

import org.json.JSONArray
import org.json.JSONObject

/** A single dashboard tile's configuration. */
data class TileSpec(
    val source: TileSource,
    val unit: TileUnit,
    /** User override of the tile label. `null`/blank → fall back to source default. */
    val label: String? = null,
    val warnHigh: Float? = null,
    val alarmHigh: Float? = null,
    val warnLow: Float? = null,
    val alarmLow: Float? = null,
    /**
     * Optional linear calibration for analog channels: voltage range
     * [[inputMinV], [inputMaxV]] maps to value range [[outputMin], [outputMax]]
     * in the tile's selected unit. All four must be set for the
     * calibration to apply; otherwise the legacy 0–5 V → 0–100 psi /
     * 0–6.895 bar default is used.
     */
    val inputMinV: Float? = null,
    val inputMaxV: Float? = null,
    val outputMin: Float? = null,
    val outputMax: Float? = null,
) {
    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() } ?: source.defaultLabel

    val calibration: LinearCalibration?
        get() {
            val vLo = inputMinV ?: return null
            val vHi = inputMaxV ?: return null
            val oLo = outputMin ?: return null
            val oHi = outputMax ?: return null
            if (vHi == vLo) return null
            return LinearCalibration(vLo, vHi, oLo, oHi)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("source", source.name)
        put("unit", unit.name)
        label?.let { put("label", it) }
        warnHigh?.let { put("warnHigh", it.toDouble()) }
        alarmHigh?.let { put("alarmHigh", it.toDouble()) }
        warnLow?.let { put("warnLow", it.toDouble()) }
        alarmLow?.let { put("alarmLow", it.toDouble()) }
        inputMinV?.let { put("inputMinV", it.toDouble()) }
        inputMaxV?.let { put("inputMaxV", it.toDouble()) }
        outputMin?.let { put("outputMin", it.toDouble()) }
        outputMax?.let { put("outputMax", it.toDouble()) }
    }

    companion object {
        fun fromJson(json: JSONObject): TileSpec? {
            val source = runCatching { TileSource.valueOf(json.getString("source")) }
                .getOrNull() ?: return null
            val unit = runCatching { TileUnit.valueOf(json.getString("unit")) }
                .getOrNull() ?: TileUnits.defaultFor(source)
            return TileSpec(
                source = source,
                unit = unit,
                label = json.optString("label").takeIf { it.isNotBlank() },
                warnHigh = json.optDoubleOrNull("warnHigh")?.toFloat(),
                alarmHigh = json.optDoubleOrNull("alarmHigh")?.toFloat(),
                warnLow = json.optDoubleOrNull("warnLow")?.toFloat(),
                alarmLow = json.optDoubleOrNull("alarmLow")?.toFloat(),
                inputMinV = json.optDoubleOrNull("inputMinV")?.toFloat(),
                inputMaxV = json.optDoubleOrNull("inputMaxV")?.toFloat(),
                outputMin = json.optDoubleOrNull("outputMin")?.toFloat(),
                outputMax = json.optDoubleOrNull("outputMax")?.toFloat(),
            )
        }

        fun listToJson(tiles: List<TileSpec>): String =
            JSONArray().apply { tiles.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String?): List<TileSpec> {
            if (text.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(text)
                buildList {
                    for (i in 0 until arr.length()) {
                        fromJson(arr.getJSONObject(i))?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())
        }

        /** First-run dashboard layout. No thresholds — user sets those per-tile. */
        val DEFAULTS = listOf(
            TileSpec(TileSource.RPM, TileUnit.RPM),
            TileSpec(TileSource.ECT, TileUnit.CELSIUS),
            TileSpec(TileSource.TPS, TileUnit.PERCENT),
            TileSpec(TileSource.AFR, TileUnit.RATIO),
        )
    }
}

enum class TileSeverity { NORMAL, WARNING, ALARM }

/** Linear mapping from a sensor's voltage range to a physical value range. */
data class LinearCalibration(
    val inputMinV: Float,
    val inputMaxV: Float,
    val outputMin: Float,
    val outputMax: Float,
) {
    fun apply(voltage: Float): Float {
        val t = (voltage - inputMinV) / (inputMaxV - inputMinV)
        return outputMin + t * (outputMax - outputMin)
    }
}

fun TileSpec.severity(value: Float?): TileSeverity {
    if (value == null) return TileSeverity.NORMAL
    val isAlarm = (alarmHigh != null && value >= alarmHigh) ||
            (alarmLow != null && value <= alarmLow)
    if (isAlarm) return TileSeverity.ALARM
    val isWarn = (warnHigh != null && value >= warnHigh) ||
            (warnLow != null && value <= warnLow)
    return if (isWarn) TileSeverity.WARNING else TileSeverity.NORMAL
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null
