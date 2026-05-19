package com.hondash.android.ui.tiles

/** Units a tile can present its value in. */
enum class TileUnit(val symbol: String) {
    NONE(""),
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    PSI("psi"),
    BAR("bar"),
    KMH("km/h"),
    MPH("mph"),
    VOLT("V"),
    PERCENT("%"),
    RPM("rpm"),
    RATIO(":1"),
    DEG("°"),
}

/** What units each [TileSource] can be shown in. First entry is the default. */
object TileUnits {
    fun availableFor(source: TileSource): List<TileUnit> = when (source) {
        TileSource.RPM -> listOf(TileUnit.RPM)
        TileSource.TPS, TileSource.ETHANOL -> listOf(TileUnit.PERCENT)
        TileSource.SPEED -> listOf(TileUnit.MPH, TileUnit.KMH)
        TileSource.AFR -> listOf(TileUnit.RATIO)
        TileSource.LAMBDA -> listOf(TileUnit.NONE)
        TileSource.ECT, TileSource.IAT, TileSource.FUEL_TEMP ->
            listOf(TileUnit.CELSIUS, TileUnit.FAHRENHEIT)
        TileSource.BATTERY -> listOf(TileUnit.VOLT)
        TileSource.MAP -> listOf(TileUnit.PSI, TileUnit.BAR)
        TileSource.GEAR, TileSource.VTEC, TileSource.IGNITION, TileSource.MIL ->
            listOf(TileUnit.NONE)
        TileSource.AN0, TileSource.AN1, TileSource.AN2, TileSource.AN3,
        TileSource.AN4, TileSource.AN5, TileSource.AN6, TileSource.AN7 ->
            listOf(TileUnit.VOLT, TileUnit.PSI, TileUnit.BAR)
    }

    fun defaultFor(source: TileSource): TileUnit = availableFor(source).first()
}
