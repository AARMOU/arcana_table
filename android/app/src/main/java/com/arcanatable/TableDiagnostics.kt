@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arcanatable

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.app.Activity
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

/** Diagnostic log: recording events, zone-change tracking and exporting a report. */

internal fun TableController.recordDiagnostic(event: String, category: DiagnosticCategory? = null, private: Boolean = false) {
    // Own-card moves through hidden zones name cards the opponent cannot see; keep them out of exports.
    val isPrivate = private || (event.startsWith("Card move") && (" hand" in event || "library" in event))
    val entry = diagnosticLog.record(category ?: DiagnosticLog.categorize(event), event, isPrivate)
    diagnosticRevision++
    val timestamp = entry.timestamp()
    val safeEvent = if (isPrivate) "[private game detail]" else entry.message
    val contextLine = "$timestamp · $safeEvent · ${if (appInForeground) "foreground" else "background"} · ${if (argentumEnabled || roomHost != null || roomClient != null || cloudRoomClient != null) "online" else "offline"} · ${if (showOverview) "table" else "player view"} · P${(selectedPlayer + 1).coerceAtLeast(1)} · turn $turnNumber ${phaseSteps.getOrNull(phaseIndex).orEmpty()}"
    optionPrefs.edit().putString("diagnostic_events", diagnosticLog.serialize())
        .putString("last_diagnostic_context", contextLine).apply()
}

internal fun TableController.zoneCounts(player: TablePlayer): String = listOf(
    "library=${player.library.size}", "hand=${player.hand.size}", "battlefield=${player.battlefield.size}",
    "graveyard=${player.graveyard.size}", "exile=${player.exile.size}", "revealed=${player.revealed.size}"
).joinToString(", ")

internal fun TableController.zoneIdentitySignature(seats: List<TablePlayer>): String = seats.joinToString("|") { player ->
    listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed)
        .joinToString("/") { zone -> zone.map { it.id }.sorted().joinToString(",") }
}

internal fun TableController.zoneCards(player: TablePlayer): List<Pair<String, GameCard>> = buildList {
    player.library.forEach { add("library" to it) }
    player.hand.forEach { add("hand" to it) }
    player.battlefield.forEach { add("battlefield" to it) }
    player.graveyard.forEach { add("graveyard" to it) }
    player.exile.forEach { add("exile" to it) }
    player.revealed.forEach { add("revealed" to it) }
}

internal fun TableController.zoneIsPublic(zone: String): Boolean = zone in setOf("battlefield", "graveyard", "exile", "revealed")

internal fun TableController.logZoneSnapshotChange(seats: List<TablePlayer>, source: String) {
    val signature = zoneIdentitySignature(seats)
    if (signature == lastDiagnosticZoneSignature) return
    val previous = lastDiagnosticZoneSignature
    val previousSnapshot = lastDiagnosticZoneSnapshot
    lastDiagnosticZoneSignature = signature
    lastDiagnosticZoneSnapshot = seats
    if (previous == null) {
        recordDiagnostic("Zone baseline captured ($source): ${seats.mapIndexed { i, p -> "P${i + 1} ${zoneCounts(p)}" }.joinToString("; ")}")
        return
    }
    previousSnapshot?.let { oldSeats ->
        seats.forEachIndexed { index, player ->
            val oldPlayer = oldSeats.getOrNull(index) ?: return@forEachIndexed
            val oldCards = zoneCards(oldPlayer).groupBy { it.second.id }
            val newCards = zoneCards(player).groupBy { it.second.id }
            (oldCards.keys + newCards.keys).forEach { id ->
                val before = oldCards[id]?.singleOrNull() ?: return@forEach
                val after = newCards[id]?.singleOrNull() ?: return@forEach
                if (before.first == after.first || (before.second.isFaceDown || after.second.isFaceDown)) return@forEach
                val identityVisible = index == localSeat || zoneIsPublic(before.first) || zoneIsPublic(after.first) ||
                    (before.first == "hand" && oldPlayer.handRevealedToAll) || (after.first == "hand" && player.handRevealedToAll)
                if (identityVisible) {
                    recordDiagnostic("Card move ($source): P${index + 1} ${after.second.name} #$id ${before.first} → ${after.first}")
                }
            }
        }
    }
    val priorSeatSignatures = previous.split('|')
    val changed = seats.mapIndexedNotNull { index, player ->
        val currentSeat = listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed)
            .joinToString("/") { zone -> zone.map { it.id }.sorted().joinToString(",") }
        if (priorSeatSignatures.getOrNull(index) == currentSeat) null else {
            val allIds = listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed).flatten().map { it.id }
            val repeatedInstanceCount = allIds.groupingBy { it }.eachCount().count { it.value > 1 }
            "P${index + 1} ${zoneCounts(player)}, repeated instance IDs=$repeatedInstanceCount"
        }
    }
    if (changed.isNotEmpty()) recordDiagnostic("Zone state changed ($source): ${changed.joinToString("; ")}")
}

internal fun TableController.diagnosticReport(): String = buildString {
    appendLine("Arcana Table diagnostic report")
    appendLine("App version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
    appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Role: ${if (argentumEnabled) "Argentum client" else if (lobbyCreated) "host" else if (roomClient != null || cloudRoomClient != null) "client" else "offline"}")
    appendLine("Connection: ${if (argentumEnabled) argentumStatus.name.lowercase() else onlineStatus}")
    appendLine("Sync revision: not provided by the current room protocol; local inbound snapshot counter=$inboundSnapshotSequence")
    appendLine("Local seat: ${localSeat + 1}; seats present: ${players.size}")
    appendLine("App state: ${if (appInForeground) "foreground" else "background"}; view=${if (showOverview) "table" else "player"}; selectedSeat=${selectedPlayer + 1}; activeSeat=${activePlayer + 1}; phase=${phaseSteps.getOrNull(phaseIndex).orEmpty()}")
    appendLine("Game started: ${gameStartedOnline || localGameStarted}")
    appendLine("Lobby readiness: ${lobbyReadyPlayers.sorted().joinToString { (it + 1).toString() }.ifBlank { "none" }}")
    appendLine("Deck status: ${players.mapIndexed { index, player -> "P${index + 1}=${if (player.deckLoaded) "loaded" else "missing"}" }.joinToString()}")
    context.getSharedPreferences("arcana_crash", Context.MODE_PRIVATE).getString("last_crash", null)?.let { crash ->
        appendLine()
        val crashPrefs = context.getSharedPreferences("arcana_crash", Context.MODE_PRIVATE)
        appendLine("Crash time: ${crashPrefs.getString("last_crash_at", "unknown")}")
        appendLine("Action context: ${DiagnosticRedactor.redact(crashPrefs.getString("last_crash_context", "unknown").orEmpty())}")
        appendLine("Last uncaught app crash:")
        appendLine(DiagnosticRedactor.redact(crash))
    }
    appendLine()
    appendLine("Zone totals include counts only. Opponents' hidden card identities are excluded; own-deck duplicate summaries may include card names.")
    appendLine("Event log (server addresses, IPs, credentials, tokens, IDs and room codes redacted; private card details omitted):")
    appendLine(diagnosticLog.exportText(diagnosticExportSelection ?: diagnosticLog.entries))
}

internal fun TableController.exportDiagnosticLog() {
    recordDiagnostic("Diagnostic export requested")
    val filename = "ArcanaTable-diagnostics-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.txt"
    diagnosticExportLauncher.launch(filename)
}
