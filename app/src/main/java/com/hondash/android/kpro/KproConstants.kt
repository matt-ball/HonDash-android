package com.hondash.android.kpro

/**
 * K-Pro V4 USB protocol constants. Ported from
 * src/devices/kpro/constants.py (KPRO4_* entries only).
 *
 * Each command writes one byte to the bulk OUT endpoint; the
 * response is read from the bulk IN endpoint and parsed at the
 * indexes below.
 */
internal object KproConstants {
    const val VENDOR_ID = 0x1C40
    const val PRODUCT_ID = 0x0434

    // Commands (single byte each)
    const val CMD_40: Byte = 0x40
    const val CMD_60: Byte = 0x60
    const val CMD_61: Byte = 0x61
    const val CMD_62: Byte = 0x62
    const val CMD_65: Byte = 0x65

    // Response from CMD_40 (data4)
    const val ECU_TYPE = 10
    const val IGN = 15
    const val SERIAL_1 = 4
    const val SERIAL_2 = 5
    const val FIRM_1 = 6
    const val FIRM_2 = 7

    // Response from CMD_60 (data0)
    const val TPS = 5
    const val AFR_1 = 16
    const val AFR_2 = 17
    const val AFR_CMD_1 = 18
    const val AFR_CMD_2 = 19
    const val VSS = 4
    const val RPM_1 = 2
    const val RPM_2 = 3
    const val MAP = 6
    const val CAM = 8
    const val GEAR = 35
    const val SWITCHES = 31    // EPS, SCS, RVSLCK, BKSW, ACSW, ACCL, FLR, FANC bitfield

    // Response from CMD_61 (data1)
    const val ECT = 2
    const val IAT = 3
    const val BAT = 4

    // Response from CMD_65 (data3)
    const val VTEC_BITS = 30   // VTP/VTS/MIL share this byte
    const val ETH = 98
    const val FLT = 99

    /**
     * Indexes of the high/low bytes of analog inputs AN0..AN7
     * inside the data3 buffer. Each pair is combined as
     * (hi shl 8) or lo, then mapped 0..4096 → 0..5 V.
     */
    val ANALOG_HI_INDEXES = intArrayOf(67, 69, 71, 73, 75, 77, 79, 81)
    val ANALOG_LO_INDEXES = intArrayOf(66, 68, 70, 72, 74, 76, 78, 80)
    const val ANALOG_CHANNEL_COUNT = 8
}
