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

@Composable
internal fun LibrarySearchDialog(
    player: TablePlayer,
    onPreview: (GameCard) -> Unit,
    onTakeToHand: (GameCard) -> Unit,
    onPutBattlefield: (GameCard) -> Unit,
    onPutGraveyard: (GameCard) -> Unit,
    onPutExile: (GameCard) -> Unit,
    onMoveToTop: (GameCard) -> Unit,
    onMoveToBottom: (GameCard) -> Unit,
    onShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var chip by remember { mutableStateOf<LibraryChip?>(null) }
    var sort by remember { mutableStateOf(LibrarySort.TYPE) }
    // A card left the library during this search: offer the shuffle a tutor needs on the way out.
    var movedOut by remember { mutableStateOf(false) }
    var shuffledSinceMove by remember { mutableStateOf(false) }
    var askShuffle by remember { mutableStateOf(false) }
    // The general browse/search control is not permission to inspect the
    // library's actual stack. Keep a randomized presentation list separate
    // from player.library so selecting a card never changes draw order.
    val unorderedLibrary = remember(player.library) {
        val shuffled = player.library.shuffled()
        if (shuffled.size > 1 && shuffled.map { it.id } == player.library.map { it.id }) {
            shuffled.drop(1) + shuffled.first()
        } else shuffled
    }
    val searched = remember(unorderedLibrary, query) {
        unorderedLibrary.filter { card ->
            query.isBlank() || card.name.contains(query.trim(), ignoreCase = true) || card.type.contains(query.trim(), ignoreCase = true) || card.oracleText.orEmpty().contains(query.trim(), ignoreCase = true)
        }
    }
    val chips = remember(unorderedLibrary) { LibraryChip.present(unorderedLibrary.map { it.type }) }
    val matches = remember(searched, chip, sort, selectedIds) {
        arrangeLibrary(searched, chip, sort, keep = { it.id in selectedIds }, name = { it.name }, typeLine = { it.type }, manaValue = { it.manaValue })
    }
    val selected = unorderedLibrary.filter { it.id in selectedIds }
    fun close() { if (movedOut && !shuffledSinceMove) askShuffle = true else onDismiss() }
    fun moveSelected(action: (GameCard) -> Unit, leavesLibrary: Boolean) {
        selected.forEach(action)
        if (leavesLibrary) { movedOut = true; shuffledSinceMove = false }
        selectedIds = emptySet()
    }
    StudioDialog("Table Dialogs 1",
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Panel
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.1", "Dialogs and overlays", true, "${player.name} · LIBRARY", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        StudioText("TableDialogs.Text.2", "Dialogs and overlays", true, "${player.library.size} cards · randomized display · library order hidden", color = Muted, fontSize = 10.sp)
                    }
                    StudioTextButton("TableDialogs.TextButton.1", "Dialogs and overlays", onClick = { onShuffle(); shuffledSinceMove = true }, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.3", "Dialogs and overlays", false, "SHUFFLE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    StudioIconButton("TableDialogs.IconButton.1", "Dialogs and overlays", onClick = ::close, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.1", "Dialogs and overlays", Icons.Filled.Close, "Close library", tint = Muted) }
                }
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.1", "Dialogs and overlays",
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    singleLine = true,
                    label = { StudioText("TableDialogs.Text.4", "Dialogs and overlays", false, "Search name, type, or rules text") },
                    placeholder = { StudioText("TableDialogs.Text.5", "Dialogs and overlays", false, "Blank search shows the full library") }
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    LibrarySort.entries.forEach { s -> StudioFilterChip("TableDialogs.FilterChip.1", "Dialogs and overlays", sort == s, { sort = s }, label = { StudioText("TableDialogs.Text.6", "Dialogs and overlays", true, s.label, fontSize = 11.sp) }) }
                    chips.forEach { (c, n) -> StudioFilterChip("TableDialogs.FilterChip.2", "Dialogs and overlays", chip == c, { chip = if (chip == c) null else c }, label = { StudioText("TableDialogs.Text.7", "Dialogs and overlays", true, "${c.label} $n", fontSize = 11.sp) }) }
                }
                if (selected.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudioText("TableDialogs.Text.8", "Dialogs and overlays", true, if (selected.size == 1) "SELECTED · ${selected.single().name}" else "${selected.size} SELECTED", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        StudioTextButton("TableDialogs.TextButton.2", "Dialogs and overlays", onClick = { selectedIds = emptySet() }, modifier = Modifier.heightIn(min = 40.dp)) { StudioText("TableDialogs.Text.9", "Dialogs and overlays", false, "CLEAR", color = Muted, fontSize = 9.sp) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            Triple("TO HAND", onTakeToHand, true),
                            Triple("TO BATTLEFIELD", onPutBattlefield, true),
                            Triple("TO GRAVEYARD", onPutGraveyard, true),
                            Triple("TO EXILE", onPutExile, true),
                            Triple("TO TOP", onMoveToTop, false),
                            Triple("TO BOTTOM", onMoveToBottom, false)
                        ).forEach { (label, action, leaves) ->
                            StudioTextButton("TableDialogs.TextButton.3", "Dialogs and overlays", onClick = { moveSelected(action, leaves) }, modifier = Modifier.heightIn(min = 44.dp)) {
                                StudioText("TableDialogs.Text.10", "Dialogs and overlays", true, label, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    StudioText("TableDialogs.Text.11", "Dialogs and overlays", false, "Tap cards to select one or more, then move them. Long press a card to inspect it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 5.dp))
                }
                HorizontalDivider(color = PanelLight)
                when {
                    player.library.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { StudioText("TableDialogs.Text.12", "Dialogs and overlays", false, "Library is empty", color = Muted) }
                    matches.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { StudioText("TableDialogs.Text.13", "Dialogs and overlays", false, "No matching cards", color = Muted) }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        matches.forEachIndexed { index, (group, card) ->
                            if (sort != LibrarySort.NAME && (index == 0 || matches[index - 1].first != group)) item(key = "h-$group") {
                                StudioText("TableDialogs.Text.14", "Dialogs and overlays", true, "${group.uppercase()} · ${matches.count { it.first == group }}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                            }
                            item(key = card.id) {
                                val isSelected = card.id in selectedIds
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSelected) PanelLight else Panel)
                                        .border(if (isSelected) 1.dp else 0.dp, if (isSelected) Mint else Panel, RoundedCornerShape(9.dp))
                                        .studioCombinedClickable("TableDialogs.combinedClickable.1", "Dialogs and overlays", onClick = { selectedIds = if (isSelected) selectedIds - card.id else selectedIds + card.id }, onLongClick = { onPreview(card) })
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CardFace(card, Modifier.width(37.dp), onClick = { selectedIds = if (isSelected) selectedIds - card.id else selectedIds + card.id }, onLongClick = { onPreview(card) }, showCounters = false)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        StudioText("TableDialogs.Text.15", "Dialogs and overlays", true, card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        StudioText("TableDialogs.Text.16", "Dialogs and overlays", true, listOfNotNull(card.manaCost?.takeIf { it.isNotBlank() }, card.type, card.power?.let { "$it/${card.toughness}" }).joinToString("  ·  "),
                                            color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) StudioText("TableDialogs.Text.17", "Dialogs and overlays", false, "✓", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.18", "Dialogs and overlays", false, "Draw and common turn commands are in the hand tray.", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    StudioTextButton("TableDialogs.TextButton.4", "Dialogs and overlays", onClick = { onShuffle(); onDismiss() }, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.19", "Dialogs and overlays", false, "DONE · SHUFFLE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (askShuffle) StudioAlertDialog("Table Dialogs 2",
        onDismissRequest = { askShuffle = false },
        title = { StudioText("TableDialogs.Text.20", "Dialogs and overlays", false, "Shuffle your library?") },
        text = { StudioText("TableDialogs.Text.21", "Dialogs and overlays", true, "You moved cards out of ${player.name}'s library. Searching usually ends with a shuffle.") },
        confirmButton = { StudioTextButton("TableDialogs.TextButton.5", "Dialogs and overlays", onClick = { askShuffle = false; onShuffle(); onDismiss() }) { StudioText("TableDialogs.Text.22", "Dialogs and overlays", false, "SHUFFLE") } },
        dismissButton = { StudioTextButton("TableDialogs.TextButton.6", "Dialogs and overlays", onClick = { askShuffle = false; onDismiss() }) { StudioText("TableDialogs.Text.23", "Dialogs and overlays", false, "DON'T SHUFFLE") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )
}

@Composable
internal fun ManaPoolDialog(
    player: TablePlayer,
    onChange: (String, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val manaColors = listOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless")
    val total = player.manaPool.values.sum()
    StudioDialog("Table Dialogs 3", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.24", "Dialogs and overlays", true, "${player.name.uppercase()} · MANA POOL", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        StudioText("TableDialogs.Text.25", "Dialogs and overlays", true, "$total mana tracked", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    StudioIconButton("TableDialogs.IconButton.2", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.2", "Dialogs and overlays", Icons.Filled.Close, "Close mana pool", tint = Muted) }
                }
                StudioText("TableDialogs.Text.26", "Dialogs and overlays", false, "Tap + when you produce mana. Payments spend from this pool; it empties when you advance to another step.", color = Muted, fontSize = 10.sp)
                manaColors.forEach { (symbol, name) ->
                    val amount = player.manaPool[symbol] ?: 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ManaPip(symbol)
                            StudioText("TableDialogs.Text.27", "Dialogs and overlays", true, name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        StudioTextButton("TableDialogs.TextButton.7", "Dialogs and overlays", onClick = { onChange(symbol, -1) }, enabled = amount > 0, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.28", "Dialogs and overlays", false, "−", color = if (amount > 0) SoftWhite else Muted, fontSize = 20.sp) }
                        StudioText("TableDialogs.Text.29", "Dialogs and overlays", true, "$amount", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 28.dp))
                        StudioTextButton("TableDialogs.TextButton.8", "Dialogs and overlays", onClick = { onChange(symbol, 1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.30", "Dialogs and overlays", false, "+", color = Mint, fontSize = 20.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StudioTextButton("TableDialogs.TextButton.9", "Dialogs and overlays", onClick = onClear, enabled = total > 0, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.31", "Dialogs and overlays", false, "CLEAR POOL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold) }
                    StudioTextButton("TableDialogs.TextButton.10", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.32", "Dialogs and overlays", false, "DONE", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun CreatureTypeChoiceDialog(cardName: String, onConfirm: (String) -> Unit) {
    var creatureType by remember(cardName) { mutableStateOf("") }
    StudioAlertDialog("Table Dialogs 4",
        onDismissRequest = {},
        title = { StudioText("TableDialogs.Text.33", "Dialogs and overlays", false, "Choose a creature type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioText("TableDialogs.Text.34", "Dialogs and overlays", true, "${cardName} asks you to choose a creature type as it enters.", color = Muted)
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.2", "Dialogs and overlays",
                    value = creatureType,
                    onValueChange = { creatureType = it.take(40) },
                    singleLine = true,
                    label = { StudioText("TableDialogs.Text.35", "Dialogs and overlays", false, "Creature type") },
                    placeholder = { StudioText("TableDialogs.Text.36", "Dialogs and overlays", false, "e.g. Elf") }
                )
            }
        },
        confirmButton = {
            StudioTextButton("TableDialogs.TextButton.11", "Dialogs and overlays", onClick = { onConfirm(creatureType.trim()) }, enabled = creatureType.isNotBlank()) {
                StudioText("TableDialogs.Text.37", "Dialogs and overlays", false, "CONFIRM", color = if (creatureType.isNotBlank()) Mint else Muted, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Panel,
        titleContentColor = SoftWhite,
        textContentColor = Muted
    )
}
@Composable
internal fun LobbyDialog(
    players: List<TablePlayer>, roomCreated: Boolean, isHost: Boolean, gameStarted: Boolean, gameEnded: Boolean, joining: Boolean, roomCode: String,
    password: String, joinCode: String, joinPassword: String, readyPlayers: Set<Int>, pendingReady: Boolean?, deckSyncConfirmed: Boolean,
    onTakeMulligan: () -> Unit, onKeepOpeningHand: (List<Int>) -> Unit,
    hostInput: String, internetMode: Boolean, cloudEndpoint: String, playerName: String, hostAddress: String, localSeat: Int, onlineStatus: String,
    turnOrder: List<Int>?, notice: String?, onRemovePlayer: (Int) -> Unit, onExportDiagnostics: () -> Unit, onDismiss: () -> Unit, onCancelJoin: () -> Unit,
    onSelectJoin: () -> Unit, onSelectCreate: () -> Unit, onSelectNetwork: (Boolean) -> Unit, onCloudEndpoint: (String) -> Unit, onPassword: (String) -> Unit,
    onJoinCode: (String) -> Unit, onJoinPassword: (String) -> Unit, onHostInput: (String) -> Unit, onPlayerName: (String) -> Unit,
    onCreate: () -> Unit, onJoin: () -> Unit, onToggleReady: (Int) -> Unit,
    onImportDeck: (Int) -> Unit, onAddAi: () -> Unit, onRandomizeTurnOrder: () -> Unit, onStartGame: () -> Unit, onLeave: () -> Unit
) {
    val context = LocalContext.current
    var removeTarget by remember { mutableIntStateOf(-1) }
    var openingHandDialogOpen by remember { mutableStateOf(false) }
    val decksReady = players.size in 1..4 && players.all { it.deckLoaded }
    val allReady = players.indices.all { it in readyPlayers }
    val canStart = isHost && roomCreated && !gameStarted && decksReady && allReady
    val canHostHandOff = players.drop(1).any { it.isOnline && !it.isComputer }
    val canLeaveRoom = internetMode || !isHost || !gameStarted || gameEnded || canHostHandOff
    val displayedOrder = turnOrder?.takeIf { it.size == players.size } ?: players.indices.toList()
    val firstPlayer = players.getOrNull(displayedOrder.firstOrNull() ?: 0)?.name ?: "—"

    StudioDialog("Table Dialogs 5", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.38", "Dialogs and overlays", false, "GAME LOBBY", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 0.8.sp)
                        StudioText("TableDialogs.Text.39", "Dialogs and overlays", false, "COMMANDER · UP TO 4 PLAYERS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        StudioText("TableDialogs.Text.40", "Dialogs and overlays", true, "APP VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp)
                    }
                    StudioIconButton("TableDialogs.IconButton.3", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.3", "Dialogs and overlays", Icons.Filled.Close, "Close game lobby", tint = Muted) }
                }

                Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFF3A3021), shape = RoundedCornerShape(9.dp)) {
                    StudioText("TableDialogs.Text.41", "Dialogs and overlays", true, if (internetMode) "INTERNET ROOM · Connect from different networks using the secure room service." else "SAME WI-FI ROOM · All phones must be on the same local network.", color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val createSelected = !joining
                    StudioButton("TableDialogs.Button.1", "Dialogs and overlays",
                        onClick = onSelectCreate,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (createSelected) Mint else PanelLight,
                            contentColor = if (createSelected) Ink else SoftWhite
                        ),
                        border = if (createSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioText("TableDialogs.Text.42", "Dialogs and overlays", false, "CREATE ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (createSelected) StudioText("TableDialogs.Text.43", "Dialogs and overlays", false, "SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val joinSelected = joining
                    StudioButton("TableDialogs.Button.2", "Dialogs and overlays",
                        onClick = onSelectJoin,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (joinSelected) Mint else PanelLight,
                            contentColor = if (joinSelected) Ink else SoftWhite
                        ),
                        border = if (joinSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioText("TableDialogs.Text.44", "Dialogs and overlays", false, "JOIN ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (joinSelected) StudioText("TableDialogs.Text.45", "Dialogs and overlays", false, "SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val localSelected = !internetMode
                    StudioButton("TableDialogs.Button.3", "Dialogs and overlays",
                        onClick = { onSelectNetwork(false) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (localSelected) Mint else PanelLight,
                            contentColor = if (localSelected) Ink else SoftWhite
                        ),
                        border = if (localSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioText("TableDialogs.Text.46", "Dialogs and overlays", false, "LOCAL WI-FI", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (localSelected) StudioText("TableDialogs.Text.47", "Dialogs and overlays", false, "SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val internetSelected = internetMode
                    StudioButton("TableDialogs.Button.4", "Dialogs and overlays",
                        onClick = { onSelectNetwork(true) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (internetSelected) Mint else PanelLight,
                            contentColor = if (internetSelected) Ink else SoftWhite
                        ),
                        border = if (internetSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioText("TableDialogs.Text.48", "Dialogs and overlays", false, "INTERNET", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (internetSelected) StudioText("TableDialogs.Text.49", "Dialogs and overlays", false, "SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated || joining) {
                    if (joining) {
                        val isConnecting = onlineStatus == "Connecting"
                        StudioText("TableDialogs.Text.50", "Dialogs and overlays", true, if (internetMode) "Join an internet room" else "Join a room on the same Wi-Fi network", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode) {
                            StudioOutlinedTextField("TableDialogs.OutlinedTextField.3", "Dialogs and overlays", cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.51", "Dialogs and overlays", false, "Room service URL") })
                            StudioText("TableDialogs.Text.52", "Dialogs and overlays", false, "Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                        }
                        else StudioOutlinedTextField("TableDialogs.OutlinedTextField.4", "Dialogs and overlays", hostInput, onHostInput, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.53", "Dialogs and overlays", false, "Host IP address · port is automatic") }, placeholder = { StudioText("TableDialogs.Text.54", "Dialogs and overlays", false, "192.168.1.25") })
                        StudioOutlinedTextField("TableDialogs.OutlinedTextField.5", "Dialogs and overlays", playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.55", "Dialogs and overlays", false, "Your player name") })
                        StudioOutlinedTextField("TableDialogs.OutlinedTextField.6", "Dialogs and overlays", joinCode, onJoinCode, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.56", "Dialogs and overlays", false, "Room code") })
                        StudioOutlinedTextField("TableDialogs.OutlinedTextField.7", "Dialogs and overlays", joinPassword, onJoinPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.57", "Dialogs and overlays", false, "Password") }, visualTransformation = PasswordVisualTransformation())
                        StudioButton("TableDialogs.Button.5", "Dialogs and overlays", onClick = onJoin, enabled = !isConnecting && (if (internetMode) cloudEndpoint.isNotBlank() else hostInput.isNotBlank()) && joinCode.isNotBlank() && joinPassword.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp)) {
                            StudioText("TableDialogs.Text.58", "Dialogs and overlays", true, if (isConnecting) "CONNECTING…" else "JOIN GAME")
                        }
                        if (isConnecting) {
                            StudioOutlinedButton("TableDialogs.OutlinedButton.1", "Dialogs and overlays", onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                                StudioText("TableDialogs.Text.59", "Dialogs and overlays", false, "CANCEL JOIN", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isConnecting || notice != null) {
                            Surface(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = if (isConnecting) PanelLight else Color(0xFF3A3021),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isConnecting) CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                                    StudioText("TableDialogs.Text.60", "Dialogs and overlays", true,
                                        notice ?: onlineStatus,
                                        color = if (isConnecting) Mint else Color(0xFFE9978B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = if (isConnecting) 10.dp else 0.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        StudioText("TableDialogs.Text.61", "Dialogs and overlays", true, if (internetMode) "Create a password-protected internet room" else "Create a password-protected room", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode && onlineStatus == "Connecting") {
                            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(9.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                                    StudioText("TableDialogs.Text.62", "Dialogs and overlays", false, "Opening a secure internet room…", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                }
                            }
                            StudioOutlinedButton("TableDialogs.OutlinedButton.2", "Dialogs and overlays", onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                                StudioText("TableDialogs.Text.63", "Dialogs and overlays", false, "CANCEL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (internetMode) {
                                StudioOutlinedTextField("TableDialogs.OutlinedTextField.8", "Dialogs and overlays", cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.64", "Dialogs and overlays", false, "Room service URL") })
                                StudioText("TableDialogs.Text.65", "Dialogs and overlays", false, "Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                                StudioOutlinedTextField("TableDialogs.OutlinedTextField.9", "Dialogs and overlays", playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.66", "Dialogs and overlays", false, "Your player name") })
                            }
                            StudioOutlinedTextField("TableDialogs.OutlinedTextField.10", "Dialogs and overlays", password, onPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { StudioText("TableDialogs.Text.67", "Dialogs and overlays", false, "Room password · 4 characters minimum") }, visualTransformation = PasswordVisualTransformation())
                            if (notice != null) StudioText("TableDialogs.Text.68", "Dialogs and overlays", true, notice, color = Color(0xFFFF9C91), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            StudioButton("TableDialogs.Button.6", "Dialogs and overlays", onClick = onCreate, enabled = password.length >= 4 && (!internetMode || cloudEndpoint.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp)) { StudioText("TableDialogs.Text.69", "Dialogs and overlays", false, "CREATE LOBBY") }
                        }
                    }
                } else {
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    StudioText("TableDialogs.Text.70", "Dialogs and overlays", false, "ROOM CODE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    StudioText("TableDialogs.Text.71", "Dialogs and overlays", true, roomCode, color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                }
                                StudioTextButton("TableDialogs.TextButton.12", "Dialogs and overlays", onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table room code", roomCode))
                                }, modifier = Modifier) { StudioText("TableDialogs.Text.72", "Dialogs and overlays", false, "COPY", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                            StudioTextButton("TableDialogs.TextButton.13", "Dialogs and overlays", onClick = {
                                val network = if (internetMode) "Internet room · $cloudEndpoint" else "Same Wi-Fi · ${hostAddress.ifBlank { "ask the host to share their IP" }}"
                                val invite = "Join my Arcana Table game!\nRoom code: $roomCode\nPassword: $password\n$network"
                                runCatching {
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, invite)
                                    }, "Share room invite"))
                                }
                            }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) {
                                StudioText("TableDialogs.Text.73", "Dialogs and overlays", false, "SHARE ROOM", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            StudioText("TableDialogs.Text.74", "Dialogs and overlays", true, "PASSWORD PROTECTED · ${"•".repeat(password.length.coerceAtMost(12))}", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                StudioText("TableDialogs.Text.75", "Dialogs and overlays", true, if (hostAddress.isBlank()) onlineStatus else "SAME WI-FI · $hostAddress", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (hostAddress.isNotBlank()) StudioTextButton("TableDialogs.TextButton.14", "Dialogs and overlays", onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table host address", hostAddress))
                                }, modifier = Modifier) { StudioText("TableDialogs.Text.76", "Dialogs and overlays", false, "COPY IP", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioText("TableDialogs.Text.77", "Dialogs and overlays", false, "PLAYERS", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        StudioText("TableDialogs.Text.78", "Dialogs and overlays", true, "${players.size}/4 SEATS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    players.forEachIndexed { index, player ->
                        val ready = index in readyPlayers
                        val canMarkReady = index == localSeat || isHost && player.isComputer
                        val deckConfirmed = index != localSeat || !roomCreated || isHost || deckSyncConfirmed
                        val deckReady = player.deckLoaded && deckConfirmed
                        val handDecisionMade = player.openingHandKept || player.isComputer
                        val canToggleReady = !gameStarted && deckReady && handDecisionMade && canMarkReady && (index != localSeat || pendingReady == null)
                        Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (index == 0) Modifier else Modifier), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (ready) Mint else Panel).border(1.dp, if (ready) Mint else Muted, CircleShape), contentAlignment = Alignment.Center) {
                                        StudioText("TableDialogs.Text.79", "Dialogs and overlays", true, "P${index + 1}", color = if (ready) Ink else SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                                        StudioText("TableDialogs.Text.80", "Dialogs and overlays", true, player.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        StudioText("TableDialogs.Text.81", "Dialogs and overlays", true, "${if (index == 0) "HOST · " else ""}${if (player.isComputer) "AI · " else if (player.isOnline) "ONLINE · " else "OFFLINE · "}${when { !player.deckLoaded -> "DECK REQUIRED"; !deckConfirmed -> "DECK SYNCING"; !handDecisionMade -> "OPENING HAND NEEDED"; else -> "DECK READY" }}${if (index == localSeat) " · YOU" else ""}",
                                            color = if ((player.isOnline || player.isComputer || index == localSeat) && deckReady && handDecisionMade) Mint else CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    StudioText("TableDialogs.Text.82", "Dialogs and overlays", true, if (ready) "READY" else "NOT READY", color = if (ready) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                                    if (!player.deckLoaded && index == localSeat) StudioTextButton("TableDialogs.TextButton.15", "Dialogs and overlays", onClick = { onImportDeck(index) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.83", "Dialogs and overlays", false, "IMPORT DECK", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (!gameStarted && index == localSeat && deckReady && !player.openingHandKept && !player.isComputer) StudioTextButton("TableDialogs.TextButton.16", "Dialogs and overlays", onClick = { openingHandDialogOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.84", "Dialogs and overlays", true, "OPENING HAND · ${player.hand.size}", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                    StudioTextButton("TableDialogs.TextButton.17", "Dialogs and overlays", onClick = { onToggleReady(index) }, enabled = canToggleReady || ready, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.85", "Dialogs and overlays", true, if (gameStarted) "GAME STARTED" else if (index == localSeat && pendingReady != null) "SENDING…" else if (ready) "CANCEL READY" else if (!handDecisionMade && index == localSeat) "CHOOSE HAND" else if (canMarkReady) if (!deckConfirmed) "SYNCING…" else "READY UP" else "WAITING", color = if (canToggleReady || ready) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (isHost && index > 0 && !internetMode) StudioTextButton("TableDialogs.TextButton.18", "Dialogs and overlays", onClick = { removeTarget = index }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.86", "Dialogs and overlays", false, "REMOVE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    if (isHost && !gameStarted && players.size < 4) {
                        StudioOutlinedButton("TableDialogs.OutlinedButton.3", "Dialogs and overlays", onClick = onAddAi, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                            StudioText("TableDialogs.Text.87", "Dialogs and overlays", true, "ADD AI OPPONENT · ${players.size}/4", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (players.size < 4) {
                        repeat(4 - players.size) { seat ->
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), color = Panel.copy(alpha = 0.55f), shape = RoundedCornerShape(9.dp)) {
                                StudioText("TableDialogs.Text.88", "Dialogs and overlays", true, "P${players.size + seat + 1} · Waiting for a player to join with the room code", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(11.dp))
                            }
                        }
                    }

                    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                StudioText("TableDialogs.Text.89", "Dialogs and overlays", false, "GOES FIRST", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                StudioText("TableDialogs.Text.90", "Dialogs and overlays", true, firstPlayer, color = Mint, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                StudioText("TableDialogs.Text.91", "Dialogs and overlays", true, if (turnOrder == null) "Current seat order" else "Randomized turn order", color = Muted, fontSize = 8.sp)
                            }
                            if (isHost) StudioTextButton("TableDialogs.TextButton.19", "Dialogs and overlays", onClick = onRandomizeTurnOrder, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.92", "Dialogs and overlays", false, "RANDOMIZE ORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (notice != null) StudioText("TableDialogs.Text.93", "Dialogs and overlays", true, notice, color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    if (!decksReady) {
                        val missingDecks = players.filterNot { it.deckLoaded }.joinToString { it.name }
                        StudioText("TableDialogs.Text.94", "Dialogs and overlays", true, "Waiting for a deck from: $missingDecks", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (!allReady) {
                        val notReady = players.mapIndexedNotNull { index, player -> player.name.takeIf { index !in readyPlayers } }.joinToString()
                        StudioText("TableDialogs.Text.95", "Dialogs and overlays", true, "Waiting for ready confirmation from: $notReady", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (isHost && canStart) {
                        StudioText("TableDialogs.Text.96", "Dialogs and overlays", false, "Everyone is ready. Start the game when you’re set.", color = Mint, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    }

                    StudioOutlinedButton("TableDialogs.OutlinedButton.4", "Dialogs and overlays", onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) {
                        StudioText("TableDialogs.Text.97", "Dialogs and overlays", false, "EXPORT DIAGNOSTIC LOG", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isHost && !gameStarted) StudioButton("TableDialogs.Button.7", "Dialogs and overlays", onClick = onStartGame, enabled = canStart, modifier = Modifier.fillMaxWidth().padding(top = 11.dp).heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = PanelLight, disabledContentColor = Muted)) {
                        StudioText("TableDialogs.Text.98", "Dialogs and overlays", false, "START GAME", fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                    } else if (isHost) StudioText("TableDialogs.Text.99", "Dialogs and overlays", true, if (gameEnded) "GAME ENDED · unanimous vote" else "GAME IN PROGRESS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    else StudioText("TableDialogs.Text.100", "Dialogs and overlays", true, "$onlineStatus · Waiting for the host to start", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    if (isHost && gameStarted && !gameEnded && !canHostHandOff) {
                        StudioText("TableDialogs.Text.101", "Dialogs and overlays", false, "A connected player must be available to take over hosting before you can leave.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    StudioTextButton("TableDialogs.TextButton.20", "Dialogs and overlays", onClick = onLeave, enabled = canLeaveRoom, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        StudioText("TableDialogs.Text.102", "Dialogs and overlays", true,
                            when {
                                isHost && internetMode && gameStarted && !gameEnded -> "LEAVE GAME · KEEP ROOM OPEN"
                                isHost && gameStarted && !gameEnded -> "LEAVE GAME · TRANSFER HOST"
                                isHost -> "CLOSE ROOM"
                                else -> "LEAVE GAME"
                            },
                            color = if (canLeaveRoom) Color(0xFFE9978B) else Muted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }
        }
    }
    if (removeTarget in players.indices && removeTarget > 0) {
        val targetName = players[removeTarget].name
        StudioAlertDialog("Table Dialogs 6",
            onDismissRequest = { removeTarget = -1 },
            title = { StudioText("TableDialogs.Text.103", "Dialogs and overlays", true, "Remove $targetName?") },
            text = { StudioText("TableDialogs.Text.104", "Dialogs and overlays", false, "This removes the player and their seat from this room.") },
            confirmButton = {
                StudioTextButton("TableDialogs.TextButton.21", "Dialogs and overlays", onClick = { onRemovePlayer(removeTarget); removeTarget = -1 }) {
                    StudioText("TableDialogs.Text.105", "Dialogs and overlays", false, "REMOVE PLAYER", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { StudioTextButton("TableDialogs.TextButton.22", "Dialogs and overlays", onClick = { removeTarget = -1 }) { StudioText("TableDialogs.Text.106", "Dialogs and overlays", false, "CANCEL") } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }
    if (openingHandDialogOpen && !gameStarted && localSeat in players.indices && players[localSeat].deckLoaded && !players[localSeat].openingHandKept) {
        OpeningHandDialog(
            player = players[localSeat],
            onMulligan = onTakeMulligan,
            onKeep = { bottomIds -> onKeepOpeningHand(bottomIds); openingHandDialogOpen = false },
            onDismiss = { openingHandDialogOpen = false }
        )
    }
}

@Composable
internal fun OpeningHandDialog(player: TablePlayer, onMulligan: () -> Unit, onKeep: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var choosingBottomCards by remember(player.id) { mutableStateOf(false) }
    var selectedCardIds by remember(player.id, player.hand.map { it.id }, player.mulligansTaken) { mutableStateOf(emptyList<Int>()) }
    var previewCard by remember(player.id) { mutableStateOf<GameCard?>(null) }
    val bottomCount = (player.mulligansTaken - 1).coerceAtLeast(0)
    val canMulliganAgain = player.mulligansTaken < 8
    val openingCardWidth = (LocalConfiguration.current.screenWidthDp * 0.36f).dp.coerceIn(132.dp, 190.dp)
    StudioDialog("Table Dialogs 7", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                StudioText("TableDialogs.Text.107", "Dialogs and overlays", true, "OPENING HAND · ${player.hand.size} CARDS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                StudioText("TableDialogs.Text.108", "Dialogs and overlays", true, "Mulligans taken: ${player.mulligansTaken} · First multiplayer mulligan is free", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                StudioText("TableDialogs.Text.109", "Dialogs and overlays", true,
                    if (choosingBottomCards) "Select exactly $bottomCount card(s) to put on the bottom of your library, then confirm."
                    else "Tap a card to enlarge it. Keep this hand or mulligan; a mulligan shuffles your whole hand into your library, then deals a fresh seven.",
                    color = SoftWhite, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )
                if (choosingBottomCards) StudioText("TableDialogs.Text.110", "Dialogs and overlays", false, "Tap a card to enlarge it; use the button under a card to select it for the bottom.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    player.hand.forEach { card ->
                        val selected = card.id in selectedCardIds
                        Column(Modifier.width(openingCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(9.dp)).padding(2.dp)) {
                                CardFace(card, Modifier.fillMaxWidth(), onClick = { previewCard = card }, onLongClick = { previewCard = card }, showCounters = false)
                            }
                            StudioText("TableDialogs.Text.111", "Dialogs and overlays", true, card.name, color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            if (choosingBottomCards) {
                                StudioOutlinedButton("TableDialogs.OutlinedButton.5", "Dialogs and overlays",
                                    onClick = { selectedCardIds = if (selected) selectedCardIds - card.id else selectedCardIds + card.id },
                                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).heightIn(min = 44.dp),
                                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, if (selected) Mint else Muted)
                                ) {
                                    StudioText("TableDialogs.Text.112", "Dialogs and overlays", true, if (selected) "${selectedCardIds.indexOf(card.id) + 1} · BOTTOM" else "SELECT", color = if (selected) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (choosingBottomCards) {
                    StudioText("TableDialogs.Text.113", "Dialogs and overlays", true, "Selected: ${selectedCardIds.size}/$bottomCount", color = if (selectedCardIds.size == bottomCount) Mint else CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioOutlinedButton("TableDialogs.OutlinedButton.6", "Dialogs and overlays", onClick = { choosingBottomCards = false; selectedCardIds = emptyList() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.114", "Dialogs and overlays", false, "BACK", color = Muted) }
                        StudioButton("TableDialogs.Button.8", "Dialogs and overlays", onClick = { onKeep(selectedCardIds) }, enabled = selectedCardIds.size == bottomCount, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.115", "Dialogs and overlays", false, "CONFIRM KEEP") }
                    }
                } else {
                    // Two wide buttons per row so longer labels never wrap on a phone.
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioOutlinedButton("TableDialogs.OutlinedButton.7", "Dialogs and overlays", onClick = onMulligan, enabled = canMulliganAgain, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.116", "Dialogs and overlays", false, "MULLIGAN", color = if (canMulliganAgain) CounterGold else Muted, maxLines = 1) }
                        StudioButton("TableDialogs.Button.9", "Dialogs and overlays", onClick = { if (bottomCount == 0) onKeep(emptyList()) else choosingBottomCards = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.117", "Dialogs and overlays", true, if (bottomCount == 0) "KEEP HAND" else "KEEP · BOTTOM $bottomCount", maxLines = 1) }
                    }
                    StudioTextButton("TableDialogs.TextButton.23", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 4.dp).heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.118", "Dialogs and overlays", false, "DECIDE LATER", color = Muted) }
                }
            }
        }
    }
    previewCard?.let { card ->
        CardPreviewPopup(item = CardPreview(playerIndex = 0, card = card, fromHand = true), onDismiss = { previewCard = null })
    }
}

@Composable
internal fun OptionsPage(
    darkMode: Boolean, timerVisible: Boolean, backgroundAlertsEnabled: Boolean, showEngineStatus: Boolean,
    followActivePlayer: Boolean, connectionLabel: String, pingResult: String?, versionLabel: String, diagnosticCount: Int,
    reducedSwipeMotion: Boolean, cardSizeScale: Float, onOpenLayoutLab: () -> Unit,
    onToggleTheme: () -> Unit, onToggleTimerVisibility: () -> Unit, onToggleBackgroundAlerts: (Boolean) -> Unit,
    onShowEngineStatus: (Boolean) -> Unit, onFollowActivePlayer: (Boolean) -> Unit, onReducedMotion: (Boolean) -> Unit, onCardSizeScale: (Float) -> Unit,
    respondToOwnStack: Boolean = false, onRespondToOwnStack: (Boolean) -> Unit = {},
    quietOpponentTurns: Boolean = false, onQuietOpponentTurns: (Boolean) -> Unit = {},
    onTestConnection: () -> Unit, onReconnect: (() -> Unit)?, onViewDiagnostics: () -> Unit, onExportDiagnostics: () -> Unit,
    onWhatsNew: () -> Unit, onTutorials: () -> Unit = {}, onOpenStudio: (() -> Unit)? = null,
    engineTrialResult: String? = null, onEngineSelfTest: (() -> Unit)? = null,
    onClearAllData: () -> Unit, onDismiss: () -> Unit,
) {
    @Composable
    fun Section(title: String, top: androidx.compose.ui.unit.Dp = 14.dp) =
        StudioText("TableDialogs.Text.119", "Dialogs and overlays", true, title, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            modifier = Modifier.tutorialTarget(when (title) {
                "GAMEPLAY" -> "setting-gameplay"
                "NOTIFICATIONS" -> "setting-notifications"
                "HELP & DIAGNOSTICS" -> "setting-help-diagnostics"
                else -> "settings-section-${title.lowercase()}"
            }).padding(top = top, bottom = 2.dp))
    @Composable
    fun LinkRow(label: String, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
        StudioTextButton("TableDialogs.TextButton.24", "Dialogs and overlays", onClick = {
            TutorialActions.tap(when (label) {
                "Learn & tutorials" -> "tutorial-entry"
                "Diagnostic log" -> "diagnostic-log"
                "Export diagnostic log" -> "diagnostic-export"
                "Run engine self-test" -> "engine-self-test"
                else -> "settings-${label.lowercase()}"
            })
            onClick()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(modifier).tutorialTarget(
            when (label) {
                "Learn & tutorials" -> "tutorial-entry"
                "Diagnostic log" -> "diagnostic-log"
                "Export diagnostic log" -> "diagnostic-export"
                "Run engine self-test" -> "engine-self-test"
                else -> "settings-${label.lowercase()}"
            }
        )) {
            Column(Modifier.fillMaxWidth()) {
                StudioText("TableDialogs.Text.120", "Dialogs and overlays", true, label, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (detail != null) StudioText("TableDialogs.Text.121", "Dialogs and overlays", true, detail, color = Muted, fontSize = 10.sp)
            }
        }
    StudioDialog("Table Dialogs 8", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.122", "Dialogs and overlays", false, "OPTIONS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.4", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.4", "Dialogs and overlays", Icons.Filled.Close, "Close options", tint = Muted) }
                }
                Section("APPEARANCE", top = 8.dp)
                StudioTextButton("TableDialogs.TextButton.25", "Dialogs and overlays", onClick = onToggleTheme, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.123", "Dialogs and overlays", true, "Theme  ·  ${if (darkMode) "Dark" else "Light"}", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                SettingsToggle("Reduce swipe animation", "Skip the animated jump when switching player tabs.", reducedSwipeMotion, onReducedMotion, Modifier)
                StudioText("TableDialogs.Text.124", "Dialogs and overlays", false, "CARD SIZE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tutorialTarget("setting-card-size").padding(top = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.82f to "SMALL", 1f to "STANDARD", 1.18f to "LARGE").forEach { (scale, label) ->
                        StudioTextButton("TableDialogs.TextButton.26", "Dialogs and overlays", onClick = { onCardSizeScale(scale) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.125", "Dialogs and overlays", true, if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) "✓ $label" else label, color = if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) Mint else SoftWhite, fontSize = 9.sp) }
                    }
                }
                // Arcana Studio replaces "Adjust UI layout" behind the single STUDIO_ENABLED switch (owner D33).
                if (onOpenStudio != null) {
                    androidx.compose.material3.TextButton(onClick = onOpenStudio, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight).tutorialTarget("setting-adjust-ui").testTag("settings-open-studio")) {
                        androidx.compose.material3.Text("ARCANA STUDIO  ›", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    }
                } else StudioTextButton("TableDialogs.TextButton.27", "Dialogs and overlays", onClick = onOpenLayoutLab, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).tutorialTarget("setting-adjust-ui").clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                    StudioText("TableDialogs.Text.126", "Dialogs and overlays", false, "ADJUST UI LAYOUT  ›", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                }
                Section("GAMEPLAY")
                SettingsToggle("Follow the active player", "Show the player whose turn it is. Your selected tab stays until the next turn.", followActivePlayer, onFollowActivePlayer, Modifier)
                SettingsToggle("Respond to my spells and abilities", "Offline games: keep priority over your own stack when you have a legal response. Turn this on before casting to build Ulalek stacks.", respondToOwnStack, onRespondToOwnStack, Modifier)
                SettingsToggle("Skip AI actions unless needed", "Offline games: pass over AI spells and abilities unless they affect you. Prompts and blocks still stop the game.", quietOpponentTurns, onQuietOpponentTurns, Modifier)
                SettingsToggle("Show turn timer", "Show the time spent on the current turn.", timerVisible, { onToggleTimerVisibility() }, Modifier)
                Section("NOTIFICATIONS")
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.127", "Dialogs and overlays", false, "Alerts while the app is in the background", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        StudioText("TableDialogs.Text.128", "Dialogs and overlays", false, "Notify you when Argentum is waiting for your move.", color = Muted, fontSize = 10.sp)
                    }
                    StudioSwitch("TableDialogs.Switch.1", "Dialogs and overlays", checked = backgroundAlertsEnabled, onCheckedChange = onToggleBackgroundAlerts)
                }
                Section("HELP & DIAGNOSTICS")
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).tutorialTarget("engine-processing-toggle"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        StudioText("TableDialogs.Text.129", "Dialogs and overlays", false, "Engine running notice", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        StudioText("TableDialogs.Text.130", "Dialogs and overlays", false, "Show the in-game processing banner and elapsed time while Argentum works.", color = Muted, fontSize = 10.sp)
                    }
                    StudioSwitch("TableDialogs.Switch.2", "Dialogs and overlays", checked = showEngineStatus, onCheckedChange = onShowEngineStatus,
                        modifier = Modifier.testTag("engine-processing-toggle"))
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelLight).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    StudioText("TableDialogs.Text.131", "Dialogs and overlays", true, connectionLabel, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (pingResult != null) StudioText("TableDialogs.Text.132", "Dialogs and overlays", true, pingResult, color = if (pingResult.startsWith("Can't")) Color(0xFFE9978B) else Muted, fontSize = 10.sp)
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudioOutlinedButton("TableDialogs.OutlinedButton.8", "Dialogs and overlays", onClick = onTestConnection, enabled = pingResult != "Testing…",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("diagnostics-test-connection")) {
                            StudioText("TableDialogs.Text.133", "Dialogs and overlays", false, "TEST CONNECTION", color = if (pingResult == "Testing…") Muted else Mint)
                        }
                        if (onReconnect != null) StudioOutlinedButton("TableDialogs.OutlinedButton.9", "Dialogs and overlays", onClick = onReconnect,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("diagnostics-reconnect")) {
                            StudioText("TableDialogs.Text.134", "Dialogs and overlays", false, "RECONNECT", color = Mint)
                        }
                    }
                }
                LinkRow("Diagnostic log", "Search $diagnosticCount recorded events", onClick = onViewDiagnostics)
                LinkRow("Export diagnostic log", "Save a text file to share when something goes wrong", onClick = onExportDiagnostics)
                LinkRow("Learn & tutorials", "20 chapters, 160 lessons and a Magic glossary · works offline", onClick = onTutorials)
                LinkRow("What's new", "Version $versionLabel", onClick = onWhatsNew)
                if (onEngineSelfTest != null) LinkRow(
                    "Run engine self-test",
                    engineTrialResult ?: "Runs a local AI-vs-AI rules check using Argentum. No server needed.",
                    modifier = Modifier.testTag("engine-self-test"),
                    onClick = { if (engineTrialResult?.startsWith("Running") != true) onEngineSelfTest() })
                HorizontalDivider(color = Muted.copy(alpha = 0.25f), modifier = Modifier.padding(top = 14.dp))
                Section("RESET", top = 8.dp)
                StudioTextButton("TableDialogs.TextButton.28", "Dialogs and overlays", onClick = onClearAllData, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 6.dp)) {
                    StudioText("TableDialogs.Text.135", "Dialogs and overlays", false, "CLEAR ALL DATA", color = Color(0xFFE9978B), fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth())
                }
                StudioText("TableDialogs.Text.136", "Dialogs and overlays", false, "Saved decks are kept when game data is cleared.", color = Muted, fontSize = 10.sp)
            }
            TutorialLayer()
            }
        }
    }
}

@Composable
internal fun LayoutLabPage(
    orientationName: String,
    cardSizeScale: Float,
    boardSpacingScale: Float,
    handSheetHeightFraction: Float,
    footerButtonScale: Float,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    handActionOrder: List<String>,
    handArrangement: HandArrangement,
    handCardScale: Float,
    showEngineStatus: Boolean,
    onHandArrangement: (HandArrangement) -> Unit,
    onHandCardScale: (Float) -> Unit,
    onShowEngineStatus: (Boolean) -> Unit,
    onCardSizeScale: (Float) -> Unit,
    onBoardSpacingScale: (Float) -> Unit,
    onHandSheetHeightFraction: (Float) -> Unit,
    onFooterButtonScale: (Float) -> Unit,
    onPlayerSectionOrder: (List<String>) -> Unit,
    onQuickActionOrder: (List<String>) -> Unit,
    onHandActionOrder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var previewScreen by remember { mutableStateOf("PLAYER") }
    var selectedElement by remember { mutableStateOf("BATTLEFIELD") }
    var addElementMenuOpen by remember { mutableStateOf(false) }
    val playerSectionCatalog = listOf("STATUS", "MANA", "COMMANDER", "STEP", "ZONES", "REVEALED", "BATTLEFIELD")
    val quickActionCatalog = listOf("LIBRARY", "HAND", "GRAVEYARD", "EXILE", "REVEALED", "MANA", "DRAW", "REVEAL", "STACK", "TOKEN", "COMBAT", "CAST_COMMANDER", "NEXT_STEP", "SHUFFLE", "LOG", "CHAT")
    val handActionCatalog = listOf("DRAW", "REVEAL", "STACK", "CAST_COMMANDER", "COMBAT", "NEXT_STEP", "PASS_TURN", "MORE")
    fun moveItem(items: List<String>, index: Int, direction: Int, save: (List<String>) -> Unit) {
        val destination = (index + direction).coerceIn(0, items.lastIndex)
        if (destination == index) return
        val updated = items.toMutableList().apply { add(destination, removeAt(index)) }
        save(updated)
    }
    fun setPreset(card: Float, spacing: Float, hand: Float, footer: Float) {
        onCardSizeScale(card)
        onBoardSpacingScale(spacing)
        onHandSheetHeightFraction(hand)
        onFooterButtonScale(footer)
    }
    StudioDialog("Table Dialogs 9", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.137", "Dialogs and overlays", true, "ADJUST UI · $orientationName", color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        StudioText("TableDialogs.Text.138", "Dialogs and overlays", true, "$orientationName layout · saves automatically", color = Muted, fontSize = 10.sp)
                    }
                    StudioIconButton("TableDialogs.IconButton.5", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        StudioIcon("TableDialogs.Icon.5", "Dialogs and overlays", Icons.Filled.Close, "Close layout lab", tint = Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("PLAYER", "HAND").forEach { screen ->
                        val selected = previewScreen == screen
                        StudioTextButton("TableDialogs.TextButton.29", "Dialogs and overlays",
                            onClick = { previewScreen = screen; selectedElement = if (screen == "HAND") "HAND" else "BATTLEFIELD" },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (selected) PanelLight else Color.Transparent)
                        ) { StudioText("TableDialogs.Text.139", "Dialogs and overlays", true, screen, color = if (selected) Mint else Muted, fontWeight = FontWeight.Bold) }
                    }
                }
                LayoutLabPreview(
                    previewScreen = previewScreen,
                    selectedElement = selectedElement,
                    cardSizeScale = cardSizeScale,
                    boardSpacingScale = boardSpacingScale,
                    handSheetHeightFraction = handSheetHeightFraction,
                    footerButtonScale = footerButtonScale,
                    playerSectionOrder = playerSectionOrder,
                    quickActionOrder = quickActionOrder,
                    handActionOrder = handActionOrder,
                    onSelectElement = { selectedElement = it },
                    onMovePlayerElement = { id, direction ->
                        val index = playerSectionOrder.indexOf(id)
                        if (index >= 0) moveItem(playerSectionOrder, index, direction, onPlayerSectionOrder)
                    },
                    onMoveQuickAction = { id, direction ->
                        val index = quickActionOrder.indexOf(id)
                        if (index >= 0) moveItem(quickActionOrder, index, direction, onQuickActionOrder)
                    },
                    onMoveHandAction = { id, direction ->
                        val index = handActionOrder.indexOf(id)
                        if (index >= 0) moveItem(handActionOrder, index, direction, onHandActionOrder)
                    }
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("COMFORTABLE", "LARGER CARDS", "COMPACT").forEach { preset ->
                        StudioTextButton("TableDialogs.TextButton.30", "Dialogs and overlays",
                            onClick = {
                                when (preset) {
                                    "LARGER CARDS" -> setPreset(1.38f, 1.05f, 0.64f, 1f)
                                    "COMPACT" -> setPreset(0.9f, 0.72f, 0.46f, 0.8f)
                                    else -> setPreset(1f, 1.2f, 0.58f, 1.05f)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            StudioText("TableDialogs.Text.140", "Dialogs and overlays", true, preset, color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp)
                        }
                    }
                }
                StudioText("TableDialogs.Text.141", "Dialogs and overlays", true, "ADJUST · $selectedElement", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (selectedElement) {
                        "BATTLEFIELD" -> {
                            LayoutTuningSlider("Card size", cardSizeScale, 0.75f..1.4f, { "${(it * 100).toInt()}%" }, onCardSizeScale)
                        }
                        "STATUS" -> {
                            StudioText("TableDialogs.Text.142", "Dialogs and overlays", false, "This element can be removed below and restored from ADD ELEMENT.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                        "COMMANDER" -> StudioText("TableDialogs.Text.143", "Dialogs and overlays", false, "This element can be removed below and restored from ADD ELEMENT.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "ZONES" -> StudioText("TableDialogs.Text.144", "Dialogs and overlays", false, "Choose which zone shortcuts and actions appear in the Quick Actions strip. Empty zones stay visible with a count of 0.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "SPACING", "STEP", "REVEALED" -> LayoutTuningSlider("Space between sections", boardSpacingScale, 0.5f..2f, { "${(it * 100).toInt()}%" }, onBoardSpacingScale)
                        "HAND" -> {
                            LayoutTuningSlider("Hand tray height", handSheetHeightFraction, 0.38f..0.95f, { "${(it * 100).toInt()}%" }, onHandSheetHeightFraction)
                            LayoutTuningSlider("Largest hand card", handCardScale, 0.7f..1.4f, { "${(it * 100).toInt()}%" }, onHandCardScale)
                            StudioText("TableDialogs.Text.145", "Dialogs and overlays", false, "HAND ARRANGEMENT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HandArrangement.entries.forEach { option ->
                                    StudioFilterChip("TableDialogs.FilterChip.3", "Dialogs and overlays", selected = handArrangement == option, onClick = { onHandArrangement(option) }, label = { StudioText("TableDialogs.Text.146", "Dialogs and overlays", true, option.label, fontSize = 11.sp) })
                                }
                            }
                            StudioText("TableDialogs.Text.147", "Dialogs and overlays", true, handArrangement.detail, color = Muted, fontSize = 10.sp)
                        }
                        "FOOTER" -> LayoutTuningSlider("Footer button size", footerButtonScale, 0.78f..1.3f, { "${(it * 100).toInt()}%" }, onFooterButtonScale)
                    }
                    StudioText("TableDialogs.Text.148", "Dialogs and overlays", false, "Tap an element to tune it. Long-press and drag elements in the sample layout; nearby items animate aside as you move.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 8.dp))
                    if (previewScreen == "PLAYER") {
                        StudioText("TableDialogs.Text.149", "Dialogs and overlays", false, "PLAYER SCREEN ELEMENTS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                        playerSectionOrder.forEachIndexed { index, id ->
                            LayoutOrderRow(
                                title = layoutSectionTitle(id),
                                subtitle = if (id == "STATUS") "Health, mana and activity" else if (id == "COMMANDER") "Commander card and cast control" else "Drag handle · long press to move",
                                selected = selectedElement == id,
                                onSelect = { selectedElement = id },
                                onMove = { direction -> moveItem(playerSectionOrder, index, direction, onPlayerSectionOrder) },
                                onRemove = { onPlayerSectionOrder(playerSectionOrder - id) }
                            )
                        }
                        if ("ZONES" in playerSectionOrder) {
                            StudioText("TableDialogs.Text.150", "Dialogs and overlays", false, "QUICK ACTIONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                            quickActionOrder.forEachIndexed { index, id ->
                                LayoutOrderRow(
                                    title = layoutQuickActionTitle(id),
                                    subtitle = "Shown in the Quick Actions strip",
                                    selected = selectedElement == "QUICK:$id",
                                    onSelect = { selectedElement = "QUICK:$id" },
                                    onMove = { direction -> moveItem(quickActionOrder, index, direction, onQuickActionOrder) },
                                    onRemove = { onQuickActionOrder(quickActionOrder - id) }
                                )
                            }
                            Box {
                                StudioTextButton("TableDialogs.TextButton.31", "Dialogs and overlays", onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { StudioText("TableDialogs.Text.151", "Dialogs and overlays", false, "ADD QUICK ACTION  +", color = Mint, fontWeight = FontWeight.Bold) }
                                DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                    quickActionCatalog.filterNot { it in quickActionOrder }.forEach { id ->
                                        StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.1", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.152", "Dialogs and overlays", true, "Add ${layoutQuickActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onQuickActionOrder(quickActionOrder + id) })
                                    }
                                }
                            }
                        }
                        Box {
                            StudioTextButton("TableDialogs.TextButton.32", "Dialogs and overlays", onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { StudioText("TableDialogs.Text.153", "Dialogs and overlays", false, "ADD ELEMENT  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                playerSectionCatalog.filterNot { it in playerSectionOrder }.forEach { id ->
                                    StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.2", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.154", "Dialogs and overlays", true, "Add ${layoutSectionTitle(id)}") }, onClick = { addElementMenuOpen = false; onPlayerSectionOrder(playerSectionOrder + id) })
                                }
                            }
                        }
                    } else {
                        StudioText("TableDialogs.Text.155", "Dialogs and overlays", false, "HAND TRAY BUTTONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                        handActionOrder.forEachIndexed { index, id ->
                            LayoutOrderRow(
                                title = layoutHandActionTitle(id),
                                subtitle = "Shown when the action is available",
                                selected = selectedElement == id,
                                onSelect = { selectedElement = id },
                                onMove = { direction -> moveItem(handActionOrder, index, direction, onHandActionOrder) },
                                onRemove = { onHandActionOrder(handActionOrder - id) }
                            )
                        }
                        Box {
                            StudioTextButton("TableDialogs.TextButton.33", "Dialogs and overlays", onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { StudioText("TableDialogs.Text.156", "Dialogs and overlays", false, "ADD BUTTON  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                handActionCatalog.filterNot { it in handActionOrder }.forEach { id ->
                                    StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.3", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.157", "Dialogs and overlays", true, "Add ${layoutHandActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onHandActionOrder(handActionOrder + id) })
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioTextButton("TableDialogs.TextButton.34", "Dialogs and overlays", onClick = onReset, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        StudioText("TableDialogs.Text.158", "Dialogs and overlays", false, "RESET TO DEFAULT", color = Color(0xFFE5B2A7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    StudioButton("TableDialogs.Button.10", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelLight)) {
                        StudioText("TableDialogs.Text.159", "Dialogs and overlays", false, "DONE", color = Mint, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LayoutLabPreview(
    previewScreen: String,
    selectedElement: String,
    cardSizeScale: Float,
    boardSpacingScale: Float,
    handSheetHeightFraction: Float,
    footerButtonScale: Float,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    handActionOrder: List<String>,
    onSelectElement: (String) -> Unit,
    onMovePlayerElement: (String, Int) -> Unit,
    onMoveQuickAction: (String, Int) -> Unit,
    onMoveHandAction: (String, Int) -> Unit
) {
    val sectionGap = (4.dp * boardSpacingScale).coerceIn(2.dp, 10.dp)
    val previewHeight = if (previewScreen == "HAND") (160.dp + 150.dp * handSheetHeightFraction).coerceIn(205.dp, 285.dp) else 226.dp
    Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(13.dp)).background(Color(0xFF101917))) {
        if (previewScreen == "PLAYER") {
            LazyColumn(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                items(playerSectionOrder, key = { "section-$it" }) { section ->
                    LayoutPreviewDraggable(
                        elementId = "section-$section",
                        onSelect = { onSelectElement(section) },
                        onMove = { direction -> onMovePlayerElement(section, direction) }
                    ) { dragModifier -> when (section) {
                    "STATUS" -> Row(
                        dragModifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "STATUS") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp))
                            .clickable { onSelectElement("STATUS") }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioText("TableDialogs.Text.160", "Dialogs and overlays", false, "● YOU · ONLINE · ACTIVE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StudioText("TableDialogs.Text.161", "Dialogs and overlays", false, "40  +", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    "MANA" -> Row(
                        dragModifier.fillMaxWidth().height(25.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "MANA") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("MANA") }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioText("TableDialogs.Text.162", "Dialogs and overlays", false, "MANA POOL", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StudioText("TableDialogs.Text.163", "Dialogs and overlays", false, "3 TOTAL", color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    "COMMANDER" -> Row(
                        dragModifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "COMMANDER") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("COMMANDER") }.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { StudioText("TableDialogs.Text.164", "Dialogs and overlays", false, "COMMAND ZONE · COMMANDER", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                    "STEP" -> Row(dragModifier.fillMaxWidth().height(24.dp).clickable { onSelectElement("STEP") }, verticalAlignment = Alignment.CenterVertically) {
                        StudioText("TableDialogs.Text.165", "Dialogs and overlays", false, "NEXT STEP  ›", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 5.dp))
                    }
                    "ZONES" -> Row(dragModifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable { onSelectElement("ZONES") }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        quickActionOrder.forEach { id ->
                            StudioText("TableDialogs.Text.166", "Dialogs and overlays", true, "${layoutQuickActionTitle(id).uppercase()}${if (id in setOf("LIBRARY", "HAND", "GRAVEYARD", "EXILE", "REVEALED")) " 0" else ""}", color = SoftWhite, fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PanelLight).padding(horizontal = 5.dp, vertical = 4.dp))
                        }
                    }
                    "REVEALED" -> StudioText("TableDialogs.Text.167", "Dialogs and overlays", false, "PUBLIC REVEALS", color = CounterGold, fontSize = 7.sp, modifier = dragModifier.clickable { onSelectElement("REVEALED") }.padding(start = 4.dp))
                    "BATTLEFIELD" -> Column(
                        dragModifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "BATTLEFIELD") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp))
                            .clickable { onSelectElement("BATTLEFIELD") }.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        StudioText("TableDialogs.Text.168", "Dialogs and overlays", false, "BATTLEFIELD · grouped by type", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tileWidth = (38.dp * cardSizeScale).coerceIn(31.dp, 54.dp)
                            val tileHeight = tileWidth / 0.72f
                            listOf("CREATURE", "ARTIFACT", "LAND").forEachIndexed { index, label ->
                                Column(Modifier.width(tileWidth).height(tileHeight).clip(RoundedCornerShape(5.dp)).background(listOf(Color(0xFF66513D), Color(0xFF364C52), Color(0xFF4A644C))[index]).padding(3.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    StudioText("TableDialogs.Text.169", "Dialogs and overlays", true, label, color = SoftWhite, fontSize = 5.sp, maxLines = 1)
                                    Box(Modifier.fillMaxWidth().height((tileHeight * 0.5f).coerceAtLeast(18.dp)).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.14f)))
                                    StudioText("TableDialogs.Text.170", "Dialogs and overlays", false, "CARD NAME", color = SoftWhite, fontSize = 5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                    }
                }
                item(key = "preview-footer") {
                    Row(
                Modifier.fillMaxWidth().height((34.dp * footerButtonScale).coerceIn(28.dp, 46.dp)).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "FOOTER") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp)).clickable { onSelectElement("FOOTER") },
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("PASS TURN", "HAND", "BACK TO TABLE").forEach { label ->
                            Box(Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF293934)), contentAlignment = Alignment.Center) {
                                StudioText("TableDialogs.Text.171", "Dialogs and overlays", true, label, color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            Row(Modifier.fillMaxWidth().height(30.dp).clickable { onSelectElement("HAND") }, verticalAlignment = Alignment.CenterVertically) {
                StudioText("TableDialogs.Text.172", "Dialogs and overlays", false, "YOUR HAND · 5 CARDS", color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StudioText("TableDialogs.Text.173", "Dialogs and overlays", false, "×", color = SoftWhite, fontSize = 18.sp)
            }
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                    .border(if (selectedElement == "HAND") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp)).clickable { onSelectElement("HAND") }
                    ,
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                items(handActionOrder, key = { "hand-action-$it" }) { id ->
                    LayoutPreviewDraggable(
                        elementId = "hand-action-$id",
                        horizontal = true,
                        onSelect = { onSelectElement(id) },
                        onMove = { direction -> onMoveHandAction(id, direction) }
                    ) { dragModifier ->
                        StudioText("TableDialogs.Text.174", "Dialogs and overlays", true, layoutHandActionTitle(id).uppercase(), color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold,
                            modifier = dragModifier.clip(RoundedCornerShape(4.dp))
                                .border(if (selectedElement == id) 1.dp else 0.dp, if (selectedElement == id) Mint else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { onSelectElement(id) }.padding(horizontal = 5.dp), maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                    .border(if (selectedElement == "HAND") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp)).clickable { onSelectElement("HAND") }.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val cardWidth = (44.dp * cardSizeScale).coerceIn(34.dp, 62.dp)
                repeat(3) { index ->
                    Box(Modifier.width(cardWidth).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(listOf(Color(0xFF7A513F), Color(0xFF3D6674), Color(0xFF665A3D))[index]), contentAlignment = Alignment.BottomCenter) {
                        StudioText("TableDialogs.Text.175", "Dialogs and overlays", false, "SAMPLE CARD", color = SoftWhite, fontSize = 5.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun LazyItemScope.LayoutPreviewDraggable(
    elementId: String,
    horizontal: Boolean = false,
    onSelect: () -> Unit = {},
    onMove: (Int) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val dragThreshold = with(density) { 30.dp.toPx() }
    val moveCurrent by rememberUpdatedState(onMove)
    val selectCurrent by rememberUpdatedState(onSelect)
    var dragging by remember(elementId) { mutableStateOf(false) }
    var dragOffset by remember(elementId) { mutableFloatStateOf(0f) }
    val dragModifier = Modifier
        .animateItem()
        .graphicsLayer {
            if (horizontal) translationX = dragOffset else translationY = dragOffset
            scaleX = if (dragging) 1.035f else 1f
            scaleY = if (dragging) 1.035f else 1f
            shadowElevation = if (dragging) 12.dp.toPx() else 0f
        }
        .zIndex(if (dragging) 1f else 0f)
        .pointerInput(elementId, horizontal, dragThreshold) {
            var accumulated = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { accumulated = 0f; dragOffset = 0f; dragging = true; selectCurrent() },
                onDragEnd = { dragOffset = 0f; dragging = false },
                onDragCancel = { dragOffset = 0f; dragging = false },
                onDrag = { change, amount ->
                    change.consume()
                    val delta = if (horizontal) amount.x else amount.y
                    accumulated += delta
                    dragOffset += delta
                    while (accumulated >= dragThreshold) {
                        moveCurrent(1)
                        accumulated -= dragThreshold
                        dragOffset -= dragThreshold
                    }
                    while (accumulated <= -dragThreshold) {
                        moveCurrent(-1)
                        accumulated += dragThreshold
                        dragOffset += dragThreshold
                    }
                }
            )
        }
    content(dragModifier)
}

@Composable
internal fun RulesLookupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheFile = remember { java.io.File(context.filesDir, "magic-comprehensive-rules.txt") }
    var rulesText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (cacheFile.exists()) rulesText = withContext(Dispatchers.IO) { cacheFile.readText() }
    }
    val ruleLines = remember(rulesText, query) {
        val all = rulesText.lineSequence().toList()
        if (query.isBlank()) all else all.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    StudioDialog("Table Dialogs 10", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.176", "Dialogs and overlays", false, "MAGIC RULEBOOK", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        StudioText("TableDialogs.Text.177", "Dialogs and overlays", true, if (rulesText.isNotBlank()) rulesText.lineSequence().take(3).lastOrNull { it.contains("effective as of", true) } ?: "Official Wizards Comprehensive Rules" else "Official Wizards Comprehensive Rules · offline after download", color = Muted, fontSize = 9.sp)
                    }
                    StudioIconButton("TableDialogs.IconButton.6", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.6", "Dialogs and overlays", Icons.Filled.Close, "Close rules", tint = Muted) }
                }
                StudioText("TableDialogs.Text.178", "Dialogs and overlays", false, "This reader uses Wizards’ official Comprehensive Rules text. Download it once to browse offline; check for updates when connected.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioOutlinedTextField("TableDialogs.OutlinedTextField.11", "Dialogs and overlays", query, { query = it }, Modifier.weight(1f), singleLine = true, label = { StudioText("TableDialogs.Text.179", "Dialogs and overlays", false, "Search rule number or phrase") })
                    StudioTextButton("TableDialogs.TextButton.35", "Dialogs and overlays", onClick = {
                        loading = true
                        status = "Downloading official rules…"
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val connection = java.net.URL("https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt").openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 15_000
                                    connection.readTimeout = 25_000
                                    connection.setRequestProperty("User-Agent", "ArcanaTable/0.15.24")
                                    try {
                                        if (connection.responseCode !in 200..299) error("Wizards returned ${connection.responseCode}.")
                                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                                        if (!text.startsWith("Magic: The Gathering Comprehensive Rules")) error("The downloaded file was not recognized as the official rules text.")
                                        cacheFile.writeText(text)
                                        text
                                    } finally { connection.disconnect() }
                                }
                            }
                            result.onSuccess { rulesText = it; status = "Official rules downloaded for offline use." }
                                .onFailure { status = "Download failed. Check your connection and try again." }
                            loading = false
                        }
                    }, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.180", "Dialogs and overlays", true, if (loading) "DOWNLOADING…" else if (rulesText.isBlank()) "DOWNLOAD" else "UPDATE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
                if (status.isNotBlank()) StudioText("TableDialogs.Text.181", "Dialogs and overlays", true, status, color = if (rulesText.isNotBlank()) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                if (rulesText.isBlank()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        StudioText("TableDialogs.Text.182", "Dialogs and overlays", false, "Download the official Comprehensive Rules to start browsing and searching. The downloaded copy stays on this device for offline use.", color = Muted, fontSize = 12.sp)
                    }
                } else {
                    StudioText("TableDialogs.Text.183", "Dialogs and overlays", true, "${ruleLines.size} matching lines", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(ruleLines.size) { index ->
                            StudioText("TableDialogs.Text.184", "Dialogs and overlays", true, ruleLines[index], color = if (ruleLines[index].matches(Regex("^\\d+(?:\\.[a-z]+)?\\..*"))) Mint else SoftWhite, fontSize = 9.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
                StudioText("TableDialogs.Text.185", "Dialogs and overlays", false, "Source: Wizards of the Coast · magic.wizards.com/en/rules", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
internal fun HandCardActionsDialog(card: GameCard, hasCommander: Boolean, isCommanderCard: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    StudioDialog("Table Dialogs 11", onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth()) {
            // Let the dialog wrap its title and actions. fillMaxSize() here made
            // a tiny card menu claim the Dialog's full available height.
            Box {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.186", "Dialogs and overlays", true, card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StudioText("TableDialogs.Text.187", "Dialogs and overlays", false, "Choose an action", color = Muted, fontSize = 10.sp)
                    }
                    StudioIconButton("TableDialogs.IconButton.7", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(42.dp)) { StudioIcon("TableDialogs.Icon.7", "Dialogs and overlays", Icons.Filled.Close, "Close actions", tint = Muted) }
                }
                val actions = listOf("PLAY", "PLAY FACE DOWN", "REVEAL", "EXILE", "DISCARD", "INSPECT") + if (isCommanderCard) listOf("COMMAND ZONE") else if (hasCommander) emptyList() else listOf("SET COMMANDER")
                actions.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { action ->
                            StudioTextButton("TableDialogs.TextButton.36", "Dialogs and overlays", onClick = { onAction(action) }, modifier = Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                                // Button text says where the card goes; the action key stays the same.
                                val label = when (action) {
                                    "REVEAL" -> "REVEAL · TO REVEALED"
                                    "COMMAND ZONE" -> "TO COMMAND ZONE"
                                    else -> action
                                }
                                StudioText("TableDialogs.Text.188", "Dialogs and overlays", true, label, color = if (action == "DISCARD" || action == "EXILE") Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun DeckEditorDialog(
    player: TablePlayer,
    highlightedCardIds: Set<Int>,
    onRename: (GameCard, String) -> Unit,
    onRemove: (GameCard) -> Unit,
    onAdd: (ScryfallCard) -> Unit,
    onReplace: (GameCard, ScryfallCard) -> Unit,
    onDismiss: () -> Unit,
    loadSupportCatalog: (suspend () -> SupportedCardCatalog?)? = null
) {
    var query by remember { mutableStateOf("") }
    // Shipped Argentum library (DeckSupport.kt): flags, offline search and swap suggestions.
    var supportCatalog by remember { mutableStateOf<SupportedCardCatalog?>(null) }
    var argentumOnly by remember { mutableStateOf(true) }
    var suggestingFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { supportCatalog = loadSupportCatalog?.invoke() }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var menuCard by remember { mutableStateOf<GameCard?>(null) }
    var renameCard by remember { mutableStateOf<GameCard?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var replacementTarget by remember { mutableStateOf<GameCard?>(null) }
    var autoFixingCardId by remember { mutableStateOf<Int?>(null) }
    var autoFixStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun autoFix(target: GameCard, copiesToFix: List<GameCard>) {
        autoFixingCardId = target.id
        autoFixStatus = "Looking up ${target.name}…"
        scope.launch {
            val search = runCatching { Scryfall.searchCards(target.name) }
            val exact = search.getOrNull()?.firstOrNull {
                it.name.equals(target.name, ignoreCase = true) && !it.imageUrl.isNullOrBlank()
            }
            val fuzzy = if (exact == null) runCatching { Scryfall.fuzzyNamedCard(target.name) }.getOrNull() else null
            val candidate = exact ?: fuzzy?.takeIf {
                it.imageUrl?.isNotBlank() == true && cardNameSimilarity(target.name, it.name) >= 0.78
            }
            if (candidate != null) {
                val flaggedCopies = copiesToFix.filter { it.importIssue != null || it.id in highlightedCardIds }
                flaggedCopies.forEach { onReplace(it, candidate) }
                autoFixStatus = if (candidate.name.equals(target.name, ignoreCase = true)) {
                    "Fixed ${flaggedCopies.size} ${target.name} ${if (flaggedCopies.size == 1) "card" else "cards"}."
                } else {
                    "Fixed ${flaggedCopies.size} flagged card(s) as ${candidate.name} (closest clear match)."
                }
            } else {
                replacementTarget = target
                query = target.name
                results = search.getOrNull().orEmpty()
                autoFixStatus = if (search.isFailure) "Card search failed. Check your connection and try again." else "No safe automatic match. Choose the right card from the search results."
            }
            autoFixingCardId = null
        }
    }
    val trackedCards = player.library + player.hand + player.battlefield + player.graveyard + player.exile + player.revealed
    val catalog = supportCatalog
    fun suggestSwaps(target: GameCard) {
        val lib = catalog ?: return
        val commanderIdentity = player.commanderCard?.let { lib.find(it.name)?.colors ?: colorsOf(it.manaCost.orEmpty()) }
        val inDeck = (trackedCards.map { it.name } + listOfNotNull(player.commanderCard?.name)).toSet()
        val suggestions = lib.suggestReplacements(target.type, target.manaValue, colorsOf(target.manaCost.orEmpty()), if (target.id == player.commanderCard?.id) null else commanderIdentity, exclude = inDeck, requireLegendary = target.id == player.commanderCard?.id)
        replacementTarget = target
        suggestingFor = target.name
        query = ""
        results = suggestions.map { it.toScryfallCard() }
        autoFixStatus = if (suggestions.isEmpty()) {
            if (cardTypesOf(target.type).isEmpty()) "Fix ${target.name}'s name first, then ask for swaps." else "No Argentum card of the same type fits this deck's colors. Search to choose one yourself."
        } else "Suggested by type, mana value and colors from Argentum's library. Indexed, not gameplay-verified; you choose."
    }
    val cards = trackedCards.filter { it.name.contains(query, ignoreCase = true) }
        .groupBy { it.name }.entries.sortedBy { it.key.lowercase() }
    val commander = player.commanderCard?.takeIf { it.name.contains(query, ignoreCase = true) }
    StudioDialog("Table Dialogs 12", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.189", "Dialogs and overlays", true, "EDIT ${player.name}'S DECK", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.8", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.8", "Dialogs and overlays", Icons.Filled.Close, "Close deck editor", tint = Muted) }
                }
                StudioText("TableDialogs.Text.190", "Dialogs and overlays", true, "Tracked cards: ${trackedCards.size}${if (player.commanderCard != null) " + commander" else ""}  ·  ${cards.size + if (commander != null) 1 else 0} names", color = Muted, fontSize = 10.sp)
                catalog?.let { lib ->
                    val report = remember(trackedCards, player.commanderCard, lib) {
                        lib.check((trackedCards + listOfNotNull(player.commanderCard)).groupBy { it.name }.map { (name, copies) -> DeckEntry(name, copies.size) })
                    }
                    StudioText("TableDialogs.Text.191", "Dialogs and overlays", true, report.summary(), color = if (report.unsupportedCopies > 0) CounterGold else Muted, fontSize = 9.sp)
                }
                val searchArgentum = argentumOnly && catalog != null
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.12", "Dialogs and overlays", query, { query = it; results = emptyList(); suggestingFor = null }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { StudioText("TableDialogs.Text.192", "Dialogs and overlays", true, if (searchArgentum) "Find an Argentum-supported card" else "Find any Magic card") }, trailingIcon = {
                    StudioTextButton("TableDialogs.TextButton.37", "Dialogs and overlays", enabled = query.trim().length >= 2 && !searching, onClick = {
                        suggestingFor = null
                        if (searchArgentum) results = catalog!!.search(query.trim()).map { it.toScryfallCard() }
                        else {
                            searching = true
                            scope.launch { results = Scryfall.searchCards(query.trim()); searching = false }
                        }
                    }) { StudioText("TableDialogs.Text.193", "Dialogs and overlays", true, if (searching) "SEARCHING…" else if (searchArgentum) "SEARCH ARGENTUM" else "SEARCH ALL", color = Mint, fontSize = 9.sp) }
                })
                if (catalog != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioCheckbox("TableDialogs.Checkbox.1", "Dialogs and overlays", checked = argentumOnly, onCheckedChange = { argentumOnly = it; results = emptyList(); suggestingFor = null })
                    StudioText("TableDialogs.Text.194", "Dialogs and overlays", false, "Argentum-supported cards only (works offline)", color = SoftWhite, fontSize = 10.sp)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (results.isNotEmpty()) {
                        item { StudioText("TableDialogs.Text.195", "Dialogs and overlays", true, suggestingFor?.let { "SUGGESTED ARGENTUM SWAPS FOR ${it.uppercase()} · tap REPLACE" } ?: if (replacementTarget != null) "CHOOSE A MATCH · REPLACE FLAGGED CARD" else "SEARCH RESULTS · tap + to add to library", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        items(results.size) { index ->
                            val result = results[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    StudioText("TableDialogs.Text.196", "Dialogs and overlays", true, result.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val resultSupport = catalog?.support(result.name)
                                    StudioText("TableDialogs.Text.197", "Dialogs and overlays", true, listOfNotNull("${result.setCode?.uppercase().orEmpty()} ${result.collectorNumber.orEmpty()}".trim().ifBlank { null }, result.manaCost, result.type, resultSupport?.label).joinToString("  ·  "), color = if (resultSupport == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                StudioTextButton("TableDialogs.TextButton.38", "Dialogs and overlays", onClick = {
                                    val target = replacementTarget
                                    if (target == null) onAdd(result) else onReplace(target, result)
                                    replacementTarget = null
                                    suggestingFor = null
                                    results = emptyList()
                                    query = ""
                                }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.198", "Dialogs and overlays", true, if (replacementTarget == null) "+ ADD" else "REPLACE", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    commander?.let { card ->
                        item(key = "commander-${card.id}") {
                            StudioText("TableDialogs.Text.199", "Dialogs and overlays", false, "COMMANDER", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (card.importIssue != null || card.id in highlightedCardIds) Color(0xFF392B21) else PanelLight)
                                    .border(if (card.importIssue != null || card.id in highlightedCardIds) 2.dp else 0.dp, if (card.importIssue != null || card.id in highlightedCardIds) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    StudioText("TableDialogs.Text.200", "Dialogs and overlays", true, card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    card.importIssue?.let { StudioText("TableDialogs.Text.201", "Dialogs and overlays", true, "NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    catalog?.support(card.name)?.takeIf { it != CardSupport.ARGENTUM }?.let { StudioText("TableDialogs.Text.202", "Dialogs and overlays", true, it.label.uppercase(), color = if (it == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp) }
                                }
                                if (catalog?.support(card.name) == CardSupport.UNSUPPORTED && card.importIssue == null) {
                                    StudioTextButton("TableDialogs.TextButton.39", "Dialogs and overlays", onClick = { suggestSwaps(card) }) { StudioText("TableDialogs.Text.203", "Dialogs and overlays", false, "SWAPS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                                if (card.importIssue != null) {
                                    StudioTextButton("TableDialogs.TextButton.40", "Dialogs and overlays", onClick = { autoFix(card, listOf(card)) }, enabled = autoFixingCardId == null) {
                                        StudioText("TableDialogs.Text.204", "Dialogs and overlays", true, if (autoFixingCardId == card.id) "FIXING…" else "AUTO FIX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    StudioTextButton("TableDialogs.TextButton.41", "Dialogs and overlays", onClick = { replacementTarget = card; query = card.name; results = emptyList() }) {
                                        StudioText("TableDialogs.Text.205", "Dialogs and overlays", false, "FIND", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                StudioTextButton("TableDialogs.TextButton.42", "Dialogs and overlays", onClick = { onRemove(card) }) { StudioText("TableDialogs.Text.206", "Dialogs and overlays", false, "REMOVE", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    item { StudioText("TableDialogs.Text.207", "Dialogs and overlays", false, "ALL ZONES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
                    if (cards.isEmpty()) item { StudioText("TableDialogs.Text.208", "Dialogs and overlays", false, "No matching cards in this deck", color = Muted, fontSize = 10.sp) }
                    items(cards.size, key = { cards[it].key }) { index ->
                        val (name, copies) = cards[index]
                        // Prefer a flagged copy so grouping identical cards never hides an import error.
                        val sample = copies.firstOrNull { it.importIssue != null || it.id in highlightedCardIds } ?: copies.first()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (sample.importIssue != null || sample.id in highlightedCardIds) Color(0xFF392B21) else PanelLight)
                                .border(if (sample.importIssue != null || sample.id in highlightedCardIds) 2.dp else 0.dp, if (sample.importIssue != null || sample.id in highlightedCardIds) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StudioText("TableDialogs.Text.209", "Dialogs and overlays", true, "${copies.size}×", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                            Column(Modifier.weight(1f)) {
                                StudioText("TableDialogs.Text.210", "Dialogs and overlays", true, name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StudioText("TableDialogs.Text.211", "Dialogs and overlays", true, buildList {
                                    val zones = listOf("hand" to player.hand, "library" to player.library, "battlefield" to player.battlefield, "graveyard" to player.graveyard, "exile" to player.exile, "revealed" to player.revealed)
                                    zones.forEach { (zone, zoneCards) -> copies.count { copy -> zoneCards.any { it.id == copy.id } }.takeIf { it > 0 }?.let { add("$it $zone") } }
                                }.joinToString(" · "), color = Muted, fontSize = 8.sp)
                                val sampleSupport = catalog?.support(name)
                                if (sampleSupport != null && sampleSupport != CardSupport.ARGENTUM) {
                                    StudioText("TableDialogs.Text.212", "Dialogs and overlays", true, sampleSupport.label.uppercase(), color = if (sampleSupport == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp)
                                    if (sampleSupport == CardSupport.UNSUPPORTED && sample.importIssue == null) StudioTextButton("TableDialogs.TextButton.43", "Dialogs and overlays", onClick = { suggestSwaps(sample) }, modifier = Modifier.heightIn(min = 36.dp)) {
                                        StudioText("TableDialogs.Text.213", "Dialogs and overlays", false, "SUGGEST ARGENTUM SWAPS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                sample.importIssue?.let {
                                    StudioText("TableDialogs.Text.214", "Dialogs and overlays", true, "NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    StudioTextButton("TableDialogs.TextButton.44", "Dialogs and overlays",
                                        onClick = { autoFix(sample, copies) },
                                        enabled = autoFixingCardId == null,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { StudioText("TableDialogs.Text.215", "Dialogs and overlays", true, if (autoFixingCardId == sample.id) "FIXING…" else "AUTO FIX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Box {
                                StudioIconButton("TableDialogs.IconButton.9", "Dialogs and overlays", onClick = { menuCard = sample }, modifier = Modifier.size(48.dp)) { StudioIcon("TableDialogs.Icon.9", "Dialogs and overlays", Icons.Filled.MoreVert, "${sample.name} options", tint = Mint) }
                                DropdownMenu(expanded = menuCard?.id == sample.id, onDismissRequest = { menuCard = null }) {
                                    if (sample.importIssue != null) StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.4", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.216", "Dialogs and overlays", false, "Search for correct card") }, onClick = { replacementTarget = sample; query = sample.name; results = emptyList(); suggestingFor = null; menuCard = null })
                                    if (catalog != null && sample.importIssue == null) StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.5", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.217", "Dialogs and overlays", false, "Replace with an Argentum card") }, onClick = { replacementTarget = sample; query = ""; results = emptyList(); suggestingFor = null; argentumOnly = true; menuCard = null })
                                    StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.6", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.218", "Dialogs and overlays", false, "Remove one copy") }, onClick = { onRemove(sample); menuCard = null })
                                    StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.7", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.219", "Dialogs and overlays", false, "Rename card") }, onClick = { renameCard = sample; renameValue = sample.name; menuCard = null })
                                }
                            }
                        }
                    }
                }
                if (autoFixStatus.isNotBlank()) StudioText("TableDialogs.Text.220", "Dialogs and overlays", true, autoFixStatus, color = if (autoFixStatus.startsWith("Fixed")) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    renameCard?.let { card -> StudioAlertDialog("Table Dialogs 13", onDismissRequest = { renameCard = null }, title = { StudioText("TableDialogs.Text.221", "Dialogs and overlays", false, "Rename card") }, text = { StudioOutlinedTextField("TableDialogs.OutlinedTextField.13", "Dialogs and overlays", renameValue, { renameValue = it }, singleLine = true, label = { StudioText("TableDialogs.Text.222", "Dialogs and overlays", false, "Card name") }) }, confirmButton = { StudioTextButton("TableDialogs.TextButton.45", "Dialogs and overlays", onClick = { if (renameValue.isNotBlank()) onRename(card, renameValue.trim()); renameCard = null }) { StudioText("TableDialogs.Text.223", "Dialogs and overlays", false, "SAVE") } }, dismissButton = { StudioTextButton("TableDialogs.TextButton.46", "Dialogs and overlays", onClick = { renameCard = null }) { StudioText("TableDialogs.Text.224", "Dialogs and overlays", false, "CANCEL") } }, containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted) }
}

/**
 * Argentum's per-defender commanderDamage is keyed by commanderName, whereas manually tracked
 * tables historically use the opposing seat ID. Do not treat a missing seat-ID lookup as zero.
 * Extra source keys (partners, a missing commander portrait, or imported legacy data) must remain
 * visible as separate commanders, never be summed toward the 21-damage threshold.
 */
internal data class CommanderDamageTrackerSource(val playerId: String, val playerName: String, val commanderName: String?)
internal data class CommanderDamageTrackerRow(val label: String, val key: String, val amount: Int)

internal fun commanderDamageTrackerRows(
    received: Map<String, Int>,
    opponents: List<CommanderDamageTrackerSource>,
): List<CommanderDamageTrackerRow> {
    val represented = mutableSetOf<String>()
    val rows = opponents.map { source ->
        val commanderName = source.commanderName?.takeIf(String::isNotBlank)
        val key = when {
            commanderName != null && commanderName in received -> commanderName
            source.playerId in received -> source.playerId
            else -> source.playerId  // Preserve manual-table storage for an unset count.
        }
        represented += key
        CommanderDamageTrackerRow(
            label = source.playerName + (commanderName?.let { " · $it" } ?: ""),
            key = key,
            amount = received[key] ?: 0,
        )
    }.toMutableList()
    // A second partner commander / hidden commander with recorded damage must not disappear.
    for ((key, amount) in received) {
        if (key.isNotBlank() && key !in represented && amount > 0) {
            rows += CommanderDamageTrackerRow("Commander · $key", key, amount)
        }
    }
    return rows
}

@Composable
internal fun PlayerTrackerDialog(player: TablePlayer, players: List<TablePlayer>, playerIndex: Int, onUpdate: (TablePlayer) -> Unit, onDismiss: () -> Unit, engineAuthoritative: Boolean = false) {
    var search by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val common = listOf("Poison", "Energy", "Experience", "Rad", "Lore", "Ticket", "Shield", "Stun", "Finality", "The Ring tempts you", "Monarch", "Initiative", "City's Blessing", "Day/Night")
    val matches = common.filter { it.contains(search, true) }
    val damageRows = commanderDamageTrackerRows(
        player.commanderDamageReceived,
        players.indices.filter { it != playerIndex }.map { index ->
            players[index].let { CommanderDamageTrackerSource(it.id, it.name, it.commanderName) }
        },
    )
    StudioDialog("Table Dialogs 14", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.225", "Dialogs and overlays", true, "${player.name} · TRACKERS", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.10", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.10", "Dialogs and overlays", Icons.Filled.Close, "Close player trackers", tint = Muted) }
                }
                StudioText("TableDialogs.Text.226", "Dialogs and overlays", false, "Commander damage is tracked per opposing commander; 21 from one commander is lethal.", color = Muted, fontSize = 10.sp)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        StudioText("TableDialogs.Text.227", "Dialogs and overlays", true, "COMMANDER CASTS · ${player.commanderCastCount} · TAX +{${player.commanderCastCount * 2}}", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ActionPill("− CAST") { onUpdate(player.copy(commanderCastCount = (player.commanderCastCount - 1).coerceAtLeast(0))) }
                            ActionPill("+ CAST") { onUpdate(player.copy(commanderCastCount = player.commanderCastCount + 1)) }
                        }
                    }
                    item {
                        StudioText("TableDialogs.Text.228", "Dialogs and overlays", false, "COMMANDER DAMAGE RECEIVED", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        if (engineAuthoritative) StudioText("TableDialogs.Text.229", "Dialogs and overlays", false, "Tracked by Argentum · read only", color = Muted, fontSize = 9.sp)
                    }
                    items(damageRows.size) { row ->
                        val record = damageRows[row]
                        val amount = record.amount
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioText("TableDialogs.Text.230", "Dialogs and overlays", true, record.label, color = SoftWhite, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!engineAuthoritative) StudioTextButton("TableDialogs.TextButton.47", "Dialogs and overlays", onClick = {
                                onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived.toMutableMap().apply {
                                    if (amount <= 1) remove(record.key) else put(record.key, amount - 1)
                                }))
                            }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.231", "Dialogs and overlays", false, "−", color = SoftWhite) }
                            StudioText("TableDialogs.Text.232", "Dialogs and overlays", true, "$amount", color = if (amount >= 21) Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                            if (!engineAuthoritative) StudioTextButton("TableDialogs.TextButton.48", "Dialogs and overlays", onClick = {
                                onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived + (record.key to amount + 1)))
                            }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.233", "Dialogs and overlays", false, "+", color = Mint) }
                        }
                    }
                    item { StudioText("TableDialogs.Text.234", "Dialogs and overlays", false, "COUNTERS AND STATUS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    item { StudioOutlinedTextField("TableDialogs.OutlinedTextField.14", "Dialogs and overlays", value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { StudioText("TableDialogs.Text.235", "Dialogs and overlays", false, "Search common trackers") }, singleLine = true) }
                    items(matches.size) { row ->
                        val name = matches[row]
                        val old = player.playerCounters[name] ?: 0
                        val toggle = name in listOf("Monarch", "Initiative", "City's Blessing", "Day/Night", "The Ring tempts you")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioText("TableDialogs.Text.236", "Dialogs and overlays", true, name, color = SoftWhite, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            if (!toggle) StudioTextButton("TableDialogs.TextButton.49", "Dialogs and overlays", onClick = { val next = if (toggle) 0 else (old - 1).coerceAtLeast(0); onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.237", "Dialogs and overlays", false, "−", color = SoftWhite) }
                            StudioText("TableDialogs.Text.238", "Dialogs and overlays", true, if (toggle) if (old > 0) "ON" else "OFF" else "$old", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                            StudioTextButton("TableDialogs.TextButton.50", "Dialogs and overlays", onClick = { val next = if (toggle) if (old > 0) 0 else 1 else old + 1; onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { StudioText("TableDialogs.Text.239", "Dialogs and overlays", true, if (!toggle) "+" else if (old > 0) "TURN OFF" else "TURN ON", color = Mint) }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudioOutlinedTextField("TableDialogs.OutlinedTextField.15", "Dialogs and overlays", value = custom, onValueChange = { custom = it }, modifier = Modifier.weight(1f), label = { StudioText("TableDialogs.Text.240", "Dialogs and overlays", false, "Add custom tracker") }, singleLine = true)
                            StudioTextButton("TableDialogs.TextButton.51", "Dialogs and overlays", onClick = { val key = custom.trim(); if (key.isNotEmpty()) { onUpdate(player.copy(playerCounters = player.playerCounters + (key to ((player.playerCounters[key] ?: 0) + 1)))); custom = "" } }, enabled = custom.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.241", "Dialogs and overlays", false, "ADD", color = Mint) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TargetSelectionDialog(stackItem: StackAction, candidates: List<StackTarget>, onConfirm: (List<StackTarget>) -> Unit) {
    val range = stackItem.targetCount ?: TargetCountRange(1, 1)
    var selectedTargets by remember(stackItem.id, stackItem.targets) { mutableStateOf(stackItem.targets) }
    var customTargetText by remember(stackItem.id) { mutableStateOf("") }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    val allowed = selectedTargets.size in range.minimum..range.maximum
    val targetLimit = if (range.maximum >= 99) Int.MAX_VALUE else range.maximum
    StudioDialog("Table Dialogs 15",
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight), shape = RoundedCornerShape(18.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioText("TableDialogs.Text.242", "Dialogs and overlays", false, "CHOOSE TARGETS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                StudioText("TableDialogs.Text.243", "Dialogs and overlays", true, stackItem.title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StudioText("TableDialogs.Text.244", "Dialogs and overlays", true,
                    when {
                        range.minimum == range.maximum -> "Select ${range.minimum} target${if (range.minimum == 1) "" else "s"}."
                        range.maximum >= 99 -> "Select any number of targets (${range.minimum} or more)."
                        else -> "Select ${range.minimum} to ${range.maximum} targets."
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
                StudioText("TableDialogs.Text.245", "Dialogs and overlays", false, "Check the card text and select only legal targets. The app does not fully parse card-specific restrictions.", color = CounterGold, fontSize = 10.sp, lineHeight = 14.sp)
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.43f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (candidates.isEmpty()) item(key = "no-candidates") {
                        StudioText("TableDialogs.Text.246", "Dialogs and overlays", false, "No visible game objects are available. Add a target by name below if the card text allows it.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    }
                    items(candidates, key = { it.key }) { candidate ->
                        val isSelected = selectedTargets.any { it.key == candidate.key }
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedTargets = if (isSelected) selectedTargets.filterNot { it.key == candidate.key }
                                else if (selectedTargets.size < targetLimit) selectedTargets + candidate else selectedTargets
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Mint.copy(alpha = 0.15f) else PanelLight
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                StudioCheckbox("TableDialogs.Checkbox.2", "Dialogs and overlays", checked = isSelected, onCheckedChange = { checked ->
                                    selectedTargets = if (checked && selectedTargets.size < targetLimit) selectedTargets + candidate else selectedTargets.filterNot { it.key == candidate.key }
                                })
                                Column(Modifier.weight(1f)) {
                                    StudioText("TableDialogs.Text.247", "Dialogs and overlays", true, candidate.label, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    StudioText("TableDialogs.Text.248", "Dialogs and overlays", true, candidate.category, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.16", "Dialogs and overlays",
                    value = customTargetText,
                    onValueChange = { customTargetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { StudioText("TableDialogs.Text.249", "Dialogs and overlays", false, "Other legal target") },
                    placeholder = { StudioText("TableDialogs.Text.250", "Dialogs and overlays", false, "For example: a counter on a permanent") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StudioTextButton("TableDialogs.TextButton.52", "Dialogs and overlays",
                        onClick = {
                            val label = customTargetText.trim()
                            if (label.isNotEmpty() && selectedTargets.size < targetLimit) {
                                selectedTargets = selectedTargets + StackTarget("manual:${java.util.UUID.randomUUID()}", label, "MANUAL TARGET")
                                customTargetText = ""
                            }
                        },
                        enabled = customTargetText.isNotBlank() && selectedTargets.size < targetLimit
                    ) { StudioText("TableDialogs.Text.251", "Dialogs and overlays", false, "ADD TARGET", color = Mint, fontSize = 10.sp) }
                    StudioText("TableDialogs.Text.252", "Dialogs and overlays", true, "${selectedTargets.size} selected", color = Muted, fontSize = 10.sp)
                }
                StudioButton("TableDialogs.Button.11", "Dialogs and overlays",
                    onClick = { onConfirm(selectedTargets) },
                    enabled = allowed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { StudioText("TableDialogs.Text.253", "Dialogs and overlays", false, "CONFIRM TARGETS", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
internal fun StackPriorityDialog(players: List<TablePlayer>, stack: List<StackAction>, priorityPlayer: Int, passedCount: Int, eligibleCount: Int, canResolve: Boolean, canAct: Boolean, onPreviewCard: (StackAction, GameCard) -> Unit, onStackItemTap: () -> Unit, onCastFromHand: () -> Unit, onAddTrigger: (String) -> Unit, onPassPriority: () -> Unit, onResolve: () -> Unit, onDismiss: () -> Unit) {
    var triggerName by remember { mutableStateOf("") }
    var manualAbilityOpen by remember { mutableStateOf(false) }
    var expandedActionId by remember { mutableStateOf<String?>(null) }
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    val maxStackListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.34f
    StudioDialog("Table Dialogs 16", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxDialogHeight), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxWidth().wrapContentHeight()) {
            Column(Modifier.fillMaxWidth().wrapContentHeight().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.254", "Dialogs and overlays", false, "STACK · PRIORITY", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.11", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.11", "Dialogs and overlays", Icons.Filled.Close, "Close stack", tint = Muted) }
                }
                StudioText("TableDialogs.Text.255", "Dialogs and overlays", true, "Priority: ${players.getOrNull(priorityPlayer)?.name ?: "—"} · passed $passedCount/$eligibleCount", color = Mint, fontSize = 10.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxStackListHeight).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stack.isEmpty()) item {
                        Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StudioText("TableDialogs.Text.256", "Dialogs and overlays", false, "NOTHING ON THE STACK", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                StudioTextButton("TableDialogs.TextButton.53", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.257", "Dialogs and overlays", false, "CLOSE STACK", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                            }
                        }
                    }
                    items(stack.indices.reversed().toList()) { index ->
                        val item = stack[index]
                        val owner = players.firstOrNull { it.id == item.controllerId }?.name ?: "Unknown player"
                        val card = item.card
                        val cardIsFaceDown = card?.isFaceDown == true
                        val expanded = expandedActionId == item.id
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp).then(if (index == stack.lastIndex) Modifier else Modifier),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (card != null) {
                                    CardFace(card, Modifier.width(48.dp), onClick = { if (!cardIsFaceDown) onPreviewCard(item, card); onStackItemTap() }, onLongClick = { if (!cardIsFaceDown) onPreviewCard(item, card); onStackItemTap() }, showCounters = false)
                                }
                                Column(
                                    Modifier.weight(1f).studioClickable("TableDialogs.clickable.1", "Dialogs and overlays", enabled = !cardIsFaceDown) { expandedActionId = if (expanded) null else item.id; onStackItemTap() },
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val visibleTitle = if (cardIsFaceDown) "Face-down spell" else item.title
                                    StudioText("TableDialogs.Text.258", "Dialogs and overlays", true, "${if (index == stack.lastIndex) "TOP · " else ""}$visibleTitle", color = if (index == stack.lastIndex) Mint else SoftWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    StudioText("TableDialogs.Text.259", "Dialogs and overlays", true, "$owner · ${item.kind}", color = Muted, fontSize = 9.sp)
                                    if (item.targets.isNotEmpty()) StudioText("TableDialogs.Text.260", "Dialogs and overlays", true, "TARGETS · ${item.targets.joinToString { it.label }}", color = Mint, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    else if (item.targetCount != null && !item.targetsConfirmed) StudioText("TableDialogs.Text.261", "Dialogs and overlays", false, "WAITING FOR TARGETS", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    if (card != null && !cardIsFaceDown) {
                                        val facts = listOfNotNull(card.manaCost?.takeIf { it.isNotBlank() }, card.type.takeIf { it.isNotBlank() })
                                        if (facts.isNotEmpty()) StudioText("TableDialogs.Text.262", "Dialogs and overlays", true, facts.joinToString(" · "), color = CounterGold, fontSize = 9.sp, maxLines = 2)
                                    }
                                    StudioText("TableDialogs.Text.263", "Dialogs and overlays", true, if (cardIsFaceDown) "FACE-DOWN CARD · DETAILS HIDDEN" else if (card != null) "CARD IMAGE: PREVIEW · TEXT: DETAILS" else if (expanded) "TAP TO COLLAPSE" else "TAP FOR DETAILS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (expanded && !cardIsFaceDown) {
                                val oracle = card?.oracleText?.takeIf { it.isNotBlank() }
                                if (oracle != null) {
                                    HorizontalDivider(color = Muted.copy(alpha = 0.25f))
                                    StudioText("TableDialogs.Text.264", "Dialogs and overlays", true, oracle, color = SoftWhite, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                } else if (card == null) {
                                    StudioText("TableDialogs.Text.265", "Dialogs and overlays", true, "${item.kind} added by $owner", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                } else {
                                    StudioText("TableDialogs.Text.266", "Dialogs and overlays", false, "No rules text is available for this card.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                }
                            }
                        }
                    }
                }
                StudioButton("TableDialogs.Button.12", "Dialogs and overlays",
                    onClick = onCastFromHand,
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) {
                    StudioText("TableDialogs.Text.267", "Dialogs and overlays", false, "CAST FROM HAND", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                StudioTextButton("TableDialogs.TextButton.54", "Dialogs and overlays",
                    onClick = { manualAbilityOpen = !manualAbilityOpen },
                    enabled = canAct,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                ) {
                    StudioText("TableDialogs.Text.268", "Dialogs and overlays", true, if (manualAbilityOpen) "HIDE MANUAL ENTRY" else "ADD MANUAL ABILITY", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (manualAbilityOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StudioOutlinedTextField("TableDialogs.OutlinedTextField.17", "Dialogs and overlays",
                        value = triggerName,
                        onValueChange = { triggerName = it },
                        modifier = Modifier.weight(1f),
                        label = { StudioText("TableDialogs.Text.269", "Dialogs and overlays", false, "Ability or effect") },
                        singleLine = true,
                        enabled = canAct
                    )
                    StudioTextButton("TableDialogs.TextButton.55", "Dialogs and overlays",
                        onClick = {
                            val title = triggerName.trim()
                            if (canAct && title.isNotBlank()) {
                                onAddTrigger(title)
                                triggerName = ""
                                manualAbilityOpen = false
                            }
                        },
                        enabled = canAct && triggerName.isNotBlank(),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { StudioText("TableDialogs.Text.270", "Dialogs and overlays", false, "ADD", color = if (canAct && triggerName.isNotBlank()) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
                StudioButton("TableDialogs.Button.13", "Dialogs and overlays",
                    onClick = onPassPriority,
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { StudioText("TableDialogs.Text.271", "Dialogs and overlays", false, "PASS PRIORITY", fontSize = 14.sp, fontWeight = FontWeight.Black) }
                StudioTextButton("TableDialogs.TextButton.56", "Dialogs and overlays",
                    onClick = onResolve,
                    enabled = canResolve,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp)
                ) { StudioText("TableDialogs.Text.272", "Dialogs and overlays", false, "RESOLVE TOP", color = if (canResolve) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            }
        }
    }
}

@Composable
internal fun CombatDialog(players: List<TablePlayer>, attackerIndex: Int, onDismiss: () -> Unit, onLog: (String) -> Unit, onCombatDamage: (GameCard, Int, Int) -> Unit) {
    val attackers = players.getOrNull(attackerIndex)?.battlefield.orEmpty().filter { it.type.contains("creature", ignoreCase = true) && !it.tapped }
    val blockers = players.flatMapIndexed { index, player ->
        if (index == attackerIndex || player.isEliminated) emptyList() else player.battlefield.filter { it.type.contains("creature", ignoreCase = true) && !it.tapped }.map { index to it }
    }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var assignments by remember { mutableStateOf(emptyMap<Int, Set<Int>>()) }
    var defenders by remember { mutableStateOf(emptyMap<Int, Int>()) }
    var damage by remember { mutableStateOf(emptyMap<Int, String>()) }
    fun combatCreature(card: GameCard): CombatCreature {
        fun stat(base: String?, power: Boolean): Int? {
            if (card.isFaceDown) return null
            val printed = printedCreatureStat(base) ?: return null
            val plusOne = (card.counters["+1/+1"] ?: 0) + if (power) (card.counters["+1/+0"] ?: 0) else (card.counters["+0/+1"] ?: 0)
            val minusOne = card.counters["-1/-1"] ?: 0
            return printed + plusOne - minusOne
        }
        return CombatCreature(card.id, stat(card.power, true), stat(card.toughness, false), card.keywords.toSet(), card.oracleText.orEmpty())
    }
    fun aiBlocks(attacker: GameCard, defenderIndex: Int, currentAssignments: Map<Int, Set<Int>>): Set<Int> {
        val defender = players.getOrNull(defenderIndex)?.takeIf { it.isComputer } ?: return emptySet()
        val reserved = currentAssignments.filterKeys { it != attacker.id }.values.flatten().toSet()
        return chooseAiBlockers(
            combatCreature(attacker),
            defender.battlefield.filter { it.type.contains("creature", true) && !it.tapped }.map(::combatCreature),
            defender.life,
            reserved
        )
    }
    StudioDialog("Table Dialogs 17", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.273", "Dialogs and overlays", true, "COMBAT · ${players.getOrNull(attackerIndex)?.name ?: "PLAYER"}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.12", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.12", "Dialogs and overlays", Icons.Filled.Close, "Close combat", tint = Muted) }
                }
                StudioText("TableDialogs.Text.274", "Dialogs and overlays", false, "Select attackers, choose who each attacks, assign blockers, and enter damage. Apply Combat logs the attack and deals unblocked damage to the defending player; resolve blocked combat yourself.", color = Muted, fontSize = 10.sp)
                if (attackers.isEmpty()) StudioText("TableDialogs.Text.275", "Dialogs and overlays", false, "No untapped creatures are available to attack.", color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(attackers.size, key = { attackers[it].id }) { index ->
                        val card = attackers[index]
                        var blockMenu by remember(card.id) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StudioCheckbox("TableDialogs.Checkbox.3", "Dialogs and overlays", checked = card.id in selected, onCheckedChange = { checked ->
                                    selected = if (checked) selected + card.id else selected - card.id
                                    if (checked) {
                                        if (card.id !in damage) printedCreatureStat(card.power)?.takeIf { it > 0 }?.let { damage = damage + (card.id to it.toString()) }
                                        val defender = defenders[card.id] ?: players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated }
                                        if (defender != null) {
                                            defenders = defenders + (card.id to defender)
                                            assignments = assignments + (card.id to aiBlocks(card, defender, assignments))
                                        }
                                    } else assignments = assignments - card.id
                                })
                                StudioText("TableDialogs.Text.276", "Dialogs and overlays", true, card.name, color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StudioText("TableDialogs.Text.277", "Dialogs and overlays", true, if (card.tapped) "TAPPED" else "READY", color = Muted, fontSize = 8.sp)
                            }
                            if (card.id in selected) {
                                val defaultDefender = players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated } ?: attackerIndex
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        StudioTextButton("TableDialogs.TextButton.57", "Dialogs and overlays", onClick = { blockMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.278", "Dialogs and overlays", true, "ATTACKING: ${players.getOrNull(defenders[card.id] ?: defaultDefender)?.name ?: "PLAYER"} ▾", color = Mint, fontSize = 9.sp) }
                                        DropdownMenu(expanded = blockMenu, onDismissRequest = { blockMenu = false }) {
                                            players.forEachIndexed { targetIndex, target ->
                                            if (targetIndex != attackerIndex && !target.isEliminated) StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.8", "Dialogs and overlays", text = { StudioText("TableDialogs.Text.279", "Dialogs and overlays", true, target.name) }, onClick = {
                                                defenders = defenders + (card.id to targetIndex)
                                                assignments = assignments + (card.id to aiBlocks(card, targetIndex, assignments))
                                                blockMenu = false
                                            })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    StudioOutlinedTextField("TableDialogs.OutlinedTextField.18", "Dialogs and overlays", value = damage[card.id].orEmpty(), onValueChange = { damage = damage + (card.id to it.filter(Char::isDigit)) }, modifier = Modifier.width(105.dp), singleLine = true, label = { StudioText("TableDialogs.Text.280", "Dialogs and overlays", false, "Damage") }, placeholder = { StudioText("TableDialogs.Text.281", "Dialogs and overlays", true, card.power ?: "?") })
                                }
                                val defenderIndex = defenders[card.id] ?: defaultDefender
                                if (players.getOrNull(defenderIndex)?.isComputer == true) StudioText("TableDialogs.Text.282", "Dialogs and overlays", false, "AI BLOCK PICK · CHANGE ANYTIME", color = Mint, fontSize = 8.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                else StudioText("TableDialogs.Text.283", "Dialogs and overlays", false, "BLOCKERS", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                blockers.filter { it.first == (defenders[card.id] ?: defaultDefender) }.forEach { (owner, blocker) ->
                                    val reservedByOther = assignments.any { (attackerId, blockerIds) -> attackerId != card.id && blocker.id in blockerIds }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 7.dp)) {
                                        StudioCheckbox("TableDialogs.Checkbox.4", "Dialogs and overlays", checked = blocker.id in assignments[card.id].orEmpty(), enabled = !reservedByOther, onCheckedChange = { checked ->
                                            val current = assignments[card.id].orEmpty()
                                            assignments = assignments + (card.id to if (checked) current + blocker.id else current - blocker.id)
                                        })
                                        StudioText("TableDialogs.Text.284", "Dialogs and overlays", true, "${players[owner].name} · ${blocker.name}${if (reservedByOther) " · ASSIGNED" else ""}", color = if (reservedByOther) Muted else SoftWhite, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                StudioText("TableDialogs.Text.285", "Dialogs and overlays", true, "Blockers available: ${blockers.size}  ·  tapped creatures excluded", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StudioTextButton("TableDialogs.TextButton.58", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("TableDialogs.Text.286", "Dialogs and overlays", false, "CANCEL") }
                    Spacer(Modifier.width(8.dp))
                    StudioTextButton("TableDialogs.TextButton.59", "Dialogs and overlays", modifier = Modifier.heightIn(min = 48.dp), onClick = {
                        if (selected.isNotEmpty()) {
                            val names = attackers.filter { it.id in selected }.joinToString { card ->
                                val amount = damage[card.id].orEmpty().takeIf { it.isNotBlank() }?.let { " ($it damage)" }.orEmpty()
                                val blocker = assignments[card.id].orEmpty().mapNotNull { id -> blockers.firstOrNull { it.second.id == id }?.let { "${players[it.first].name}'s ${it.second.name}" } }.joinToString().let { if (it.isBlank()) "unblocked" else "blocked by $it" }
                                val defender = defenders[card.id] ?: players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated } ?: attackerIndex
                                val damageAmount = damage[card.id].orEmpty().toIntOrNull() ?: 0
                                if (assignments[card.id].orEmpty().isEmpty() && damageAmount > 0) onCombatDamage(card, defender, damageAmount)
                                "${card.name} → ${players.getOrNull(defender)?.name ?: "player"}$amount · $blocker"
                            }
                            onLog("declared attackers: $names")
                        }
                        onDismiss()
                    }) { StudioText("TableDialogs.Text.287", "Dialogs and overlays", false, "APPLY COMBAT", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun CardZoneDialog(zone: String, player: String, cards: List<GameCard>, onDismiss: () -> Unit, onPreview: (GameCard) -> Unit, canMoveCards: Boolean = false, onMoveCard: (GameCard, String) -> Unit = { _, _ -> }, commanderId: Int? = null, canMoveCommander: Boolean = false, manualPlayAllowed: Boolean = false, argentumPlayableIds: Set<Int> = emptySet(), onPlayCard: (GameCard) -> Unit = {}) {
    StudioDialog("Table Dialogs 18", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.288", "Dialogs and overlays", true, "$player · $zone (${cards.size})", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.13", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.13", "Dialogs and overlays", Icons.Filled.Close, "Close $zone", tint = Muted) }
                }
                if (cards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { StudioText("TableDialogs.Text.289", "Dialogs and overlays", false, "No cards here", color = Muted) }
                else LazyVerticalGrid(columns = GridCells.Adaptive(90.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(cards.size, key = { cards[it].id }) { index ->
                        val card = cards[index]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val hasEngineAction = card.id in argentumPlayableIds
                            val canPlay = manualPlayAllowed || hasEngineAction
                            Box(Modifier.fillMaxWidth()) {
                                CardFace(card, Modifier.fillMaxWidth(), onClick = { if (canPlay) onPlayCard(card) else onPreview(card) }, onLongClick = { onPreview(card) })
                                if (canMoveCards) {
                                    var menuOpen by remember(card.id) { mutableStateOf(false) }
                                    Box(Modifier.align(Alignment.TopEnd).padding(1.dp), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xCC101615)), contentAlignment = Alignment.Center) {
                                                StudioIcon("TableDialogs.Icon.14", "Dialogs and overlays", Icons.Filled.MoreVert, if (card.isFaceDown) "Card actions" else "Move ${card.name}", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        listOf("Hand", "Battlefield", "Library (top)", "Library (bottom)", "Graveyard", "Exile", "Revealed")
                                            .filterNot { it == zone }
                                            .forEach { destination ->
                                                StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.9", "Dialogs and overlays",
                                                    text = { StudioText("TableDialogs.Text.290", "Dialogs and overlays", true, "Move to $destination") },
                                                    onClick = { menuOpen = false; onMoveCard(card, destination) }
                                                )
                                            }
                                        if (canMoveCommander && card.id == commanderId) StudioDropdownMenuItem("TableDialogs.DropdownMenuItem.10", "Dialogs and overlays",
                                            text = { StudioText("TableDialogs.Text.291", "Dialogs and overlays", false, "Move to Command Zone") },
                                            onClick = { menuOpen = false; onMoveCard(card, "Command Zone") }
                                        )
                                    }
                                }
                            }
                            if (canPlay) StudioTextButton("TableDialogs.TextButton.60", "Dialogs and overlays",
                                onClick = { onPlayCard(card) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                StudioText("TableDialogs.Text.292", "Dialogs and overlays", true, if (hasEngineAction) "LEGAL ACTIONS" else "PLAY / CAST · MANUAL", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CounterPickerDialog(card: GameCard, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = counterCatalog.filter { it.contains(query, ignoreCase = true) }
    StudioDialog("Table Dialogs 19", onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                StudioText("TableDialogs.Text.293", "Dialogs and overlays", true, "Add a counter · ${card.name}", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.19", "Dialogs and overlays", query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { StudioText("TableDialogs.Text.294", "Dialogs and overlays", false, "Search counter types") })
                if (query.isNotBlank() && matches.none { it.equals(query.trim(), true) }) {
                    StudioTextButton("TableDialogs.TextButton.61", "Dialogs and overlays", onClick = { onAdd(query.trim()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.295", "Dialogs and overlays", true, "ADD CUSTOM “${query.trim()}” COUNTER", color = CounterGold) }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(matches) { counter ->
                        StudioTextButton("TableDialogs.TextButton.62", "Dialogs and overlays", onClick = { onAdd(counter) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.296", "Dialogs and overlays", true, counter, color = SoftWhite, modifier = Modifier.weight(1f)); StudioText("TableDialogs.Text.297", "Dialogs and overlays", false, "+", color = CounterGold, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                StudioTextButton("TableDialogs.TextButton.63", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.298", "Dialogs and overlays", false, "CANCEL") }
            }
        }
    }
}

@Composable
internal fun TokenPickerDialog(onDismiss: () -> Unit, onAdd: (ScryfallCard) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(400)
        loading = true
        try {
            results = Scryfall.searchTokens(query)
            error = if (results.isEmpty()) "No token cards found. Try another name." else null
        } catch (_: Exception) { error = "Token search needs an internet connection." }
        finally { loading = false }
    }
    StudioDialog("Table Dialogs 20", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("TableDialogs.Text.299", "Dialogs and overlays", false, "Add a token", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        StudioText("TableDialogs.Text.300", "Dialogs and overlays", false, "Search Scryfall’s token cards", color = Muted, fontSize = 10.sp)
                    }
                    StudioIconButton("TableDialogs.IconButton.14", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.15", "Dialogs and overlays", Icons.Filled.Close, "Close token search", tint = Muted) }
                }
                StudioOutlinedTextField("TableDialogs.OutlinedTextField.20", "Dialogs and overlays", query, { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, label = { StudioText("TableDialogs.Text.301", "Dialogs and overlays", false, "Search tokens") }, placeholder = { StudioText("TableDialogs.Text.302", "Dialogs and overlays", false, "Treasure, Clue, Zombie…") })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Treasure", "Clue", "Food", "Blood", "Zombie", "Goblin", "Soldier").forEach { suggestion ->
                        StudioAssistChip("TableDialogs.AssistChip.1", "Dialogs and overlays", onClick = { query = suggestion }, label = { StudioText("TableDialogs.Text.303", "Dialogs and overlays", true, suggestion, fontSize = 9.sp) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                error?.let { StudioText("TableDialogs.Text.304", "Dialogs and overlays", true, it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(6.dp)) }
                if (query.trim().length >= 2 && results.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                    StudioTextButton("TableDialogs.TextButton.64", "Dialogs and overlays", onClick = { onAdd(ScryfallCard(query.trim(), "Custom token", null)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        StudioText("TableDialogs.Text.305", "Dialogs and overlays", true, "ADD CUSTOM “${query.trim()}” TOKEN", color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { token ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(PanelLight).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardFace(GameCard(-token.name.hashCode(), token.name, token.type, Color(0xFF6D836D), imageUrl = token.imageUrl, isToken = true), Modifier.width(46.dp), onClick = {}, onLongClick = {}, showCounters = false)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                StudioText("TableDialogs.Text.306", "Dialogs and overlays", true, token.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                StudioText("TableDialogs.Text.307", "Dialogs and overlays", true, token.type, color = Muted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            StudioTextButton("TableDialogs.TextButton.65", "Dialogs and overlays", onClick = { onAdd(token) }, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.308", "Dialogs and overlays", false, "ADD", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedDecksDialog(decks: List<SavedDeck>, onLoad: (SavedDeck) -> Unit, onDelete: (SavedDeck) -> Unit, onDismiss: () -> Unit) {
    StudioDialog("Table Dialogs 21", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.309", "Dialogs and overlays", true, "SAVED DECKS · ${decks.size}", color = SoftWhite, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.15", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.16", "Dialogs and overlays", Icons.Filled.Close, "Close saved decks", tint = Muted) }
                }
                if (decks.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { StudioText("TableDialogs.Text.310", "Dialogs and overlays", false, "No saved decks yet. Choose ‘Save this imported deck for later’ when importing.", color = Muted, fontSize = 12.sp) }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(decks.size) { index ->
                            val deck = decks[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                    StudioText("TableDialogs.Text.311", "Dialogs and overlays", true, deck.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StudioText("TableDialogs.Text.312", "Dialogs and overlays", true, "${parseDeckList(deck.text).sumOf { it.count }} cards", color = Muted, fontSize = 9.sp)
                                }
                                StudioTextButton("TableDialogs.TextButton.66", "Dialogs and overlays", onClick = { onLoad(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) Modifier else Modifier)) { StudioText("TableDialogs.Text.313", "Dialogs and overlays", false, "LOAD", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                StudioTextButton("TableDialogs.TextButton.67", "Dialogs and overlays", onClick = { onDelete(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) Modifier else Modifier)) { StudioText("TableDialogs.Text.314", "Dialogs and overlays", false, "DELETE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                StudioTextButton("TableDialogs.TextButton.68", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { StudioText("TableDialogs.Text.315", "Dialogs and overlays", false, "CLOSE", color = Muted) }
            }
            }
        }
    }
}

@Composable
internal fun GameLogDialog(entries: List<String>, ownership: List<Boolean?>? = null, turnMarkers: List<Boolean>? = null, previewableCards: List<Pair<Int, GameCard>>, onPreviewCard: (Int, GameCard) -> Unit, onDismiss: () -> Unit, groupingKeys: List<String?>? = null) {
    val rows = remember(entries, ownership, turnMarkers, groupingKeys) {
        collapsedGameLogRows(entries, ownership, turnMarkers, groupingKeys)
    }
    // Paginate expanded events to keep thousand-trigger turns responsive.
    var expandedCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    StudioDialog("Table Dialogs 22", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TableDialogs.Text.316", "Dialogs and overlays", true, "GAME LOG · ${entries.size}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StudioIconButton("TableDialogs.IconButton.16", "Dialogs and overlays", onClick = onDismiss, modifier = Modifier.size(44.dp)) { StudioIcon("TableDialogs.Icon.17", "Dialogs and overlays", Icons.Filled.Close, "Close game log", tint = Muted) }
                }
                StudioText("TableDialogs.Text.317", "Dialogs and overlays", true, if (ownership != null) "Whole game · newest first · your actions marked YOU" else "Actions · mana payments · triggers · effects",
                    color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 7.dp))
                if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { StudioText("TableDialogs.Text.318", "Dialogs and overlays", false, "No actions logged yet", color = Muted) }
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(rows.size) { displayIndex ->
                        val row = rows[rows.lastIndex - displayIndex]
                        val entry = row.description
                        if (row.isTurnMarker) {
                            StudioText("TableDialogs.Text.319", "Dialogs and overlays", true, entry.trim('-', ' ').uppercase(), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp, start = 3.dp))
                            return@items
                        }
                        val yours = row.isYours == true
                        val entryColor = when {
                            "trigger" in entry.lowercase() -> Mint
                            "paid " in entry.lowercase() || "payment" in entry.lowercase() -> CounterGold
                            "automatically" in entry.lowercase() || "effect" in entry.lowercase() || "life" in entry.lowercase() || "token" in entry.lowercase() -> Color(0xFF34749B)
                            else -> SoftWhite
                        }
                        val preview = remember(entry, previewableCards) {
                            previewableCards.sortedByDescending { it.second.name.length }
                                .firstOrNull { (_, card) -> entry.contains(card.name, ignoreCase = true) }
                        }
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight)
                                    .border(if (yours) 1.dp else 0.dp, if (yours) Mint else PanelLight, RoundedCornerShape(8.dp))
                                    .studioClickable("TableDialogs.clickable.2", "Dialogs and overlays", enabled = row.count > 1) {
                                        expandedCounts = if (row.firstIndex in expandedCounts) expandedCounts - row.firstIndex
                                            else expandedCounts + (row.firstIndex to minOf(25, row.count))
                                    }
                                    .padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (yours) StudioText("TableDialogs.Text.320", "Dialogs and overlays", false, "YOU", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Mint).padding(horizontal = 4.dp, vertical = 2.dp))
                                ManaSymbolText(entry, color = entryColor, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(3.dp))
                                if (row.count > 1) StudioText("TableDialogs.Text.321", "Dialogs and overlays", true, "×${row.count} ${if (row.firstIndex in expandedCounts) "▲" else "▼"}",
                                    color = CounterGold, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                preview?.let { (owner, card) ->
                                    CardFace(card, Modifier.width(31.dp), onClick = { onPreviewCard(owner, card) },
                                        onLongClick = { onPreviewCard(owner, card) }, showCounters = false)
                                }
                            }
                            val shown = expandedCounts[row.firstIndex] ?: 0
                            if (row.count > 1 && shown > 0) {
                                val displayed = minOf(shown, row.count)
                                // These are references to the original log entries, not fabricated summaries.
                                for (original in row.firstIndex until row.firstIndex + displayed) {
                                    StudioText("TableDialogs.Text.322", "Dialogs and overlays", true, "#${original + 1} · ${entries[original]}", color = Muted, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp, bottom = 2.dp))
                                }
                                if (displayed < row.count) StudioTextButton("TableDialogs.TextButton.69", "Dialogs and overlays",
                                    onClick = { expandedCounts = expandedCounts + (row.firstIndex to minOf(row.count, displayed + 25)) }
                                ) { StudioText("TableDialogs.Text.323", "Dialogs and overlays", true, "SHOW NEXT ${minOf(25, row.count - displayed)} · ${row.count - displayed} REMAIN", color = Mint, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                if (ownership == null) StudioText("TableDialogs.Text.324", "Dialogs and overlays", false, "Unsupported or ambiguous effects are marked for manual resolution.",
                    color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** "What's new": the build history, newest first. Kept out of TableScreenContent so that method stays under the JVM size limit. */
@Composable
internal fun ChangelogDialog(onDismiss: () -> Unit) {
    StudioAlertDialog("Table Dialogs 23",
        onDismissRequest = { onDismiss() },
        title = { StudioText("TableDialogs.Text.325", "Dialogs and overlays", true, "What's new · Build ${BuildConfig.VERSION_CODE}") },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.62f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BEGIN changelog-since-build121 — generated next-APK release notes
                StudioText("TableDialogs.Text.326", "Dialogs and overlays", false, "BUILD 142 · NOV 6 ARCANA STUDIO TEST", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.327", "Dialogs and overlays", false, "• Arcana Studio opens a full-screen native editor. Edit the real hand canvas or inspect current app screens; move, resize, restyle and bind supported buttons to available native actions.\n• Separate portrait/landscape and named profiles, information density, palette, draft/Apply, undo/redo, reset and validated JSON import/export. Actual game data and legal-action guards remain authoritative.\n• Keeps the classic Adjust UI editor as a recovery path. Separate Studio Preview install preserves earlier apps. Physical phone/Fold acceptance and independent review remain pending; this is a development test preview.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.328", "Dialogs and overlays", false, "BUILD 142 · NOV 6 IMAGES + COLORS TEST PREVIEW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.329", "Dialogs and overlays", false, "• Adjust UI: choose four color presets or customize seven colors with HEX/RGB controls. Palette preferences are saved.\n• Card images retry temporary failures with bounded backoff; permanent failures are not repeatedly requested.\n• Includes the repaired-engine Build 142 base. Please test image loading, picker fit and saved colors on your phone.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.330", "Dialogs and overlays", false, "BUILD 142 · NOV 5 ENGINE TEST PREVIEW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.331", "Dialogs and overlays", false, "• Updated the offline rules engine and card library to the reviewed November 5 engine snapshot, with Arcana's compatibility patches.\n• Keeps the previous hand information and tap-to-assign combat preview.\n• This test preview does not include the pending Legion card update, colored attack lines or the UI Designer work. Slow AI turns and online reconnect issues remain under investigation.\n• Phone testing is still needed. This is a debug test preview, not a Play Store release.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.332", "Dialogs and overlays", false, "BUILD 141 · NOV 4 GAME UI PREVIEW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.333", "Dialogs and overlays", false, "• Your hand header shows the turn, step, playable-card count and stack size.\n• Pick an attack target to see that defender's possible blockers. Tap a single creature to assign it to the selected target; tap again to clear an optional attack. Stacks keep their count picker.\n• Colored attack lines are still pending.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.334", "Dialogs and overlays", false, "UPGRADES SINCE BUILD 121 · VERIFIED VS. PREPARED", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.335", "Dialogs and overlays", false, "Newest first. Builds 122–142 (including Build 141 updates) are test previews and development milestones, not Play Store releases. Tests passing in development is not the same as full testing on a real phone.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.336", "Dialogs and overlays", false, "BUILD 141 · UPDATE 4 · PREVIOUS TEST PREVIEW (13 NEW NECRON CARDS)", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.337", "Dialogs and overlays", false, "\u2022 Necron Dynasties grows from 79 to 92 of 100 cards. New: Imotekh the Stormlord, Trazyn the Infinite, Technomancer, The War in Heaven, Triarch Stalker, Caged Sun, Biotransference, Ghost Ark, Lychguard, Tomb Fortress, Shard of the Nightbringer, Necron Monolith and Canoptek Scarab Swarm.\n\u2022 Biotransference: your creatures (and creature spells and cards) are also artifacts. Each time you cast an artifact spell, you lose 1 life and get a 2/2 Necron Warrior artifact creature token.\n\u2022 Trazyn the Infinite has every activated ability of the artifact cards in your graveyard.\n\u2022 Known issue: when Technomancer asks you to pick cards worth 6 or less in total, the game doesn\u2019t stop you picking too many; it quietly drops the extra picks so the result is always legal.\n\u2022 Known issues still being fixed: in big late-game boards the computer players can take several seconds each to decide not to respond; an online room can drop to offline after you switch apps and come back.\n\u2022 Install note: uninstall the earlier Arcana TEST B141 app first, then install this one. This is a debug test build that installs beside your normal app; it is not a Play Store release.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.338", "Dialogs and overlays", false, "BUILD 141 · UPDATE 3 · TEST PREVIEW (ELDRAZI INCURSION COMPLETE)", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.339", "Dialogs and overlays", false, "\u2022 Eldrazi Incursion is complete: all 100 cards are in. Benthic Anomaly and Opal Palace are the last two.\n\u2022 Benthic Anomaly: when you cast it, choose a creature from each opponent. You get a colorless Eldrazi token copy of one of them, with power and toughness equal to the totals of all the creatures you chose.\n\u2022 Opal Palace: when its mana helps cast your commander, that commander enters with a +1/+1 counter for each time you\u2019ve cast it from the command zone this game, counting this time. With partners, each commander counts only its own casts.\n\u2022 Smarter AI: the computer opponent no longer sits on cheap artifacts and mana rocks doing nothing. This was the likely cause of the \u201cAI freeze\u201d in Eldrazi Incursion games. If a game still stops responding, go back to the menu, start a new one, and tell us.\n\u2022 Install note: uninstall the earlier Arcana TEST B141 app first, then install this one. This is a debug test build that installs beside your normal app; it is not a Play Store release.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.340", "Dialogs and overlays", false, "BUILD 141 · UPDATE 2 · TEST PREVIEW (7 NEW CARDS)", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.341", "Dialogs and overlays", false, "\u2022 Eldrazi Incursion is now 98 of 100: Azlask, the Swelling Scourge, Angelic Aberration, Hideous Taskmaster, Mutated Cultist and Twins of Discord are in.\n\u2022 Seeker of Slaanesh joins The Ruinous Powers: each opponent must attack with at least one creature each combat if able.\n\u2022 Darksteel Monolith joins Eldrazi Unbound: once each turn you may cast a colorless spell from your hand without paying its mana cost.\n\u2022 Bloodthirst is now a real keyword, so creatures that gain it from Twins of Discord show it and use it exactly like printed bloodthirst.\n\u2022 Known issue: the AI may occasionally freeze in Eldrazi Incursion games. We\u2019re working on it. If a game stops responding, go back to the menu and start a new one.\n\u2022 Install note: uninstall the earlier Arcana TEST B141 app first, then install this one. This is a debug test build that installs beside your normal app; it is not a Play Store release.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.342", "Dialogs and overlays", false, "BUILD 141 · FIRST TEST PREVIEW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.343", "Dialogs and overlays", false, "• Newer rules engine: the latest Argentum plus 23 Arcana fixes. \u201cCan\u2019t be sacrificed\u201d now stops edicts, and face-down exiled cards stay hidden unless something lets you look.\n• Redesigned combat screens: whole cards, pinch-to-zoom, outcome previews and compact counters.\n• Your first mulligan in each game is free, in every mode. Guests see their real opening hand.\n• Online: the host can set each AI player from Easy to Brutal in Wi-Fi and internet lobbies, and everyone sees the level. New mid-game Lobby button. Online games reconnect when you come back to the app. The old \u201cOnline with Argentum\u201d option is removed from Play.\n• Smarter AI: combat tricks such as Akroma\u2019s Will are saved for combat instead of wasted.\n• Five more original WOTC precons as work in progress (Forces of the Imperium, Tyranid Swarm, Necron Dynasties, The Ruinous Powers, Eldrazi Unbound), next to Eldrazi Incursion. The Swarmlord is added, so Tyranid Swarm now appears.\n• New cards include Tangleweave Armor, Anrakyr the Traveller, Arch of Orazca, Star of Extinction, Assault Suit, Ulamog\u2019s Nullifier, Phyrexian Triniform, Zephyrim, Aspiring Champion and Thunderhawk Gunship.\n• Tutorial coach placement fix. This is a debug test build that installs beside your normal app; it is not a Play Store release.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.344", "Dialogs and overlays", false, "BUILD 140 · FOUR-PLAYER ONLINE, IMPORTING AND NEW CARDS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.345", "Dialogs and overlays", false, "• Online play for up to four players over the internet, as well as Wi-Fi.\n• Deck importing is back on, with the Deck Builder and opt-in requests for cards the app doesn\u2019t have yet.\n• Dozens of new approved cards, including Poxwalkers, Resurrection Orb, Blood for the Blood God!, Celestine, Psychomancer, Mawloc, Marneus Calgar, Past in Flames, Kenrith\u2019s Transformation and Mirrorpool.\n• Eldrazi Temple and Corrupted Crossroads, plus Azlask engine support.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.346", "Dialogs and overlays", false, "BUILDS 135–139 · TEST PREVIEWS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.347", "Dialogs and overlays", false, "• Full-screen multiplayer lobby: commander portraits, room code copy and share, READY UP, 2\u20134 seats, AI or friend per seat, and Start only when everyone is ready.\n• Auto-pass is safer and easier: hold-to-auto-pass works while you wait, and it stops for spells that could affect you.\n• The played-card popup stays up for exactly the seconds you choose. Equipment and Auras stack with the creature they\u2019re attached to.\n• Faster AI on big boards with many copies of the same trigger. Tutorial HAND step fix.\n• The importer flags cards the engine can\u2019t run yet and suggests swaps. The engine now updates automatically each week, checking imported cards.\n• Card fixes: The Lunar Whale crew, Iron Spider, Suffer the Past, Sire of Stagnation; card zoom works above every dialog.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.348", "Dialogs and overlays", false, "BUILD 134 · NO-GOOSE TEST PREVIEW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.349", "Dialogs and overlays", false, "• The original Arcana launcher icon is restored. The oversized developer goose is removed from the home screen; debug test scenarios remain available in the debug APK.\n• A coherent source-built Argentum engine, Ulalek/stack-copying/restricted-mana tests and 94 indexed original Eldrazi Incursion slots are included. Indexed cards are not all independently gameplay-certified.\n• Offline AI has Easy, Normal, Hard and Brutal options with bounded searches, persistence and recovery from rejected actions. Harder levels still need device and broad win-rate testing.\n• The 2–4-player online/Wi-Fi candidate uses an authoritative host, per-seat private game state, human/AI seat selection and reconnect handling. Cross-network gameplay on real phones still needs acceptance testing.\n• The interactive tutorial has 20 chapters, 160 steps, a 31-term glossary and four offline engine-backed lessons. The Table-view HAND step-two fix is prepared for the next test candidate, NOT in the previous no-goose Build 134 APK.\n• Help & Diagnostics has the Engine running notice toggle, an original-logo check and cleaner menu controls. Card/priority/deck and multiplayer fixes were regression-tested in CI.\n• Build 134 here means the validated TEST preview, not a Play Store release; import and deck-edit features are not enabled in the production-style variant.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.350", "Dialogs and overlays", false, "BUILD 133 · ELDRAZI AND ENGINE CANDIDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.351", "Dialogs and overlays", false, "• Combined newer Argentum source and Eldrazi Incursion's 94th original indexed slot (Azlask) with real-card scenarios and strict terminal AI/deck testing.\n• Build 133 attempts exposed a shock-fetch/Android unit-test regression; fixes and passing exact-source tests were required before any subsequent preview. A test attempt is not a released upgrade.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.352", "Dialogs and overlays", false, "BUILD 132 · ENGINE, DECK AND ANDROID INTEGRATION", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.353", "Dialogs and overlays", false, "• Prepared 93 original Eldrazi Incursion slots, updated Argentum runtime patches and Ulalek stack-copying, Eldrazi Temple and Corrupted Crossroads scenarios.\n• The existing developer-only deck builder searches the packaged playable card library offline, validates Commander lists, supports undo/redo and suggests replacements without silently changing a deck.\n• Fixed Android-incompatible regular expressions that crashed the deck editor and added automatic regex guards. The original Build 132 combined validation did not establish a releasable APK.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.354", "Dialogs and overlays", false, "BUILD 131 · ARGENTUM AND PRECON INTEGRATION", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.355", "Dialogs and overlays", false, "• Upgraded the on-device Argentum engine, rebuilt coherent runtime JARs and card assets, and established repeatable hash-checked runtime packaging.\n• Expanded official Warhammer and Eldrazi precon work and custom-card testing; not every card has complete rules support.\n• Improved game-result diagnostics and rules-regression coverage. Priority stops, token-heavy performance and more rules cases remained follow-up work.", color = SoftWhite, fontSize = 12.sp)
                StudioText("TableDialogs.Text.356", "Dialogs and overlays", false, "BUILDS 124–130 · GAMEPLAY AND DEBUG EVOLUTION", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.357", "Dialogs and overlays", false, "• Iterative fixes and investigations covered Treasure mana, legal cast/payment feedback, complex token/trigger performance, opening-hand overlays, and Lunar Whale, Iron Spider and Myriad rules.\n• Improved source-built engine integration, diagnostics and repeatable card/AI scenario testing. Several items remained open; inclusion here does not claim all bugs are fixed.\n• Further official precon and custom-card implementations were staged across development branches rather than shipped uniformly in every intermediate APK.", color = SoftWhite, fontSize = 12.sp)
                // END changelog-since-build121
                StudioText("TableDialogs.Text.358", "Dialogs and overlays", false, "BUILD 123 · LARGE END-STEP AI FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.359", "Dialogs and overlays", false, "• The offline AI skips pointless redirect searches on targetless spells and abilities.\\n• AI simulations share a deadline, including nested choices, and long resolution sequences show progress updates.\\n• Retains the Build 122 fixes and Quick UI Tips, based on Build 120.", color = SoftWhite, fontSize = 13.sp)
                StudioText("TableDialogs.Text.360", "Dialogs and overlays", false, "BUILD 122 · FIXES AND QUICK UI TIPS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.361", "Dialogs and overlays", false, "• Smaug copies keep their death ability and create 14 Treasures when they die.\n• Valkyrie’s Call returns Cid as an Angel with flying and a +1/+1 counter. A returned Angel’s next death ends the legend-rule sequence.\n• The offline AI spends less time searching on large boards, with bounded thinking budgets and more useful timing diagnostics.\n• New Quick UI Tips popup, grouped by interaction, with Dismiss and Don’t remind me again.\n• Temporarily removed the custom-card/opponent notice after choosing your opening hand.\n• Based on Build 120, preserving the seven WotC precons. Build 122 is now shown consistently in the app and installer.", color = SoftWhite, fontSize = 13.sp)
                StudioText("TableDialogs.Text.362", "Dialogs and overlays", false, "EARLIER HISTORY · BUILDS 105 AND BEFORE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.363", "Dialogs and overlays", false, "BUILDS 105–106 · NEW CARDS AND DECKS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.364", "Dialogs and overlays", false, "• 62 more custom cards (92 in all). New premade decks for you or the AI: Ayula Bears, Sliver Overlord, Hazel Squirrels.\n• Waiting for your friend shows in the bar; a secret nudge; prompts name the card asking; custom cards load their art.", color = SoftWhite, fontSize = 13.sp)
                StudioText("TableDialogs.Text.365", "Dialogs and overlays", false, "BUILD 104 · ONLINE WITH A FRIEND", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.366", "Dialogs and overlays", false, "• PLAY → Play with a friend → Host online. Your phone runs the game and shows a room code; your friend picks Join online and types it. Works across any networks.", color = SoftWhite, fontSize = 13.sp)
                StudioText("TableDialogs.Text.367", "Dialogs and overlays", false, "BUILDS 100–102 · WI-FI AND SPEED", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.368", "Dialogs and overlays", false, "• Wi-Fi with a friend: PLAY → Wi-Fi with a friend. One phone hosts, the other joins (Commander decks).\n• Offline games stay fast on big boards; the bar says ENGINE THINKING… while the engine works.\n• Counters show as small badges (+1/+1 ×8); only creatures show power/toughness.\n• One Notice time setting in Adjust UI for banners, pop-ups and the recap. Triggered abilities go in the game log. Your tab comes right after TABLE.", color = SoftWhite, fontSize = 13.sp)
                StudioText("TableDialogs.Text.369", "Dialogs and overlays", false, "BUILD 99 · ADJUST UI", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.370", "Dialogs and overlays", false, "• New Adjust UI screen: choose your Quick Actions, and switch the card pop-up, recent plays, banners, recap, turn banner, buzz and card stacking on or off. Pop-up time and combat card size sliders.\n• Options regrouped. New: skip AI actions unless they affect you.\n• Recent-plays strip folds away. Fetched shock lands ask to pay life (offline). X never offers more than you can pay.", color = SoftWhite, fontSize = 13.sp)

                StudioText("TableDialogs.Text.371", "Dialogs and overlays", false, "BUILD 98 · CLEAN-UP", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.372", "Dialogs and overlays", false, "• The old guided tutorial is gone (a new one is coming).\n• Treasures now really pay for spells offline (they were asking for a colour behind the scenes and giving up).\n• Costs like \"Return an Elf you control\" now let you pick the card.\n• Behind the scenes: the app's code is split into smaller files, ready for 4-player games.", color = SoftWhite, fontSize = 13.sp)

                StudioText("TableDialogs.Text.373", "Dialogs and overlays", false, "BUILD 97 · NEW HOME, TREASURES, FASTER COMBAT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.374", "Dialogs and overlays", false, "• New home screen: your deck up top, one PLAY button. Pick offline vs AI (and the AI's deck) or online.\n• Your decks are saved and the one you pick stays picked, even after a game ends.\n• Treasures pay for spells offline, and show \"Sacrifice for mana\" in their menu. USE 5 / USE ALL for stacks of Treasures, Clues and Foods.\n• Attack with ALL, and tap a stack (×N) to attack with every copy.\n• See what the AI plays: a big card pop-up, a side strip of recent plays, ability banners, and a recap when your turn starts.\n• X picker with −5 / +5 and type-in. \"Always yes this turn\" for repeated prompts.\n• Fewer pointless pass stops; no more passing over an open prompt.", color = SoftWhite, fontSize = 13.sp)

                StudioText("TableDialogs.Text.375", "Dialogs and overlays", false, "BUILD 96 · CUSTOM CARDS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.376", "Dialogs and overlays", false, "• Arcana's own custom cards: 30 cards Argentum doesn't have yet, so Tokens Win (Rocco) and Artificer (Tails) play offline. Only Goblin Welder and Ratchet are still swapped for basic lands. Argentum's own version of a card always wins once it has one.\n• Offline games now show the AI's turn step by step, and your combat steps too, instead of jumping to your next move.\n• Offline mulligans (London rules) and undo.\n• Whose turn it is: you're gold, the AI is blue. The phase bar, the seat tabs (YOUR TURN / AI'S TURN) and the active board's outline use that colour, a banner shows when a turn starts, and your turn gives a small buzz.\n• The pass button lights up gold when you can respond on the AI's turn, and works whichever board you're looking at.\n• Battlefield: pinch with two fingers to zoom (remembered for each screen shape), rows wrap instead of running off the side, and identical cards stack with a count.\n• The bottom buttons are lined up, with a slightly larger hand drawer, and the little card boxes in it are gone.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.377", "Dialogs and overlays", false, "BUILD 95 · PLAY OFFLINE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.378", "Dialogs and overlays", false, "• New PLAY OFFLINE VS AI button on the home screen: a full game against Argentum's AI with no internet and no server.\n• All 15,942 Argentum cards are on the phone, so your imported Commander deck plays offline against one of Argentum's premade Commander decks. Cards Argentum doesn't have are left out and replaced with basic lands (your saved deck isn't changed).\n• Not yet offline: mulligans (you keep your opening hand) and undo.", color = SoftWhite, fontSize = 13.sp)

                StudioText("TableDialogs.Text.379", "Dialogs and overlays", false, "BUILD 94 · ENGINE TRIAL", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.380", "Dialogs and overlays", false, "• Argentum's rules engine and AI now run inside the app, with no server, as a trial. Options → Help & diagnostics → Run engine self-test plays one AI-vs-AI game with the Portal cards and reports how long it took and how much memory it used.\n• Cards ship as Argentum's own card data instead of compiled code.\n• Everything else is the same as Build 93.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.381", "Dialogs and overlays", false, "BUILD 93 · MORE ROOM FOR CARDS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.382", "Dialogs and overlays", false, "• A slimmer table: phases sit in one strip at the top, each player's life is on their tab, and the commander and mana are small chips. Landscape phones now show the battlefield.\n• The status box only appears for problems, a waiting confirmation or something on the stack.\n• On an unfolded Fold, an opponent's board sits beside yours.\n• Your hand opens from every table screen, including the Table tab.\n• New combat board: attackers on top, your blockers below. Tap a blocker, then the attacker; lines link them and a summary reads like \"Ramosian Captain 2/2 first strike ← 3 Rabbit Tokens (1/1)\". Attacking and combat damage use the same board.\n• Hold any card to see it large. Let go to keep it up; slide off to close.\n• Fixes: \"TYPE · null\" badges and \"null\" mana costs.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.383", "Dialogs and overlays", false, "BUILD 92 · QUICKER TURNS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.384", "Dialogs and overlays", false, "• The game log shows the whole game from Argentum, newest first, with turn headers and your actions marked YOU.\n• The pass button says where passing takes you, like PASS → TO COMBAT.\n• PASS TO MY TURN keeps passing for you and stops when you're attacked, an opponent casts something, or Argentum needs a choice.\n• UNDO appears when Argentum lets you take back your last action.\n• Quick Actions only show buttons that work in Argentum games: Graveyard, Exile, Stack, Game log, Cast commander, Pass to my turn.\n• The table follows whose turn it is (turn this off in Options).\n• Options: Developer Options are gone; a new Help & diagnostics section has the connection test, diagnostic log, What's new and the guided tour.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.385", "Dialogs and overlays", false, "BUILD 89 · POLISHED ARGENTUM TABLE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.386", "Dialogs and overlays", false, "• Game HUD: phase tracker, life totals that flash on change, who has priority, the live stack, and the latest moves from Argentum's game log.\n• Cards Argentum lets you play glow; tapping a card opens a sheet with its art, rules text and every action Argentum allows right now.\n• Battlefield badges for attacking, blocking, power/toughness with damage, and summoning sickness.\n• Every Argentum choice now has a control: multi-target spells, divided damage, X, mana colour, piles, replacement choices and mode budgets.\n• Full-screen opening hand, a Victory / Defeat screen with Play again, and a buzz (or background alert) when it's your move.\n• Fixes: graveyard targets, stale actions after a new state, and an old room dialog appearing at Argentum game over.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.387", "Dialogs and overlays", false, "BUILD 88 · COMBAT DAMAGE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.388", "Dialogs and overlays", false, "• Combat damage now opens a damage board instead of stopping at Needs handling. It starts from Argentum's own assignment, so Confirm damage applies it in one tap; adjust amounts per blocker and trample target with lethal hints, and Arcana only enables Confirm for splits Argentum accepts.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.389", "Dialogs and overlays", false, "BUILD 87 · ARGENTUM INTEGRATION", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.390", "Dialogs and overlays", false, "• Attack and block declarations come from Argentum; the main button names what it will do (Pass priority, Declare attackers, Declare blockers).\n• More Argentum choices open in Arcana dialogs: modes, library search, ordering, mana auto-pay, damage assignment and distribution. Anything else shows a Needs handling notice with Cancel, Refresh or View table instead of blocking the app.\n• Unsupported deck cards open a review: play without them for this game, play the full deck in the Arcana sandbox, or edit the deck. Your saved deck is never changed.\n• No automatic resume: after a relaunch, choose Resume game or End that game. A connection lost in the background waits for Resume.\n• Engine status strip, searchable diagnostic log with redacted export, and a hand drawer that rearranges cards as you resize it (Adjust UI → Hand).", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.391", "Dialogs and overlays", false, "BUILD 86 · ARGENTUM PREMADE DECKS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.392", "Dialogs and overlays", false, "• Removed random external deck fetching. The importer now offers Argentum's own premade Commander lists and only shows lists that pass the live Argentum Commander validator.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.393", "Dialogs and overlays", false, "BUILD 84 · ARGENTUM COMPATIBLE-DECK FILTERING", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.394", "Dialogs and overlays", false, "• Practice, create-room, and join-room actions now use Argentum Commander lobbies. The host can copy the server room code, joining validates the imported deck with Argentum first, and the Argentum stack panel displays server state and submits server-provided Pass Priority actions.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.395", "Dialogs and overlays", false, "BUILD 82 · ARGENTUM NATIVE TABLE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.396", "Dialogs and overlays", false, "• Argentum practice games now run through Arcana’s existing table and player screens. The server validates the imported Commander deck, unsupported cards are removed and replaced only with Argentum-supported basic lands, and Argentum supplies the masked game state and legal actions. The native client keeps its connection active while switching apps and reconnects after a drop. Common opening-hand, target, color, option, number, and card-selection decisions are shown in Arcana dialogs.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.397", "Dialogs and overlays", false, "BUILD 81 · QUICK ACTIONS AND TURN COUNTER", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.398", "Dialogs and overlays", false, "• The player-screen zone strip is now a configurable Quick Actions bar. Add, remove, and reorder zone shortcuts and actions in Adjust UI; empty zones stay visible with a count of 0. The Mana Pool element now appears on the live player board when enabled.\n• Mana pips are larger across card costs and text, and card three-dot menus have smaller visible buttons with an accessible tap area.\n• The TURN counter advances after every eligible player has completed a turn. It remains labeled TURN.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.399", "Dialogs and overlays", false, "BUILD 80 · PLAYER BATTLEFIELD HEIGHT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.400", "Dialogs and overlays", false, "• Removed the extra weighted spacer above the player footer. The player board now receives the full available height, so the battlefield fills the space above the footer instead of leaving a large blank gap.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.401", "Dialogs and overlays", false, "BUILD 79 · TABLE CARDS AND LIBRARY PRIVACY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.402", "Dialogs and overlays", false, "• Deck imports automatically try close Scryfall name matches before opening review; the review menu appears only for cards or deck issues that remain unresolved, and successful corrections are reported.\n• The table overview enlarges cards to use each player panel's available width. Player battlefield windows use the full space above the footer and open at the top when switching boards.\n• General library browsing displays a randomized card list without library positions. The Adjust UI editor now selects the matching list item when you tap or drag a preview element.\n• Mana pool is a separate removable and reorderable player-screen element. Tapping the health/activity bar opens Player Status with life, commander damage, counters, and turn details; the life buttons remain quick controls.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.403", "Dialogs and overlays", false, "BUILD 78 · DRAGGABLE UI EDITOR AND PLAYER LAYOUT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.404", "Dialogs and overlays", false, "• Long-press and drag player-screen elements or hand-tray buttons in the editor's sample layout. The active item lifts and tracks your finger while neighboring items animate into place.\n• Player-screen footer controls stay aligned to the bottom, with a larger hand button. The battlefield automatically fills available space and scrolls when its cards exceed that space.\n• Removed the Large View toggle from the table view.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.405", "Dialogs and overlays", false, "BUILD 77 · DRAG-AND-DROP UI EDITOR", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.406", "Dialogs and overlays", false, "• Adjust UI now lets you drag to reorder player-screen sections, remove and restore sections such as the health/activity bar and commander strip, and add, remove, and reorder hand-tray buttons. Layouts are saved separately for portrait and landscape.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.407", "Dialogs and overlays", false, "BUILD 76 · PLAYER LAYOUT AND HAND ACTIONS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.408", "Dialogs and overlays", false, "• Player-screen footer buttons now align on the bottom edge. Opening-hand cards are larger, and the hand tray includes Cast Commander when it can be cast. Pass Priority is the primary stack action; Resolve Top remains available as a smaller action after everyone passes.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.409", "Dialogs and overlays", false, "BUILD 75 · MANA PIPS AND UI ADJUSTMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.410", "Dialogs and overlays", false, "• Mana-choice buttons show colored mana pips. Open the app menu and tap Adjust UI to go straight to the layout controls.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.411", "Dialogs and overlays", false, "BUILD 74 · HAND TRAY AND PLAYER CONTROLS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.412", "Dialogs and overlays", false, "• Opening-hand cards are larger, and hand-tray cards grow or shrink with the drawer. The tray now holds Draw, Combat, Stack, turn flow, and secondary game commands; the Stack shortcut is off the view selector. The player footer stays at the bottom, and the old drag-to-play hint is gone.\n• Mana symbols use colored pips. Permanents that say to choose a creature type prompt for one as they enter and display the choice. Public card mentions in the game log can open a card preview.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.413", "Dialogs and overlays", false, "BUILD 73 · TARGETING EXPERIMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.414", "Dialogs and overlays", false, "• Targeted spells, Auras, activated abilities, and recognized targeted triggers pause for target selection. The picker lists visible players, permanents, stack items, and cards in visible zones; add a legal target by name if it is not listed. Chosen targets appear on the stack and in the game log, and sync to the room. The picker estimates target count, but players still verify card-specific restrictions. This experimental app installs separately from the stable release.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.415", "Dialogs and overlays", false, "BUILD 72 · PLAYER TABS AND ACTIVE BOARD", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.416", "Dialogs and overlays", false, "• Player view tabs use cropped commander artwork with the player name at the bottom. On the full table, the active player's board opens automatically and the view scrolls to it; the previous active board closes when the turn changes. The player footer has PASS TURN, a larger centered HAND button, and BACK TO TABLE. Mana access is now in the player status header. ADD TOKEN is in MORE; the footer no longer shows the library count. The command-zone strip is more compact while preserving commander casting, cast count, tax, and status.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.417", "Dialogs and overlays", false, "BUILD 71 · CHAT COPY AND PASTE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.418", "Dialogs and overlays", false, "• Chat messages support text selection by long-pressing, with Android's copy action. Added a PASTE control to append clipboard text to the draft; the message field also keeps Android's normal text paste menu.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.419", "Dialogs and overlays", false, "BUILD 70 · DUPLICATE PERMANENT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.420", "Dialogs and overlays", false, "• Resolving an activated or loyalty ability now leaves its source permanent in its current zone. Only resolving a spell moves that spell card to the battlefield or graveyard.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.421", "Dialogs and overlays", false, "BUILD 69 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.422", "Dialogs and overlays", false, "• Added trigger detection for recognized spell-cast, draw, and creature-entry events. Triggers show their event and effect, offer target-player selection for supported player-target effects, and keep modes or unsupported effects on a manual-resolution path. Rhystic Study and Smothering Tithe keep their pay-or-resolve choices.\n• Expanded simple automatic effect handling for targeted player life, draw, and token effects after a target is chosen.\n• The Activity Log now includes turn phase, spell cost/payment detail, trigger events, automatic changes, and manual-resolution notes, with visual emphasis for payments, triggers, and effects.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.423", "Dialogs and overlays", false, "BUILD 68 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.424", "Dialogs and overlays", false, "• The opening-hand screen now shows card faces. Tap any card to open a large preview; while choosing cards to bottom, use the separate button beneath each card to select it. The selection count and required bottom count remain visible.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.425", "Dialogs and overlays", false, "BUILD 67 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.426", "Dialogs and overlays", false, "• Added a multiplayer opening-hand step before Ready Up. Each player can keep or mulligan; every mulligan shuffles the whole hand into the library and deals a fresh seven. The first multiplayer mulligan is free.\n• After keeping, players tap the exact number of cards to put on the bottom of their library.\n• Ready Up now requires a confirmed opening hand. LAN and Internet room state carries mulligan status, and room servers reject ready requests until both the deck and hand decision are set.\n• Starting the lobby game preserves each player's chosen hand and library order instead of dealing a new hand.\n\nThis is a source-only update; the Build 66 APK does not include these changes.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.427", "Dialogs and overlays", false, "BUILD 66 · CHANGES SINCE BUILD 65", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.428", "Dialogs and overlays", false, "• The hand panel can now be resized by dragging its top handle and remembers its height.\n• The stack window closes automatically when the stack is empty and pending triggers or payment choices have finished; reopen it any time from the Stack button.\n• Player views use tighter spacing, compact zone chips, and a battlefield area sized to its cards that scrolls when crowded.\n• Diagnostic logs now capture repeated deck quantities, visible card moves, zone totals, repeated card-instance IDs, and inbound snapshot sequence numbers to help trace import and sync problems.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.429", "Dialogs and overlays", false, "BUILD 65", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.430", "Dialogs and overlays", false, "• Added an import check that flags unmatched or unverifiable cards. Auto Fix closes the review when every card is matched; unresolved cards can be opened in the editor for manual correction.\n• Added automatic handling for supported fixed life, draw, and token effects, plus simple creature-entry life triggers. Effects the app cannot safely parse prompt the player to resolve them manually.\n• Added a persistent Stack button beside the view tabs and another in the hand tray; the tab button highlights while the stack has items.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.431", "Dialogs and overlays", false, "EARLIER CHANGES · BUILDS 60–64", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StudioText("TableDialogs.Text.432", "Dialogs and overlays", false, "BUILD 60", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.433", "Dialogs and overlays", false, "• Rebuilt the optional visual guide with screen highlights and straight red arrows.\n• Added Share Room, the default room URL reminder, tapped-entry choices, and shock-land life prompts.\n• Added an empty-stack notice and simplified menu labels.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.434", "Dialogs and overlays", false, "BUILD 61", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.435", "Dialogs and overlays", false, "• Enlarged the tutorial panels and controls. The room URL step now explains that the default address is already set and can continue without opening the keyboard.\n• Improved card-action dialog layout so titles and actions fit.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.436", "Dialogs and overlays", false, "BUILD 62", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.437", "Dialogs and overlays", false, "• Added Next to every tutorial step, plus an import review with Auto Fix and Trim Excess.\n• Added per-card moves from Graveyard and Exile, and hid Set as Commander after one is assigned.\n• Concealed face-down card details. Made Cast from Hand the primary stack action and tucked manual ability entry behind a small control.\n• Replaced clunky view swipes with synchronized table/player paging and aligned the hand-sheet drag handle.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.438", "Dialogs and overlays", false, "BUILD 63", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.439", "Dialogs and overlays", false, "• Improved stack-window sizing.\n• Updated the deck editor to track cards in every zone and keep rename/remove actions available after cards move.\n• Improved AI readiness and recovery from unexpected turn errors.\n• Expanded mana-ability tracking for common mana rocks, dual-color choices, and basic lands.", color = SoftWhite, fontSize = 11.sp)

                StudioText("TableDialogs.Text.440", "Dialogs and overlays", false, "BUILD 64", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                StudioText("TableDialogs.Text.441", "Dialogs and overlays", false, "• Fixed the Table overview layout so player panels remain visible.", color = SoftWhite, fontSize = 11.sp)
            }
        },
        confirmButton = { StudioTextButton("TableDialogs.TextButton.70", "Dialogs and overlays", onClick = { onDismiss() }) { StudioText("TableDialogs.Text.442", "Dialogs and overlays", false, "CLOSE", color = Mint) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )
}
