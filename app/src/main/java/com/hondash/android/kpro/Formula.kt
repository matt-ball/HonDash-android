package com.hondash.android.kpro

/**
 * Unit conversions and the K-Pro temperature regression.
 * Ported from src/devices/formula.py.
 */
internal object Formula {

    fun celsiusToFahrenheit(c: Double): Double = c * 1.8 + 32.0

    fun kmhToMph(kmh: Double): Double = kmh * 0.6214

    fun barToPsi(bar: Double): Double = bar * 14.503773773

    /**
     * Convert the raw byte the K-Pro reports for ECT/IAT into
     * celsius. Linear regression coefficients come straight from
     * the Python implementation.
     */
    fun kproTempCelsius(raw: Int): Double {
        val x = raw.toDouble()
        return (-2.7168631716148286e0 * x
                + 3.5250001884568352e-2 * Math.pow(x, 2.0)
                - 4.6668312213461976e-4 * Math.pow(x, 3.0)
                + 6.2314622546038854e-6 * Math.pow(x, 4.0)
                - 5.5155685454381802e-8 * Math.pow(x, 5.0)
                + 2.6888773098684158e-10 * Math.pow(x, 6.0)
                - 6.5904712075799765e-13 * Math.pow(x, 7.0)
                + 6.3467552343485511e-16 * Math.pow(x, 8.0)
                + 1.5037636674235824e2)
    }
}
