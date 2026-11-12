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
    Dialog(
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
                        Text("${player.name} · LIBRARY", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${player.library.size} cards · randomized display · library order hidden", color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = { onShuffle(); shuffledSinceMove = true }, modifier = Modifier.heightIn(min = 44.dp)) { Text("SHUFFLE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = ::close, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close library", tint = Muted) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    singleLine = true,
                    label = { Text("Search name, type, or rules text") },
                    placeholder = { Text("Blank search shows the full library") }
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    LibrarySort.entries.forEach { s -> FilterChip(sort == s, { sort = s }, label = { Text(s.label, fontSize = 11.sp) }) }
                    chips.forEach { (c, n) -> FilterChip(chip == c, { chip = if (chip == c) null else c }, label = { Text("${c.label} $n", fontSize = 11.sp) }) }
                }
                if (selected.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selected.size == 1) "SELECTED · ${selected.single().name}" else "${selected.size} SELECTED", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.heightIn(min = 40.dp)) { Text("CLEAR", color = Muted, fontSize = 9.sp) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            Triple("TO HAND", onTakeToHand, true),
                            Triple("TO BATTLEFIELD", onPutBattlefield, true),
                            Triple("TO GRAVEYARD", onPutGraveyard, true),
                            Triple("EXILE", onPutExile, true),
                            Triple("TOP", onMoveToTop, false),
                            Triple("BOTTOM", onMoveToBottom, false)
                        ).forEach { (label, action, leaves) ->
                            TextButton(onClick = { moveSelected(action, leaves) }, modifier = Modifier.heightIn(min = 44.dp)) {
                                Text(label, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Text("Tap cards to select one or more, then move them. Long press a card to inspect it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 5.dp))
                }
                HorizontalDivider(color = PanelLight)
                when {
                    player.library.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Library is empty", color = Muted) }
                    matches.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No matching cards", color = Muted) }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        matches.forEachIndexed { index, (group, card) ->
                            if (sort != LibrarySort.NAME && (index == 0 || matches[index - 1].first != group)) item(key = "h-$group") {
                                Text("${group.uppercase()} · ${matches.count { it.first == group }}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                            }
                            item(key = card.id) {
                                val isSelected = card.id in selectedIds
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSelected) PanelLight else Panel)
                                        .border(if (isSelected) 1.dp else 0.dp, if (isSelected) Mint else Panel, RoundedCornerShape(9.dp))
                                        .combinedClickable(onClick = { selectedIds = if (isSelected) selectedIds - card.id else selectedIds + card.id }, onLongClick = { onPreview(card) })
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CardFace(card, Modifier.width(37.dp), onClick = { selectedIds = if (isSelected) selectedIds - card.id else selectedIds + card.id }, onLongClick = { onPreview(card) }, showCounters = false)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(listOfNotNull(card.manaCost?.takeIf { it.isNotBlank() }, card.type, card.power?.let { "$it/${card.toughness}" }).joinToString("  ·  "),
                                            color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) Text("✓", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Draw and common turn commands are in the hand tray.", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onShuffle(); onDismiss() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("DONE · SHUFFLE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (askShuffle) AlertDialog(
        onDismissRequest = { askShuffle = false },
        title = { Text("Shuffle your library?") },
        text = { Text("You moved cards out of ${player.name}'s library. Searching usually ends with a shuffle.") },
        confirmButton = { TextButton(onClick = { askShuffle = false; onShuffle(); onDismiss() }) { Text("SHUFFLE") } },
        dismissButton = { TextButton(onClick = { askShuffle = false; onDismiss() }) { Text("DON'T SHUFFLE") } },
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${player.name.uppercase()} · MANA POOL", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("$total mana tracked", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close mana pool", tint = Muted) }
                }
                Text("Tap + when you produce mana. Payments spend from this pool; it empties when you advance to another step.", color = Muted, fontSize = 10.sp)
                manaColors.forEach { (symbol, name) ->
                    val amount = player.manaPool[symbol] ?: 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ManaPip(symbol)
                            Text(name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onChange(symbol, -1) }, enabled = amount > 0, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−", color = if (amount > 0) SoftWhite else Muted, fontSize = 20.sp) }
                        Text("$amount", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 28.dp))
                        TextButton(onClick = { onChange(symbol, 1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+", color = Mint, fontSize = 20.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear, enabled = total > 0, modifier = Modifier.heightIn(min = 44.dp)) { Text("CLEAR POOL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("DONE", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun CreatureTypeChoiceDialog(cardName: String, onConfirm: (String) -> Unit) {
    var creatureType by remember(cardName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Choose a creature type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${cardName} asks you to choose a creature type as it enters.", color = Muted)
                OutlinedTextField(
                    value = creatureType,
                    onValueChange = { creatureType = it.take(40) },
                    singleLine = true,
                    label = { Text("Creature type") },
                    placeholder = { Text("e.g. Elf") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(creatureType.trim()) }, enabled = creatureType.isNotBlank()) {
                Text("CONFIRM", color = if (creatureType.isNotBlank()) Mint else Muted, fontWeight = FontWeight.Bold)
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GAME LOBBY", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 0.8.sp)
                        Text("COMMANDER · UP TO 4 PLAYERS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        Text("APP VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close game lobby", tint = Muted) }
                }

                Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFF3A3021), shape = RoundedCornerShape(9.dp)) {
                    Text(if (internetMode) "INTERNET ROOM · Connect from different networks using the secure room service." else "SAME WI-FI ROOM · All phones must be on the same local network.", color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val createSelected = !joining
                    Button(
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
                            Text("CREATE ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (createSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val joinSelected = joining
                    Button(
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
                            Text("JOIN ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (joinSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val localSelected = !internetMode
                    Button(
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
                            Text("LOCAL WI-FI", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (localSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val internetSelected = internetMode
                    Button(
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
                            Text("INTERNET", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (internetSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated || joining) {
                    if (joining) {
                        val isConnecting = onlineStatus == "Connecting"
                        Text(if (internetMode) "Join an internet room" else "Join a room on the same Wi-Fi network", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode) {
                            OutlinedTextField(cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room service URL") })
                            Text("Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                        }
                        else OutlinedTextField(hostInput, onHostInput, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Host IP address · port is automatic") }, placeholder = { Text("192.168.1.25") })
                        OutlinedTextField(playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Your player name") })
                        OutlinedTextField(joinCode, onJoinCode, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room code") })
                        OutlinedTextField(joinPassword, onJoinPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                        Button(onClick = onJoin, enabled = !isConnecting && (if (internetMode) cloudEndpoint.isNotBlank() else hostInput.isNotBlank()) && joinCode.isNotBlank() && joinPassword.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp)) {
                            Text(if (isConnecting) "CONNECTING…" else "JOIN GAME")
                        }
                        if (isConnecting) {
                            OutlinedButton(onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                                Text("CANCEL JOIN", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
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
                                    Text(
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
                        Text(if (internetMode) "Create a password-protected internet room" else "Create a password-protected room", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode && onlineStatus == "Connecting") {
                            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(9.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                                    Text("Opening a secure internet room…", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                }
                            }
                            OutlinedButton(onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                                Text("CANCEL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (internetMode) {
                                OutlinedTextField(cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room service URL") })
                                Text("Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                                OutlinedTextField(playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Your player name") })
                            }
                            OutlinedTextField(password, onPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room password · 4 characters minimum") }, visualTransformation = PasswordVisualTransformation())
                            if (notice != null) Text(notice, color = Color(0xFFFF9C91), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = onCreate, enabled = password.length >= 4 && (!internetMode || cloudEndpoint.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp)) { Text("CREATE LOBBY") }
                        }
                    }
                } else {
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("ROOM CODE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(roomCode, color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                }
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table room code", roomCode))
                                }, modifier = Modifier) { Text("COPY", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                            TextButton(onClick = {
                                val network = if (internetMode) "Internet room · $cloudEndpoint" else "Same Wi-Fi · ${hostAddress.ifBlank { "ask the host to share their IP" }}"
                                val invite = "Join my Arcana Table game!\nRoom code: $roomCode\nPassword: $password\n$network"
                                runCatching {
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, invite)
                                    }, "Share room invite"))
                                }
                            }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) {
                                Text("SHARE ROOM", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("PASSWORD PROTECTED · ${"•".repeat(password.length.coerceAtMost(12))}", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (hostAddress.isBlank()) onlineStatus else "SAME WI-FI · $hostAddress", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (hostAddress.isNotBlank()) TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table host address", hostAddress))
                                }, modifier = Modifier) { Text("COPY IP", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("PLAYERS", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("${players.size}/4 SEATS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                        Text("P${index + 1}", color = if (ready) Ink else SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                                        Text(player.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${if (index == 0) "HOST · " else ""}${if (player.isComputer) "AI · " else if (player.isOnline) "ONLINE · " else "OFFLINE · "}${when { !player.deckLoaded -> "DECK REQUIRED"; !deckConfirmed -> "DECK SYNCING"; !handDecisionMade -> "OPENING HAND NEEDED"; else -> "DECK READY" }}${if (index == localSeat) " · YOU" else ""}",
                                            color = if ((player.isOnline || player.isComputer || index == localSeat) && deckReady && handDecisionMade) Mint else CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(if (ready) "READY" else "NOT READY", color = if (ready) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                                    if (!player.deckLoaded && index == localSeat) TextButton(onClick = { onImportDeck(index) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("IMPORT DECK", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (!gameStarted && index == localSeat && deckReady && !player.openingHandKept && !player.isComputer) TextButton(onClick = { openingHandDialogOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("OPENING HAND · ${player.hand.size}", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                    TextButton(onClick = { onToggleReady(index) }, enabled = canToggleReady || ready, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (gameStarted) "GAME STARTED" else if (index == localSeat && pendingReady != null) "SENDING…" else if (ready) "CANCEL READY" else if (!handDecisionMade && index == localSeat) "CHOOSE HAND" else if (canMarkReady) if (!deckConfirmed) "SYNCING…" else "READY UP" else "WAITING", color = if (canToggleReady || ready) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (isHost && index > 0 && !internetMode) TextButton(onClick = { removeTarget = index }, modifier = Modifier.heightIn(min = 48.dp)) { Text("REMOVE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    if (isHost && !gameStarted && players.size < 4) {
                        OutlinedButton(onClick = onAddAi, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                            Text("ADD AI OPPONENT · ${players.size}/4", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (players.size < 4) {
                        repeat(4 - players.size) { seat ->
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), color = Panel.copy(alpha = 0.55f), shape = RoundedCornerShape(9.dp)) {
                                Text("P${players.size + seat + 1} · Waiting for a player to join with the room code", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(11.dp))
                            }
                        }
                    }

                    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("GOES FIRST", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(firstPlayer, color = Mint, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Text(if (turnOrder == null) "Current seat order" else "Randomized turn order", color = Muted, fontSize = 8.sp)
                            }
                            if (isHost) TextButton(onClick = onRandomizeTurnOrder, modifier = Modifier.heightIn(min = 44.dp)) { Text("RANDOMIZE ORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (notice != null) Text(notice, color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    if (!decksReady) {
                        val missingDecks = players.filterNot { it.deckLoaded }.joinToString { it.name }
                        Text("Waiting for a deck from: $missingDecks", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (!allReady) {
                        val notReady = players.mapIndexedNotNull { index, player -> player.name.takeIf { index !in readyPlayers } }.joinToString()
                        Text("Waiting for ready confirmation from: $notReady", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (isHost && canStart) {
                        Text("Everyone is ready. Start the game when you’re set.", color = Mint, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    }

                    OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) {
                        Text("EXPORT DIAGNOSTIC LOG", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isHost && !gameStarted) Button(onClick = onStartGame, enabled = canStart, modifier = Modifier.fillMaxWidth().padding(top = 11.dp).heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = PanelLight, disabledContentColor = Muted)) {
                        Text("START GAME", fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                    } else if (isHost) Text(if (gameEnded) "GAME ENDED · unanimous vote" else "GAME IN PROGRESS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    else Text("$onlineStatus · Waiting for the host to start", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    if (isHost && gameStarted && !gameEnded && !canHostHandOff) {
                        Text("A connected player must be available to take over hosting before you can leave.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    TextButton(onClick = onLeave, enabled = canLeaveRoom, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text(
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
        AlertDialog(
            onDismissRequest = { removeTarget = -1 },
            title = { Text("Remove $targetName?") },
            text = { Text("This removes the player and their seat from the online room.") },
            confirmButton = {
                TextButton(onClick = { onRemovePlayer(removeTarget); removeTarget = -1 }) {
                    Text("REMOVE PLAYER", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = -1 }) { Text("CANCEL") } }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text("OPENING HAND · ${player.hand.size} CARDS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("Mulligans taken: ${player.mulligansTaken} · First multiplayer mulligan is free", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (choosingBottomCards) "Select exactly $bottomCount card(s) to put on the bottom of your library, then confirm."
                    else "Tap a card to enlarge it. Keep this hand or mulligan; a mulligan shuffles your whole hand into your library, then deals a fresh seven.",
                    color = SoftWhite, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )
                if (choosingBottomCards) Text("Tap a card to enlarge it; use the button under a card to select it for the bottom.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    player.hand.forEach { card ->
                        val selected = card.id in selectedCardIds
                        Column(Modifier.width(openingCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(9.dp)).padding(2.dp)) {
                                CardFace(card, Modifier.fillMaxWidth(), onClick = { previewCard = card }, onLongClick = { previewCard = card }, showCounters = false)
                            }
                            Text(card.name, color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            if (choosingBottomCards) {
                                OutlinedButton(
                                    onClick = { selectedCardIds = if (selected) selectedCardIds - card.id else selectedCardIds + card.id },
                                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).heightIn(min = 38.dp),
                                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, if (selected) Mint else Muted)
                                ) {
                                    Text(if (selected) "${selectedCardIds.indexOf(card.id) + 1} · BOTTOM" else "SELECT", color = if (selected) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (choosingBottomCards) {
                    Text("Selected: ${selectedCardIds.size}/$bottomCount", color = if (selectedCardIds.size == bottomCount) Mint else CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { choosingBottomCards = false; selectedCardIds = emptyList() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("BACK", color = Muted) }
                        Button(onClick = { onKeep(selectedCardIds) }, enabled = selectedCardIds.size == bottomCount, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CONFIRM KEEP") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                        OutlinedButton(onClick = onMulligan, enabled = canMulliganAgain, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("MULLIGAN", color = if (canMulliganAgain) CounterGold else Muted) }
                        Button(onClick = { if (bottomCount == 0) onKeep(emptyList()) else choosingBottomCards = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (bottomCount == 0) "KEEP HAND" else "KEEP · BOTTOM $bottomCount") }
                    }
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
    darkMode: Boolean, timerVisible: Boolean, backgroundAlertsEnabled: Boolean,
    followActivePlayer: Boolean, connectionLabel: String, pingResult: String?, versionLabel: String, diagnosticCount: Int,
    reducedSwipeMotion: Boolean, cardSizeScale: Float, onOpenLayoutLab: () -> Unit,
    onToggleTheme: () -> Unit, onToggleTimerVisibility: () -> Unit, onToggleBackgroundAlerts: (Boolean) -> Unit,
    onFollowActivePlayer: (Boolean) -> Unit, onReducedMotion: (Boolean) -> Unit, onCardSizeScale: (Float) -> Unit,
    quietOpponentTurns: Boolean = false, onQuietOpponentTurns: (Boolean) -> Unit = {},
    onTestConnection: () -> Unit, onReconnect: (() -> Unit)?, onViewDiagnostics: () -> Unit, onExportDiagnostics: () -> Unit,
    onWhatsNew: () -> Unit,
    engineTrialResult: String? = null, onEngineSelfTest: (() -> Unit)? = null,
    onClearAllData: () -> Unit, onDismiss: () -> Unit
) {
    @Composable
    fun Section(title: String, top: androidx.compose.ui.unit.Dp = 14.dp) =
        Text(title, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = top, bottom = 2.dp))
    @Composable
    fun LinkRow(label: String, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(modifier)) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (detail != null) Text(detail, color = Muted, fontSize = 10.sp)
            }
        }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OPTIONS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close options", tint = Muted) }
                }
                Section("APPEARANCE", top = 8.dp)
                TextButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Theme  ·  ${if (darkMode) "Dark" else "Light"}", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                SettingsToggle("Reduce swipe animation", "Skip the animated jump when switching player tabs.", reducedSwipeMotion, onReducedMotion, Modifier)
                Text("CARD SIZE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.82f to "SMALL", 1f to "STANDARD", 1.18f to "LARGE").forEach { (scale, label) ->
                        TextButton(onClick = { onCardSizeScale(scale) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text(if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) "✓ $label" else label, color = if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) Mint else SoftWhite, fontSize = 9.sp) }
                    }
                }
                TextButton(onClick = onOpenLayoutLab, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                    Text("ADJUST UI LAYOUT  ›", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                }
                Section("GAMEPLAY")
                SettingsToggle("Follow the active player", "Show the player whose turn it is. Your selected tab stays until the next turn.", followActivePlayer, onFollowActivePlayer, Modifier)
                SettingsToggle("Skip AI actions unless needed", "Offline games: pass over AI spells and abilities unless they affect you. Prompts and blocks still stop the game.", quietOpponentTurns, onQuietOpponentTurns, Modifier)
                SettingsToggle("Show turn timer", "Show the time spent on the current turn.", timerVisible, { onToggleTimerVisibility() }, Modifier)
                Section("NOTIFICATIONS")
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alerts while the app is in the background", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Notify you when Argentum is waiting for your move.", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = backgroundAlertsEnabled, onCheckedChange = onToggleBackgroundAlerts)
                }
                Section("HELP & DIAGNOSTICS")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelLight).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(connectionLabel, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (pingResult != null) Text(pingResult, color = if (pingResult.startsWith("Can't")) Color(0xFFE9978B) else Muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        HeaderButton("TEST CONNECTION", onTestConnection, enabled = pingResult != "Testing…")
                        if (onReconnect != null) HeaderButton("RECONNECT", onReconnect)
                    }
                }
                LinkRow("Diagnostic log", "Search $diagnosticCount recorded events", onClick = onViewDiagnostics)
                LinkRow("Export diagnostic log", "Save a text file to share when something goes wrong", onClick = onExportDiagnostics)
                LinkRow("What's new", "Version $versionLabel", onClick = onWhatsNew)
                if (onEngineSelfTest != null) LinkRow(
                    "Run engine self-test",
                    engineTrialResult ?: "Plays one AI-vs-AI game with Argentum inside this phone, no server. Takes up to a minute.",
                    modifier = Modifier.testTag("engine-self-test"),
                    onClick = { if (engineTrialResult?.startsWith("Running") != true) onEngineSelfTest() })
                HorizontalDivider(color = Muted.copy(alpha = 0.25f), modifier = Modifier.padding(top = 14.dp))
                TextButton(onClick = onClearAllData, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 6.dp)) {
                    Text("CLEAR ALL DATA", color = Color(0xFFE9978B), fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth())
                }
                Text("Saved decks are kept when game data is cleared.", color = Muted, fontSize = 10.sp)
            }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ADJUST UI · $orientationName", color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("$orientationName layout · saves automatically", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Close, "Close layout lab", tint = Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("PLAYER", "HAND").forEach { screen ->
                        val selected = previewScreen == screen
                        TextButton(
                            onClick = { previewScreen = screen; selectedElement = if (screen == "HAND") "HAND" else "BATTLEFIELD" },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (selected) PanelLight else Color.Transparent)
                        ) { Text(screen, color = if (selected) Mint else Muted, fontWeight = FontWeight.Bold) }
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
                        TextButton(
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
                            Text(preset, color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp)
                        }
                    }
                }
                Text("ADJUST · $selectedElement", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (selectedElement) {
                        "BATTLEFIELD" -> {
                            LayoutTuningSlider("Card size", cardSizeScale, 0.75f..1.4f, { "${(it * 100).toInt()}%" }, onCardSizeScale)
                        }
                        "STATUS" -> {
                            Text("This element can be removed below and restored from ADD ELEMENT.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                        "COMMANDER" -> Text("This element can be removed below and restored from ADD ELEMENT.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "ZONES" -> Text("Choose which zone shortcuts and actions appear in the Quick Actions strip. Empty zones stay visible with a count of 0.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "SPACING", "STEP", "REVEALED" -> LayoutTuningSlider("Space between sections", boardSpacingScale, 0.5f..2f, { "${(it * 100).toInt()}%" }, onBoardSpacingScale)
                        "HAND" -> {
                            LayoutTuningSlider("Hand tray height", handSheetHeightFraction, 0.38f..0.95f, { "${(it * 100).toInt()}%" }, onHandSheetHeightFraction)
                            LayoutTuningSlider("Largest hand card", handCardScale, 0.7f..1.4f, { "${(it * 100).toInt()}%" }, onHandCardScale)
                            Text("HAND ARRANGEMENT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HandArrangement.entries.forEach { option ->
                                    FilterChip(selected = handArrangement == option, onClick = { onHandArrangement(option) }, label = { Text(option.label, fontSize = 11.sp) })
                                }
                            }
                            Text(handArrangement.detail, color = Muted, fontSize = 10.sp)
                        }
                        "FOOTER" -> LayoutTuningSlider("Footer button size", footerButtonScale, 0.78f..1.3f, { "${(it * 100).toInt()}%" }, onFooterButtonScale)
                    }
                    Text("Tap an element to tune it. Long-press and drag elements in the sample layout; nearby items animate aside as you move.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 8.dp))
                    if (previewScreen == "PLAYER") {
                        Text("PLAYER SCREEN ELEMENTS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
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
                            Text("QUICK ACTIONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
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
                                TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD QUICK ACTION  +", color = Mint, fontWeight = FontWeight.Bold) }
                                DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                    quickActionCatalog.filterNot { it in quickActionOrder }.forEach { id ->
                                        DropdownMenuItem(text = { Text("Add ${layoutQuickActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onQuickActionOrder(quickActionOrder + id) })
                                    }
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD ELEMENT  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                playerSectionCatalog.filterNot { it in playerSectionOrder }.forEach { id ->
                                    DropdownMenuItem(text = { Text("Add ${layoutSectionTitle(id)}") }, onClick = { addElementMenuOpen = false; onPlayerSectionOrder(playerSectionOrder + id) })
                                }
                            }
                        }
                    } else {
                        Text("HAND TRAY BUTTONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
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
                            TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD BUTTON  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                handActionCatalog.filterNot { it in handActionOrder }.forEach { id ->
                                    DropdownMenuItem(text = { Text("Add ${layoutHandActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onHandActionOrder(handActionOrder + id) })
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReset, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("RESET TO DEFAULT", color = Color(0xFFE5B2A7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelLight)) {
                        Text("DONE", color = Mint, fontWeight = FontWeight.Black)
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
                        Text("● YOU · ONLINE · ACTIVE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("40  +", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    "MANA" -> Row(
                        dragModifier.fillMaxWidth().height(25.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "MANA") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("MANA") }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MANA POOL", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("3 TOTAL", color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    "COMMANDER" -> Row(
                        dragModifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "COMMANDER") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("COMMANDER") }.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("COMMAND ZONE · COMMANDER", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                    "STEP" -> Row(dragModifier.fillMaxWidth().height(24.dp).clickable { onSelectElement("STEP") }, verticalAlignment = Alignment.CenterVertically) {
                        Text("NEXT STEP  ›", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 5.dp))
                    }
                    "ZONES" -> Row(dragModifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable { onSelectElement("ZONES") }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        quickActionOrder.forEach { id ->
                            Text("${layoutQuickActionTitle(id).uppercase()}${if (id in setOf("LIBRARY", "HAND", "GRAVEYARD", "EXILE", "REVEALED")) " 0" else ""}", color = SoftWhite, fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PanelLight).padding(horizontal = 5.dp, vertical = 4.dp))
                        }
                    }
                    "REVEALED" -> Text("PUBLIC REVEALS", color = CounterGold, fontSize = 7.sp, modifier = dragModifier.clickable { onSelectElement("REVEALED") }.padding(start = 4.dp))
                    "BATTLEFIELD" -> Column(
                        dragModifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "BATTLEFIELD") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp))
                            .clickable { onSelectElement("BATTLEFIELD") }.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("BATTLEFIELD · grouped by type", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tileWidth = (38.dp * cardSizeScale).coerceIn(31.dp, 54.dp)
                            val tileHeight = tileWidth / 0.72f
                            listOf("CREATURE", "ARTIFACT", "LAND").forEachIndexed { index, label ->
                                Column(Modifier.width(tileWidth).height(tileHeight).clip(RoundedCornerShape(5.dp)).background(listOf(Color(0xFF66513D), Color(0xFF364C52), Color(0xFF4A644C))[index]).padding(3.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = SoftWhite, fontSize = 5.sp, maxLines = 1)
                                    Box(Modifier.fillMaxWidth().height((tileHeight * 0.5f).coerceAtLeast(18.dp)).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.14f)))
                                    Text("CARD NAME", color = SoftWhite, fontSize = 5.sp, maxLines = 1)
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
                                Text(label, color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            Row(Modifier.fillMaxWidth().height(30.dp).clickable { onSelectElement("HAND") }, verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR HAND · 5 CARDS", color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("×", color = SoftWhite, fontSize = 18.sp)
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
                        Text(layoutHandActionTitle(id).uppercase(), color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold,
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
                        Text("SAMPLE CARD", color = SoftWhite, fontSize = 5.sp, modifier = Modifier.padding(bottom = 5.dp))
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MAGIC RULEBOOK", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(if (rulesText.isNotBlank()) rulesText.lineSequence().take(3).lastOrNull { it.contains("effective as of", true) } ?: "Official Wizards Comprehensive Rules" else "Official Wizards Comprehensive Rules · offline after download", color = Muted, fontSize = 9.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close rules", tint = Muted) }
                }
                Text("This reader uses Wizards’ official Comprehensive Rules text. Download it once to browse offline; check for updates when connected.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Search rule number or phrase") })
                    TextButton(onClick = {
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
                    }, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (loading) "DOWNLOADING" else if (rulesText.isBlank()) "DOWNLOAD" else "UPDATE", color = Mint, fontSize = 8.sp) }
                }
                if (status.isNotBlank()) Text(status, color = if (rulesText.isNotBlank()) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                if (rulesText.isBlank()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Download the official Comprehensive Rules to start browsing and searching. The downloaded copy stays on this device for offline use.", color = Muted, fontSize = 12.sp)
                    }
                } else {
                    Text("${ruleLines.size} matching lines", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(ruleLines.size) { index ->
                            Text(ruleLines[index], color = if (ruleLines[index].matches(Regex("^\\d+(?:\\.[a-z]+)?\\..*"))) Mint else SoftWhite, fontSize = 9.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("Source: Wizards of the Coast · magic.wizards.com/en/rules", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
internal fun HandCardActionsDialog(card: GameCard, hasCommander: Boolean, isCommanderCard: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth()) {
            // Let the dialog wrap its title and actions. fillMaxSize() here made
            // a tiny card menu claim the Dialog's full available height.
            Box {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Choose an action", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.Close, "Close actions", tint = Muted) }
                }
                val actions = listOf("PLAY", "PLAY FACE DOWN", "REVEAL", "EXILE", "DISCARD", "INSPECT") + if (isCommanderCard) listOf("COMMAND ZONE") else if (hasCommander) emptyList() else listOf("SET COMMANDER")
                actions.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { action ->
                            TextButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                                Text(action, color = if (action == "DISCARD" || action == "EXILE") Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EDIT ${player.name}'S DECK", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close deck editor", tint = Muted) }
                }
                Text("Tracked cards: ${trackedCards.size}${if (player.commanderCard != null) " + commander" else ""}  ·  ${cards.size + if (commander != null) 1 else 0} names", color = Muted, fontSize = 10.sp)
                catalog?.let { lib ->
                    val report = remember(trackedCards, player.commanderCard, lib) {
                        lib.check((trackedCards + listOfNotNull(player.commanderCard)).groupBy { it.name }.map { (name, copies) -> DeckEntry(name, copies.size) })
                    }
                    Text(report.summary(), color = if (report.unsupportedCopies > 0) CounterGold else Muted, fontSize = 9.sp)
                }
                val searchArgentum = argentumOnly && catalog != null
                OutlinedTextField(query, { query = it; results = emptyList(); suggestingFor = null }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text(if (searchArgentum) "Find an Argentum-supported card" else "Find any Magic card") }, trailingIcon = {
                    TextButton(enabled = query.trim().length >= 2 && !searching, onClick = {
                        suggestingFor = null
                        if (searchArgentum) results = catalog!!.search(query.trim()).map { it.toScryfallCard() }
                        else {
                            searching = true
                            scope.launch { results = Scryfall.searchCards(query.trim()); searching = false }
                        }
                    }) { Text(if (searching) "SEARCHING" else if (searchArgentum) "SEARCH ARGENTUM" else "SEARCH ALL", color = Mint, fontSize = 9.sp) }
                })
                if (catalog != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = argentumOnly, onCheckedChange = { argentumOnly = it; results = emptyList(); suggestingFor = null })
                    Text("Argentum-supported cards only (works offline)", color = SoftWhite, fontSize = 10.sp)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (results.isNotEmpty()) {
                        item { Text(suggestingFor?.let { "SUGGESTED ARGENTUM SWAPS FOR ${it.uppercase()} · tap REPLACE" } ?: if (replacementTarget != null) "CHOOSE A MATCH · REPLACE FLAGGED CARD" else "SEARCH RESULTS · tap + to add to library", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        items(results.size) { index ->
                            val result = results[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(result.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val resultSupport = catalog?.support(result.name)
                                    Text(listOfNotNull("${result.setCode?.uppercase().orEmpty()} ${result.collectorNumber.orEmpty()}".trim().ifBlank { null }, result.manaCost, result.type, resultSupport?.label).joinToString("  ·  "), color = if (resultSupport == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = {
                                    val target = replacementTarget
                                    if (target == null) onAdd(result) else onReplace(target, result)
                                    replacementTarget = null
                                    suggestingFor = null
                                    results = emptyList()
                                    query = ""
                                }, modifier = Modifier.heightIn(min = 40.dp)) { Text(if (replacementTarget == null) "+ ADD" else "REPLACE", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    commander?.let { card ->
                        item(key = "commander-${card.id}") {
                            Text("COMMANDER", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (card.importIssue != null || card.id in highlightedCardIds) Color(0xFF392B21) else PanelLight)
                                    .border(if (card.importIssue != null || card.id in highlightedCardIds) 2.dp else 0.dp, if (card.importIssue != null || card.id in highlightedCardIds) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    card.importIssue?.let { Text("NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    catalog?.support(card.name)?.takeIf { it != CardSupport.ARGENTUM }?.let { Text(it.label.uppercase(), color = if (it == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp) }
                                }
                                if (catalog?.support(card.name) == CardSupport.UNSUPPORTED && card.importIssue == null) {
                                    TextButton(onClick = { suggestSwaps(card) }) { Text("SWAPS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                                if (card.importIssue != null) {
                                    TextButton(onClick = { autoFix(card, listOf(card)) }, enabled = autoFixingCardId == null) {
                                        Text(if (autoFixingCardId == card.id) "…" else "AUTO-FIX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { replacementTarget = card; query = card.name; results = emptyList() }) {
                                        Text("FIND", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                TextButton(onClick = { onRemove(card) }) { Text("REMOVE", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    item { Text("ALL ZONES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
                    if (cards.isEmpty()) item { Text("No matching cards in this deck", color = Muted, fontSize = 10.sp) }
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
                            Text("${copies.size}×", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(buildList {
                                    val zones = listOf("hand" to player.hand, "library" to player.library, "battlefield" to player.battlefield, "graveyard" to player.graveyard, "exile" to player.exile, "revealed" to player.revealed)
                                    zones.forEach { (zone, zoneCards) -> copies.count { copy -> zoneCards.any { it.id == copy.id } }.takeIf { it > 0 }?.let { add("$it $zone") } }
                                }.joinToString(" · "), color = Muted, fontSize = 8.sp)
                                val sampleSupport = catalog?.support(name)
                                if (sampleSupport != null && sampleSupport != CardSupport.ARGENTUM) {
                                    Text(sampleSupport.label.uppercase(), color = if (sampleSupport == CardSupport.UNSUPPORTED) CounterGold else Muted, fontSize = 8.sp)
                                    if (sampleSupport == CardSupport.UNSUPPORTED && sample.importIssue == null) TextButton(onClick = { suggestSwaps(sample) }, modifier = Modifier.heightIn(min = 36.dp)) {
                                        Text("SUGGEST ARGENTUM SWAPS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                sample.importIssue?.let {
                                    Text("NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(
                                        onClick = { autoFix(sample, copies) },
                                        enabled = autoFixingCardId == null,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) { Text(if (autoFixingCardId == sample.id) "TRYING…" else "AUTO-FIX", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Box {
                                TextButton(onClick = { menuCard = sample }, modifier = Modifier.heightIn(min = 40.dp)) { Text("⋮", color = Mint, fontSize = 18.sp) }
                                DropdownMenu(expanded = menuCard?.id == sample.id, onDismissRequest = { menuCard = null }) {
                                    if (sample.importIssue != null) DropdownMenuItem(text = { Text("Search for correct card") }, onClick = { replacementTarget = sample; query = sample.name; results = emptyList(); suggestingFor = null; menuCard = null })
                                    if (catalog != null && sample.importIssue == null) DropdownMenuItem(text = { Text("Replace with an Argentum card") }, onClick = { replacementTarget = sample; query = ""; results = emptyList(); suggestingFor = null; argentumOnly = true; menuCard = null })
                                    DropdownMenuItem(text = { Text("Remove one copy") }, onClick = { onRemove(sample); menuCard = null })
                                    DropdownMenuItem(text = { Text("Rename card") }, onClick = { renameCard = sample; renameValue = sample.name; menuCard = null })
                                }
                            }
                        }
                    }
                }
                if (autoFixStatus.isNotBlank()) Text(autoFixStatus, color = if (autoFixStatus.startsWith("Fixed")) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    renameCard?.let { card -> AlertDialog(onDismissRequest = { renameCard = null }, title = { Text("Rename card") }, text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text("Card name") }) }, confirmButton = { TextButton(onClick = { if (renameValue.isNotBlank()) onRename(card, renameValue.trim()); renameCard = null }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = { renameCard = null }) { Text("CANCEL") } }) }
}

@Composable
internal fun PlayerTrackerDialog(player: TablePlayer, players: List<TablePlayer>, playerIndex: Int, onUpdate: (TablePlayer) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val common = listOf("Poison", "Energy", "Experience", "Rad", "Lore", "Ticket", "Shield", "Stun", "Finality", "The Ring tempts you", "Monarch", "Initiative", "City's Blessing", "Day/Night")
    val matches = common.filter { it.contains(search, true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${player.name} · TRACKERS", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close player trackers", tint = Muted) }
                }
                Text("Commander damage is tracked per opposing commander; 21 from one commander is lethal.", color = Muted, fontSize = 10.sp)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text("COMMANDER CASTS · ${player.commanderCastCount} · TAX +{${player.commanderCastCount * 2}}", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ActionPill("− CAST") { onUpdate(player.copy(commanderCastCount = (player.commanderCastCount - 1).coerceAtLeast(0))) }
                            ActionPill("+ CAST") { onUpdate(player.copy(commanderCastCount = player.commanderCastCount + 1)) }
                        }
                    }
                    item { Text("COMMANDER DAMAGE RECEIVED", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    items(players.indices.filter { it != playerIndex }.size) { row ->
                        val source = players[players.indices.filter { it != playerIndex }[row]]
                        val amount = player.commanderDamageReceived[source.id] ?: 0
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${source.name}${source.commanderName?.let { " · $it" }.orEmpty()}", color = SoftWhite, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived.toMutableMap().apply { if (amount <= 1) remove(source.id) else put(source.id, amount - 1) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text("−", color = SoftWhite) }
                            Text("$amount", color = if (amount >= 21) Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived + (source.id to amount + 1))) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text("+", color = Mint) }
                        }
                    }
                    item { Text("COUNTERS AND STATUS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search common trackers") }, singleLine = true) }
                    items(matches.size) { row ->
                        val name = matches[row]
                        val old = player.playerCounters[name] ?: 0
                        val toggle = name in listOf("Monarch", "Initiative", "City's Blessing", "Day/Night", "The Ring tempts you")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = SoftWhite, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { val next = if (toggle) 0 else (old - 1).coerceAtLeast(0); onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text(if (toggle) "OFF" else "−", color = Muted) }
                            Text(if (toggle) if (old > 0) "ON" else "OFF" else "$old", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { val next = if (toggle) if (old > 0) 0 else 1 else old + 1; onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text(if (toggle) "SET" else "+", color = Mint) }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = custom, onValueChange = { custom = it }, modifier = Modifier.weight(1f), label = { Text("Add custom tracker") }, singleLine = true)
                            TextButton(onClick = { val key = custom.trim(); if (key.isNotEmpty()) { onUpdate(player.copy(playerCounters = player.playerCounters + (key to ((player.playerCounters[key] ?: 0) + 1)))); custom = "" } }, enabled = custom.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("ADD", color = Mint) }
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
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight), shape = RoundedCornerShape(18.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CHOOSE TARGETS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(stackItem.title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        range.minimum == range.maximum -> "Select ${range.minimum} target${if (range.minimum == 1) "" else "s"}."
                        range.maximum >= 99 -> "Select any number of targets (${range.minimum} or more)."
                        else -> "Select ${range.minimum} to ${range.maximum} targets."
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
                Text("Check the card text and select only legal targets. The app does not fully parse card-specific restrictions.", color = CounterGold, fontSize = 10.sp, lineHeight = 14.sp)
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.43f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (candidates.isEmpty()) item(key = "no-candidates") {
                        Text("No visible game objects are available. Add a target by name below if the card text allows it.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
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
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    selectedTargets = if (checked && selectedTargets.size < targetLimit) selectedTargets + candidate else selectedTargets.filterNot { it.key == candidate.key }
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.label, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.category, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = customTargetText,
                    onValueChange = { customTargetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Other legal target") },
                    placeholder = { Text("For example: a counter on a permanent") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val label = customTargetText.trim()
                            if (label.isNotEmpty() && selectedTargets.size < targetLimit) {
                                selectedTargets = selectedTargets + StackTarget("manual:${java.util.UUID.randomUUID()}", label, "MANUAL TARGET")
                                customTargetText = ""
                            }
                        },
                        enabled = customTargetText.isNotBlank() && selectedTargets.size < targetLimit
                    ) { Text("ADD TARGET", color = Mint, fontSize = 10.sp) }
                    Text("${selectedTargets.size} selected", color = Muted, fontSize = 10.sp)
                }
                Button(
                    onClick = { onConfirm(selectedTargets) },
                    enabled = allowed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { Text("CONFIRM TARGETS", fontWeight = FontWeight.Black) }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxDialogHeight), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxWidth().wrapContentHeight()) {
            Column(Modifier.fillMaxWidth().wrapContentHeight().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STACK · PRIORITY", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close stack", tint = Muted) }
                }
                Text("Priority: ${players.getOrNull(priorityPlayer)?.name ?: "—"} · passed $passedCount/$eligibleCount", color = Mint, fontSize = 10.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxStackListHeight).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stack.isEmpty()) item {
                        Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("NOTHING ON THE STACK", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("CLOSE STACK", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
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
                                    Modifier.weight(1f).clickable(enabled = !cardIsFaceDown) { expandedActionId = if (expanded) null else item.id; onStackItemTap() },
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val visibleTitle = if (cardIsFaceDown) "Face-down spell" else item.title
                                    Text("${if (index == stack.lastIndex) "TOP · " else ""}$visibleTitle", color = if (index == stack.lastIndex) Mint else SoftWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("$owner · ${item.kind}", color = Muted, fontSize = 9.sp)
                                    if (item.targets.isNotEmpty()) Text("TARGETS · ${item.targets.joinToString { it.label }}", color = Mint, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    else if (item.targetCount != null && !item.targetsConfirmed) Text("WAITING FOR TARGETS", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    if (card != null && !cardIsFaceDown) {
                                        val facts = listOfNotNull(card.manaCost?.takeIf { it.isNotBlank() }, card.type.takeIf { it.isNotBlank() })
                                        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = CounterGold, fontSize = 9.sp, maxLines = 2)
                                    }
                                    Text(if (cardIsFaceDown) "FACE-DOWN CARD · DETAILS HIDDEN" else if (card != null) "CARD IMAGE: PREVIEW · TEXT: DETAILS" else if (expanded) "TAP TO COLLAPSE" else "TAP FOR DETAILS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (expanded && !cardIsFaceDown) {
                                val oracle = card?.oracleText?.takeIf { it.isNotBlank() }
                                if (oracle != null) {
                                    HorizontalDivider(color = Muted.copy(alpha = 0.25f))
                                    Text(oracle, color = SoftWhite, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                } else if (card == null) {
                                    Text("${item.kind} added by $owner", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                } else {
                                    Text("No rules text is available for this card.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onCastFromHand,
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) {
                    Text("CAST FROM HAND", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                TextButton(
                    onClick = { manualAbilityOpen = !manualAbilityOpen },
                    enabled = canAct,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 34.dp)
                ) {
                    Text(if (manualAbilityOpen) "HIDE MANUAL ENTRY" else "ADD MANUAL ABILITY", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (manualAbilityOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = triggerName,
                        onValueChange = { triggerName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Ability or effect") },
                        singleLine = true,
                        enabled = canAct
                    )
                    TextButton(
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
                    ) { Text("ADD", color = if (canAct && triggerName.isNotBlank()) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
                Button(
                    onClick = onPassPriority,
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { Text("PASS PRIORITY", fontSize = 14.sp, fontWeight = FontWeight.Black) }
                TextButton(
                    onClick = onResolve,
                    enabled = canResolve,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp)
                ) { Text("RESOLVE TOP", color = if (canResolve) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COMBAT · ${players.getOrNull(attackerIndex)?.name ?: "PLAYER"}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close combat", tint = Muted) }
                }
                Text("Select attackers, assign blockers, and enter damage. The app records declarations; resolve combat rules manually.", color = Muted, fontSize = 10.sp)
                if (attackers.isEmpty()) Text("No untapped creatures are available to attack.", color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(attackers.size, key = { attackers[it].id }) { index ->
                        val card = attackers[index]
                        var blockMenu by remember(card.id) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = card.id in selected, onCheckedChange = { checked ->
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
                                Text(card.name, color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (card.tapped) "TAPPED" else "READY", color = Muted, fontSize = 8.sp)
                            }
                            if (card.id in selected) {
                                val defaultDefender = players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated } ?: attackerIndex
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        TextButton(onClick = { blockMenu = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("TARGET: ${players.getOrNull(defenders[card.id] ?: defaultDefender)?.name ?: "PLAYER"} ▾", color = Mint, fontSize = 9.sp) }
                                        DropdownMenu(expanded = blockMenu, onDismissRequest = { blockMenu = false }) {
                                            players.forEachIndexed { targetIndex, target ->
                                            if (targetIndex != attackerIndex && !target.isEliminated) DropdownMenuItem(text = { Text(target.name) }, onClick = {
                                                defenders = defenders + (card.id to targetIndex)
                                                assignments = assignments + (card.id to aiBlocks(card, targetIndex, assignments))
                                                blockMenu = false
                                            })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    OutlinedTextField(value = damage[card.id].orEmpty(), onValueChange = { damage = damage + (card.id to it.filter(Char::isDigit)) }, modifier = Modifier.width(105.dp), singleLine = true, label = { Text("Damage") }, placeholder = { Text(card.power ?: "?") })
                                }
                                val defenderIndex = defenders[card.id] ?: defaultDefender
                                if (players.getOrNull(defenderIndex)?.isComputer == true) Text("AI BLOCK PICK · CHANGE ANYTIME", color = Mint, fontSize = 8.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                else Text("Blockers assigned", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                blockers.filter { it.first == (defenders[card.id] ?: defaultDefender) }.forEach { (owner, blocker) ->
                                    val reservedByOther = assignments.any { (attackerId, blockerIds) -> attackerId != card.id && blocker.id in blockerIds }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 7.dp)) {
                                        Checkbox(checked = blocker.id in assignments[card.id].orEmpty(), enabled = !reservedByOther, onCheckedChange = { checked ->
                                            val current = assignments[card.id].orEmpty()
                                            assignments = assignments + (card.id to if (checked) current + blocker.id else current - blocker.id)
                                        })
                                        Text("${players[owner].name} · ${blocker.name}${if (reservedByOther) " · ASSIGNED" else ""}", color = if (reservedByOther) Muted else SoftWhite, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Blockers available: ${blockers.size}  ·  tapped creatures excluded", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    TextButton(onClick = {
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
                    }) { Text("LOG COMBAT", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
internal fun CardZoneDialog(zone: String, player: String, cards: List<GameCard>, onDismiss: () -> Unit, onPreview: (GameCard) -> Unit, canMoveCards: Boolean = false, onMoveCard: (GameCard, String) -> Unit = { _, _ -> }, commanderId: Int? = null, canMoveCommander: Boolean = false, manualPlayAllowed: Boolean = false, argentumPlayableIds: Set<Int> = emptySet(), onPlayCard: (GameCard) -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$player · $zone (${cards.size})", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close", tint = Muted) }
                }
                if (cards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No cards here", color = Muted) }
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
                                                Icon(Icons.Filled.MoreVert, if (card.isFaceDown) "Card actions" else "Move ${card.name}", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        listOf("Hand", "Battlefield", "Library (top)", "Library (bottom)", "Graveyard", "Exile", "Revealed")
                                            .filterNot { it == zone }
                                            .forEach { destination ->
                                                DropdownMenuItem(
                                                    text = { Text("Move to $destination") },
                                                    onClick = { menuOpen = false; onMoveCard(card, destination) }
                                                )
                                            }
                                        if (canMoveCommander && card.id == commanderId) DropdownMenuItem(
                                            text = { Text("Move to Command Zone") },
                                            onClick = { menuOpen = false; onMoveCard(card, "Command Zone") }
                                        )
                                    }
                                }
                            }
                            if (canPlay) TextButton(
                                onClick = { onPlayCard(card) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text(if (hasEngineAction) "LEGAL ACTIONS" else "PLAY / CAST · MANUAL", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Add a counter · ${card.name}", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Search counter types") })
                if (query.isNotBlank() && matches.none { it.equals(query.trim(), true) }) {
                    TextButton(onClick = { onAdd(query.trim()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("ADD CUSTOM “${query.trim()}” COUNTER", color = CounterGold) }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(matches) { counter ->
                        TextButton(onClick = { onAdd(counter) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(counter, color = SoftWhite, modifier = Modifier.weight(1f)); Text("+", color = CounterGold, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { Text("CANCEL") }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add a token", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Search Scryfall’s token cards", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close token search", tint = Muted) }
                }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, label = { Text("Search tokens") }, placeholder = { Text("Treasure, Clue, Zombie…") })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Treasure", "Clue", "Food", "Blood", "Zombie", "Goblin", "Soldier").forEach { suggestion ->
                        AssistChip(onClick = { query = suggestion }, label = { Text(suggestion, fontSize = 9.sp) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                error?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(6.dp)) }
                if (query.trim().length >= 2 && results.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                    TextButton(onClick = { onAdd(ScryfallCard(query.trim(), "Custom token", null)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text("ADD CUSTOM TOKEN: ${query.trim()}", color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { token ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(PanelLight).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardFace(GameCard(-token.name.hashCode(), token.name, token.type, Color(0xFF6D836D), imageUrl = token.imageUrl, isToken = true), Modifier.width(46.dp), onClick = {}, onLongClick = {}, showCounters = false)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(token.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(token.type, color = Muted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onAdd(token) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("ADD", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedDecksDialog(decks: List<SavedDeck>, onLoad: (SavedDeck) -> Unit, onDelete: (SavedDeck) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SAVED DECKS · ${decks.size}", color = SoftWhite, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close saved decks", tint = Muted) }
                }
                if (decks.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No saved decks yet. Choose ‘Save this imported deck for later’ when importing.", color = Muted, fontSize = 12.sp) }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(decks.size) { index ->
                            val deck = decks[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                    Text(deck.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${parseDeckList(deck.text).sumOf { it.count }} cards", color = Muted, fontSize = 9.sp)
                                }
                                TextButton(onClick = { onLoad(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) Modifier else Modifier)) { Text("LOAD", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onDelete(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) Modifier else Modifier)) { Text("DELETE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("CLOSE", color = Muted) }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTIVITY LOG · ${entries.size}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close game log", tint = Muted) }
                }
                Text(if (ownership != null) "Whole game · newest first · your actions marked YOU" else "Actions · mana payments · triggers · effects",
                    color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 7.dp))
                if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No actions logged yet", color = Muted) }
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(rows.size) { displayIndex ->
                        val row = rows[rows.lastIndex - displayIndex]
                        val entry = row.description
                        if (row.isTurnMarker) {
                            Text(entry.trim('-', ' ').uppercase(), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
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
                                    .clickable(enabled = row.count > 1) {
                                        expandedCounts = if (row.firstIndex in expandedCounts) expandedCounts - row.firstIndex
                                            else expandedCounts + (row.firstIndex to minOf(25, row.count))
                                    }
                                    .padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (yours) Text("YOU", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Mint).padding(horizontal = 4.dp, vertical = 2.dp))
                                ManaSymbolText(entry, color = entryColor, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(3.dp))
                                if (row.count > 1) Text("×${row.count} ${if (row.firstIndex in expandedCounts) "▲" else "▼"}",
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
                                    Text("#${original + 1} · ${entries[original]}", color = Muted, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp, bottom = 2.dp))
                                }
                                if (displayed < row.count) TextButton(
                                    onClick = { expandedCounts = expandedCounts + (row.firstIndex to minOf(row.count, displayed + 25)) }
                                ) { Text("SHOW NEXT ${minOf(25, row.count - displayed)} · ${row.count - displayed} REMAIN", color = Mint, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                if (ownership == null) Text("Unsupported or ambiguous effects are marked for manual resolution.",
                    color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** "What's new": the build history, newest first. Kept out of TableScreenContent so that method stays under the JVM size limit. */
@Composable
internal fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("What's new · Build ${BuildConfig.VERSION_CODE}") },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.62f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("BUILD 123 · LARGE END-STEP AI FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The offline AI skips pointless redirect searches on targetless spells and abilities.\\n• AI simulations share a deadline, including nested choices, and long resolution sequences show progress updates.\\n• Retains the Build 122 fixes and Quick UI Tips, based on Build 120.", color = SoftWhite, fontSize = 13.sp)
                Text("BUILD 122 · FIXES AND QUICK UI TIPS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Smaug copies keep their death ability and create 14 Treasures when they die.\n• Valkyrie’s Call returns Cid as an Angel with flying and a +1/+1 counter. A returned Angel’s next death ends the legend-rule sequence.\n• The offline AI spends less time searching on large boards, with bounded thinking budgets and more useful timing diagnostics.\n• New Quick UI Tips popup, grouped by interaction, with Dismiss and Don’t remind me again.\n• Temporarily removed the custom-card/opponent notice after choosing your opening hand.\n• Based on Build 120, preserving the seven WotC precons. Build 122 is now shown consistently in the app and installer.", color = SoftWhite, fontSize = 13.sp)
                Text("BUILDS 105–106 · NEW CARDS AND DECKS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• 62 more custom cards (92 in all). New premade decks for you or the AI: Ayula Bears, Sliver Overlord, Hazel Squirrels.\n• Waiting for your friend shows in the bar; a secret nudge; prompts name the card asking; custom cards load their art.", color = SoftWhite, fontSize = 13.sp)
                Text("BUILD 104 · ONLINE WITH A FRIEND", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• PLAY → Play with a friend → Host online. Your phone runs the game and shows a room code; your friend picks Join online and types it. Works across any networks.", color = SoftWhite, fontSize = 13.sp)
                Text("BUILDS 100–102 · WI-FI AND SPEED", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Wi-Fi with a friend: PLAY → Wi-Fi with a friend. One phone hosts, the other joins (Commander decks).\n• Offline games stay fast on big boards; the bar says ENGINE THINKING… while the engine works.\n• Counters show as small badges (+1/+1 ×8); only creatures show power/toughness.\n• One Notice time setting in Adjust UI for banners, pop-ups and the recap. Triggered abilities go in the game log. Your tab comes right after TABLE.", color = SoftWhite, fontSize = 13.sp)
                Text("BUILD 99 · ADJUST UI", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• New Adjust UI screen: choose your Quick Actions, and switch the card pop-up, recent plays, banners, recap, turn banner, buzz and card stacking on or off. Pop-up time and combat card size sliders.\n• Options regrouped. New: skip AI actions unless they affect you.\n• Recent-plays strip folds away. Fetched shock lands ask to pay life (offline). X never offers more than you can pay.", color = SoftWhite, fontSize = 13.sp)

                Text("BUILD 98 · CLEAN-UP", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The old guided tutorial is gone (a new one is coming).\n• Treasures now really pay for spells offline (they were asking for a colour behind the scenes and giving up).\n• Costs like \"Return an Elf you control\" now let you pick the card.\n• Behind the scenes: the app's code is split into smaller files, ready for 4-player games.", color = SoftWhite, fontSize = 13.sp)

                Text("BUILD 97 · NEW HOME, TREASURES, FASTER COMBAT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• New home screen: your deck up top, one PLAY button. Pick offline vs AI (and the AI's deck) or online.\n• Your decks are saved and the one you pick stays picked, even after a game ends.\n• Treasures pay for spells offline, and show \"Sacrifice for mana\" in their menu. USE 5 / USE ALL for stacks of Treasures, Clues and Foods.\n• Attack with ALL, and tap a stack (×N) to attack with every copy.\n• See what the AI plays: a big card pop-up, a side strip of recent plays, ability banners, and a recap when your turn starts.\n• X picker with −5 / +5 and type-in. \"Always yes this turn\" for repeated prompts.\n• Fewer pointless pass stops; no more passing over an open prompt.", color = SoftWhite, fontSize = 13.sp)

                Text("BUILD 96 · CUSTOM CARDS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Arcana's own custom cards: 30 cards Argentum doesn't have yet, so Tokens Win (Rocco) and Artificer (Tails) play offline. Only Goblin Welder and Ratchet are still swapped for basic lands. Argentum's own version of a card always wins once it has one.\n• Offline games now show the AI's turn step by step, and your combat steps too, instead of jumping to your next move.\n• Offline mulligans (London rules) and undo.\n• Whose turn it is: you're gold, the AI is blue. The phase bar, the seat tabs (YOUR TURN / AI'S TURN) and the active board's outline use that colour, a banner shows when a turn starts, and your turn gives a small buzz.\n• The pass button lights up gold when you can respond on the AI's turn, and works whichever board you're looking at.\n• Battlefield: pinch with two fingers to zoom (remembered for each screen shape), rows wrap instead of running off the side, and identical cards stack with a count.\n• The bottom buttons are lined up, with a slightly larger hand drawer, and the little card boxes in it are gone.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 95 · PLAY OFFLINE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• New PLAY OFFLINE VS AI button on the home screen: a full game against Argentum's AI with no internet and no server.\n• All 15,942 Argentum cards are on the phone, so your imported Commander deck plays offline against one of Argentum's premade Commander decks. Cards Argentum doesn't have are left out and replaced with basic lands (your saved deck isn't changed).\n• Not yet offline: mulligans (you keep your opening hand) and undo.", color = SoftWhite, fontSize = 13.sp)

                Text("BUILD 94 · ENGINE TRIAL", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Argentum's rules engine and AI now run inside the app, with no server, as a trial. Options → Help & diagnostics → Run engine self-test plays one AI-vs-AI game with the Portal cards and reports how long it took and how much memory it used.\n• Cards ship as Argentum's own card data instead of compiled code.\n• Everything else is the same as Build 93.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 93 · MORE ROOM FOR CARDS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• A slimmer table: phases sit in one strip at the top, each player's life is on their tab, and the commander and mana are small chips. Landscape phones now show the battlefield.\n• The status box only appears for problems, a waiting confirmation or something on the stack.\n• On an unfolded Fold, an opponent's board sits beside yours.\n• Your hand opens from every table screen, including the Table tab.\n• New combat board: attackers on top, your blockers below. Tap a blocker, then the attacker; lines link them and a summary reads like \"Ramosian Captain 2/2 first strike ← 3 Rabbit Tokens (1/1)\". Attacking and combat damage use the same board.\n• Hold any card to see it large. Let go to keep it up; slide off to close.\n• Fixes: \"TYPE · null\" badges and \"null\" mana costs.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 92 · QUICKER TURNS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The game log shows the whole game from Argentum, newest first, with turn headers and your actions marked YOU.\n• The pass button says where passing takes you, like PASS → TO COMBAT.\n• PASS TO MY TURN keeps passing for you and stops when you're attacked, an opponent casts something, or Argentum needs a choice.\n• UNDO appears when Argentum lets you take back your last action.\n• Quick Actions only show buttons that work in Argentum games: Graveyard, Exile, Stack, Game log, Cast commander, Pass to my turn.\n• The table follows whose turn it is (turn this off in Options).\n• Options: Developer Options are gone; a new Help & diagnostics section has the connection test, diagnostic log, What's new and the guided tour.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 89 · POLISHED ARGENTUM TABLE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Game HUD: phase tracker, life totals that flash on change, who has priority, the live stack, and the latest moves from Argentum's game log.\n• Cards Argentum lets you play glow; tapping a card opens a sheet with its art, rules text and every action Argentum allows right now.\n• Battlefield badges for attacking, blocking, power/toughness with damage, and summoning sickness.\n• Every Argentum choice now has a control: multi-target spells, divided damage, X, mana colour, piles, replacement choices and mode budgets.\n• Full-screen opening hand, a Victory / Defeat screen with Play again, and a buzz (or background alert) when it's your move.\n• Fixes: graveyard targets, stale actions after a new state, and an old room dialog appearing at Argentum game over.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 88 · COMBAT DAMAGE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Combat damage now opens a damage board instead of stopping at Needs handling. It starts from Argentum's own assignment, so Confirm damage applies it in one tap; adjust amounts per blocker and trample target with lethal hints, and Arcana only enables Confirm for splits Argentum accepts.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 87 · ARGENTUM INTEGRATION", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Attack and block declarations come from Argentum; the main button names what it will do (Pass priority, Declare attackers, Declare blockers).\n• More Argentum choices open in Arcana dialogs: modes, library search, ordering, mana auto-pay, damage assignment and distribution. Anything else shows a Needs handling notice with Cancel, Refresh or View table instead of blocking the app.\n• Unsupported deck cards open a review: play without them for this game, play the full deck in the Arcana sandbox, or edit the deck. Your saved deck is never changed.\n• No automatic resume: after a relaunch, choose Resume game or End that game. A connection lost in the background waits for Resume.\n• Engine status strip, searchable diagnostic log with redacted export, and a hand drawer that rearranges cards as you resize it (Adjust UI → Hand).", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 86 · ARGENTUM PREMADE DECKS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Removed random external deck fetching. The importer now offers Argentum's own premade Commander lists and only shows lists that pass the live Argentum Commander validator.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 84 · ARGENTUM COMPATIBLE-DECK FILTERING", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Practice, create-room, and join-room actions now use Argentum Commander lobbies. The host can copy the server room code, joining validates the imported deck with Argentum first, and the Argentum stack panel displays server state and submits server-provided Pass Priority actions.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 82 · ARGENTUM NATIVE TABLE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Argentum practice games now run through Arcana’s existing table and player screens. The server validates the imported Commander deck, unsupported cards are removed and replaced only with Argentum-supported basic lands, and Argentum supplies the masked game state and legal actions. The native client keeps its connection active while switching apps and reconnects after a drop. Common opening-hand, target, color, option, number, and card-selection decisions are shown in Arcana dialogs.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 81 · QUICK ACTIONS AND TURN COUNTER", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The player-screen zone strip is now a configurable Quick Actions bar. Add, remove, and reorder zone shortcuts and actions in Adjust UI; empty zones stay visible with a count of 0. The Mana Pool element now appears on the live player board when enabled.\n• Mana pips are larger across card costs and text, and card three-dot menus have smaller visible buttons with an accessible tap area.\n• The TURN counter advances after every eligible player has completed a turn. It remains labeled TURN.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 80 · PLAYER BATTLEFIELD HEIGHT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Removed the extra weighted spacer above the player footer. The player board now receives the full available height, so the battlefield fills the space above the footer instead of leaving a large blank gap.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 79 · TABLE CARDS AND LIBRARY PRIVACY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Deck imports automatically try close Scryfall name matches before opening review; the review menu appears only for cards or deck issues that remain unresolved, and successful corrections are reported.\n• The table overview enlarges cards to use each player panel's available width. Player battlefield windows use the full space above the footer and open at the top when switching boards.\n• General library browsing displays a randomized card list without library positions. The Adjust UI editor now selects the matching list item when you tap or drag a preview element.\n• Mana pool is a separate removable and reorderable player-screen element. Tapping the health/activity bar opens Player Status with life, commander damage, counters, and turn details; the life buttons remain quick controls.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 78 · DRAGGABLE UI EDITOR AND PLAYER LAYOUT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Long-press and drag player-screen elements or hand-tray buttons in the editor's sample layout. The active item lifts and tracks your finger while neighboring items animate into place.\n• Player-screen footer controls stay aligned to the bottom, with a larger hand button. The battlefield automatically fills available space and scrolls when its cards exceed that space.\n• Removed the Large View toggle from the table view.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 77 · DRAG-AND-DROP UI EDITOR", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Adjust UI now lets you drag to reorder player-screen sections, remove and restore sections such as the health/activity bar and commander strip, and add, remove, and reorder hand-tray buttons. Layouts are saved separately for portrait and landscape.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 76 · PLAYER LAYOUT AND HAND ACTIONS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Player-screen footer buttons now align on the bottom edge. Opening-hand cards are larger, and the hand tray includes Cast Commander when it can be cast. Pass Priority is the primary stack action; Resolve Top remains available as a smaller action after everyone passes.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 75 · MANA PIPS AND UI ADJUSTMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Mana-choice buttons show colored mana pips. Open the app menu and tap Adjust UI to go straight to the layout controls.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 74 · HAND TRAY AND PLAYER CONTROLS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Opening-hand cards are larger, and hand-tray cards grow or shrink with the drawer. The tray now holds Draw, Combat, Stack, turn flow, and secondary game commands; the Stack shortcut is off the view selector. The player footer stays at the bottom, and the old drag-to-play hint is gone.\n• Mana symbols use colored pips. Permanents that say to choose a creature type prompt for one as they enter and display the choice. Public card mentions in the game log can open a card preview.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 73 · TARGETING EXPERIMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Targeted spells, Auras, activated abilities, and recognized targeted triggers pause for target selection. The picker lists visible players, permanents, stack items, and cards in visible zones; add a legal target by name if it is not listed. Chosen targets appear on the stack and in the game log, and sync to the room. The picker estimates target count, but players still verify card-specific restrictions. This experimental app installs separately from the stable release.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 72 · PLAYER TABS AND ACTIVE BOARD", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Player view tabs use cropped commander artwork with the player name at the bottom. On the full table, the active player's board opens automatically and the view scrolls to it; the previous active board closes when the turn changes. The player footer has PASS TURN, a larger centered HAND button, and BACK TO TABLE. Mana access is now in the player status header. ADD TOKEN is in MORE; the footer no longer shows the library count. The command-zone strip is more compact while preserving commander casting, cast count, tax, and status.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 71 · CHAT COPY AND PASTE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Chat messages support text selection by long-pressing, with Android's copy action. Added a PASTE control to append clipboard text to the draft; the message field also keeps Android's normal text paste menu.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 70 · DUPLICATE PERMANENT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Resolving an activated or loyalty ability now leaves its source permanent in its current zone. Only resolving a spell moves that spell card to the battlefield or graveyard.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 69 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Added trigger detection for recognized spell-cast, draw, and creature-entry events. Triggers show their event and effect, offer target-player selection for supported player-target effects, and keep modes or unsupported effects on a manual-resolution path. Rhystic Study and Smothering Tithe keep their pay-or-resolve choices.\n• Expanded simple automatic effect handling for targeted player life, draw, and token effects after a target is chosen.\n• The Activity Log now includes turn phase, spell cost/payment detail, trigger events, automatic changes, and manual-resolution notes, with visual emphasis for payments, triggers, and effects.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 68 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The opening-hand screen now shows card faces. Tap any card to open a large preview; while choosing cards to bottom, use the separate button beneath each card to select it. The selection count and required bottom count remain visible.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 67 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Added a multiplayer opening-hand step before Ready Up. Each player can keep or mulligan; every mulligan shuffles the whole hand into the library and deals a fresh seven. The first multiplayer mulligan is free.\n• After keeping, players tap the exact number of cards to put on the bottom of their library.\n• Ready Up now requires a confirmed opening hand. LAN and Internet room state carries mulligan status, and room servers reject ready requests until both the deck and hand decision are set.\n• Starting the lobby game preserves each player's chosen hand and library order instead of dealing a new hand.\n\nThis is a source-only update; the Build 66 APK does not include these changes.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 66 · CHANGES SINCE BUILD 65", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The hand panel can now be resized by dragging its top handle and remembers its height.\n• The stack window closes automatically when the stack is empty and pending triggers or payment choices have finished; reopen it any time from the Stack button.\n• Player views use tighter spacing, compact zone chips, and a battlefield area sized to its cards that scrolls when crowded.\n• Diagnostic logs now capture repeated deck quantities, visible card moves, zone totals, repeated card-instance IDs, and inbound snapshot sequence numbers to help trace import and sync problems.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 65", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Added an import check that flags unmatched or unverifiable cards. Auto Fix closes the review when every card is matched; unresolved cards can be opened in the editor for manual correction.\n• Added automatic handling for supported fixed life, draw, and token effects, plus simple creature-entry life triggers. Effects the app cannot safely parse prompt the player to resolve them manually.\n• Added a persistent Stack button beside the view tabs and another in the hand tray; the tab button highlights while the stack has items.", color = SoftWhite, fontSize = 11.sp)

                Text("EARLIER CHANGES · BUILDS 60–64", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("BUILD 60", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Rebuilt the optional visual guide with screen highlights and straight red arrows.\n• Added Share Room, the default room URL reminder, tapped-entry choices, and shock-land life prompts.\n• Added an empty-stack notice and simplified menu labels.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 61", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Enlarged the tutorial panels and controls. The room URL step now explains that the default address is already set and can continue without opening the keyboard.\n• Improved card-action dialog layout so titles and actions fit.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 62", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Added Next to every tutorial step, plus an import review with Auto Fix and Trim Excess.\n• Added per-card moves from Graveyard and Exile, and hid Set as Commander after one is assigned.\n• Concealed face-down card details. Made Cast from Hand the primary stack action and tucked manual ability entry behind a small control.\n• Replaced clunky view swipes with synchronized table/player paging and aligned the hand-sheet drag handle.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 63", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Improved stack-window sizing.\n• Updated the deck editor to track cards in every zone and keep rename/remove actions available after cards move.\n• Improved AI readiness and recovery from unexpected turn errors.\n• Expanded mana-ability tracking for common mana rocks, dual-color choices, and basic lands.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 64", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Fixed the Table overview layout so player panels remain visible.", color = SoftWhite, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("CLOSE", color = Mint) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )
}
