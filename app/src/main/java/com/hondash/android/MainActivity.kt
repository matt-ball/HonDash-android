package com.hondash.android

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.hondash.android.ui.DashboardScreen
import com.hondash.android.ui.DashboardViewModel
import com.hondash.android.ui.theme.HonDashTheme

/**
 * Single-Activity entry point. Renders the Compose dashboard and
 * forwards USB attach intents to the [DashboardViewModel].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HonDashTheme {
                DashboardScreen(viewModel)
            }
        }
        handleUsbIntent(intent)
        // Cover the case where the K-Pro is already plugged in
        // and the activity is launched from the launcher icon.
        viewModel.tryConnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        viewModel.tryConnect(device)
    }
}
