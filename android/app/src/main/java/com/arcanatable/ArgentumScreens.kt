@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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

/**
 * Card action sheet for Argentum games: the card large, its rules text, and every action Argentum
 * offers for it right now (or all legal actions when opened from a menu). Unavailable actions are
 * labelled instead of hidden so the player can see why nothing happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArgentumActionSheet(card: GameCard?, choices: List<JSONObject>, hidManaAbilities: Boolean, busy: Boolean, manaChoices: List<JSONObject> = emptyList(), stackCount: (JSONObject) -> Int = { 1 }, onChooseMany: (JSONObject, Int) -> Unit = { _, _ -> }, onChoose: (JSONObject) -> Unit, onDismiss: () -> Unit, onSecretNudge: () -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            if (card != null) Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(118.dp).aspectRatio(0.716f).clip(RoundedCornerShape(8.dp)).background(card.color)) {
                    val url = card.imageUrl
                    if (url != null && !card.isFaceDown) CardArt(url, Modifier.fillMaxSize(), fallbackName = card.name, fallbackType = card.type)
                    else Text(card.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(card.name, color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    card.manaCost?.takeIf { it.isNotBlank() }?.let { ManaCostPips(it) }
                    Text(card.type, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    if (card.power != null && card.toughness != null) Text("${card.power}/${card.toughness}" + if (card.damage > 0) " · ${card.damage} damage" else "", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                    card.oracleText?.let { ManaSymbolText(it, SoftWhite, 12.sp, Modifier.padding(top = 6.dp)) }
                }
            }
            Text(if (card == null) "ACTIONS" else if (choices.isEmpty() && manaChoices.isEmpty()) "NOTHING TO DO WITH THIS CARD RIGHT NOW" else if (choices.isEmpty()) "MANA" else "WHAT YOU CAN DO NOW",
                color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            if (choices.isEmpty() && manaChoices.isEmpty()) Text(if (card == null) "Argentum isn't offering you any actions at the moment." else "Argentum offers no action for this card at this moment (wrong timing, no mana, or not your priority).", color = Muted, fontSize = 12.sp)
            if (hidManaAbilities) Text("Mana is paid automatically: your lands and mana sources are tapped for you when you cast or activate.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            // Things you can do first; ones you can't afford right now go to the bottom.
            choices.sortedBy { !it.optBoolean("isAffordable", true) }.forEach { info ->
                val unsupported = ArgentumActions.needsHandlingReason(info)
                val affordable = info.optBoolean("isAffordable", true)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (unsupported == null && affordable) PanelLight else Panel)
                        .border(1.dp, if (unsupported == null && affordable) Mint.copy(alpha = 0.5f) else Muted.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .combinedClickable(enabled = !busy, onClick = { onChoose(info) })
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(if (ArgentumActions.isSacrificeMana(info)) "Sacrifice for mana" else if (info.optJSONObject("action")?.optString("type") == "PassPriority") "Pass" else info.optString("description", info.optJSONObject("action")?.optString("type") ?: "Action"), color = if (unsupported == null) SoftWhite else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (ArgentumActions.isSacrificeMana(info)) Text(info.optString("description"), color = Muted, fontSize = 11.sp)
                    info.optString("manaCostString").takeIf { it.isNotBlank() && it != "null" }?.let { ManaCostPips(it) }
                    val copies = if (unsupported == null && affordable) stackCount(info) else 1
                    if (copies > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (copies > 5) OutlinedButton(enabled = !busy, onClick = { onChooseMany(info, 5) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("USE 5", color = Mint, fontWeight = FontWeight.Black) }
                        OutlinedButton(enabled = !busy, onClick = { onChooseMany(info, copies) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("USE ALL ($copies)", color = Mint, fontWeight = FontWeight.Black) }
                    }
                    when {
                        unsupported != null -> Text("NEEDS HANDLING · $unsupported", color = CounterGold, fontSize = 10.sp)
                        !affordable -> Text("Not enough mana right now.", color = Muted, fontSize = 10.sp)
                        info.optBoolean("requiresTargets") || info.optBoolean("hasXCost") -> Text("You'll choose targets or X next.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            // Float mana: this source's mana abilities, last and quieter (auto-pay still taps for you).
            if (manaChoices.isNotEmpty()) {
                if (choices.isNotEmpty()) Text("ADD MANA (FLOATS UNTIL THE STEP ENDS)", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                else Text("Floats in your mana pool until the step ends. Casting uses floating mana first.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    manaChoices.forEach { info ->
                        OutlinedButton(enabled = !busy, onClick = { onChoose(info) }, modifier = Modifier.heightIn(min = 44.dp).testTag("float-mana"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Muted.copy(alpha = 0.35f))) {
                            ManaSymbolText(floatManaLabel(info.optString("description")), Muted, 12.sp)
                        }
                    }
                }
            }
            // Two tiny invisible spots on this row: left, then right within 1 second, sends the secret nudge.
            var leftTapAt by remember { mutableStateOf(0L) }
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                val spot = 28.dp
                Box(Modifier.offset(x = maxWidth * 0.2f - spot / 2).align(Alignment.CenterStart).size(spot)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { leftTapAt = System.currentTimeMillis() })
                Box(Modifier.offset(x = maxWidth * 0.67f - spot / 2).align(Alignment.CenterStart).size(spot)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        if (leftTapAt > 0 && System.currentTimeMillis() - leftTapAt <= 1_000) { leftTapAt = 0; onSecretNudge() } else leftTapAt = 0
                    })
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp)) { Text("CLOSE", color = Muted) }
            }
        }
    }
}

/** Full-screen opening hand (keep / mulligan) and bottom-card selection for Argentum games. */
@Composable
internal fun ArgentumOpeningHandScreen(
    cards: List<GameCard>, title: String, subtitle: String,
    selectable: Boolean, selection: Set<Int>, onToggle: (GameCard) -> Unit,
    primaryLabel: String, primaryEnabled: Boolean, onPrimary: () -> Unit,
    secondaryLabel: String?, secondaryEnabled: Boolean, onSecondary: () -> Unit,
) {
    var inspected by remember { mutableStateOf<GameCard?>(null) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(18.dp)) {
                Text("ARGENTUM", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(title, color = SoftWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val grid = HandLayout.compute(cards.size, maxWidth.value, maxHeight.value, HandArrangement.AUTO_FIT, 1.25f)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(HandLayout.GAP.dp)) {
                        cards.chunked(grid.columns.coerceAtLeast(1)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(HandLayout.GAP.dp)) {
                                row.forEach { card ->
                                    val chosen = card.id in selection
                                    Box(Modifier.width(grid.cardWidth.dp)) {
                                        CardFace(card, Modifier.width(grid.cardWidth.dp), onClick = { if (selectable) onToggle(card) else inspected = card },
                                            onLongClick = { inspected = card }, showCounters = false, glow = chosen)
                                        if (chosen) Text("BOTTOM", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                            modifier = Modifier.align(Alignment.TopCenter).padding(4.dp).clip(RoundedCornerShape(5.dp)).background(Mint).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Text(if (selectable) "Long-press a card to read it." else "Tap a card to read it.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (secondaryLabel != null) OutlinedButton(onClick = onSecondary, enabled = secondaryEnabled, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text(secondaryLabel, fontWeight = FontWeight.Bold) }
                    Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(if (secondaryLabel != null) 1.4f else 1f).heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text(primaryLabel, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
    inspected?.let { card ->
        AlertDialog(
            onDismissRequest = { inspected = null },
            title = { Text(card.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    card.manaCost?.let { ManaCostPips(it) }
                    Text(card.type, color = Muted, fontSize = 12.sp)
                    if (card.power != null && card.toughness != null) Text("${card.power}/${card.toughness}", color = SoftWhite, fontWeight = FontWeight.Bold)
                    card.oracleText?.let { ManaSymbolText(it, SoftWhite, 13.sp, Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { inspected = null }) { Text("CLOSE") } }
        )
    }
}


/** "{T}: Add {G}." → "Add {G}"; other mana abilities keep their text without the trailing period. */
internal fun floatManaLabel(description: String): String {
    val add = Regex("Add [^.]*", RegexOption.IGNORE_CASE).find(description)?.value
    return (if (add != null && description.trim().startsWith("{T}:")) add else description).trim().trimEnd('.')
}
