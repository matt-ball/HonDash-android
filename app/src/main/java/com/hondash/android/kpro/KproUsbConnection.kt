package com.hondash.android.kpro

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

/**
 * Thin wrapper around an open [UsbDeviceConnection] that exposes
 * the bulk IN/OUT endpoints used to talk to the K-Pro V4.
 */
internal class KproUsbConnection private constructor(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint,
) {

    /** Write a single command byte to the bulk OUT endpoint. */
    fun writeCommand(cmd: Byte, timeoutMs: Int = 1000): Boolean {
        val buf = byteArrayOf(cmd)
        return connection.bulkTransfer(bulkOut, buf, buf.size, timeoutMs) >= 0
    }

    /**
     * Read up to [maxLen] bytes from the bulk IN endpoint. Returns
     * an empty array on error or timeout.
     */
    fun read(maxLen: Int, timeoutMs: Int = 1000): ByteArray {
        val buf = ByteArray(maxLen)
        val n = connection.bulkTransfer(bulkIn, buf, buf.size, timeoutMs)
        return if (n > 0) buf.copyOf(n) else ByteArray(0)
    }

    fun close() {
        try {
            connection.releaseInterface(iface)
        } finally {
            connection.close()
        }
    }

    companion object {
        /**
         * Open the K-Pro device and claim its first interface,
         * returning a ready-to-use connection or null on failure.
         */
        fun open(manager: UsbManager, device: UsbDevice): KproUsbConnection? {
            if (device.interfaceCount == 0) return null
            val iface = device.getInterface(0)

            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                when (ep.direction) {
                    UsbConstants.USB_DIR_IN -> if (bulkIn == null) bulkIn = ep
                    UsbConstants.USB_DIR_OUT -> if (bulkOut == null) bulkOut = ep
                }
            }
            if (bulkIn == null || bulkOut == null) return null

            val connection = manager.openDevice(device) ?: return null
            if (!connection.claimInterface(iface, true)) {
                connection.close()
                return null
            }
            return KproUsbConnection(connection, iface, bulkIn, bulkOut)
        }
    }
}
