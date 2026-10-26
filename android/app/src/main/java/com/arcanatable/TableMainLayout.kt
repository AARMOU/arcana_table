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

/** The table itself: top strip, player tabs, boards and the footer controls. */
@Composable
internal fun TableController.TableMainLayout() {
    Column(
        Modifier.fillMaxSize().background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 10.dp, top = 8.dp, end = 10.dp)
    ) {
        // Argentum game: one strip for connection, phase and timer (life is on the player tabs).
        val compactArgentumTable = argentumEnabled && argentumSnapshot != null
        // The colour of whoever's turn it is: gold for you, blue for an opponent.
        val turnAccent = argentumSnapshot?.let { TurnColors.forSeat(it.activePlayer == localSeat) } ?: Mint
        // Player tabs (seat tabs with life in Argentum games). In landscape they share the top strip.
        val seatTabs: @Composable (Modifier) -> Unit = { rowModifier ->
            Row(rowModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier) { ViewChip("TABLE", selected = showOverview, compact = compactArgentumTable, onClick = { run { showOverview = true; showHand = false } }) }
                // Your own tab always comes right after TABLE, then everyone else in seat order.
                val tabOrder = players.indices.sortedBy { if (it == localSeat) -1 else it }
                tabOrder.forEach { index -> val player = players[index]
                    Box(Modifier.testTag(if (onlineMode && index == localSeat) "player-tab-you" else "player-tab-$index").tutorialTarget(if (index == localSeat) "your-tab" else "opponent-tab")) {
                        val snap = argentumSnapshot
                        if (compactArgentumTable && snap != null) ArgentumSeatTab(
                            seat = ArgentumSeatChip(player.name, player.life, isYou = index == localSeat, isActive = index == snap.activePlayer, hasPriority = index == snap.priorityPlayer),
                            turnLabel = if (index == snap.activePlayer) TurnColors.label(index == localSeat, player.name, player.isComputer) else null,
                            artUrl = player.commanderCard?.let { it.artCropUrl ?: it.imageUrl }, artLabel = player.commanderCard?.name,
                            selected = !showOverview && selectedPlayer == index,
                            onClick = { run { selectedPlayer = index; showOverview = false; TutorialActions.tap(if (index == localSeat) "your-tab" else "opponent-tab") } }
                        ) else PlayerViewTab(
                            name = if (onlineMode && index == localSeat) "YOU" else player.name,
                            commander = player.commanderCard,
                            selected = !showOverview && selectedPlayer == index,
                            onClick = { run { selectedPlayer = index; showOverview = false; showHand = false; TutorialActions.tap(if (index == localSeat) "your-tab" else "opponent-tab") } }
                        )
                    }
                }
            }
        }
        val tabsInTopBar = compactArgentumTable && isLandscape
        val sideBySideBoards = compactArgentumTable && !isLandscape && screenWidthDp >= 600 && configuration.screenHeightDp >= 600 && players.size >= 2 && localSeat in players.indices
        Row(Modifier.fillMaxWidth().heightIn(min = if (compactArgentumTable) 44.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compactArgentumTable) {
                val dotColor = when { argentumStatus != ArgentumConnectionStatus.CONNECTED -> Color(0xFFE9978B); argentumAwaitingUpdate -> CounterGold; else -> Mint }
                Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor).semantics { contentDescription = "Argentum ${argentumStatus.name.lowercase()}" })
                Spacer(Modifier.width(8.dp))
                if (tabsInTopBar) {
                    PhaseTracker(argentumSnapshot?.stepKey.orEmpty(), Modifier.width(320.dp).tutorialTarget("phase-tracker"), accent = turnAccent)
                    Spacer(Modifier.width(8.dp))
                    seatTabs(Modifier.weight(1f))
                } else PhaseTracker(argentumSnapshot?.stepKey.orEmpty(), Modifier.weight(1f).tutorialTarget("phase-tracker"), accent = turnAccent)
                Spacer(Modifier.width(6.dp))
            } else Column {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                if (!argentumEnabled) {
                    Text("VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                    val inProgress = if (onlineMode) gameStartedOnline && !gameEndedOnline else localGameStarted
                    Text(if (inProgress) "● GAME IN PROGRESS" else "● GAME NOT STARTED", color = if (inProgress) Mint else CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                } else Text("ARGENTUM ENGINE", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            if (!compactArgentumTable) Spacer(Modifier.weight(1f))
            Box(Modifier) {
                Text(if (compactArgentumTable) "T${turnNumber}${if (turnTimerVisible) " · " + formatClock(elapsedTurnSeconds) else ""}" else "GAME ${formatClock(elapsedGameSeconds)} · TURN ${if (turnTimerVisible) formatClock(elapsedTurnSeconds) else "HIDDEN"}", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).tutorialTarget("turn-timer").wrapContentHeight(Alignment.CenterVertically).combinedClickable(enabled = !onlineMode || localSeat == 0, onClick = { run { timerMenuOpen = true } }, onLongClick = { turnTimerVisible = !turnTimerVisible }))
                DropdownMenu(expanded = timerMenuOpen, onDismissRequest = { timerMenuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (gameTimerRunning) "Stop session timer" else "Start session timer") }, onClick = { gameTimerRunning = !gameTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset session timer") }, onClick = { elapsedGameSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerRunning) "Stop turn timer" else "Start turn timer") }, onClick = { turnTimerRunning = !turnTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset turn timer") }, onClick = { elapsedTurnSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerVisible) "Hide turn timer display" else "Show turn timer display") }, onClick = { turnTimerVisible = !turnTimerVisible; timerMenuOpen = false })
                }
            }
            // Argentum does not relay Arcana chat, so the chat entry point is hidden during Argentum games.
            if (!argentumEnabled) Box(Modifier) {
                IconButton(onClick = { run { chatOpen = true; unreadChatCount = 0 } }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Chat, "Open chat${if (unreadChatCount > 0) ", $unreadChatCount unread" else ""}", tint = if (unreadChatCount > 0) CounterGold else SoftWhite)
                }
                if (unreadChatCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(9.dp).clip(CircleShape).background(Color(0xFFE35656)))
            }
            Box(Modifier) {
                IconButton(onClick = { run { topMenuSection = null; topMenuExpanded = true; TutorialActions.tap("game-menu") } }, modifier = Modifier.size(46.dp).tutorialTarget("game-menu")) { Icon(Icons.Filled.MoreVert, "App menu", tint = SoftWhite) }
                DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false; topMenuSection = null }) {
                    tutorialStep?.takeIf { it.screen == "menu" }?.let { step ->
                        DropdownMenuItem(
                            text = { Column { Text("TUTORIAL · " + step.title, color = Mint, fontWeight = FontWeight.Bold); Text(step.body, fontSize = 11.sp) } },
                            onClick = { if (step.actionKind == "next") tutorialAdvance() },
                            enabled = step.actionKind == "next"
                        )
                    }
                    if (topMenuSection == null) {
                        DropdownMenuItem(text = { Text("Learn & tutorials") }, onClick = { topMenuExpanded = false; topMenuSection = null; tutorialHubOpen = true })
                                                DropdownMenuItem(text = { Text("Game tools") }, modifier = Modifier, onClick = { run { topMenuSection = "tools"; TutorialActions.tap("menu-game-tools") } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Room & game") }, modifier = Modifier, onClick = { run { topMenuSection = "game" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("What's new") }, onClick = { topMenuExpanded = false; changelogOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Adjust UI") }, onClick = { topMenuExpanded = false; topMenuSection = null; layoutLabOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Options") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; optionsOpen = true } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                    } else {
                        DropdownMenuItem(text = { Text("‹  MAIN MENU") }, modifier = Modifier, onClick = { run { topMenuSection = null } })
                        when (topMenuSection) {
                            "decks" -> {
                                if (!argentumEnabled) DropdownMenuItem(text = { Text(if (players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) "Replace imported deck" else "Import deck") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } })
                                if (!argentumEnabled && players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) DropdownMenuItem(text = { Text("Edit imported deck") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; deckEditorOpen = true } })
                                DropdownMenuItem(text = { Text("Saved decks · ${savedDecks.size}") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; savedDecksOpen = true } })
                            }
                            "tools" -> {
                                if (!onlineMode) DropdownMenuItem(text = { Text("Add AI opponent · ${players.size}/4") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; addAiOpponent() } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Add token") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; tokenTarget = selectedPlayer; tokenPickerOpen = true } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Mana pool · ${trackedMana(selectedPlayer)}") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; manaTarget = selectedPlayer; manaPoolOpen = true } })
                                if (!argentumEnabled && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Search / browse library") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; librarySearchPlayer = selectedPlayer } })
                                if (argentumEnabled && gameInProgress) DropdownMenuItem(text = { Text("Argentum legal actions · ${ArgentumActions.visibleChoices(argentumSnapshot?.legalActions.orEmpty()).size}") }, onClick = { topMenuExpanded = false; topMenuSection = null; run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } })
                                if (gameInProgress) DropdownMenuItem(text = { Text("Stack and priority · $visibleStackCount") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; stackOpen = true } })
                                DropdownMenuItem(text = { Text("Game log") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; logOpen = true } })
                                DropdownMenuItem(text = { Text("MTG rules") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; rulesOpen = true } })
                            }
                            "game" -> {
                                if (argentumOffline && wifiRole == null && argentumSnapshot != null && !gameEndedOnline)
                                    DropdownMenuItem(text = { Text("Save full offline game") }, onClick = {
                                        topMenuExpanded = false
                                        topMenuSection = null
                                        saveOfflineGame()
                                    })
                                DropdownMenuItem(text = { Text("Room home") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; homeOpen = true; showOverview = true; showHand = false } })
                                // Argentum server play parked for now (Build 113); plumbing kept.
                                if (!onlineMode && !localGameStarted) DropdownMenuItem(text = { Text("Start Argentum game", color = Muted.copy(alpha = .6f)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; android.widget.Toast.makeText(context, "Not yet available", android.widget.Toast.LENGTH_SHORT).show() } })
                                // Argentum server play parked for now (Build 113); plumbing kept.
                                if (!argentumEnabled) DropdownMenuItem(text = { Text("Argentum room", color = Muted.copy(alpha = .6f)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; android.widget.Toast.makeText(context, "Not yet available", android.widget.Toast.LENGTH_SHORT).show() } })
                                if (onlineMode && gameStartedOnline && !gameEndedOnline) DropdownMenuItem(text = { Text("Propose ending the game") }, modifier = Modifier, onClick = { run {
                                    topMenuExpanded = false; topMenuSection = null
                                    if (endProposalBy < 0) { endProposalBy = localSeat; endVotes = mapOf(localSeat to true); logAction(localSeat, "proposed ending the game") }
                                } })
                                if (onlineMode) DropdownMenuItem(text = { Text("Leave game…") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (argentumEnabled) argentumLeaveConfirm = true else lobbyOpen = true } })
                                if (!onlineMode || localSeat == 0 && gameEndedOnline) DropdownMenuItem(text = { Text("Reset game", color = Color(0xFFE9978B)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; resetConfirmOpen = true } })
                                if (!onlineMode) DropdownMenuItem(text = { Text("Reset and clear all data", color = Color(0xFFE9978B)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; clearAllConfirmOpen = true } })
                            }
                        }
                    }
                }
            }
        }

        // Compact table: the strip appears only for problems, a waiting confirmation, game over or a non-empty stack.
        val stripNeeded = argentumStatus != ArgentumConnectionStatus.CONNECTED || argentumNeedsHandling != null || argentumAwaitingUpdate || gameEndedOnline ||
            argentumSnapshot == null || argentumSnapshot?.stack?.isNotEmpty() == true
        if (argentumEnabled && stripNeeded) ArgentumStatusStrip(
            model = ArgentumStatusModel(
                connection = argentumStatus,
                gameActive = argentumSnapshot != null && !gameEndedOnline,
                gameOver = gameEndedOnline,
                awaitingUpdate = argentumAwaitingUpdate,
                turnLabel = argentumSnapshot?.let { "Turn ${it.turnNumber} · ${it.stepName.lowercase().replaceFirstChar(Char::uppercase)} · ${if (it.activePlayer == localSeat) "your turn" else "${players.getOrNull(it.activePlayer)?.name ?: "opponent"}'s turn"}" },
                priorityLabel = argentumPrimaryLabel,
                needsHandling = argentumNeedsHandling,
                stepKey = argentumSnapshot?.stepKey.orEmpty(),
                seats = argentumSnapshot?.let { snap -> snap.players.mapIndexed { i, p ->
                    ArgentumSeatChip(p.name, p.life, isYou = i == snap.viewingPlayer, isActive = i == snap.activePlayer, hasPriority = i == snap.priorityPlayer)
                } }.orEmpty(),
                stack = argentumSnapshot?.stack.orEmpty(),
                feed = argentumSnapshot?.recentLog.orEmpty(),
            ),
            onOpenLog = { logOpen = true },
            onResume = { recordDiagnostic("Player chose Resume", DiagnosticCategory.CONNECTION); argentumSocket.retryNow() },
            onReconnect = { recordDiagnostic("Player chose Reconnect", DiagnosticCategory.CONNECTION); argentumSocket.retryNow() },
            onPrimary = { argentumPrimary() },
            onNeedsHandling = { argentumDeferredDecisionId = null },
            onLeave = { argentumLeaveConfirm = true },
            compact = true,
        )
        if (!tabsInTopBar) Row(Modifier.fillMaxWidth().padding(vertical = if (compactArgentumTable) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
            seatTabs(Modifier.weight(1f))
        }

        HorizontalPager(
            state = viewPagerState,
            modifier = Modifier.weight(1f),
            // Player boards contain horizontal card and zone lists. Keep
            // gestures inside those lists; use the player tabs to switch boards.
            userScrollEnabled = showOverview,
            verticalAlignment = Alignment.Top,
            key = { page -> if (page == 0) "table" else players.getOrNull(page - 1)?.id ?: page }
        ) { page ->
        // Pager pages need their own Column scope: the overview grid and player
        // board use weight to fill the page beneath the player tabs.
        Column(Modifier.fillMaxSize()) {
        val showingView = page - 1
        if (showingView < 0) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FULL TABLE · ${players.size}/4 PLAYERS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            }
            val compactOverview = screenWidthDp < 900 && isLandscape && players.size >= 3
            val overviewColumns = when {
                screenWidthDp >= 1800 -> 4
                screenWidthDp >= 1300 -> minOf(3, players.size)
                screenWidthDp >= 700 -> 2
                else -> 1
            }
            // Shared across all overview player panels; independent of the full board zoom.
             val tableZoom = rememberTableZoom()
             val tableZoomContext = LocalContext.current
             val tableZoomConfig = LocalConfiguration.current
             val overviewGridState = rememberLazyGridState()
            LaunchedEffect(activePlayer, players.size) {
                if (activePlayer in players.indices) {
                    if (reducedSwipeMotion) overviewGridState.scrollToItem(activePlayer)
                    else overviewGridState.animateScrollToItem(activePlayer)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(overviewColumns.coerceAtLeast(1)),
                state = overviewGridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(players.size) { index ->
                    val player = players[index]
                    PlayerOverview(
                        player.copy(name = if (onlineMode && index == localSeat) "YOU" else player.name), index,
                        Modifier.fillMaxWidth().then(if (index == selectedPlayer) Modifier else Modifier),
                        compact = compactOverview,
                        isActivePlayer = index == activePlayer,
                        turnNumber = turnNumber,
                        phaseName = if (index == activePlayer) phaseSteps[phaseIndex] else "WAITING",
                        turnTime = if (index == activePlayer) elapsedTurnSeconds else null,
                        showTurnTimer = turnTimerVisible,
                        onToggleTimer = { if (!onlineMode || localSeat == 0) turnTimerVisible = !turnTimerVisible },
                        onMoveUp = { if (!onlineMode) movePlayerCard(index, -1) }, onMoveDown = { if (!onlineMode) movePlayerCard(index, 1) },
                        canReorder = !onlineMode,
                         tableZoom = tableZoom,
                         onTableZoomEnd = { saveTableZoom(tableZoomContext, tableZoomConfig.screenWidthDp, tableZoomConfig.screenHeightDp, it) },
                        onOpen = { run { selectedPlayer = index; showOverview = false } },
                        onZone = { zoneDialog = index to it },
                        onPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = gameInProgress && (!onlineMode || index == localSeat) && !gameEndedOnline) },
                        onPublicPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = false) }
                    )
                }
            }
        } else {
            // One board, or on a big screen (unfolded Fold) an opponent's board beside yours.
            @Composable
            fun BoardFor(boardIndex: Int, boardModifier: Modifier) {
                val boardCanControl = gameInProgress && !argentumEnabled && (!onlineMode || boardIndex == localSeat) && !gameEndedOnline && !players[boardIndex].isEliminated
                PlayerBoard(
                    player = players[boardIndex].copy(name = if (onlineMode && boardIndex == localSeat) "YOU" else players[boardIndex].name), playerIndex = boardIndex, isLandscape = isLandscape, cardSizeScale = cardSizeScale,
                    boardSpacingScale = boardSpacingScale,
                    playerSectionOrder = playerSectionOrderRaw.split('|').filter(String::isNotBlank),
                    quickActionOrder = quickActionOrderRaw.split('|').filter(String::isNotBlank),
                    stackIdenticalCards = stackSameCards,
                    gameInProgress = gameInProgress,
                    stackCount = visibleStackCount,
                    commanderUnavailable = players[boardIndex].commanderCard?.let { commander ->
                        players[boardIndex].library.any { it.id == commander.id } || players[boardIndex].hand.any { it.id == commander.id } || players[boardIndex].battlefield.any { it.id == commander.id } || players[boardIndex].graveyard.any { it.id == commander.id } || players[boardIndex].exile.any { it.id == commander.id } || players[boardIndex].revealed.any { it.id == commander.id } || stackActions.any { it.card?.id == commander.id }
                    } ?: false,
                    canControl = boardCanControl,
                    isActivePlayer = boardIndex == activePlayer, turnNumber = turnNumber, phaseName = phaseSteps[phaseIndex],
                    modifier = boardModifier
                        .turnHighlight(argentumEnabled && argentumSnapshot != null, boardIndex == argentumSnapshot?.activePlayer, boardIndex == localSeat)
                        .onGloballyPositioned { if (boardIndex == localSeat || !sideBySideBoards) battlefieldBounds = it.boundsInRoot() },
                    onLife = { delta -> run { if (boardCanControl) { val nextLife = (players[boardIndex].life + delta).coerceAtLeast(0); updatePlayer(boardIndex) { it.copy(life = nextLife) }; logAction(boardIndex, if (delta > 0) "gained life" else "lost life"); if (nextLife == 0) eliminatePlayer(boardIndex, "Life total reached 0") } } },
                    onDraw = { run { if (boardCanControl) { drawCard(boardIndex); showHand = true } } },
                    onShowHand = { run { if (boardCanControl || (argentumEnabled && boardIndex == localSeat)) showHand = true } },
                    onShowHandFromZone = { run { if (boardCanControl || (argentumEnabled && boardIndex == localSeat)) showHand = true } },
                    engineControlled = argentumEnabled && boardIndex == localSeat,
                    engineGame = argentumEnabled,
                    undoAvailable = argentumEnabled && boardIndex == localSeat && argentumUndoAvailable && !argentumAwaitingUpdate,
                    passToMyTurnActive = passToMyTurnFrom != null,
                    onPassToMyTurn = { run { togglePassToMyTurn() } },
                    onUndo = { run { requestArgentumUndo() } },
                    playableIds = if (boardIndex == localSeat) argentumPlayableIds else emptySet(),
                    argentumGame = argentumEnabled,
                    localPlayerEliminated = players.getOrNull(localSeat)?.isEliminated != false,
                    onToken = { run { if (boardCanControl) { tokenTarget = boardIndex; tokenPickerOpen = true } } },
                    onManageMana = { run { if (boardCanControl) { manaTarget = boardIndex; manaPoolOpen = true } } },
                    onZone = { zone -> run { zoneDialog = boardIndex to zone } },
                    onPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = boardCanControl) },
                    onPublicPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = false) },
                    onTapCard = { id -> run {
                        val card = players.getOrNull(boardIndex)?.battlefield?.firstOrNull { it.id == id }
                        if (argentumEnabled && card != null && boardIndex == localSeat) openArgentumActions(card)
                        else if (boardCanControl) card?.let { tapPermanent(boardIndex, it) }
                    } },
                    onCardAction = { card, action ->
                        if (argentumEnabled && action != "INSPECT" && boardIndex == localSeat) openArgentumActions(card)
                        else if (boardCanControl || action == "INSPECT") when (action) {
                            "COUNTER" -> counterTarget = boardIndex to card.id
                            "TOKEN" -> { tokenTarget = boardIndex; tokenPickerOpen = true }
                            "TAP" -> tapPermanent(boardIndex, card)
                            "FACE UP" -> { updateCard(boardIndex, card.id) { it.copy(isFaceDown = false) }; logAction(boardIndex, "turned a face-down card face up") }
                            "SET COMMANDER" -> if (players[boardIndex].commanderCard == null && players[boardIndex].commanderName.isNullOrBlank()) { updatePlayer(boardIndex) { it.copy(commanderName = card.name, commanderCard = card) }; logAction(boardIndex, "marked ${card.name} as commander") }
                            "COMMAND ZONE" -> if (players[boardIndex].commanderCard?.id == card.id) {
                                updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, commanderCard = card, commanderName = card.name) }
                                logAction(boardIndex, "moved ${card.name} to the command zone")
                            } else message = "Only your designated commander can move to the command zone."
                            "GRAVEYARD" -> { updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, graveyard = p.graveyard + card) }; logAction(boardIndex, "moved ${card.name} to graveyard") }
                            "EXILE" -> { updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, exile = p.exile + card) }; logAction(boardIndex, "exiled ${card.name}") }
                            "INSPECT" -> preview = CardPreview(boardIndex, card, fromHand = false)
                            else -> if (action.startsWith("ABILITY:")) {
                                val abilityText = action.removePrefix("ABILITY:")
                                val kind = if (card.type.contains("planeswalker", ignoreCase = true)) "LOYALTY ABILITY" else "ACTIVATED ABILITY"
                                val stackItem = StackAction(title = "${card.name}: $abilityText", controllerId = players[boardIndex].id, kind = kind, card = card, targetText = abilityText)
                                stackActions = stackActions + stackItem
                                if (stackItem.targetCount != null) targetSelectionStackItemId = stackItem.id
                                priorityPassed = emptySet()
                                priorityPlayer = boardIndex
                                stackOpen = stackItem.targetCount == null
                                logAction(boardIndex, "activated an ability of ${card.name}; resolve its effect manually")
                            }
                        }
                    },
                    onNextStep = { run { if (argentumEnabled && boardIndex == localSeat) sendArgentumPriority() else if (boardCanControl && boardIndex == activePlayer) advanceStep() } }, onOpenLog = { logOpen = true }, onOpenChat = { chatOpen = true },
                    onCombat = { run { if (argentumEnabled && boardIndex == localSeat) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else if (boardCanControl && boardIndex == activePlayer) combatDialogOpen = true } },
                    onTrackers = { if (boardCanControl) trackerPlayer = boardIndex },
                    onSearchLibrary = { run { if (boardCanControl) librarySearchPlayer = boardIndex } },
                    onStack = { run { stackOpen = true } },
                    onOpenMore = { run {} },
            onCastCommander = { run {
                        if (boardCanControl) {
                            val commander = players[boardIndex].commanderCard
                            if (commander == null) message = "Set a commander from your deck or battlefield first."
                            else { paymentSourceZone = "Command Zone"; paymentCard = boardIndex to commander }
                        } else if (argentumEnabled && boardIndex == localSeat) players[boardIndex].commanderCard?.let(::openArgentumActions)
                    } },
                    onConcede = { if (boardCanControl) concedePlayer = boardIndex },
                    onReveal = {
                        if (boardCanControl) {
                            updatePlayer(boardIndex) { p ->
                                if (p.library.isEmpty()) p else p.copy(library = p.library.drop(1), revealed = p.revealed + p.library.first())
                            }
                            logAction(boardIndex, "revealed the top card")
                        }
                    },
                    onShuffle = { if (boardCanControl) { updatePlayer(boardIndex) { it.copy(library = it.library.shuffled()) }; logAction(boardIndex, "shuffled their library") } }
                )
            }
            val shownIndex = showingView.coerceIn(players.indices)
            if (sideBySideBoards) {
                val leftIndex = if (shownIndex != localSeat) shownIndex
                    else lastOpponentViewed.takeIf { it in players.indices && it != localSeat } ?: players.indices.first { it != localSeat }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardFor(leftIndex, Modifier.weight(1f).fillMaxHeight())
                    BoardFor(localSeat, Modifier.weight(1f).fillMaxHeight())
                }
            } else BoardFor(shownIndex, Modifier.weight(1f).fillMaxWidth())
        }
        }
        }

        if (showOverview) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (argentumEnabled && gameInProgress) HandPeekBar(
                    players.getOrNull(localSeat)?.hand?.size ?: 0,
                    modifier = Modifier.weight(1f).testTag("hand-peek-table").tutorialTarget("hand-handle"),
                    onClick = { run { showHand = true } }
                ) else Text("Tap a player to inspect their board", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (argentumEnabled) {
                    if (passToMyTurnFrom != null) HeaderButton("AUTO-PASSING · STOP", onClick = { run { togglePassToMyTurn() } }, modifier = Modifier, onLongClick = { run { togglePassToMyTurn() } })
                    else if (argentumPrimaryLabel != null) PassButton(argentumPrimaryLabel!!, onClick = { run { argentumPrimary() } }, enabled = !argentumAwaitingUpdate, canRespond = argentumCanRespond && respondGlow, modifier = Modifier, onLongClick = { run { togglePassToMyTurn() } })
                    else Text(when { gameEndedOnline -> "GAME OVER"; argentumSnapshot == null -> "WAITING FOR ARGENTUM"; argentumPendingDecision != null -> "YOUR CHOICE IS NEEDED"; argentumAwaitingUpdate -> if (argentumSlow) "STILL WORKING…" else if (argentumOffline) "ENGINE THINKING…" else "WAITING FOR ARGENTUM"; wifiRole != null -> "WAITING FOR ${(argentumSnapshot?.let { players.getOrNull(it.priorityPlayer)?.name } ?: "YOUR FRIEND").uppercase()}…"; else -> "OPPONENT HAS PRIORITY" }, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) HeaderButton("NEXT · ${phaseSteps[phaseIndex]}", onClick = { run { if (argentumEnabled) sendArgentumPriority() else advanceStep() } }, enabled = !argentumAwaitingUpdate, modifier = Modifier)
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) {
                    Spacer(Modifier.width(5.dp))
                    HeaderButton("PASS TURN", onClick = { run { if (argentumEnabled) sendArgentumPriority() else passTurn() } }, enabled = !argentumAwaitingUpdate, modifier = Modifier)
                } else if (gameInProgress) Text(if (players[activePlayer].deckLoaded) "COMPUTER TURN" else "AI NEEDS DECK", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                else Text("START GAME TO PLAY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                }
                if (!players[selectedPlayer].deckLoaded && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) {
                    Spacer(Modifier.width(6.dp))
                    HeaderButton("IMPORT DECK", onClick = { run { if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } }, modifier = Modifier)
                }
            }
        } else {
            val canPassTurnFromBoard = gameInProgress && !gameEndedOnline && selectedPlayer == activePlayer && !players[selectedPlayer].isComputer && !players[selectedPlayer].isEliminated && (!onlineMode || localSeat == activePlayer) && !argentumAwaitingUpdate
            val footerControlHeight = (54.dp * footerButtonScale).coerceIn(44.dp, 76.dp)
            val handControlHeight = (64.dp * footerButtonScale).coerceIn(50.dp, 84.dp)
            // Pass · Hand · Table share one baseline and height; the hand drawer is the one larger
            // control (a little taller and wider). The pass button works whichever board you're
            // looking at, and lights up when you can respond on someone else's turn.
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).testTag("bottom-bar"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                PassButton(
                    if (argentumEnabled && passToMyTurnFrom != null) "AUTO-PASSING · STOP" else if (argentumEnabled) argentumPrimaryLabel ?: "WAITING" else "PASS TURN",
                    onClick = { run { if (argentumEnabled && passToMyTurnFrom != null) togglePassToMyTurn() else if (argentumEnabled) argentumPrimary() else passTurn() } },
                    enabled = if (argentumEnabled && passToMyTurnFrom != null) true else if (argentumEnabled) argentumPrimaryLabel != null && !argentumAwaitingUpdate else canPassTurnFromBoard,
                    canRespond = argentumCanRespond && respondGlow,
                    modifier = Modifier.weight(1f).height(footerControlHeight).testTag("bottom-bar-pass-button").tutorialTarget("bottom-bar-pass-button"),
                    onLongClick = if (argentumEnabled) ({ run { togglePassToMyTurn() } }) else null
                )
                Box(Modifier.weight(1.3f)) {
                    HandPeekBar(
                        players[if (argentumEnabled) localSeat.coerceIn(players.indices) else selectedPlayer.coerceIn(players.indices)].hand.size,
                        modifier = Modifier.fillMaxWidth().height(handControlHeight).testTag("hand-peek").tutorialTarget("hand-handle"),
                        onClick = { run { if (canControlSelectedPlayer || argentumEnabled) { showHand = true; TutorialActions.tap("hand-handle") } } }
                    )
                }
                // Same box style as the pass button, so both fill their full height and line up.
                PassButton(
                    if (argentumEnabled) "TABLE" else "BACK TO TABLE", onClick = { run { showOverview = true; showHand = false } },
                    enabled = true, canRespond = false,
                    modifier = Modifier.weight(1f).height(footerControlHeight)
                )
            }
        }
    }
}
