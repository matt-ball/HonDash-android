package com.hondash.android.ui.tiles

/**
 * All values that can be displayed on a tile.
 *
 * [defaultLabel] is what we show when the user has not given the
 * tile a custom name. [configurable] indicates that the *meaning*
 * of the channel is up to the user (the analog inputs), so the
 * UI offers a free-form label override.
 */
enum class TileSource(
    val defaultLabel: String,
    val configurable: Boolean = false,
) {
    RPM("RPM"),
    TPS("TPS"),
    SPEED("Speed"),
    AFR("AFR"),
    LAMBDA("Lambda"),
    ECT("ECT"),
    IAT("IAT"),
    BATTERY("Battery"),
    MAP("MAP"),
    GEAR("Gear"),
    VTEC("VTEC"),
    ETHANOL("Ethanol"),
    FUEL_TEMP("Fuel T"),
    IGNITION("Ign"),
    MIL("MIL"),
    AN0("AN0", configurable = true),
    AN1("AN1", configurable = true),
    AN2("AN2", configurable = true),
    AN3("AN3", configurable = true),
    AN4("AN4", configurable = true),
    AN5("AN5", configurable = true),
    AN6("AN6", configurable = true),
    AN7("AN7", configurable = true);
}
