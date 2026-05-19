package com.hondash.android.ui.tiles

import com.hondash.android.kpro.KproSnapshot
import com.hondash.android.kpro.VtecState

/** What a tile shows for a particular [KproSnapshot]. */
data class TileValue(val text: String, val numeric: Float?)

/** Convert the latest snapshot into this tile's display value. */
fun TileSpec.read(snap: KproSnapshot): TileValue = when (source) {
    TileSource.RPM -> num(snap.rpm)
    TileSource.TPS -> num(snap.tps)
    TileSource.SPEED -> if (unit == TileUnit.KMH) num(snap.vssKmh) else num(snap.vssMph)
    TileSource.AFR -> dec(snap.afr)
    TileSource.LAMBDA -> dec(snap.lambda)
    TileSource.ECT -> if (unit == TileUnit.FAHRENHEIT) num(snap.ectFahrenheit) else num(snap.ectCelsius)
    TileSource.IAT -> if (unit == TileUnit.FAHRENHEIT) num(snap.iatFahrenheit) else num(snap.iatCelsius)
    TileSource.FUEL_TEMP -> {
        val c = snap.fuelTempCelsius
        if (unit == TileUnit.FAHRENHEIT) num(c * 9 / 5 + 32) else num(c)
    }
    TileSource.BATTERY -> dec(snap.batteryVolts)
    TileSource.MAP -> if (unit == TileUnit.BAR) dec(snap.mapBar) else dec(snap.mapPsi)
    TileSource.GEAR -> TileValue(
        if (snap.gear == 0) "N" else snap.gear.toString(),
        snap.gear.toFloat(),
    )
    TileSource.VTEC -> TileValue(snap.vtec.name, if (snap.vtec == VtecState.ON) 1f else 0f)
    TileSource.IGNITION -> bool(snap.ignition)
    TileSource.MIL -> bool(snap.mil)
    TileSource.ETHANOL -> num(snap.ethanolPercent)
    TileSource.AN0 -> analog(snap, 0)
    TileSource.AN1 -> analog(snap, 1)
    TileSource.AN2 -> analog(snap, 2)
    TileSource.AN3 -> analog(snap, 3)
    TileSource.AN4 -> analog(snap, 4)
    TileSource.AN5 -> analog(snap, 5)
    TileSource.AN6 -> analog(snap, 6)
    TileSource.AN7 -> analog(snap, 7)
}

private fun TileSpec.analog(snap: KproSnapshot, ch: Int): TileValue {
    val v = snap.analogVolts.getOrElse(ch) { 0f }
    // Voltage display always shows the raw reading; calibration only
    // affects converted physical units.
    if (unit == TileUnit.VOLT) return TileValue("%.2f".format(v), v)

    val cal = calibration
    val converted = when {
        cal != null -> cal.apply(v)
        unit == TileUnit.PSI -> (v / 5f) * 100f       // legacy default
        unit == TileUnit.BAR -> (v / 5f) * 6.895f     // legacy default
        else -> v
    }
    val text = if (unit == TileUnit.BAR) "%.1f".format(converted)
               else "%.0f".format(converted)
    return TileValue(text, converted)
}

private fun num(v: Int) = TileValue(v.toString(), v.toFloat())
private fun dec(v: Float) = TileValue("%.1f".format(v), v)
private fun bool(v: Boolean) = TileValue(if (v) "ON" else "OFF", if (v) 1f else 0f)
