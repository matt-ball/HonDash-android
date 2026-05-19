package com.hondash.android.kpro

import com.hondash.android.kpro.KproConstants as K
import kotlin.math.roundToInt

/**
 * K-Pro V4 reader. Owns a [KproUsbConnection], runs the bulk
 * read cycle on whatever thread calls [pollOnce], and exposes a
 * [snapshot] computed from the latest buffers.
 *
 * Ported from the KPRO4 branches of src/devices/kpro/kpro.py.
 */
internal class KproV4(private val conn: KproUsbConnection) {

    @Volatile private var data0: ByteArray = ByteArray(0)
    @Volatile private var data1: ByteArray = ByteArray(0)
    @Volatile private var data3: ByteArray = ByteArray(0)
    @Volatile private var data4: ByteArray = ByteArray(0)

    /** Run one full read cycle (cmd 0x40, 0x60, 0x61, 0x62, 0x65). */
    fun pollOnce(): Boolean {
        if (!exchange(K.CMD_40, 1024) { data4 = it }) return false
        if (!exchange(K.CMD_60, 1024) { data0 = it }) return false
        if (!exchange(K.CMD_61, 1024) { data1 = it }) return false
        if (!exchange(K.CMD_62, 1024) { /* data2 unused for MVP */ }) return false
        if (!exchange(K.CMD_65, 128)  { data3 = it }) return false
        return true
    }

    fun close() = conn.close()

    private inline fun exchange(cmd: Byte, maxLen: Int, store: (ByteArray) -> Unit): Boolean {
        if (!conn.writeCommand(cmd)) return false
        val resp = conn.read(maxLen)
        if (resp.isEmpty()) return false
        store(resp)
        return true
    }

    /** Build a fully-decoded snapshot from the most recent buffers. */
    fun snapshot(): KproSnapshot {
        val rpm = (((data0.u(K.RPM_2) shl 8) or data0.u(K.RPM_1)) * 0.25).toInt()
        val tps = ((data0.u(K.TPS) - 21).coerceIn(0, 208) * 100 / 208)
        val vssKmh = data0.u(K.VSS)
        val vssMph = Formula.kmhToMph(vssKmh.toDouble()).toInt()

        val afrDen = (data0.u(K.AFR_2) shl 8) or data0.u(K.AFR_1)
        val lambda = if (afrDen == 0) 0.0 else 32768.0 / afrDen
        val afr = lambda * 14.7

        val ectC = Formula.kproTempCelsius(data1.u(K.ECT)).roundToInt()
        val iatC = Formula.kproTempCelsius(data1.u(K.IAT)).roundToInt()
        val battery = (data1.u(K.BAT) * 0.1f)

        val mapBar = data0.u(K.MAP) / 100.0
        val mapPsi = Formula.barToPsi(mapBar)

        val camDeg = ((data0.u(K.CAM) - 40) * 0.5f)
        val gear = data0.u(K.GEAR)

        val vtecByte = data3.u(K.VTEC_BITS)
        val vtp = (vtecByte and 0x01) != 0
        val vts = (vtecByte and 0x02) != 0
        val vtec = when {
            vts && vtp -> VtecState.ON
            vts xor vtp -> VtecState.MALFUNCTION
            else -> VtecState.OFF
        }
        val mil = data3.u(K.VTEC_BITS) >= 36

        val ign = data4.u(K.IGN, default = 0) == 1
        val firmware = "%d.%02d".format(
            data4.u(K.FIRM_2, default = 0),
            data4.u(K.FIRM_1, default = 0),
        )
        val serial = (data4.u(K.SERIAL_2, default = 0) shl 8) or data4.u(K.SERIAL_1, default = 0)

        return KproSnapshot(
            rpm = rpm,
            tps = tps,
            vssKmh = vssKmh,
            vssMph = vssMph,
            afr = afr.toFloat(),
            lambda = lambda.toFloat(),
            ectCelsius = ectC,
            ectFahrenheit = Formula.celsiusToFahrenheit(ectC.toDouble()).roundToInt(),
            iatCelsius = iatC,
            iatFahrenheit = Formula.celsiusToFahrenheit(iatC.toDouble()).roundToInt(),
            batteryVolts = battery,
            mapBar = mapBar.toFloat(),
            mapPsi = mapPsi.toFloat(),
            camDegrees = camDeg,
            gear = gear,
            vtec = vtec,
            ethanolPercent = data3.u(K.ETH),
            fuelTempCelsius = data3.u(K.FLT),
            ignition = ign,
            mil = mil,
            firmware = firmware,
            serial = serial,
            analogVolts = List(K.ANALOG_CHANNEL_COUNT) { analogVoltage(it) },
        )
    }

    /**
     * Voltage on analog input [channel] (0..7), reconstructed
     * from the 12-bit ADC pair stored in data3 and scaled to
     * 0..5 V. Mirrors the KPRO4 branch of analog_input() in
     * src/devices/kpro/kpro.py.
     */
    private fun analogVoltage(channel: Int): Float {
        val hi = data3.u(K.ANALOG_HI_INDEXES[channel])
        val lo = data3.u(K.ANALOG_LO_INDEXES[channel])
        val raw = ((hi shl 8) or lo).coerceIn(0, 4096)
        return raw / 4096f * 5f
    }

    /** Read byte at [index] as an unsigned 0..255 int, or [default]. */
    private fun ByteArray.u(index: Int, default: Int = 0): Int =
        if (index in indices) this[index].toInt() and 0xFF else default
}
