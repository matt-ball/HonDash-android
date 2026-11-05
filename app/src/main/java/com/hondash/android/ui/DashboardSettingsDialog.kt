package com.hondash.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Section slots for the dashboard features delivered after the layout foundation. */
@Composable
fun DashboardSettingsDialog(
    onDismiss: () -> Unit,
    shiftLight: @Composable () -> Unit = {},
    theme: @Composable () -> Unit = {},
    alerts: @Composable () -> Unit = {},
    layout: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dashboard settings") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Shift light", style = MaterialTheme.typography.titleSmall)
                shiftLight()
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                theme()
                Text("Alerts", style = MaterialTheme.typography.titleSmall)
                alerts()
                Text("Layout", style = MaterialTheme.typography.titleSmall)
                layout()
            }
        },
    )
}
