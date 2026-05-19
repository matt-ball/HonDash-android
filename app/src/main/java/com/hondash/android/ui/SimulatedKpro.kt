package com.hondash.android.ui

import com.hondash.android.kpro.KproConstants
import com.hondash.android.kpro.KproSnapshot
import com.hondash.android.kpro.VtecState
import kotlin.math.sin
import kotlin.math.PI

/**
 * Emits synthetic [KproSnapshot]s with values that drift through a plausible
 * range, so the UI can be exercised without a real ECU on the bus.
 *
 * Driven by elapsed seconds since simulation start; pure function, no state.
 */
internal object SimulatedKpro {

    fun sample(tSec: Double): KproSnapshot {
        // Slow warm-up of ECT: 25°C cold → 90°C, with a small ripple.
        val warmup = (1.0 - kotlin.math.exp(-tSec / 30.0)).coerceIn(0.0, 1.0)
        val ect = (25.0 + 65.0 * warmup + sin(tSec * 0.5) * 1.5).toInt()

        val rpmSwing = (3000.0 + 2500.0 * sin(tSec * 0.4)).coerceIn(800.0, 7500.0)

        // Eight independent analog channels, each drifting through the
        // 0.5–4.5 V band most industrial sensors live in, at a unique
        // frequency/phase so the UI can tell them apart at a glance.
        val analog = List(KproConstants.ANALOG_CHANNEL_COUNT) { ch ->
            val phase = ch * 0.7
            val freq = 0.25 + ch * 0.13
            val v = 2.5 + 2.0 * sin(tSec * freq + phase)
            v.toFloat().coerceIn(0f, 5f)
        }

        val rpm = rpmSwing.toInt()
        val mapBar = (0.3 + 0.7 * (rpmSwing - 800.0) / 6700.0).toFloat()

        return KproSnapshot(
            rpm = rpm,
            tps = ((rpmSwing - 800.0) / 6700.0 * 100.0).toInt().coerceIn(0, 100),
            vssKmh = (rpm / 50).coerceIn(0, 200),
            vssMph = (rpm / 80).coerceIn(0, 130),
            afr = (14.7f + (sin(tSec * 0.8) * 0.6).toFloat()),
            lambda = 1.0f,
            ectCelsius = ect,
            ectFahrenheit = (ect * 9 / 5 + 32),
            iatCelsius = 28,
            iatFahrenheit = 82,
            batteryVolts = (13.8f + (sin(tSec * 0.2) * 0.2).toFloat()),
            mapBar = mapBar,
            mapPsi = mapBar * 14.5038f,
            camDegrees = 12f,
            gear = (1 + ((tSec / 5.0) % 5).toInt()).coerceIn(1, 5),
            vtec = if (rpm > 5500) VtecState.ON else VtecState.OFF,
            ethanolPercent = 10,
            fuelTempCelsius = 30,
            ignition = true,
            mil = false,
            firmware = "SIM",
            serial = 0,
            analogVolts = analog,
        )
    }

    @Suppress("unused")
    private const val TWO_PI = 2.0 * PI
}
