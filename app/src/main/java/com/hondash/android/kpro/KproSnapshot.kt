package com.hondash.android.kpro

/** Computed snapshot of the latest K-Pro values. */
data class KproSnapshot(
    val rpm: Int,
    val tps: Int,            // 0..100 %
    val vssKmh: Int,
    val vssMph: Int,
    val afr: Float,
    val lambda: Float,
    val ectCelsius: Int,
    val ectFahrenheit: Int,
    val iatCelsius: Int,
    val iatFahrenheit: Int,
    val batteryVolts: Float,
    val mapBar: Float,
    val mapPsi: Float,
    val camDegrees: Float,
    val gear: Int,
    val vtec: VtecState,
    val ethanolPercent: Int,
    val fuelTempCelsius: Int,
    val ignition: Boolean,
    val mil: Boolean,
    val firmware: String,
    val serial: Int,
    /**
     * Raw 0..5 V reading per analog channel, indexed AN0..AN7.
     * Held as a [List] (not [FloatArray]) so the enclosing data
     * class participates in structural equality — otherwise every
     * snapshot would compare as different and force recomposition.
     */
    val analogVolts: List<Float>,
) {
    companion object {
        val EMPTY = KproSnapshot(
            rpm = 0, tps = 0, vssKmh = 0, vssMph = 0,
            afr = 0f, lambda = 0f,
            ectCelsius = 0, ectFahrenheit = 0,
            iatCelsius = 0, iatFahrenheit = 0,
            batteryVolts = 0f,
            mapBar = 0f, mapPsi = 0f,
            camDegrees = 0f, gear = 0,
            vtec = VtecState.OFF,
            ethanolPercent = 0, fuelTempCelsius = 0,
            ignition = false, mil = false,
            firmware = "-", serial = 0,
            analogVolts = List(KproConstants.ANALOG_CHANNEL_COUNT) { 0f },
        )
    }
}

enum class VtecState { OFF, ON, MALFUNCTION }
