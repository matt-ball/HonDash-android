package com.hondash.android.ui.tiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hondash.android.kpro.KproSnapshot
import kotlinx.coroutines.delay

private val NORMAL_BG = Color(0xFF1E1E1E)
private val WARN_BG = Color(0xFFB45309)   // amber 700
private val ALARM_BG = Color(0xFFB91C1C)  // red 700

/**
 * A single configurable tile. Severity colouring via
 * [TileSpec.severity]; alarm state hard-flashes between full red
 * and the neutral dark at [FLASH_INTERVAL_MS] cadence — no tween,
 * meant to read like a rev-limiter light, not a fade.
 *
 * Long-press fires [onLongPress] so the host can open the config dialog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ValueTile(
    spec: TileSpec,
    snapshot: KproSnapshot,
    live: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = spec.read(snapshot)
    // Only evaluate thresholds when there's a real source of data; otherwise
    // the all-zero placeholder snapshot would trip every low-threshold alarm.
    val severity = if (live) spec.severity(value.numeric) else TileSeverity.NORMAL

    // Discrete on/off toggle for the alarm flash; restarted whenever the
    // tile transitions into ALARM so successive alarms always start lit.
    var flashOn by remember { mutableStateOf(true) }
    LaunchedEffect(severity) {
        if (severity == TileSeverity.ALARM) {
            flashOn = true
            while (true) {
                delay(FLASH_INTERVAL_MS)
                flashOn = !flashOn
            }
        } else {
            flashOn = true
        }
    }

    val container = when (severity) {
        TileSeverity.NORMAL -> NORMAL_BG
        TileSeverity.WARNING -> WARN_BG
        TileSeverity.ALARM -> if (flashOn) ALARM_BG else NORMAL_BG
    }
    // Tile background is hard-coded dark in every severity state, so the
    // text must be light regardless of the app's MaterialTheme palette.
    val onContainer = Color.White
    val labelColor = when (severity) {
        TileSeverity.NORMAL -> Color(0xFFB0B0B0)
        else -> Color.White.copy(alpha = 0.85f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        // Position label and value independently so the value can occupy
        // the full vertical centre of the tile instead of being pushed down
        // by a `Column` whose first child is the label.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                spec.displayLabel,
                color = labelColor,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            // Value + unit share a single baseline so the unit doesn't
            // claim its own vertical line.
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    value.text,
                    color = onContainer,
                    fontSize = 72.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (spec.unit.symbol.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        spec.unit.symbol,
                        color = labelColor,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        // Nudge the unit up so it sits under the value's
                        // cap-height, not its descender.
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }
    }
}

private const val FLASH_INTERVAL_MS = 250L

/** Placeholder tile shown after the last configured tile; tap to add a new one. */
@Composable
fun AddTileSlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}
