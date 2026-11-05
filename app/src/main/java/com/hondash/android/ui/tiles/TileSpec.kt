package com.hondash.android.ui.tiles

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
    val style: TileStyle = TileStyle.NUMERIC,
    val gaugeMin: Float? = null,
    val gaugeMax: Float? = null,
    val alarmSilenced: Boolean = false,
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

    companion object {
        /** First-run dashboard layout. No thresholds — user sets those per-tile. */
        val DEFAULTS = listOf(
            TileSpec(TileSource.RPM, TileUnit.RPM),
            TileSpec(TileSource.ECT, TileUnit.CELSIUS),
            TileSpec(TileSource.TPS, TileUnit.PERCENT),
            TileSpec(TileSource.AFR, TileUnit.RATIO),
        )
    }
}

enum class TileStyle { NUMERIC, LARGE, BAR, SWEEP }

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
        val value = outputMin + t * (outputMax - outputMin)
        // Clamp to the configured output range so sub-millivolt noise at
        // the sensor's zero-pressure floor doesn't extrapolate into small
        // negatives (e.g. -0.3 psi → "-0", -0.6 → "-1"), and a saturated
        // sensor doesn't run past its calibrated maximum. min/max keeps
        // reverse calibrations (outputMin > outputMax) working.
        val lo = kotlin.math.min(outputMin, outputMax)
        val hi = kotlin.math.max(outputMin, outputMax)
        return value.coerceIn(lo, hi)
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
