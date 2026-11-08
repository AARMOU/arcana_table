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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.semantics.selected
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

internal data class CardPreview(val playerIndex: Int, val card: GameCard, val fromHand: Boolean, val canManipulate: Boolean = true)
internal val debugCardPool = listOf(
    "Sol Ring" to "Artifact", "Arcane Signet" to "Artifact", "Command Tower" to "Land", "Island" to "Basic Land — Island", "Forest" to "Basic Land — Forest", "Plains" to "Basic Land — Plains", "Mountain" to "Basic Land — Mountain", "Swamp" to "Basic Land — Swamp",
    "Swords to Plowshares" to "Instant", "Counterspell" to "Instant", "Cultivate" to "Sorcery", "Beast Within" to "Instant", "Lightning Bolt" to "Instant", "Rhystic Study" to "Enchantment", "Smothering Tithe" to "Enchantment", "Llanowar Elves" to "Creature — Elf", "Sakura-Tribe Elder" to "Creature — Snake", "Mulldrifter" to "Creature — Elemental", "Solemn Simulacrum" to "Artifact Creature", "Swiftfoot Boots" to "Artifact", "Commander's Sphere" to "Artifact", "Murder" to "Instant", "Negate" to "Instant", "Elvish Mystic" to "Creature — Elf", "Treasure Token" to "Artifact Token — Treasure", "Goblin Token" to "Creature Token — Goblin", "Thopter Token" to "Artifact Creature Token — Thopter"
)

@Composable
internal fun HeaderButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, glow: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val studio=studioWidget("TableBoardViews.HeaderButton", "Table and player", "action", label, enabled, onClick=onClick)
    if(studio?.style?.visible==false){StudioNativeCopies(studio);return}
    val studioClick:()->Unit={studio?.click() ?: onClick()}
    val studioEnabled=studio?.let{it.runtime.editing || it.enabled} ?: enabled
    val studioModifier=modifier.studioWidget(studio)
    val studioHold:(()->Unit)?=if(studio?.runtime?.editing==true) null else onLongClick
    CompositionLocalProvider(LocalStudioControlParent provides studio?.parent(true)) {
    val pulse = if (glow && studioEnabled) {
        val transition = rememberInfiniteTransition(label = "commander-affordable-pulse")
        val alpha by transition.animateFloat(.25f, .45f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "commander-affordable-alpha")
        alpha
    } else .25f
    val shape = RoundedCornerShape(8.dp)
    StudioText("TableBoardViews.Text.1", "Table and player", true, label, color = if (studioEnabled) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = studioModifier.heightIn(min = 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(studio?.style?.radius?.let{RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: (shape)).background(studio?.style?.fill?.let(::parseTableColorHex) ?: (if (glow && studioEnabled) Mint.copy(alpha = pulse) else if (studioEnabled) PanelLight else Panel)).border(if (glow && studioEnabled) 2.dp else 0.dp, if (glow && studioEnabled) Mint else Color.Transparent, shape).studioCombinedClickable("TableBoardViews.combinedClickable.1", "Table and player", enabled = studioEnabled, onClick = studioClick, onLongClick = studioHold).padding(horizontal = 13.dp, vertical = 12.dp))

    }
    StudioNativeCopies(studio)
}

@Composable
internal fun ManaPip(symbol: String) {
    val normalized = symbol.uppercase()
    val fill = when (normalized.firstOrNull()) {
        'W' -> Color(0xFFF3EAD2)
        'U' -> Color(0xFF4E93C8)
        'B' -> Color(0xFF69636F)
        'R' -> Color(0xFFC85450)
        'G' -> Color(0xFF4F9363)
        'C' -> Color(0xFF87918D)
        else -> Color(0xFF747D79)
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(fill).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        StudioText("TableBoardViews.Text.2", "Table and player", true, normalized, color = if (normalized == "W") Color(0xFF40362A) else Color.White, fontSize = if (normalized.length > 1) 9.sp else 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
internal fun ManaCostPips(cost: String) {
    val tokenPattern = remember { Regex("[\\[{]([^}\\]]+)[}\\]]") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        var cursor = 0
        tokenPattern.findAll(cost).forEach { match ->
            if (match.range.first > cursor) StudioText("TableBoardViews.Text.3", "Table and player", true, cost.substring(cursor, match.range.first), fontSize = 12.sp)
            val symbol = match.groupValues[1]
            if (symbol.length == 1 && symbol.first().uppercaseChar() in "WUBRGC") ManaPip(symbol)
            else Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF737C78)).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                StudioText("TableBoardViews.Text.4", "Table and player", true, symbol, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            cursor = match.range.last + 1
        }
        if (cursor < cost.length) StudioText("TableBoardViews.Text.5", "Table and player", true, cost.substring(cursor), fontSize = 12.sp)
    }
}

@Composable
internal fun ManaSymbolText(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val tokenPattern = remember { Regex("[\\[{]([WUBRGC])[}\\]]") }
    val matches = remember(text) { tokenPattern.findAll(text).toList() }
    val annotated = buildAnnotatedString {
        var cursor = 0
        matches.forEachIndexed { index, match ->
            append(text.substring(cursor, match.range.first))
            appendInlineContent("mana-pip-$index", alternateText = match.value)
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    val inline = matches.mapIndexed { index, match ->
        "mana-pip-$index" to InlineTextContent(
            Placeholder(fontSize * 1.55f, fontSize * 1.55f, PlaceholderVerticalAlign.Center)
        ) { ManaPip(match.groupValues[1]) }
    }.toMap()
    StudioText("TableBoardViews.Text.6", "Table and player", true, annotated, modifier = modifier, color = color, fontSize = fontSize, inlineContent = inline)
}

@Composable
internal fun PlayerOverview(player: TablePlayer, index: Int, modifier: Modifier = Modifier, compact: Boolean = false, isActivePlayer: Boolean = false, turnNumber: Int, phaseName: String, turnTime: Long?, showTurnTimer: Boolean, onToggleTimer: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onOpen: () -> Unit, onZone: (String) -> Unit, onPreview: (GameCard) -> Unit, onPublicPreview: (GameCard) -> Unit, tableZoom: MutableFloatState, onTableZoomEnd: (Float) -> Unit, canReorder: Boolean = true, isLastSeat: Boolean = false) {
    // A turn change must not expand or collapse a player's overview panel.
    var expanded by rememberSaveable(player.id) { mutableStateOf(false) }
    val paymentCards = player.battlefield.filter { card ->
        if (card.isFaceDown) return@filter false
        val name = card.name.lowercase()
        val oracle = card.oracleText.orEmpty().lowercase()
        name == "rhystic study" || name == "smothering tithe" ||
            (("whenever an opponent" in oracle || "whenever a player" in oracle) && ("unless" in oracle && "pay" in oracle || "may pay" in oracle))
    }
    Column(modifier.animateContentSize().clip(RoundedCornerShape(14.dp)).background(Panel).border(if (isActivePlayer) 2.dp else 1.dp, if (isActivePlayer) Mint else Color(0xFF33423D), RoundedCornerShape(14.dp)).studioCombinedClickable("TableBoardViews.combinedClickable.2", "Table and player", onClick = onOpen).padding(if (compact) 6.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StudioText("TableBoardViews.Text.7", "Table and player", true, "${if (player.isEliminated) "✕ " else if (player.isOnline) "● " else ""}${player.name} · P${index + 1}${if (player.isComputer) " · AI" else ""}${if (player.isEliminated) " · OUT" else if (isActivePlayer) " · TURN" else ""}", color = if (player.isEliminated) Muted else if (isActivePlayer || player.isOnline) Mint else SoftWhite, fontWeight = FontWeight.Bold, fontSize = if (compact) 9.sp else 12.sp, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) StudioText("TableBoardViews.Text.8", "Table and player", true, "${player.hand.size} in hand  ·  ${player.library.size} in library", color = Muted, fontSize = 10.sp)
            }
            if (compact) StudioTextButton("TableBoardViews.TextButton.1", "Table and player", onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableBoardViews.Text.9", "Table and player", false, "VIEW", color = Mint, fontSize = 9.sp) }
            StudioText("TableBoardViews.Text.10", "Table and player", true, "${player.life}", color = Mint, fontSize = if (compact) 20.sp else 27.sp, fontWeight = FontWeight.Black)
        }
        if (player.commanderName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                player.commanderCard?.let { commander ->
                    val crop = commanderArtOnly(commander.artCropUrl ?: commander.imageUrl)
                    if (!crop.isNullOrBlank()) CardArt(
                        crop,
                        Modifier.size(width = if (compact) 60.dp else 92.dp, height = if (compact) 38.dp else 58.dp)
                            .clip(RoundedCornerShape(6.dp)).border(1.dp, CounterGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .studioCombinedClickable("TableBoardViews.combinedClickable.3", "Table and player", onClick = { onPreview(commander) }, onLongClick = { onPreview(commander) }),
                        contentScale = ContentScale.Crop,
                        label = commander.name
                    ) else Box(
                        Modifier.size(width = if (compact) 60.dp else 92.dp, height = if (compact) 38.dp else 58.dp)
                            .clip(RoundedCornerShape(6.dp)).background(PanelLight).border(1.dp, CounterGold, RoundedCornerShape(6.dp))
                            .studioCombinedClickable("TableBoardViews.combinedClickable.4", "Table and player", onClick = { onPreview(commander) }, onLongClick = { onPreview(commander) }),
                        contentAlignment = Alignment.Center
                    ) { StudioText("TableBoardViews.Text.11", "Table and player", false, "COMMANDER", color = CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
                }
                // Owner note 12: the name was cut off ("COMMANDER · Mabe…"). It now wraps to a second line and takes the room left.
                StudioText("TableBoardViews.Text.12", "Table and player", true, "COMMANDER · ${player.commanderName}", color = CounterGold, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 9.sp else 11.sp, modifier = Modifier.weight(1f, fill = false))
            }
        }
        val commanderDamageTotal = player.commanderDamageReceived.values.sum()
        val visibleCounters = player.playerCounters.filterValues { it > 0 }
        if (commanderDamageTotal > 0 || visibleCounters.isNotEmpty() || player.isEliminated) {
            StudioText("TableBoardViews.Text.13", "Table and player", true, buildList {
                if (commanderDamageTotal > 0) add("CMD DAMAGE $commanderDamageTotal")
                visibleCounters.forEach { (name, amount) -> add("$name $amount") }
                if (player.isEliminated) add(player.eliminationReason ?: "ELIMINATED")
            }.joinToString("  ·  "), color = if (player.isEliminated) Color(0xFFE9978B) else CounterGold, fontSize = if (compact) 7.sp else 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        StudioText("TableBoardViews.Text.14", "Table and player", true, "TURN $turnNumber · $phaseName${if (showTurnTimer && turnTime != null) " · ${formatClock(turnTime)}" else ""}", color = if (isActivePlayer) Mint else Muted, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (turnTime != null) Modifier.studioCombinedClickable("TableBoardViews.combinedClickable.5", "Table and player", onClick = {}, onLongClick = onToggleTimer) else Modifier)
        if (paymentCards.isNotEmpty()) {
            StudioText("TableBoardViews.Text.15", "Table and player", true, "PAYMENT TRIGGER${if (paymentCards.size > 1) "S" else ""} · ${paymentCards.joinToString { it.name }}", color = CounterGold, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
        val typeCounts = player.battlefield.groupingBy { battlefieldGroups(listOf(it)).firstOrNull()?.first ?: "Other" }.eachCount()
        val zoneSummary = listOfNotNull(
            player.graveyard.size.takeIf { it > 0 }?.let { "GY $it" },
            player.exile.size.takeIf { it > 0 }?.let { "EX $it" },
            player.revealed.size.takeIf { it > 0 }?.let { "PUBLIC $it" }
        ).joinToString("  ·  ")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StudioText("TableBoardViews.Text.16", "Table and player", true, "BOARD · ${player.battlefield.size} permanents", color = Muted, fontSize = if (compact) 8.sp else 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
        if (typeCounts.isNotEmpty()) StudioText("TableBoardViews.Text.17", "Table and player", true, typeCounts.entries.joinToString("  ·  ") { "${it.key} ${it.value}" }, color = Muted, fontSize = if (compact) 7.sp else 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (zoneSummary.isNotBlank()) StudioText("TableBoardViews.Text.18", "Table and player", true, zoneSummary, color = Muted, fontSize = if (compact) 7.sp else 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (expanded) {
            Spacer(Modifier.height(5.dp))
            val cardGroups = listOf(
                "BATTLEFIELD" to player.battlefield,
                "GRAVEYARD" to player.graveyard,
                "EXILE" to player.exile,
                "PUBLICLY REVEALED" to player.revealed
            ).filter { it.second.isNotEmpty() }
            if (cardGroups.isEmpty()) StudioText("TableBoardViews.Text.19", "Table and player", false, "No public cards to show", color = Muted, fontSize = 10.sp)
            else {
                Column(Modifier.fillMaxWidth().pinchToZoom(tableZoom, onTableZoomEnd).testTag("table-card-zoom"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioText("TableBoardViews.Text.20", "Table and player", false, "PINCH TO RESIZE CARDS · SCROLL TO BROWSE", color = Muted, fontSize = 8.sp)
                    cardGroups.forEach { (title, cards) ->
                        StudioText("TableBoardViews.Text.21", "Table and player", true, title, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val panelWidth = maxWidth
                            val cardGap = 7.dp
                            val minCardWidth = (if (compact) 62.dp else 116.dp) * tableZoom.floatValue
                            val maxCardWidth = (if (compact) 120.dp else 190.dp) * tableZoom.floatValue
                            val cardRows = buildList {
                                var row = mutableListOf<GameCard>()
                                var occupiedWidth = 0.dp
                                cards.forEach { card ->
                                    // A tapped card is rotated, so reserve its
                                    // landscape width when deciding how many
                                    // cards fit across the table.
                                    val slotWidth = minCardWidth * if (card.tapped) (1f / 0.72f) else 1f
                                    val nextWidth = occupiedWidth + (if (row.isEmpty()) 0.dp else cardGap) + slotWidth
                                    if (row.isNotEmpty() && nextWidth > panelWidth) {
                                        add(row.toList())
                                        row = mutableListOf()
                                        occupiedWidth = 0.dp
                                    }
                                    occupiedWidth += (if (row.isEmpty()) 0.dp else cardGap) + slotWidth
                                    row.add(card)
                                }
                                if (row.isNotEmpty()) add(row.toList())
                            }
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                cardRows.forEach { rowCards ->
                                    val widthFactors = rowCards.sumOf { if (it.tapped) (1.0 / 0.72) else 1.0 }
                                    val availableWidth = panelWidth - cardGap * (rowCards.size - 1)
                                    val cardWidth = (availableWidth / widthFactors.toFloat()).coerceAtMost(maxCardWidth)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cardGap), verticalAlignment = Alignment.CenterVertically) {
                                        rowCards.forEach { card ->
                                            OverviewCard(card, cardWidth, onClick = { if (title == "BATTLEFIELD") onOpen() else onPublicPreview(card) }, onLongClick = { onPublicPreview(card) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (player.handRevealedToAll && player.hand.isNotEmpty()) {
                        StudioText("TableBoardViews.Text.22", "Table and player", false, "HAND REVEALED TO EVERYONE", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.hintedHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            player.hand.forEach { card -> CardFace(card, Modifier.width(if (compact) 30.dp else 44.dp), onClick = { onPreview(card) }, onLongClick = { onPreview(card) }, showCounters = false) }
                        }
                    }
                }
            }
            if (!compact) Row(Modifier.hintedHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (player.graveyard.isNotEmpty()) ZoneChip("GRAVEYARD", player.graveyard.size) { onZone("Graveyard") }
                if (player.exile.isNotEmpty()) ZoneChip("EXILE", player.exile.size) { onZone("Exile") }
                if (player.revealed.isNotEmpty()) ZoneChip("REVEALED", player.revealed.size) { onZone("Revealed") }
            }
        }
        // 48dp touch target around the 28dp chevron.
        StudioIconButton("TableBoardViews.IconButton.1", "Table and player", onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally).size(48.dp)) {
            StudioIcon("TableBoardViews.Icon.1", "Table and player", if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse player details" else "Expand player details", tint = Muted, modifier = Modifier.size(28.dp))
        }
        // Seat order only changes on a local table; online games take their order from the room or engine.
        if (expanded && canReorder) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StudioTextButton("TableBoardViews.TextButton.2", "Table and player", onClick = onMoveUp, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableBoardViews.Text.23", "Table and player", false, "MOVE UP ↑", color = if (index > 0) Mint else Muted, fontSize = 9.sp) }
                StudioTextButton("TableBoardViews.TextButton.3", "Table and player", onClick = onMoveDown, enabled = !isLastSeat, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableBoardViews.Text.24", "Table and player", false, "MOVE DOWN ↓", color = if (!isLastSeat) Mint else Muted, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
internal fun PlayerBoard(
    player: TablePlayer, playerIndex: Int, isLandscape: Boolean, cardSizeScale: Float = 1f, boardSpacingScale: Float = 1f, gameInProgress: Boolean, isActivePlayer: Boolean, canControl: Boolean, turnNumber: Int, phaseName: String, modifier: Modifier,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    stackCount: Int, commanderUnavailable: Boolean,
    onLife: (Int) -> Unit, onDraw: () -> Unit, onShowHand: () -> Unit, onShowHandFromZone: () -> Unit, onToken: () -> Unit, onManageMana: () -> Unit,
    onZone: (String) -> Unit, onPreview: (GameCard) -> Unit, onPublicPreview: (GameCard) -> Unit, onTapCard: (Int) -> Unit,
    onCardAction: (GameCard, String) -> Unit, onReveal: () -> Unit, onShuffle: () -> Unit,
    onNextStep: () -> Unit, onOpenLog: () -> Unit, onOpenChat: () -> Unit, onCombat: () -> Unit,
    onTrackers: () -> Unit, onStack: () -> Unit, onOpenMore: () -> Unit, onSearchLibrary: () -> Unit, onCastCommander: () -> Unit, onConcede: () -> Unit,
    /** Own seat in an Argentum game: the engine owns rules, so manual-only tools are hidden. */
    engineControlled: Boolean = false,
    /** Cards Argentum currently offers an action for; drawn with a glow. */
    playableIds: Set<Int> = emptySet(),
    /** Any board in an Argentum game: Quick Actions show only controls that work there. */
    engineGame: Boolean = false,
    /** Whether identical Argentum permanents are drawn as one counted stack. */
    stackIdenticalCards: Boolean = true,
    undoAvailable: Boolean = false,
    passToMyTurnActive: Boolean = false,
    onPassToMyTurn: () -> Unit = {},
    onUndo: () -> Unit = {},
    argentumGame: Boolean = false,
    localPlayerEliminated: Boolean = false,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val battlefieldListState = rememberLazyListState()
    LaunchedEffect(player.id) {
        // Each player board has its own battlefield. Do not carry a previous
        // player's scroll offset into this board, where it can leave card rows
        // clipped at both edges when the player view changes.
        battlefieldListState.scrollToItem(0)
    }
    val battlefieldCardWidth = when {
        screenWidthDp >= 1000 -> (142 * cardSizeScale).dp
        screenWidthDp >= 700 -> (128 * cardSizeScale).dp
        isLandscape -> (108 * cardSizeScale).dp
        else -> (126 * cardSizeScale).dp
    }
    var moreOpen by remember { mutableStateOf(false) }
    var shuffleConfirm by remember { mutableStateOf(false) }
    // Pinch to zoom the battlefield; remembered per screen shape.
    val boardZoom = rememberBoardZoom()
    val zoomContext = LocalContext.current
    val zoomConfig = LocalConfiguration.current
    val sectionContent: Map<String, @Composable ColumnScope.() -> Unit> = mapOf(
            "STATUS" to {
        if (!engineGame) Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(if (isActivePlayer) 2.dp else 0.dp, if (isActivePlayer) Mint else Panel, RoundedCornerShape(12.dp)).studioCombinedClickable("TableBoardViews.combinedClickable.6", "Table and player", onClick = onTrackers).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StudioText("TableBoardViews.Text.25", "Table and player", true, "${if (player.isEliminated) "✕ " else if (player.isOnline) "● " else ""}${player.name}${if (player.isEliminated) " · ELIMINATED" else if (player.isOnline) " · ONLINE" else ""}${if (player.isComputer) " · AI" else ""}${if (isActivePlayer && !player.isEliminated) " · ACTIVE" else ""}", color = if (player.isEliminated) Muted else if (isActivePlayer || player.isOnline) Mint else SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StudioText("TableBoardViews.Text.26", "Table and player", true, "TURN $turnNumber · ${phaseName} · P${playerIndex + 1} · ${player.hand.size} IN HAND · ${player.library.size} IN LIBRARY", color = Muted, fontSize = 8.sp, letterSpacing = 0.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canControl) StudioIconButton("TableBoardViews.IconButton.2", "Table and player", onClick = { onLife(-1) }, modifier = Modifier.size(44.dp)) { StudioIcon("TableBoardViews.Icon.2", "Table and player", Icons.Filled.Remove, "Decrease life", tint = SoftWhite) }
            StudioText("TableBoardViews.Text.27", "Table and player", true, "${player.life}", color = Mint, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 36.dp))
            if (canControl) StudioIconButton("TableBoardViews.IconButton.3", "Table and player", onClick = { onLife(1) }, modifier = Modifier.size(44.dp)) { StudioIcon("TableBoardViews.Icon.3", "Table and player", Icons.Filled.Add, "Increase life", tint = Mint) }
        }

            },
            "MANA" to {
        // Engine games show the pool as a chip in the action row, and only when it has mana.
        if (!engineGame) Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(9.dp))
                .background(if (canControl) PanelLight else Panel)
                .studioCombinedClickable("TableBoardViews.combinedClickable.7", "Table and player", enabled = canControl, onClick = onManageMana)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioText("TableBoardViews.Text.28", "Table and player", false, "MANA POOL", color = if (canControl) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Spacer(Modifier.weight(1f))
            StudioText("TableBoardViews.Text.29", "Table and player", true, "${player.manaPool.values.sum()} TOTAL", color = if (canControl) SoftWhite else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

            },
            "COMMANDER" to {
        val commander = player.commanderCard
        if (engineGame) Unit else if (commander != null || player.commanderDamageReceived.isNotEmpty() || player.playerCounters.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Panel).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                if (commander != null) {
                    CardFace(commander, Modifier.width(30.dp), glow = commander.id in playableIds, onClick = { if (!commanderUnavailable && (canControl || engineControlled)) onCastCommander() }, onLongClick = { if (!commanderUnavailable && (canControl || engineControlled)) onCastCommander() }, showCounters = false)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        StudioText("TableBoardViews.Text.30", "Table and player", true, "${if (commanderUnavailable) "IN PLAY / STACK" else "COMMAND ZONE"} · ${commander.name}", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val trackerSummary = player.playerCounters.entries.joinToString(" · ") { "${it.key} ${it.value}" }
                        StudioText("TableBoardViews.Text.31", "Table and player", true, "Cast ${player.commanderCastCount} · tax +{${player.commanderCastCount * 2}}${if (trackerSummary.isNotBlank()) " · $trackerSummary" else ""}", color = Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StudioTextButton("TableBoardViews.TextButton.4", "Table and player", onClick = onCastCommander, enabled = (canControl || engineControlled) && !commanderUnavailable && !player.isEliminated, modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { StudioText("TableBoardViews.Text.32", "Table and player", false, "CAST", color = if (canControl && !commanderUnavailable && !player.isEliminated) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                } else StudioText("TableBoardViews.Text.33", "Table and player", true, "PLAYER TRACKERS · ${player.playerCounters.entries.joinToString { "${it.key} ${it.value}" }.ifBlank { "none" }}", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                val incomingDamage = player.commanderDamageReceived.entries.sumOf { it.value }
                if (incomingDamage > 0) StudioText("TableBoardViews.Text.34", "Table and player", true, "CMD DMG $incomingDamage", color = if (player.commanderDamageReceived.values.any { it >= 21 }) Color(0xFFE9978B) else CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

            },
            "STEP" to {
        if (gameInProgress && !engineGame) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (engineControlled && !player.isEliminated) StudioText("TableBoardViews.Text.35", "Table and player", true, "ARGENTUM RUNS THE RULES · USE ${if (isActivePlayer) "PASS PRIORITY / ACTIONS" else "ACTIONS WHEN OFFERED"}", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                else if (player.isComputer || player.isEliminated) StudioText("TableBoardViews.Text.36", "Table and player", true, if (player.isEliminated) "ELIMINATED · ${player.eliminationReason ?: "OUT"}" else if (player.deckLoaded) "AI TURN IN PROGRESS" else "IMPORT A DECK FOR THE AI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
                else {
                    StudioTextButton("TableBoardViews.TextButton.5", "Table and player", onClick = onNextStep, enabled = isActivePlayer && canControl, modifier = Modifier.heightIn(min = 48.dp)) {
                        StudioText("TableBoardViews.Text.37", "Table and player", true, if (!canControl) "OTHER PLAYER · READ ONLY" else if (isActivePlayer) "NEXT STEP  ›" else "WAITING FOR TURN", color = if (isActivePlayer && canControl) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

            },
            "ZONES" to {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Keep the actual floating pool visible even at zero. Untapped lands are
            // deliberately labelled as lands, NOT as spendable mana: fetch lands,
            // restrictions and conditional sources make that number different.
            if (engineGame && engineControlled && studioPieceShown("manaBar")) {
                val floating = player.manaPool.filterValues { it > 0 }
                val readyLands = player.battlefield.count { it.type.contains("Land", ignoreCase = true) && !it.tapped }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(PanelLight).padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("argentum-mana-summary").tutorialTarget("argentum-mana-summary"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        StudioText("TableBoardViews.Text.38", "Table and player", false, "MANA POOL  ·  " + floating.values.sum(), color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        StudioText("TableBoardViews.Text.39", "Table and player", true,
                            if (floating.isEmpty()) "NO FLOATING MANA"
                            else floating.entries.joinToString("  ") { (color, amount) -> "${manaLetter(color)}$amount" },
                            color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StudioText("TableBoardViews.Text.40", "Table and player", true, "UNTAPPED LANDS  ·  $readyLands", color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        StudioText("TableBoardViews.Text.41", "Table and player", false, "Other mana: tap its card", color = Muted, fontSize = 9.sp)
                    }
                }
            }
            if (studioPieceShown("buttonRow")) Row(Modifier.fillMaxWidth().tutorialTarget("quick-actions").studioRowScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // In an Argentum game only controls that work are shown: Argentum owns draws, tokens,
            // mana, steps and shuffles, so those manual shortcuts would be silent no-ops.
            if (engineGame) {
                // Quick Actions in the order chosen in Adjust UI (only actions that work in Argentum games).
                quickActionOrder.distinct().sortedBy { if (it == "PASS_TO_TURN") 0 else if (it == "UNDO") 1 else 2 }.forEach { action -> when (action) {
                    "CAST_COMMANDER" -> player.commanderCard?.takeIf { studioPieceShown("commanderChip") }?.let { commander ->
                        // Commander as a chip: art + name; tap to see Argentum's actions for it (cast).
                        val castable = engineControlled && !commanderUnavailable && !player.isEliminated
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(9.dp)).background(PanelLight)
                                .border(if (commander.id in playableIds) 2.dp else 0.dp, if (commander.id in playableIds) Mint else PanelLight, RoundedCornerShape(9.dp))
                                .studioCombinedClickable("TableBoardViews.combinedClickable.8", "Table and player", onClick = { if (castable) onCastCommander() else onPublicPreview(commander) }, onLongClick = { onPublicPreview(commander) })
                                .padding(end = 10.dp).testTag("commander-chip").tutorialTarget("commander-chip"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CardFace(commander, Modifier.width(29.dp), onClick = { if (castable) onCastCommander() else onPublicPreview(commander) }, onLongClick = { onPublicPreview(commander) }, showCounters = false)
                            Column(Modifier.padding(start = 6.dp)) {
                                StudioText("TableBoardViews.Text.42", "Table and player", true, commander.name, color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                                StudioText("TableBoardViews.Text.43", "Table and player", true, if (commanderUnavailable) "IN PLAY" else if (castable) "COMMAND ZONE · CAST" else "COMMAND ZONE", color = if (castable) Mint else Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "GRAVEYARD" -> ZoneChip("GRAVEYARD", player.graveyard.size, enabled = true, onClick = { onZone("Graveyard") })
                    "EXILE" -> ZoneChip("EXILE", player.exile.size, enabled = true, onClick = { onZone("Exile") })
                    "STACK" -> QuickActionChip(if (stackCount > 0) "STACK · $stackCount" else "STACK", enabled = true, onClick = onStack)
                    "LOG" -> QuickActionChip("GAME LOG", enabled = true, onClick = onOpenLog)
                    "PASS_TO_TURN" -> if (argentumGame) QuickActionChip(if (passToMyTurnActive) "STOP AUTO-PASS" else "PASS TO MY TURN", enabled = !localPlayerEliminated, onClick = onPassToMyTurn)
                    "UNDO" -> if (engineControlled && undoAvailable) QuickActionChip("UNDO", enabled = true, onClick = onUndo)
                    "HAND" -> QuickActionChip("HAND · ${player.hand.size}", enabled = true, onClick = onShowHandFromZone)
                } }
                // The non-scrolling mana summary above replaces the old hidden-when-zero
                // chip. It remains visible without horizontally scrolling Quick Actions.
                StudioText("TableBoardViews.Text.44", "Table and player", true, "${player.library.size} IN LIBRARY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.height(44.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 4.dp))
            } else quickActionOrder.distinct().forEach { action ->
                val zoneCount = when (action) {
                    "LIBRARY" -> player.library.size
                    "HAND" -> player.hand.size
                    "GRAVEYARD" -> player.graveyard.size
                    "EXILE" -> player.exile.size
                    "REVEALED" -> player.revealed.size
                    else -> null
                }
                val click: (() -> Unit)? = when (action) {
                    "LIBRARY" -> onSearchLibrary
                    "HAND" -> onShowHandFromZone
                    "GRAVEYARD" -> ({ onZone("Graveyard") })
                    "EXILE" -> ({ onZone("Exile") })
                    "REVEALED" -> ({ onZone("Revealed") })
                    "MANA" -> onManageMana
                    "DRAW" -> onDraw
                    "REVEAL" -> onReveal
                    "STACK" -> onStack
                    "TOKEN" -> onToken
                    "COMBAT" -> onCombat
                    "CAST_COMMANDER" -> onCastCommander
                    "NEXT_STEP" -> onNextStep
                    "SHUFFLE" -> ({ shuffleConfirm = true })
                    "LOG" -> onOpenLog
                    "CHAT" -> onOpenChat
                    else -> null
                }
                if (click != null) {
                    val label = if (zoneCount != null) action else layoutQuickActionTitle(action).uppercase()
                    if (zoneCount != null) {
                        Box(Modifier) {
                            ZoneChip(label, zoneCount, enabled = canControl, onClick = click)
                        }
                    } else QuickActionChip(label, enabled = canControl && (action != "NEXT_STEP" || isActivePlayer), onClick = click)
                }
            }
        }
        }

            },
            "REVEALED" to {
        if (player.revealed.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioText("TableBoardViews.Text.45", "Table and player", false, "PUBLIC REVEALS", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 7.dp))
                Row(Modifier.hintedHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    player.revealed.take(10).forEach { card -> CardFace(card, Modifier.width(40.dp), onClick = {}, onLongClick = { onPublicPreview(card) }, showCounters = false) }
                }
            }
        }

            },
            "BATTLEFIELD" to {
        if (!engineGame) StudioText("TableBoardViews.Text.46", "Table and player", false, "BATTLEFIELD  ·  grouped by type  ·  tap to tap/untap  ·  long-press to inspect", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, modifier = Modifier.padding(start = 3.dp))
        if (player.battlefield.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).tutorialTarget("battlefield-row").clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                StudioText("TableBoardViews.Text.47", "Table and player", false, "Your battlefield is empty", color = Muted, fontSize = 12.sp)
            }
        } else {
            // Equipment and Auras sit with the permanent they're attached to (Argentum games).
            val (standalone, attachedByHost) = remember(player.battlefield) { splitAttachments(player.battlefield) }
            val spreadHosts = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
            val drawCard: @Composable (GameCard, androidx.compose.ui.unit.Dp, Boolean) -> Unit = { card, width, glow ->
                BattlefieldCard(
                    card, width,
                    interactive = canControl,
                    isCommander = card.id == player.commanderCard?.id,
                    canSetCommander = player.commanderCard == null && player.commanderName.isNullOrBlank(),
                    onTap = { onTapCard(card.id) }, onPreview = { onPreview(card) },
                    onAction = { action -> onCardAction(card, action) },
                    glow = glow || card.id in playableIds
                )
            }
            LazyColumn(
                state = battlefieldListState,
                modifier = Modifier.fillMaxWidth().weight(1f).tutorialTarget("battlefield-row").tutorialTarget("table-zoom").clip(RoundedCornerShape(12.dp)).background(Panel)
                    .pinchToZoom(boardZoom) { saveBoardZoom(zoomContext, zoomConfig.screenWidthDp, zoomConfig.screenHeightDp, it) }
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(3.dp)
            ) {
                battlefieldGroups(standalone).forEach { (type, cards) ->
                    item(key = "header-$type") { StudioText("TableBoardViews.Text.48", "Table and player", true, "$type  ·  ${cards.size}", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 3.dp, top = 2.dp)) }
                    item(key = "cards-$type") {
                        // Full rows wrap onto the next line; identical cards share one spot with a count.
                        // A permanent with something attached never merges into a count stack.
                        val stacks = if (engineGame && stackIdenticalCards) stackIdentical(cards, attachedByHost.keys) else cards.map { CardStack(listOf(it)) }
                        WrappingCardStacks(stacks, battlefieldCardWidth * boardZoom.floatValue) { stack, width ->
                            val attachments = attachedByHost[stack.top.id].orEmpty()
                            if (stack.count == 1 && attachments.isNotEmpty()) {
                                val host = stack.top
                                AttachedPile(
                                    host, attachments, width,
                                    expanded = spreadHosts[host.id] == true,
                                    onToggle = { spreadHosts[host.id] = spreadHosts[host.id] != true },
                                    glow = (attachments + host).any { it.id in playableIds },
                                    card = drawCard,
                                )
                            } else {
                                // Tapping a stack acts on a copy Argentum offers something for, if any.
                                val card = stack.cards.firstOrNull { it.id in playableIds } ?: stack.top
                                drawCard(card, width, stack.cards.any { it.id in playableIds })
                            }
                        }
                    }
                }
            }
        }
            }
    )
    val defaultSectionOrder = listOf("STATUS", "MANA", "COMMANDER", "STEP", "ZONES", "REVEALED", "BATTLEFIELD")
    val normalizedSectionOrder = playerSectionOrder.filter { it in defaultSectionOrder }.distinct()
    // The board sits in the space between the top player tabs and the fixed
    // hand tray. Fill that measured height so the weighted battlefield list
    // can use the available space instead of wrapping its card contents.
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp * boardSpacingScale)) {
        normalizedSectionOrder.forEach { section -> sectionContent[section]?.invoke(this) }
    }
}

@Composable
internal fun BattlefieldCard(card: GameCard, tileWidth: androidx.compose.ui.unit.Dp, interactive: Boolean = true, isCommander: Boolean = false, canSetCommander: Boolean = false, onTap: () -> Unit, onPreview: () -> Unit, onAction: (String) -> Unit, glow: Boolean = false) {
    var menuOpen by remember { mutableStateOf(false) }
    val activatedAbilities = remember(card.id, card.oracleText) { cardActivatedAbilityLines(card) }
    val cardHeight = tileWidth / 0.72f
    Box(Modifier.width(if (card.tapped) cardHeight else tileWidth).height(cardHeight), contentAlignment = Alignment.Center) {
        CardFace(card, Modifier.width(tileWidth).tutorialTarget("battlefield-card:${card.name}"), onClick = onTap, onLongClick = onPreview, animateTapped = true, glow = glow, tutorialZone = "battlefield")
        card.chosenCreatureType?.takeIf { it.isNotBlank() }?.let { chosenType ->
            StudioText("TableBoardViews.Text.49", "Table and player", true,
                "TYPE · $chosenType",
                color = SoftWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color(0xDD101615)).padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        if (interactive) Box(Modifier.align(Alignment.TopEnd).padding(1.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xCC101615)), contentAlignment = Alignment.Center) {
                    StudioIcon("TableBoardViews.Icon.4", "Table and player", Icons.Filled.MoreVert, "${card.name} actions", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.1", "Table and player", text = { StudioText("TableBoardViews.Text.50", "Table and player", true, if (card.tapped) "Untap" else "Tap") }, onClick = { menuOpen = false; onAction("TAP") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.2", "Table and player", text = { StudioText("TableBoardViews.Text.51", "Table and player", false, "Add counter…") }, onClick = { menuOpen = false; onAction("COUNTER") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.3", "Table and player", text = { StudioText("TableBoardViews.Text.52", "Table and player", false, "Add token…") }, onClick = { menuOpen = false; onAction("TOKEN") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.4", "Table and player", text = { StudioText("TableBoardViews.Text.53", "Table and player", false, "Move to graveyard") }, onClick = { menuOpen = false; onAction("GRAVEYARD") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.5", "Table and player", text = { StudioText("TableBoardViews.Text.54", "Table and player", false, "Exile") }, onClick = { menuOpen = false; onAction("EXILE") })
                if (!card.isFaceDown && activatedAbilities.isNotEmpty()) {
                    HorizontalDivider(color = Muted.copy(alpha = 0.3f))
                    StudioText("TableBoardViews.Text.55", "Table and player", true, if (card.type.contains("planeswalker", ignoreCase = true)) "LOYALTY ABILITIES" else "ACTIVATED ABILITIES", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    activatedAbilities.forEach { ability ->
                        StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.6", "Table and player",
                            text = { StudioText("TableBoardViews.Text.56", "Table and player", true, ability, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
                            onClick = { menuOpen = false; onAction("ABILITY:$ability") }
                        )
                    }
                }
                if (canSetCommander && !card.isFaceDown) StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.7", "Table and player", text = { StudioText("TableBoardViews.Text.57", "Table and player", false, "Set as commander") }, onClick = { menuOpen = false; onAction("SET COMMANDER") })
                if (isCommander) StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.8", "Table and player", text = { StudioText("TableBoardViews.Text.58", "Table and player", false, "Move commander to command zone") }, onClick = { menuOpen = false; onAction("COMMAND ZONE") })
                if (card.isFaceDown) StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.9", "Table and player", text = { StudioText("TableBoardViews.Text.59", "Table and player", false, "Turn face up") }, onClick = { menuOpen = false; onAction("FACE UP") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.10", "Table and player", text = { StudioText("TableBoardViews.Text.60", "Table and player", false, "Inspect card") }, onClick = { menuOpen = false; onAction("INSPECT") })
            }
        }
    }
}

@Composable
internal fun OverviewCard(card: GameCard, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val height = width / 0.72f
    Box(Modifier.width(if (card.tapped) height else width).height(height), contentAlignment = Alignment.Center) {
        CardFace(card, Modifier.width(width), onClick = onClick, onLongClick = onLongClick, animateTapped = true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CardFace(card: GameCard, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit, showCounters: Boolean = true, animateTapped: Boolean = false, glow: Boolean = false, tutorialZone: String? = null,cornerRadius:Float=8f) {
    val studio=studioWidget("TableBoardViews.CardFace","Table and player","card-action",if(card.isFaceDown)"Face-down card" else card.name,protectedLabel=true,onClick=onClick)
    if(studio?.style?.visible==false){StudioNativeCopies(studio);return}
    val rotation by animateFloatAsState(targetValue = if (animateTapped && card.tapped) 90f else 0f, animationSpec = tween(260), label = "card-tap")
    // Argentum games: holding any card shows it large (CardZoom.kt) instead of the older long-press actions.
    val zoom = LocalCardZoom.current
    val holdAction: () -> Unit = {
        if(studio?.runtime?.editing==true)studio.runtime.selected=studio.id
        else {
            if (zoom.enabled) zoom.hold(card) else onLongClick()
            if (tutorialZone != null) TutorialActions.hold("$tutorialZone-card:${card.name}")
        }
    }
    CompositionLocalProvider(LocalStudioControlParent provides studio?.parent(true)) {
    Box(
        modifier.studioWidget(studio).aspectRatio(0.72f).graphicsLayer(rotationZ = rotation)
            .clip(RoundedCornerShape((studio?.style?.radius ?: cornerRadius).coerceIn(0f,100f).dp))
            .background(studio?.style?.fill?.let(::parseTableColorHex) ?: card.color)
            .border(if (glow) 2.5.dp else if (card.tapped) 2.dp else 1.dp, studio?.style?.fill?.let(::parseTableColorHex) ?: if (glow) Mint else if (card.tapped) Mint else Color.White.copy(alpha = 0.28f), RoundedCornerShape((studio?.style?.radius ?: cornerRadius).coerceIn(0f,100f).dp))
            .studioCombinedClickable("TableBoardViews.combinedClickable.9", "Table and player", onClick = {
                if(studio?.runtime?.editing!=true && tutorialZone != null) TutorialActions.tap("$tutorialZone-card:${card.name}")
                studio?.click() ?: onClick()
            }, onLongClick = holdAction)
            .then(if (zoom.enabled) Modifier.cardZoomRelease(zoom) else Modifier)
    ) {
        if (card.isFaceDown) {
            Box(Modifier.fillMaxSize().background(Color(0xFF12221E)), contentAlignment = Alignment.Center) {
                StudioText("TableBoardViews.Text.61", "Table and player", false, "FACE\nDOWN", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        } else if (!card.imageUrl.isNullOrBlank()) CardArt(card.imageUrl, Modifier.fillMaxSize(), fallbackName = card.name, fallbackType = card.type)
        else Column(Modifier.fillMaxSize().background(card.color).padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            StudioText("TableBoardViews.Text.62", "Table and player", true, card.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            StudioText("TableBoardViews.Text.63", "Table and player", true, card.type, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (card.isToken && !card.isFaceDown) StudioText("TableBoardViews.Text.64", "Table and player", false, "TOKEN", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(if (zoom.enabled) Alignment.TopEnd else Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xDD15221D)).padding(horizontal = 5.dp, vertical = 3.dp))
        val visibleCounters = card.counters.entries.filter { it.value > 0 }
        // Counters: small corner badges ("+1/+1 ×8"), not a strip across the art.
        if (showCounters && visibleCounters.isNotEmpty()) Column(Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            visibleCounters.take(2).forEach { (name, amount) ->
                StudioText("TableBoardViews.Text.65", "Table and player", true, if (amount == 1) name else "$name ×$amount", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(CounterGold).padding(horizontal = 4.dp, vertical = 1.dp))
            }
            if (visibleCounters.size > 2) StudioText("TableBoardViews.Text.66", "Table and player", true, "+${visibleCounters.size - 2} more", color = Ink, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(CounterGold).padding(horizontal = 4.dp, vertical = 1.dp))
        }
        // Engine facts (Argentum games): combat role, summoning sickness, power/toughness with damage.
        if (card.summoningSick && !card.isFaceDown) Box(Modifier.matchParentSize().background(Color(0x55000000)))
        card.combatRole?.let { role ->
            StudioText("TableBoardViews.Text.67", "Table and player", true, if (role == "ATTACKING") "⚔ ATTACKING" else "🛡 BLOCKING", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (role == "ATTACKING") Color(0xEEC2453F) else Color(0xEE3F78C2)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
        if (card.summoningSick && card.combatRole == null && !card.isFaceDown) StudioText("TableBoardViews.Text.68", "Table and player", false, "NEW", color = SoftWhite, fontSize = 7.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xDD15221D)).padding(horizontal = 4.dp, vertical = 2.dp))
        if (!card.isFaceDown && card.power != null && card.toughness != null) {
            val remaining = card.toughness.toIntOrNull()?.minus(card.damage)
            StudioText("TableBoardViews.Text.69", "Table and player", true, if (card.damage > 0 && remaining != null) "${card.power}/$remaining" else "${card.power}/${card.toughness}",
                color = if (card.damage > 0) Color(0xFFFFB4A8) else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 3.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xE6101615)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
    }
    StudioNativeCopies(studio)
}

/**
 * A transient image failure must not strand a visible card on text-only art.
 * Bound network work to three attempts; stagger retries by URL to avoid synchronizing the board.
 * A disposed card or changed URL cancels the calling LaunchedEffect and its retry pauses.
 */
internal suspend fun <T : Any> loadCardArtWithRetry(
    url: String,
    pause: suspend (Long) -> Unit = { delay(it) },
    load: suspend (CardArtRetryContext) -> CardArtLoadResult<T>
): T? {
    val retryContext = CardArtRetryContext()
    val jitterMs = url.hashCode().toLong() and 1023L
    repeat(3) { attempt ->
        when (val result = load(retryContext)) {
            is CardArtLoadResult.Loaded -> return result.value
            CardArtLoadResult.PermanentFailure -> return null
            CardArtLoadResult.RetryableFailure -> {
                if (attempt == 2) return null
                pause((if (attempt == 0) 2_000L else 8_000L) + jitterMs)
            }
        }
    }
    return null
}

@Composable
internal fun CardArt(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, fallbackName: String? = null, fallbackType: String? = null, label: String? = null) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(CardBitmapCache.get(url)) }
    LaunchedEffect(url, fallbackName) {
        if (bitmap == null) bitmap = loadCardArtWithRetry(url) { retryContext -> CardImages.loadForCardArt(url, fallbackName, retryContext) }
    }
    // Loaded art is named after the card, so screen readers (and tests) can still tell cards apart.
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = cardArtDescription(label, fallbackName), modifier = modifier, contentScale = contentScale)
    // While art loads (or if it cannot load), keep the card identifiable.
    else if (fallbackName != null) Column(modifier.background(Color(0xFF263B35)).padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        StudioText("TableBoardViews.Text.70", "Table and player", true, fallbackName, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
        StudioText("TableBoardViews.Text.71", "Table and player", false, "✦", color = Mint, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        StudioText("TableBoardViews.Text.72", "Table and player", true, fallbackType.orEmpty(), color = Muted, fontSize = 8.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    else Box(modifier.background(Color(0xFF263B35)), contentAlignment = Alignment.Center) { StudioText("TableBoardViews.Text.73", "Table and player", false, "✦", color = Mint, fontSize = 16.sp) }
}

/** Accessibility label for loaded card art: the card's name when known, never a blank label. */
internal fun cardArtDescription(label: String?, fallbackName: String?): String =
    label?.takeIf { it.isNotBlank() } ?: fallbackName?.takeIf { it.isNotBlank() } ?: "Magic card art"

@Composable
internal fun HandSheet(cards: List<GameCard>, isLandscape: Boolean, availableHeight: androidx.compose.ui.unit.Dp, highlightedCardId: Int?, handRevealed: Boolean, canAdvance: Boolean, canControl: Boolean, commanderCanCast: Boolean, actionOrder: List<String>, stackCount: Int, onToggleReveal: (Boolean) -> Unit, onClose: () -> Unit, onDraw: () -> Unit, onCombat: () -> Unit, onNextStep: () -> Unit, onPassTurn: () -> Unit, onStack: () -> Unit, onCastCommander: () -> Unit, onMoreAction: (String) -> Unit, onSelect: (GameCard) -> Unit, onDragDrop: (GameCard, Offset) -> Unit,
    argentumMode: Boolean = false, argentumPrimaryLabel: String? = null, legalActionCount: Int = 0, onUndo: (() -> Unit)? = null,
    arrangement: HandArrangement = HandArrangement.AUTO_FIT, cardScale: Float = 1f, playableIds: Set<Int> = emptySet(),
    canRespond: Boolean = false, commanderAffordable: Boolean = false,
    autoPassing: Boolean = false, autoPassAvailable: Boolean = false, onAutoPass: () -> Unit = {},
    turnContext: String? = null, onActions: () -> Unit = onStack, onCast: (GameCard?) -> Unit = {},
    castableIds: Set<Int> = emptySet(), actionsBusy: Boolean = false,
    libraryCount: Int = 0, graveyardCount: Int = 0, exileCount: Int = 0,
    onPreview: (GameCard) -> Unit = onSelect, studioLayout: StudioLayout? = null) {
    val handScrollState = rememberScrollState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selectedCard = cards.firstOrNull { it.id == selectedId }
    var libraryInfo by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    if (libraryInfo) StudioAlertDialog("Table Board Views 1", onDismissRequest = { libraryInfo = false },
        title = { StudioText("TableBoardViews.Text.74", "Table and player", false, "Your library") },
        text = { StudioText("TableBoardViews.Text.75", "Table and player", true, "$libraryCount cards. Library cards stay hidden. Searches and reveals are available only when the game permits them.") },
        confirmButton = { StudioTextButton("TableBoardViews.TextButton.6", "Table and player", onClick = { libraryInfo = false }) { StudioText("TableBoardViews.Text.76", "Table and player", false, "CLOSE") } })
    val studioGeometryProblems = studioLayout?.viewportProblems(LocalConfiguration.current.screenWidthDp.toFloat(),
        LocalDensity.current.fontScale, LocalConfiguration.current.screenHeightDp.toFloat()).orEmpty()
    if (studioLayout != null && argentumMode && studioGeometryProblems.isEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            // Owner, Nov 7: with a custom hand design the drawer top matches the editor: one compact line with the ✕,
            // no row of its own and no empty band.
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioText("TableBoardViews.Text.80", "Table and player", true,
                    compactHandLine(cards.size, turnContext, if (argentumMode) cards.count { it.id in playableIds } else null, if (argentumMode) stackCount else 0),
                    color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp).testTag("hand-context"))
                StudioIconButton("TableBoardViews.IconButton.4", "Table and player", onClick = onClose, modifier = Modifier.size(44.dp)) { StudioIcon("TableBoardViews.Icon.5", "Table and player", Icons.Filled.Close, "Close hand", tint = SoftWhite) }
            }
            StudioHandCanvas(studioLayout, StudioHandState(
                labels = mapOf("pass" to (if (autoPassing) "AUTO-PASSING · STOP" else argentumPrimaryLabel ?: "WAITING"),
                    "undo" to "UNDO", "actions" to "ACTIONS · ${if (actionsBusy) 0 else legalActionCount}", "cast" to "CAST",
                    "metric0" to "STACK\n$stackCount", "metric1" to "LIBRARY\n$libraryCount",
                    "metric2" to "GRAVEYARD\n$graveyardCount", "metric3" to "EXILE\n$exileCount"),
                enabled = buildSet {
                    addAll(listOf("metric0","metric1","metric2","metric3"))
                    if (autoPassing || !actionsBusy && argentumPrimaryLabel != null) add("pass")
                    if (!actionsBusy) {
                        if (onUndo != null) add("undo")
                        if (legalActionCount > 0) add("actions")
                        if (if (selectedCard == null) cards.any { it.id in castableIds } else selectedCard.id in castableIds) add("cast")
                    }
                }, cards = cards, playable = playableIds, selected = selectedId),
                onAction = { binding -> when (binding) {
                    "pass" -> if (autoPassing) onAutoPass() else if (!actionsBusy) onPassTurn()
                    "undo" -> if (!actionsBusy) onUndo?.invoke()
                    "actions" -> if (!actionsBusy) onActions()
                    "cast" -> if (!actionsBusy) onCast(selectedCard)
                    "metric0" -> onStack()
                    "metric1" -> libraryInfo = true
                    "metric2" -> onMoreAction("GRAVEYARD")
                    "metric3" -> onMoreAction("EXILE")
                } },
                onCard = { selectedId = it.id; onSelect(it) },
                onPreview = { selectedId = it.id; onPreview(it) })
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        // Owner note 9 (Studio builds): one compact line instead of three, e.g. "Hand 7 · Main 1 · 3 playable".
        if (BuildConfig.STUDIO_ENABLED) Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            val line = compactHandLine(cards.size, turnContext, if (argentumMode) cards.count { it.id in playableIds } else null, if (argentumMode) stackCount else 0)
            StudioText("TableBoardViews.Text.80", "Table and player", true, line, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("hand-context"))
            StudioIconButton("TableBoardViews.IconButton.5", "Table and player", onClick = onClose, modifier = Modifier.size(44.dp)) { StudioIcon("TableBoardViews.Icon.6", "Table and player", Icons.Filled.Close, "Close hand", tint = SoftWhite) }
        } else Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StudioText("TableBoardViews.Text.78", "Table and player", true, "YOUR HAND  ·  ${cards.size} CARDS", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                turnContext?.let { StudioText("TableBoardViews.Text.79", "Table and player", true, it, color = Muted, fontSize = 10.sp, maxLines = 2) }
                if (argentumMode) StudioText("TableBoardViews.Text.80", "Table and player", true, "${cards.count { it.id in playableIds }} playable now · $stackCount on stack",
                    color = Mint, fontSize = 10.sp, modifier = Modifier.testTag("hand-context"))
            }
            StudioIconButton("TableBoardViews.IconButton.5", "Table and player", onClick = onClose, modifier = Modifier.size(44.dp)) { StudioIcon("TableBoardViews.Icon.6", "Table and player", Icons.Filled.Close, "Close hand", tint = SoftWhite) }
        }
        if (argentumMode) {
            HandDrawerControls(
                primaryLabel = when { autoPassing -> "AUTO-PASSING · STOP"; actionsBusy -> null; else -> argentumPrimaryLabel },
                undoAvailable = onUndo != null && !actionsBusy,
                actionCount = if (actionsBusy) 0 else legalActionCount,
                castAvailable = !actionsBusy && (if (selectedCard == null) cards.any { it.id in castableIds } else selectedCard.id in castableIds),
                stackCount = stackCount, libraryCount = libraryCount, graveyardCount = graveyardCount, exileCount = exileCount,
                onPass = { if (autoPassing) onAutoPass() else if (!actionsBusy) onPassTurn() },
                onUndo = { onUndo?.invoke() }, onActions = onActions, onCast = { onCast(selectedCard) },
                onStack = onStack, onLibrary = { libraryInfo = true },
                onGraveyard = { onMoreAction("GRAVEYARD") }, onExile = { onMoreAction("EXILE") },
                onPassHold = if (autoPassing || autoPassAvailable && !actionsBusy) onAutoPass else null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StudioText("TableBoardViews.Text.81", "Table and player", true, if (selectedCard != null) "Selected: ${selectedCard.name}" else "Select a card · hold to inspect", color = Muted,
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("hand-selection"))
                Box {
                    StudioIconButton("TableBoardViews.IconButton.6", "Table and player", onClick = { moreOpen = true }, modifier = Modifier.size(32.dp)) { StudioIcon("TableBoardViews.Icon.7", "Table and player", Icons.Filled.MoreVert, "More hand options", tint = Muted) }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        if (commanderCanCast) StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.11", "Table and player", text = { StudioText("TableBoardViews.Text.82", "Table and player", false, "Cast commander") }, enabled = !actionsBusy,
                            onClick = { moreOpen = false; onCastCommander() })
                        StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.12", "Table and player", text = { StudioText("TableBoardViews.Text.83", "Table and player", false, "Game log") }, onClick = { moreOpen = false; onMoreAction("LOG") })
                        StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.13", "Table and player", text = { StudioText("TableBoardViews.Text.84", "Table and player", true, if (autoPassing) "Stop auto-pass" else "Pass to my turn") },
                            enabled = autoPassing || autoPassAvailable && !actionsBusy, onClick = { moreOpen = false; onAutoPass() })
                    }
                }
            }
        } else {
        Row(Modifier.fillMaxWidth().studioRowScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            actionOrder.forEach { action ->
                when (action) {
                    "DRAW" -> if (canControl) HeaderButton("DRAW", onDraw)
                    "REVEAL" -> HeaderButton(if (handRevealed) "HIDE HAND" else "REVEAL HAND", { onToggleReveal(!handRevealed) })
                    "STACK" -> HeaderButton("STACK · $stackCount", onStack, modifier = Modifier)
                    "CAST_COMMANDER" -> if (commanderCanCast) HeaderButton("CAST COMMANDER", onCastCommander, modifier = Modifier)
                    "COMBAT" -> if (canAdvance) HeaderButton("COMBAT", onCombat, modifier = Modifier)
                    "NEXT_STEP" -> if (canAdvance) HeaderButton("NEXT STEP", onNextStep, modifier = Modifier)
                    "PASS_TURN" -> if (canAdvance) HeaderButton("PASS TURN", onPassTurn, modifier = Modifier)
                    "MORE" -> HeaderButton("MORE  ⋮", { moreOpen = true }, modifier = Modifier)
                }
            }
            if (!argentumMode && !canControl && actionOrder.none { it == "REVEAL" || it == "STACK" || it == "MORE" }) StudioText("TableBoardViews.Text.85", "Table and player", false, "WAITING", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                if (canControl) {
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.14", "Table and player", text = { StudioText("TableBoardViews.Text.86", "Table and player", false, "Add token…") }, modifier = Modifier, onClick = { moreOpen = false; onMoreAction("TOKEN") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.15", "Table and player", text = { StudioText("TableBoardViews.Text.87", "Table and player", false, "Reveal top card") }, onClick = { moreOpen = false; onMoreAction("REVEAL") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.16", "Table and player", text = { StudioText("TableBoardViews.Text.88", "Table and player", false, "Shuffle library…") }, onClick = { moreOpen = false; onMoreAction("SHUFFLE") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.17", "Table and player", text = { StudioText("TableBoardViews.Text.89", "Table and player", false, "Player trackers and commander damage") }, onClick = { moreOpen = false; onMoreAction("TRACKERS") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.18", "Table and player", text = { StudioText("TableBoardViews.Text.90", "Table and player", false, "Search / browse library") }, onClick = { moreOpen = false; onMoreAction("SEARCH") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.19", "Table and player", text = { StudioText("TableBoardViews.Text.91", "Table and player", false, "Concede game…", color = Color(0xFFE9978B)) }, onClick = { moreOpen = false; onMoreAction("CONCEDE") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.20", "Table and player", text = { StudioText("TableBoardViews.Text.92", "Table and player", false, "View graveyard") }, onClick = { moreOpen = false; onMoreAction("GRAVEYARD") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.21", "Table and player", text = { StudioText("TableBoardViews.Text.93", "Table and player", false, "View exile") }, onClick = { moreOpen = false; onMoreAction("EXILE") })
                    StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.22", "Table and player", text = { StudioText("TableBoardViews.Text.94", "Table and player", false, "View revealed zone") }, onClick = { moreOpen = false; onMoreAction("REVEALED") })
                    HorizontalDivider(color = Muted.copy(alpha = 0.3f))
                }
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.23", "Table and player", text = { StudioText("TableBoardViews.Text.95", "Table and player", false, "Open game log") }, onClick = { moreOpen = false; onMoreAction("LOG") })
                StudioDropdownMenuItem("TableBoardViews.DropdownMenuItem.24", "Table and player", text = { StudioText("TableBoardViews.Text.96", "Table and player", false, "Open text chat") }, onClick = { moreOpen = false; onMoreAction("CHAT") })
            }
        }
        }
        if (cards.isEmpty()) StudioText("TableBoardViews.Text.97", "Table and player", false, "Your hand is empty", color = Muted, modifier = Modifier.padding(12.dp))
        else BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Tray height minus drag handle, hint, title and action rows (see HandLayout.kt).
            val areaHeight = (availableHeight - (if (argentumMode) 252.dp else 172.dp)).coerceAtLeast(if (isLandscape) 96.dp else 120.dp)
            val grid = HandLayout.drawer(cards.size, maxWidth.value, areaHeight.value, arrangement, cardScale)
            val cardWidth = grid.cardWidth.dp
            val cardHeight = grid.cardHeight.dp
            val gap = (if (arrangement == HandArrangement.SINGLE_ROW) HandLayout.GAP else 4f).dp
            val cardStepPx = with(density) { (cardWidth + gap).roundToPx() }
            val rowStepPx = with(density) { (cardHeight + gap).roundToPx() }
            LaunchedEffect(highlightedCardId, cards, grid) {
                val drawnIndex = cards.indexOfFirst { it.id == highlightedCardId }
                if (drawnIndex >= 0) {
                    val target = if (grid.rows == 1) drawnIndex * cardStepPx else (drawnIndex / grid.columns) * rowStepPx
                    handScrollState.animateScrollTo(target.coerceAtMost(handScrollState.maxValue))
                }
            }
            val rows = cards.chunked(grid.columns.coerceAtLeast(1))
            val container = if (grid.rows == 1) Modifier.fillMaxWidth().hintedHorizontalScroll(handScrollState)
                else Modifier.fillMaxWidth().heightIn(max = areaHeight).verticalScroll(handScrollState)
            Column(container.testTag("hand-grid").tutorialTarget("hand-grid"), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { rowCards -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            rowCards.forEach { card ->
                var coordinates by remember(card.id) { mutableStateOf<LayoutCoordinates?>(null) }
                CardFace(
                    card,
                    Modifier.width(cardWidth).height(cardHeight)
                        .semantics { selected = card.id == selectedId }
                        .tutorialTarget("hand-card:${card.name}")
                        .onGloballyPositioned { coordinates = it }
                        // Argentum games: holding a hand card previews it (no drag-to-play there).
                        .then(if (argentumMode || LocalStudioControls.current?.editing==true) Modifier else Modifier.pointerInput(card.id) {
                            var dragStart = Offset.Zero
                            var dragDelta = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragStart = it; dragDelta = Offset.Zero },
                                onDrag = { change, amount -> change.consume(); dragDelta += amount },
                                onDragEnd = {
                                    val coords = coordinates
                                    if (coords != null) onDragDrop(card, coords.localToRoot(dragStart + dragDelta))
                                }
                            )
                        })
                        .then(if (cards.firstOrNull()?.id == card.id) Modifier else Modifier)
                        .then(if (card.id == selectedId || card.id == highlightedCardId) Modifier.border(3.dp, Mint, RoundedCornerShape(8.dp)) else Modifier),
                    onClick = { selectedId = card.id; onSelect(card) }, onLongClick = { selectedId = card.id; onPreview(card) }, showCounters = false, glow = card.id in playableIds, tutorialZone = "hand"
                )
            }
            } }
            }
        }
    }
}

@Composable
internal fun CardPreviewPopup(item: CardPreview, onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val previewWidth = minOf(280.dp, (screenHeight - 64.dp).coerceAtLeast(180.dp) * 0.72f)
    StudioDialog("Table Board Views 2", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = LocalView.current.parent as? DialogWindowProvider
        SideEffect {
            dialogWindow?.window?.setDimAmount(0f)
            dialogWindow?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        Box(Modifier.fillMaxSize().studioCombinedClickable("TableBoardViews.combinedClickable.10", "Table and player", onClick = onDismiss), contentAlignment = Alignment.Center) {
            Box(Modifier.studioCombinedClickable("TableBoardViews.combinedClickable.11", "Table and player", onClick = onDismiss, onLongClick = {}), contentAlignment = Alignment.TopEnd) {
            CardFace(item.card, Modifier.width(previewWidth), onClick = onDismiss, onLongClick = {}, showCounters = false)
            StudioIconButton("TableBoardViews.IconButton.7", "Table and player", onClick = onDismiss, modifier = Modifier.padding(5.dp).size(42.dp).clip(CircleShape).background(Color(0xCC101615))) {
                StudioIcon("TableBoardViews.Icon.8", "Table and player", Icons.Filled.Close, "Close card preview", tint = Color.White)
            }
            }
        }
    }
}

/** Owner note 9: "Hand 7 · Main 1 · 3 playable [· Stack N]", the one-line drawer top (game and Studio editor). */
internal fun compactHandLine(cards: Int, turnContext: String?, playable: Int?, stack: Int): String {
    val phase = turnContext?.split(" · ")?.getOrNull(1)?.let { when (it.lowercase()) {
        "precombat main" -> "Main 1"; "postcombat main" -> "Main 2"; else -> it } }
    return listOfNotNull("Hand $cards", phase, playable?.let { "$it playable" }, if (stack > 0) "Stack $stack" else null).joinToString(" · ")
}
