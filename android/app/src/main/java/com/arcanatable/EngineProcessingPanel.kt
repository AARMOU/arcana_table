package com.arcanatable

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Actual elapsed time, never an invented percent or estimated completion time. */
internal data class EngineProcessingInfo(val label: String, val elapsedSeconds: Long, val lastCompletedMs: Long?)

internal fun processingInfo(
    isOffline: Boolean,
    awaitingAction: Boolean,
    nonInteractiveEngineUpdate: Boolean,
    elapsedMs: Long,
    lastCompletedMs: Long?,
): EngineProcessingInfo? {
    if (!awaitingAction && !nonInteractiveEngineUpdate) return null
    val label = when {
        isOffline && nonInteractiveEngineUpdate -> "Argentum is advancing the game"
        isOffline -> "Argentum is resolving your action"
        else -> "Waiting for Argentum to confirm your action"
    }
    return EngineProcessingInfo(label, elapsedMs.coerceAtLeast(0L) / 1000, lastCompletedMs)
}

@Composable
internal fun EngineProcessingPanel(
    offline: Boolean,
    awaitingAction: Boolean,
    nonInteractiveEngineUpdate: Boolean,
    lastCompletedMs: Long?,
    modifier: Modifier = Modifier,
) {
    val processing = awaitingAction || nonInteractiveEngineUpdate
    val start = remember(processing, nonInteractiveEngineUpdate) { SystemClock.elapsedRealtime() }
    var now by remember(processing, nonInteractiveEngineUpdate) { mutableLongStateOf(start) }
    LaunchedEffect(processing, nonInteractiveEngineUpdate) {
        while (processing) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val data = processingInfo(offline, awaitingAction, nonInteractiveEngineUpdate, now - start, lastCompletedMs)
        ?: return
    Column(
        modifier.background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, CounterGold, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(data.label, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Elapsed: ${data.elapsedSeconds}s · No artificial action/trigger limits",
            color = CounterGold, fontSize = 10.sp)
        data.lastCompletedMs?.let {
            Text("Previous engine update: ${it}ms (not an ETA)", color = Muted, fontSize = 9.sp)
        }
    }
}
