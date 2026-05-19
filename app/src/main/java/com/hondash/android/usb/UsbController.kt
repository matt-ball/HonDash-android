package com.hondash.android.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hondash.android.kpro.KproConstants

/**
 * Centralises USB device lookup and the runtime permission flow
 * for the K-Pro V4. Use [findKpro] to locate an attached device
 * and [requestPermission] to prompt the user.
 */
class UsbController(private val context: Context) {

    private val manager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Return the first attached K-Pro V4 device, or null. */
    fun findKpro(): UsbDevice? = manager.deviceList.values.firstOrNull(::isKpro)

    fun isKpro(device: UsbDevice): Boolean =
        device.vendorId == KproConstants.VENDOR_ID &&
                device.productId == KproConstants.PRODUCT_ID

    fun hasPermission(device: UsbDevice): Boolean = manager.hasPermission(device)

    val usbManager: UsbManager get() = manager

    /**
     * Trigger Android's USB permission dialog. [onResult] is
     * invoked once with the user's decision; the receiver
     * unregisters itself afterward.
     */
    fun requestPermission(device: UsbDevice, onResult: (granted: Boolean) -> Unit) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                ctx.unregisterReceiver(this)
                onResult(granted)
            }
        }
        val filter = IntentFilter(ACTION_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        manager.requestPermission(device, pending)
    }

    companion object {
        private const val ACTION_PERMISSION = "com.hondash.android.USB_PERMISSION"
    }
}
