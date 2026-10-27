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

/** Overlays and dialogs drawn over the table (part 1: Argentum game screens, hand tray, decisions). */
@Composable
internal fun TableController.TableOverlays() {
            Box(Modifier.fillMaxSize()) {
        if (argentumEnabled && !homeOpen && gameInProgress && showStackPile) StackPile(argentumSnapshot?.stack.orEmpty(), onOpenStack = { stackOpen = true })
        if (argentumEnabled && !homeOpen && showTurnBanner) TurnBanner(turnBanner, onDismiss = { turnBanner = null })
        if (argentumEnabled && !homeOpen && showEngineStatus &&
            (argentumAwaitingUpdate || argentumEngineAdvancing)) {
            EngineProcessingPanel(
                offline = argentumOffline, awaitingAction = argentumAwaitingUpdate,
                nonInteractiveEngineUpdate = argentumEngineAdvancing,
                lastCompletedMs = argentumLastCompletedEngineMs,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 148.dp)
            )
        }
        // Distinct from fading play notifications: this remains until Argentum removes
        // the targeting spell/ability from the stack. Never dismiss on a timer.
        if (argentumEnabled && !homeOpen) {
            argentumSnapshot?.incomingAlerts()?.firstOrNull()?.let { alert ->
                TargetingAlertPanel(alert, onInspect = { stackOpen = true },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 118.dp))
            }
        }
        if (argentumEnabled && !homeOpen) PlayFeedOverlay(if (showPlayedPopup) playPopup else null, if (showRecentPlays) playFeed else emptyList(), if (showAbilityBanners) playBanner else null, if (showAiRecap) recapText else null,
            onDismissPopup = { playPopup = null }, onPreview = { playPopup = it },
            feedOpen = playFeedOpen, onToggleFeed = { playFeedOpen = !playFeedOpen; runCatching { context.getSharedPreferences("arcana-table-ui", Context.MODE_PRIVATE).edit().putBoolean("playFeedOpen", playFeedOpen).apply() } },
            popupDurationSeconds = playedPopupSeconds,
            onDismissBanner = { playBanner = null }, onDismissRecap = { recapText = null })
        if (homeOpen) {
            if (offlineDiscardConfirm) AlertDialog(
                onDismissRequest = { offlineDiscardConfirm = false },
                title = { Text("Discard saved offline game?") },
                text = { Text("This permanently deletes the full offline checkpoint. It cannot be recovered.") },
                confirmButton = {
                    TextButton(onClick = {
                        OfflineCheckpointStore.clear(context)
                        offlineResumeAvailable = false
                        offlineDiscardConfirm = false
                    }) { Text("DISCARD SAVE", color = CounterGold) }
                },
                dismissButton = {
                    TextButton(onClick = { offlineDiscardConfirm = false }) { Text("CANCEL") }
                }
            )
            if (quickUiTipsOpen) QuickUiTipsDialog(
                onDismiss = {
                    optionPrefs.edit().putBoolean("tutorial_offer_seen", true).apply()
                    quickUiTipsOpen = false
                },
                onDontRemind = {
                    optionPrefs.edit().putBoolean("tutorial_offer_seen", true).putBoolean("quick_ui_tips_disabled", true).apply()
                    quickUiTipsOpen = false
                },
                onOpenTutorials = {
                    optionPrefs.edit().putBoolean("tutorial_offer_seen", true).apply()
                    quickUiTipsOpen = false
                    tutorialHubOpen = true
                },
            )
            val deckChoices = remember(savedDecks, premadeDecks, preconDecks) {
                ReleaseFeatures.availableDecks(preconDecks.map { DeckChoice("precon:${it.id}", it.name, it.commander, builtIn = true, category = DeckChoiceCategory.PRECON) } +
                    premadeDecks.map { DeckChoice("builtin:${it.id}", it.name, it.commander, builtIn = true, category = argentumDeckCategory(it.id)) } +
                    savedDecks.map { DeckChoice(it.name, it.name, it.commander ?: parseDeckList(it.text).firstOrNull { e -> e.commander }?.name, builtIn = false, category = DeckChoiceCategory.OTHER) })
            }
            val seat = players.getOrNull(localSeat)
            val activeDeck = deckChoices.firstOrNull { it.id == activeDeckId }
                ?: seat?.takeIf { it.deckLoaded && ReleaseFeatures.deckImportEnabled }?.let { DeckChoice("table", it.commanderName ?: "Your table deck", it.commanderName, builtIn = false) }
            // Commander art: the table card's own picture, else the app's card data (cached on the
            // phone by CardImages), and only then a Scryfall name lookup.
            val commanderName = activeDeck?.commander
            val tableArt = commanderName?.let { name -> seat?.commanderCard?.takeIf { it.name.equals(name, true) }?.let { it.artCropUrl ?: it.imageUrl } }
            val bundledArt by androidx.compose.runtime.produceState<String?>(null, commanderName) {
                value = commanderName?.let { CardImages.bundledImage(context, it) }?.let { CardImages.atSize(it, "art_crop") }
            }
            val art = commanderName?.let { name -> homeArtUrl(tableArt, bundledArt, name) }
            ArcanaHomeScreen(
                wide = screenWidthDp >= 700,
                deck = activeDeck, commanderArt = art,
                argentumActive = argentumEnabled,
                working = argentumDeckValidationRunning || offlineStarting,
                workingLabel = if (offlineStarting) "PREPARING OFFLINE GAME…" else "CHECKING DECK WITH ARGENTUM…",
                resumeAvailable = argentumResumeAvailable && !argentumEnabled,
                offlineResumeAvailable = offlineResumeAvailable && !argentumEnabled,
                onResumeOffline = { resumeOfflineGame() },
                onDiscardOffline = { offlineDiscardConfirm = true },
                testScenarios = TestScenarios.available,
                onTestScenario = { startTestScenario(it) },
                onResume = {
                    recordDiagnostic("Player chose to resume the previous Argentum game", DiagnosticCategory.CONNECTION)
                    argentumResumeRequested = true
                    argentumLocalPlayersBackup = players.toList()
                    argentumEnabled = true
                    homeOpen = false
                    argentumSocket.retryNow()
                },
                onDismissResume = {
                    recordDiagnostic("Player chose to end the previous Argentum game", DiagnosticCategory.CONNECTION)
                    argentumEndPreviousRequested = true
                    argentumSocket.connect(lobbyPlayerName)
                },
                onPlay = { playSheetOpen = true; TutorialActions.tap("home-play") },
                onChangeDeck = { deckPickerOpen = true; TutorialActions.tap("home-change-deck") },
                onImport = { selectedPlayer = localSeat; importError = null; importOpen = true },
                onSettings = { optionsOpen = true; TutorialActions.tap("home-settings") },
                onReturnToTable = { run { homeOpen = false } },
            )
            if (playSheetOpen) PlaySheet(
                deckName = activeDeck?.name,
                aiDecks = premadeDecks.map { DeckChoice(it.id, it.name, it.commander, builtIn = true) },
                aiDeckId = offlineAiDeckId,
                onAiDeck = { offlineAiDeckId = it; TutorialActions.tap("setup-ai-deck") },
                aiCount = offlineAiCount,
                onAiCount = { offlineAiCount = ReleaseFeatures.aiCount(it); optionPrefs.edit().putInt("offline_ai_count", offlineAiCount).apply() },
                aiDifficulties = offlineAiDifficulties,
                onAiDifficulty = { seat, level -> setOfflineAiDifficulty(seat, level) },
                onOffline = {
                    playSheetOpen = false
                    // PLAY always starts the game; a Choose-a-game lesson ends here, its job done.
                    if (tutorialChapterId != null && !tutorialPracticeActive) { TutorialActions.tap("setup-start"); stopTutorial(reopenHub = false) }
                    startOfflineGame()
                },
                onOnline = { playSheetOpen = false; run { startArgentumPractice(vsAi = true) } },
                onCreateRoom = { playSheetOpen = false; run { startArgentumPractice(vsAi = false) } },
                onJoinRoom = { playSheetOpen = false; run { argentumLastError = null; argentumJoinCode = ""; argentumJoinDialogOpen = true } },
                onDismiss = { playSheetOpen = false },
                onWifiMultiHost = { playSheetOpen = false; run {
                    recordDiagnostic("Multiplayer: Wi-Fi host setup opened", DiagnosticCategory.ACTION)
                    wifiMultiOnline = false
                    wifiMultiConfigOpen = true
                } },
                onWifiJoin = { playSheetOpen = false; run { recordDiagnostic("Multiplayer: Wi-Fi join opened", DiagnosticCategory.ACTION); wifiFound = emptyList(); wifiJoinOpen = true; searchWifiHosts() } },
                onOnlineMultiHost = { playSheetOpen = false; run {
                    recordDiagnostic("Multiplayer: internet host setup opened", DiagnosticCategory.ACTION)
                    wifiMultiOnline = true
                    wifiMultiConfigOpen = true
                } },
                onOnlineMultiJoin = { playSheetOpen = false; run {
                    recordDiagnostic("Multiplayer: internet join opened", DiagnosticCategory.ACTION)
                    onlineCodeInput = ""
                    onlineJoinOpen = true
                } },
                tutorialStep = if (tutorialChapterId != null) tutorialStep else null,
            )
            val pickDeck: (DeckChoice) -> Unit = { choice ->
                    deckPickerOpen = false
                    // Choosing a deck during the deck lesson really chooses it, then the lesson moves on.
                    if (tutorialChapterId != null && !tutorialPracticeActive) TutorialActions.tap("deck-select")
                    run {
                    activeDeckId = choice.id; saveActiveDeck(context, choice.id)
                    recordDiagnostic("Deck chosen: ${choice.name}", DiagnosticCategory.ACTION)
                    // Also put it on your table (card art, online play). Offline games read the choice directly.
                    val text = preconDecks.firstOrNull { "precon:${it.id}" == choice.id }?.decklist
                        ?: savedDecks.firstOrNull { it.name == choice.id }?.text
                        ?: premadeDecks.firstOrNull { "builtin:${it.id}" == choice.id }?.let { d -> "Commander\n1 ${d.commander}\nDeck\n" + d.cards.filterNot { it == d.commander }.groupingBy { it }.eachCount().entries.joinToString("\n") { (n, c) -> "$c $n" } }
                    if (text != null) { selectedPlayer = localSeat; importDeck(text, saveAsMine = false) }
                    }
            }
            if (deckPickerOpen) DeckPickerSheet(deckChoices, activeDeckId,
                onPick = pickDeck,
                onImport = { deckPickerOpen = false; selectedPlayer = localSeat; importError = null; importOpen = true },
                onDismiss = { deckPickerOpen = false },
                onDeckBuilder = if (DeckBuilderFlag.enabled) { { deckPickerOpen = false; deckBuilderOpen = true } } else null,
                tutorialStep = if (tutorialChapterId != null) tutorialStep else null)
            if (deckBuilderOpen && DeckBuilderFlag.enabled) DeckManagerDialog(
                decks = savedDecks,
                loadCatalog = { loadSupportCatalog() },
                onDecksChanged = { updated -> savedDecks = updated; saveSavedDecks(context, updated) },
                onPlay = { deck ->
                    deckBuilderOpen = false
                    pickDeck(DeckChoice(deck.name, deck.name, deck.commander, builtIn = false, category = DeckChoiceCategory.OTHER))
                },
                onDismiss = { deckBuilderOpen = false })
        }

    if (argentumEnabled && argentumSnapshot == null && !homeOpen) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text("ARGENTUM GAME", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
                Text(
                    when {
                        argentumStatus == ArgentumConnectionStatus.CONNECTING -> "Connecting to Argentum…"
                        argentumStatus == ArgentumConnectionStatus.REPLACED -> "This Argentum session was opened on another device."
                        argentumStatus == ArgentumConnectionStatus.DISCONNECTED -> "Connection lost. Arcana will retry automatically."
                        argentumStatus == ArgentumConnectionStatus.PAUSED -> "Paused while Arcana was in the background. Tap Reconnect when you're ready."
                        argentumInbound?.optString("type") == "quickGameLobbyState" -> if (argentumVsAiLobby) "Your practice table is ready when Argentum finishes preparing it." else "Your room is open. Share its code so other players can join."
                        else -> if (argentumVsAiLobby) "Connected. Creating your practice table…" else "Connected. Creating your Argentum room…"
                    },
                    color = SoftWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)
                )
                val lobbyState = argentumInbound?.takeIf { it.optString("type") == "quickGameLobbyState" }
                val lobbyId = lobbyState?.optString("lobbyId")
                if (!lobbyId.isNullOrBlank()) {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(18.dp)) {
                        Text("ARGENTUM ROOM CODE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(lobbyId, color = CounterGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (lobbyState.optBoolean("vsAi", argentumVsAiLobby).not()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Argentum room code", lobbyId))
                                    message = "Argentum room code copied."
                                },
                                modifier = Modifier.padding(top = 10.dp).heightIn(min = 44.dp)
                            ) { Text("COPY ROOM CODE") }
                        }
                    }
                }
                val lobbyPlayers = lobbyState?.optJSONArray("players")
                if (lobbyPlayers != null) {
                    Text("PLAYERS · ${lobbyPlayers.length()}/${if (lobbyState?.optBoolean("vsAi", argentumVsAiLobby) == true) 2 else 4}", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
                    for (index in 0 until lobbyPlayers.length()) {
                        val lobbyPlayer = lobbyPlayers.optJSONObject(index) ?: continue
                        val status = when {
                            lobbyPlayer.optBoolean("isAi") && lobbyPlayer.optBoolean("ready") -> "ARGENTUM AI · READY"
                            lobbyPlayer.optBoolean("ready") -> "DECK READY"
                            lobbyPlayer.optBoolean("deckSelected") -> "DECK SELECTED"
                            else -> "CHOOSING A DECK"
                        }
                        Text("${lobbyPlayer.optString("playerName", "PLAYER")} · $status · ${lobbyPlayer.optInt("deckCardCount")} cards", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Text(
                    "Your Arcana table and card presentation stay here. Argentum validates the deck and controls every game action.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp)
                )
                if (argentumLastError != null) Text(argentumLastError!!, color = Color(0xFFFFB4A8), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { argentumSocket.retryNow() }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("RECONNECT") }
                    OutlinedButton(onClick = {
                        argentumSocket.leaveQuickGameLobby()
                        argentumSocket.close()
                        argentumEnabled = false
                        setArgentumGameActive(false)
                        argentumSnapshot = null
                        argentumPendingDecision = null
                        argentumLocalPlayersBackup?.let { players = it; argentumLocalPlayersBackup = null }
                        argentumDeckForLobby = null
                        argentumCommanderForLobby = null
                        argentumLobbySubmitted = false
                        gameStartedOnline = false
                        lobbyOpen = false
                        gameEndedOnline = false
                        gameTimerRunning = false
                        turnTimerRunning = false
                        argentumLastError = null
                        homeOpen = true
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("LEAVE") }
                }
            }
        }
    }

    if (argentumJoinDialogOpen) AlertDialog(
        onDismissRequest = { argentumJoinDialogOpen = false },
        title = { Text("Join Argentum room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the room code shared by the host. Arcana will check your deck with Argentum before joining.")
                OutlinedTextField(
                    value = argentumJoinCode,
                    onValueChange = { argentumJoinCode = it.trim() },
                    label = { Text("Room code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = argentumJoinCode.isNotBlank() && !argentumDeckValidationRunning,
                onClick = {
                    val code = argentumJoinCode
                    argentumJoinDialogOpen = false
                    startArgentumPractice(vsAi = false, joinLobbyCode = code)
                }
            ) { Text(if (argentumDeckValidationRunning) "CHECKING…" else "CHECK DECK & JOIN") }
        },
        dismissButton = { TextButton(onClick = { argentumJoinDialogOpen = false }) { Text("CANCEL") } }
    )

    // Start-up problems (deck validation, connecting, resuming). In-game errors never reach here,
    // and errors already shown as a pop-up are not stored for it.
    if (argentumLastError != null && argentumSnapshot == null && homeOpen) AlertDialog(
        onDismissRequest = { argentumLastError = null },
        title = { Text("Couldn't start the game") },
        text = { Text(argentumLastError.orEmpty()) },
        confirmButton = { TextButton(onClick = { argentumLastError = null }) { Text("OK") } }
    )


    // Game over: result, then play again / look at the final board / go home.
    if (argentumEnabled && gameEndedOnline && !argentumGameOverDismissed) {
        val won = argentumWon ?: argentumSnapshot?.winnerName?.let { it == players.getOrNull(localSeat)?.name }
        AlertDialog(
            onDismissRequest = { argentumGameOverDismissed = true },
            title = { Text(if (argentumMatchUnfinished) "Eliminated" else when (won) { true -> "Victory"; false -> "Defeat"; null -> "Game over" }, fontWeight = FontWeight.Black, color = if (argentumMatchUnfinished) Color(0xFFFFB4A8) else when (won) { true -> Mint; false -> Color(0xFFFFB4A8); null -> SoftWhite }) },
            text = {
                Column {
                    Text(argentumGameOverText ?: argentumSnapshot?.winnerName?.let { "$it won." } ?: "The game has ended.", color = SoftWhite)
                    Text("Turn ${argentumSnapshot?.turnNumber ?: turnNumber} · ${formatClock(elapsedGameSeconds)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = {
                recordDiagnostic("Player chose Play again", DiagnosticCategory.ACTION)
                val wasOffline = argentumOffline
                val wifi = wifiRole
                closeArgentumSession(concede = false)
                when {
                    // Multiplayer: the host reopens the 2–4 seat lobby setup (keeping the last
                    // seat choices); a guest looks for or re-enters a room, as from the Play menu.
                    wifi == "host" || wifi == "online-host" -> { wifiMultiOnline = wifi == "online-host"; wifiMultiConfigOpen = true }
                    wifi == "guest" -> { wifiFound = emptyList(); wifiJoinOpen = true; searchWifiHosts() }
                    wifi == "online-guest" -> { onlineCodeInput = ""; onlineJoinOpen = true }
                    wasOffline -> startOfflineGame()
                    else -> startArgentumPractice(vsAi = true)
                }
            }) { Text("PLAY AGAIN") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { argentumGameOverDismissed = true }) { Text("VIEW BOARD") }
                    TextButton(onClick = { closeArgentumSession(concede = false) }) { Text("HOME") }
                }
            }
        )
    }

    if (argentumLeaveConfirm) AlertDialog(
        onDismissRequest = { argentumLeaveConfirm = false },
        title = { Text("Leave Argentum game?") },
        text = { Text(if (argentumSnapshot == null) "This will leave the Argentum room." else "Leaving a live game concedes the match to Argentum.") },
        confirmButton = { TextButton(onClick = {
            closeArgentumSession(concede = true)
            argentumLeaveConfirm = false
        }) { Text("LEAVE GAME") } },
        dismissButton = { TextButton(onClick = { argentumLeaveConfirm = false }) { Text("CANCEL") } }
    )

    if (argentumMulliganPrompt && argentumSnapshot == null) ArgentumOpeningHandScreen(
        cards = argentumOpeningHand,
        title = "Opening hand",
        subtitle = argentumMulliganInfo?.let { (count, onPlay, _) ->
            listOfNotNull(if (onPlay) "You're on the play" else "You're on the draw", if (count > 0) "Mulligan $count · you'll put $count on the bottom after keeping" else null).joinToString(" · ")
        } ?: "Argentum dealt your opening seven.",
        selectable = false, selection = emptySet(), onToggle = {},
        primaryLabel = "KEEP ${argentumOpeningHand.size}", primaryEnabled = !argentumAwaitingUpdate && argentumOpeningHand.isNotEmpty(),
        onPrimary = { if (argentumSocket.keepHand()) { argentumMulliganPrompt = false; argentumAwaitingUpdate = true; tutorialKeepPending = tutorialPracticeActive; recordDiagnostic("Kept opening hand", DiagnosticCategory.ACTION) } },
        secondaryLabel = "MULLIGAN", secondaryEnabled = !argentumAwaitingUpdate,
        onSecondary = { if (argentumSocket.mulligan()) { argentumAwaitingUpdate = true; tutorialMulliganPending = tutorialPracticeActive; recordDiagnostic("Took a mulligan", DiagnosticCategory.ACTION) } },
    )

    argentumBottomCardIds?.let { ids ->
        val idFor = { serverId: String -> (serverId.hashCode() and 0x7fffffff).coerceAtLeast(1) }
        val hand = argentumOpeningHand
        ArgentumOpeningHandScreen(
            cards = ids.mapNotNull { id -> hand.firstOrNull { it.id == idFor(id) } },
            title = "Put ${argentumBottomCardCount} on the bottom",
            subtitle = "Tap ${if (argentumBottomCardCount == 1) "the card" else "$argentumBottomCardCount cards"} to put on the bottom of your library.",
            selectable = true,
            selection = argentumBottomSelection.map(idFor).toSet(),
            onToggle = { card ->
                val serverId = ids.firstOrNull { idFor(it) == card.id } ?: return@ArgentumOpeningHandScreen
                argentumBottomSelection = if (serverId in argentumBottomSelection) argentumBottomSelection - serverId
                    else if (argentumBottomSelection.size < argentumBottomCardCount) argentumBottomSelection + serverId else argentumBottomSelection
            },
            primaryLabel = "CONFIRM ${argentumBottomSelection.size}/$argentumBottomCardCount",
            primaryEnabled = argentumBottomSelection.size == argentumBottomCardCount && !argentumAwaitingUpdate,
            onPrimary = { if (argentumSocket.chooseBottomCards(argentumBottomSelection.toList())) { argentumBottomCardIds = null; argentumAwaitingUpdate = true } },
            secondaryLabel = null, secondaryEnabled = false, onSecondary = {},
        )
    }

    if (argentumActionChoices.isNotEmpty()) ArgentumActionSheet(
        card = argentumActionCard,
        choices = ArgentumActions.visibleChoices(argentumActionChoices),
        hidManaAbilities = false,
        // A land or mana source's own sheet lists its mana abilities at the bottom (float mana).
        manaChoices = if (argentumActionCard != null) ArgentumActions.floatManaChoices(argentumActionChoices) else emptyList(),
        busy = argentumAwaitingUpdate,
        stackCount = { info -> StackUses.count(info, argentumSnapshot?.legalActions.orEmpty()) { id -> argentumPiece(id).name } },
        onChooseMany = { info, count ->
            val source = info.optJSONObject("action")?.optString("sourceId").orEmpty()
            argentumStackUse = StackUse(argentumPiece(source).name, info.optString("description"), remaining = count - 1)
            argentumActionChoices = emptyList(); argentumActionCard = null; chooseArgentumLegalAction(info)
        },
        onChoose = { info -> argentumActionChoices = emptyList(); argentumActionCard = null; argentumStackUse = null; chooseArgentumLegalAction(info) },
        onSecretNudge = { if (wifiRole != null) argentumSocket.send("arcanaNudge") },
            onDismiss = { argentumActionChoices = emptyList(); argentumActionCard = null }
    )

    // Combat damage is split on the combat board rather than in a dialog.
    argentumPendingDecision?.takeIf { it.optString("type") == "CombatResolutionDecision" && it.optString("id") != argentumDeferredDecisionId }?.let { decision ->
        ArgentumCombatResolution.from(decision)?.let { board ->
            ArgentumDamageBoard(board, decision.optString("playerId"), ::argentumPiece, argentumAmounts, busy = argentumAwaitingUpdate,
                onChange = { argentumAmounts = it },
                onConfirm = { submitArgentumDecision(board.response(decision.optString("playerId"), argentumAmounts)) },
                onDismiss = { argentumDeferredDecisionId = decision.optString("id") })
        }
    }
    argentumPendingDecision?.takeUnless { it.optString("type") == "PlayCardDecision" || it.optString("id") == argentumDeferredDecisionId ||
        (it.optString("type") == "CombatResolutionDecision" && ArgentumCombatResolution.from(it) != null) }?.let { decision ->
        val decisionId = decision.optString("id")
        val kind = decision.optString("type")
        val prompt = decision.decisionDisplayText("prompt", "Argentum needs a choice").let { base ->
            // Say which card is asking, so "Choose up to 1 card" reads "Lady Octopus: Choose up to 1 card".
            val source = decision.optJSONObject("context")?.optString("sourceName")?.takeIf { it.isNotBlank() && it != "null" }
            if (source != null && !base.contains(source, ignoreCase = true)) "$source: $base" else base
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(prompt) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    when (kind) {
                        "YesNoDecision" -> Text(decision.optString("hint").takeIf(String::isNotBlank).orEmpty(), color = Muted)
                        "ChooseOptionDecision" -> (decision.optJSONArray("options") ?: JSONArray()).let { options ->
                            var optionQuery by remember(decisionId) { mutableStateOf("") }
                            if (options.length() > SEARCH_THRESHOLD) PickSearchField(optionQuery, { optionQuery = it })
                            val shownOptions = (0 until options.length()).filter { matchesSearch(options.optString(it), optionQuery) }
                            if (shownOptions.isEmpty()) Text("Nothing matches \"$optionQuery\".", color = Muted, fontSize = 12.sp)
                            for (index in shownOptions) {
                                val option = options.optString(index)
                                TextButton(onClick = {
                                    submitArgentumDecision(JSONObject().put("type", "OptionChosenResponse").put("optionIndex", index))
                                }, modifier = Modifier.fillMaxWidth()) { Text(option, color = Mint) }
                            }
                        }
                        "ChooseNumberDecision" -> {
                            val min = decision.optInt("minValue", 0)
                            val max = decision.optInt("maxValue", min)
                            var chosenNumber by remember(decision.optString("id")) { mutableIntStateOf(min) }
                            NumberPicker(chosenNumber, min..maxOf(min, max), onChange = { chosenNumber = it })
                            TextButton(onClick = {
                                submitArgentumDecision(JSONObject().put("type", "NumberChosenResponse").put("number", chosenNumber))
                            }, modifier = Modifier.fillMaxWidth()) { Text("CHOOSE $chosenNumber", color = Mint, fontWeight = FontWeight.Black) }
                        }
                        "ChooseColorDecision" -> {
                            val colors = decision.optJSONArray("availableColors") ?: JSONArray()
                            for (index in 0 until colors.length()) {
                                val color = colors.optString(index)
                                val multi = decision.optInt("maxColors", 1) > 1
                                Row(Modifier.fillMaxWidth().clickable {
                                    argentumDecisionColors = if (multi && color in argentumDecisionColors) argentumDecisionColors - color
                                        else if (multi) argentumDecisionColors + color else setOf(color)
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (multi) Checkbox(checked = color in argentumDecisionColors, onCheckedChange = { checked ->
                                        argentumDecisionColors = if (checked) argentumDecisionColors + color else argentumDecisionColors - color
                                    }) else RadioButton(selected = color in argentumDecisionColors, onClick = { argentumDecisionColors = setOf(color) })
                                    Text(color, color = SoftWhite)
                                }
                            }
                        }
                        "SelectCardsDecision" -> {
                            val options = decision.optJSONArray("options") ?: JSONArray()
                            val limits = decision.optJSONObject("selectionLimits")
                            val cardInfo = decision.optJSONObject("cardInfo")
                            val maxSel = decision.optInt("maxSelections", 1)
                            val pickItems = (0 until options.length()).map { index ->
                                val id = options.optString(index)
                                val base = argentumPickCard(id)
                                base.withDecisionInfo(cardInfo?.optJSONObject(id), nameKnown = argentumSnapshot?.targets?.get(id) != null)
                            }
                            fun canAdd(id: String): Boolean { val cap = limits?.optInt(id, 1) ?: 1
                                return argentumDecisionSelection.size < maxSel && argentumDecisionSelection.count { limits?.optInt(it, 1) == cap } < cap }
                            CardPickGrid(pickItems, argentumDecisionSelection.toSet(), isEnabled = { it in argentumDecisionSelection || canAdd(it) }, onTap = { id ->
                                argentumDecisionSelection = if (id in argentumDecisionSelection) argentumDecisionSelection - id
                                    else if (maxSel == 1) setOf(id) else if (canAdd(id)) argentumDecisionSelection + id else argentumDecisionSelection
                            })
                            Text("Choose ${decision.optInt("minSelections", 0)}–${decision.optInt("maxSelections", 1)} card(s).", color = Muted, fontSize = 11.sp)
                        }
                        "ChooseTargetsDecision" -> {
                            val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                            val legalTargets = decision.optJSONObject("legalTargets")
                            for (requirementPosition in 0 until requirements.length()) {
                                val requirementValue = requirements.opt(requirementPosition)
                                val requirement = requirementValue as? JSONObject ?: continue
                                val requirementIndex = requirement.optInt("index", requirementPosition)
                                val chosen = argentumDecisionTargets[requirementIndex].orEmpty()
                                val maxTargets = requirement.optInt("maxTargets", 1)
                                Text(requirement.optString("description", "Choose target(s)"), color = Mint, fontSize = 12.sp)
                                val prefixPools = requirement.optJSONObject("legalTargetsByPrefix")
                                val prefixLength = requirement.optInt("prefixLength", requirementIndex)
                                val prefixKey = (0 until prefixLength).joinToString(",") { priorIndex ->
                                    argentumDecisionTargets[priorIndex].orEmpty().joinToString(",")
                                }
                                val ids = prefixPools?.optJSONArray(prefixKey)
                                    ?: legalTargets?.optJSONArray(requirementIndex.toString())
                                    ?: JSONArray()
                                val selectionGroups = requirement.optJSONArray("selectionGroups")
                                val currentSet = chosen.toSet()
                                val idList = (0 until ids.length()).map { ids.optString(it) }
                                fun allowed(id: String): Boolean {
                                    if (id in chosen) return true
                                    val groupAllows = selectionGroups == null || selectionGroups.length() == 0 ||
                                        (0 until selectionGroups.length()).any { groupIndex ->
                                            val group = selectionGroups.optJSONArray(groupIndex) ?: return@any false
                                            (0 until group.length()).map { group.optString(it) }.containsAll(currentSet + id)
                                        }
                                    return groupAllows && (chosen.size < maxTargets || maxTargets == 1)
                                }
                                CardPickGrid(idList.map { argentumPickCard(it) }, currentSet, isEnabled = ::allowed, onTap = { id ->
                                    argentumDecisionTargets = argentumDecisionTargets.toMutableMap().apply {
                                        put(requirementIndex, when { id in chosen -> chosen - id; maxTargets == 1 -> listOf(id); chosen.size < maxTargets -> chosen + id; else -> chosen })
                                    }
                                })
                                Text("Choose ${requirement.optInt("minTargets", 0)}–$maxTargets target(s).", color = Muted, fontSize = 11.sp)
                            }
                        }
                        "ChooseModeDecision" -> {
                            val modes = decision.optJSONArray("modes") ?: JSONArray()
                            val maxModes = decision.optInt("maxModes", 1)
                            for (position in 0 until modes.length()) {
                                val mode = modes.optJSONObject(position) ?: continue
                                val index = mode.optInt("index", position)
                                val available = mode.optBoolean("available", true)
                                val on = index in argentumModeSelection
                                Row(Modifier.fillMaxWidth().clickable(enabled = available) {
                                    argentumModeSelection = if (on) argentumModeSelection - index else if (maxModes == 1) setOf(index) else if (argentumModeSelection.size < maxModes) argentumModeSelection + index else argentumModeSelection
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (maxModes == 1) RadioButton(selected = on, enabled = available, onClick = { argentumModeSelection = setOf(index) })
                                    else Checkbox(checked = on, enabled = available, onCheckedChange = { argentumModeSelection = if (it) argentumModeSelection + index else argentumModeSelection - index })
                                    Text(mode.optString("text", "Mode ${index + 1}"), color = if (available) SoftWhite else Muted, fontSize = 12.sp)
                                }
                            }
                            Text("Choose ${decision.optInt("minModes", 1)}–$maxModes mode(s).", color = Muted, fontSize = 11.sp)
                        }
                        "SearchLibraryDecision" -> {
                            val options = decision.optJSONArray("options") ?: JSONArray()
                            val libraryInfo = decision.optJSONObject("cards") ?: decision.optJSONObject("cardInfo")
                            val maxSel = decision.optInt("maxSelections", 1)
                            Text(decision.optString("filterDescription"), color = Muted, fontSize = 11.sp)
                            if (options.length() == 0) Text("No matching cards. Confirm to finish the search.", color = Muted, fontSize = 12.sp)
                            else {
                                val allLibraryItems = remember(decision) {
                                    (0 until options.length()).map { index ->
                                        val id = options.optString(index)
                                        libraryPick(id, libraryInfo?.optJSONObject(id), ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name })
                                    }
                                }
                                // Type chips and sorting only arrange what Argentum offered; picks stay visible.
                                var libraryChip by remember(decision) { mutableStateOf<LibraryChip?>(null) }
                                var librarySort by remember(decision) { mutableStateOf(LibrarySort.TYPE) }
                                val chips = remember(allLibraryItems) { LibraryChip.present(allLibraryItems.map { it.typeLine }) }
                                if (allLibraryItems.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    listOf(LibrarySort.TYPE, LibrarySort.NAME).forEach { sort ->
                                        FilterChip(librarySort == sort, { librarySort = sort }, label = { Text(sort.label) }, modifier = Modifier.testTag("library-sort-${sort.name}"))
                                    }
                                    chips.forEach { (chip, n) ->
                                        FilterChip(libraryChip == chip, { libraryChip = if (libraryChip == chip) null else chip }, label = { Text("${chip.label} $n") },
                                            modifier = Modifier.testTag("library-chip-${chip.name}"))
                                    }
                                }
                                val libraryItems = arrangeLibrary(allLibraryItems, libraryChip, librarySort, keep = { it.id in argentumDecisionSelection },
                                    name = { it.name }, typeLine = { it.typeLine }, manaValue = { null })
                                    .map { (group, item) -> item.copy(group = group, groupOrder = LIBRARY_GROUP_ORDER.indexOf(group).coerceAtLeast(0)) }
                                if (maxSel > 1) Text("${argentumDecisionSelection.size} of up to $maxSel chosen", color = if (argentumDecisionSelection.isEmpty()) Muted else Mint,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                CardPickGrid(libraryItems, argentumDecisionSelection.toSet(), isEnabled = { it in argentumDecisionSelection || argentumDecisionSelection.size < maxSel || maxSel == 1 }, onTap = { id ->
                                    argentumDecisionSelection = when {
                                        id in argentumDecisionSelection -> argentumDecisionSelection - id
                                        maxSel == 1 -> setOf(id)
                                        argentumDecisionSelection.size < maxSel -> argentumDecisionSelection + id
                                        else -> argentumDecisionSelection
                                    }
                                })
                            }
                            Text("Choose ${decision.optInt("minSelections", 0)}–$maxSel card(s).", color = Muted, fontSize = 11.sp)
                        }
                        "OrderObjectsDecision", "ReorderLibraryDecision" -> ArgentumOrderEditor(
                            items = argentumOrderSelection,
                            nameOf = { id -> ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name } },
                            firstLabel = decision.optString("firstLabel").takeIf { it.isNotBlank() && it != "null" },
                            lastLabel = decision.optString("lastLabel").takeIf { it.isNotBlank() && it != "null" },
                            onChange = { argentumOrderSelection = it }
                        )
                        "SelectManaSourcesDecision" -> Text("Pay ${decision.optString("requiredCost")}. Argentum can choose the mana sources for you.", color = SoftWhite, fontSize = 12.sp)
                        "AssignDamageDecision" -> {
                            val ids = (decision.optJSONArray("orderedTargets") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } } +
                                listOfNotNull(decision.optString("defenderId").takeIf { it.isNotBlank() && it != "null" })
                            val minimums = decision.optJSONObject("minimumAssignments")
                            Text("${argentumName(decision.optString("attackerId"))} assigns ${decision.optInt("availablePower")} damage" + (if (decision.optBoolean("hasTrample")) " (trample)" else "") + (if (decision.optBoolean("hasDeathtouch")) " (deathtouch)" else "") + ".", color = Muted, fontSize = 11.sp)
                            ArgentumAmountEditor(ids.distinct(), ::argentumName, argentumAmounts, decision.optInt("availablePower"),
                                minEach = ids.associateWith { minimums?.optInt(it, 0) ?: 0 }, maxEach = emptyMap(), onChange = { argentumAmounts = it })
                        }
                        "SplitPilesDecision" -> ArgentumPileEditor(ArgentumPileSplit(decision), { id -> ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name } }, argentumAmounts) { argentumAmounts = it }
                        "ChooseReplacementDecision" -> ArgentumReplacementEditor(ArgentumReplacementChoice(decision), argentumReplacementPick.first, argentumReplacementPick.second) { f, t -> argentumReplacementPick = f to t }
                        "BudgetModalDecision" -> ArgentumBudgetEditor(ArgentumBudgetModes(decision), argentumBudgetCounts) { argentumBudgetCounts = it }
                        "CombatResolutionDecision" -> ArgentumCombatResolution.from(decision)?.let { board ->
                            ArgentumCombatDamageEditor(board, decision.optString("playerId"), argentumAmounts, onChange = { argentumAmounts = it })
                        }
                        "DistributeDecision" -> {
                            val ids = (decision.optJSONArray("targets") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } }
                            val caps = decision.optJSONObject("maxPerTarget")
                            ArgentumAmountEditor(ids, ::argentumName, argentumAmounts, decision.optInt("totalAmount"),
                                minEach = ids.associateWith { decision.optInt("minPerTarget", 0) },
                                maxEach = ids.associateWith { caps?.optInt(it, decision.optInt("totalAmount")) ?: decision.optInt("totalAmount") },
                                onChange = { argentumAmounts = it })
                        }
                        else -> {
                            val reason = ArgentumDecisions.needsHandlingReason(decision) ?: "Arcana cannot present this choice."
                            Text("NEEDS HANDLING", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(reason, color = SoftWhite, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            Text("Nothing has been chosen for you. You can " + (if (ArgentumDecisions.canCancel(decision)) "cancel this if Argentum allows it, " else "") +
                                "refresh in case Argentum sends a different prompt, or hide this to inspect the table with Arcana's viewing tools (the game waits for this choice). The event is in the diagnostic log.",
                                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                when (kind) {
                    "YesNoDecision" -> Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true)) }) { Text(decision.decisionDisplayText("yesText", "YES")) }
                        TextButton(onClick = {
                            argentumAutoYes = argentumAutoYes + (autoYesKey(decision) to (argentumSnapshot?.turnNumber ?: 0))
                            submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true))
                        }) { Text("${decision.decisionDisplayText("yesText", "YES").uppercase()} · ALWAYS THIS TURN", fontSize = 12.sp) }
                    }
                    "BatchYesNoDecision" -> Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", true).put("applyToAll", false)) }) { Text("${decision.decisionDisplayText("yesText", "YES")} · ONCE") }
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", true).put("applyToAll", true)) }) { Text("${decision.decisionDisplayText("yesText", "YES")} · ALL") }
                    }
                    "ChooseColorDecision" -> TextButton(enabled = argentumDecisionColors.isNotEmpty() && argentumDecisionColors.size <= decision.optInt("maxColors", 1), onClick = {
                        val colors = argentumDecisionColors.toList()
                        submitArgentumDecision(JSONObject().put("type", "ColorChosenResponse").put("color", colors.first()).put("colors", JSONArray(colors)))
                    }) { Text("CONFIRM") }
                    "SelectCardsDecision" -> TextButton(enabled = argentumDecisionSelection.size in decision.optInt("minSelections", 0)..decision.optInt("maxSelections", 1), onClick = {
                        submitArgentumDecision(JSONObject().put("type", "CardsSelectedResponse").put("selectedCards", JSONArray(argentumDecisionSelection.toList())))
                    }) { Text("CONFIRM") }
                    "ChooseTargetsDecision" -> TextButton(enabled = run {
                        val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                        (0 until requirements.length()).all { position ->
                            val requirement = requirements.optJSONObject(position) ?: return@all false
                            val index = requirement.optInt("index", position)
                            val count = argentumDecisionTargets[index].orEmpty().size
                            count in requirement.optInt("minTargets", 0)..requirement.optInt("maxTargets", 1)
                        }
                    }, onClick = {
                        val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                        val targets = JSONObject()
                        for (position in 0 until requirements.length()) {
                            val requirement = requirements.optJSONObject(position) ?: continue
                            val index = requirement.optInt("index", position)
                            targets.put(index.toString(), JSONArray(argentumDecisionTargets[index].orEmpty()))
                        }
                        submitArgentumDecision(JSONObject().put("type", "TargetsResponse").put("selectedTargets", targets))
                        argentumDecisionTargets = emptyMap()
                    }) { Text("CONFIRM TARGET") }
                    "ChooseModeDecision" -> TextButton(enabled = argentumModeSelection.size in decision.optInt("minModes", 1)..decision.optInt("maxModes", 1), onClick = {
                        submitArgentumDecision(ArgentumDecisions.modesResponse(argentumModeSelection.sorted()))
                    }) { Text("CONFIRM") }
                    "SearchLibraryDecision" -> TextButton(enabled = argentumDecisionSelection.size in decision.optInt("minSelections", 0)..decision.optInt("maxSelections", 1), onClick = {
                        submitArgentumDecision(ArgentumDecisions.cardsSelectedResponse(argentumDecisionSelection))
                    }, modifier = Modifier.testTag("library-confirm")) {
                        // Finding nothing is a legal answer to most tutors; say so instead of a bare CONFIRM.
                        Text(if (argentumDecisionSelection.isEmpty() && decision.optInt("minSelections", 0) == 0) "FIND NOTHING"
                            else if (argentumDecisionSelection.size > 1) "CONFIRM ${argentumDecisionSelection.size}" else "CONFIRM")
                    }
                    "OrderObjectsDecision", "ReorderLibraryDecision" -> TextButton(enabled = argentumOrderSelection.isNotEmpty(), onClick = {
                        submitArgentumDecision(ArgentumDecisions.orderedResponse(argentumOrderSelection))
                    }) { Text("CONFIRM ORDER") }
                    "SelectManaSourcesDecision" -> TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.autoPayManaResponse()) }) { Text("AUTO-PAY") }
                    "AssignDamageDecision" -> TextButton(enabled = ArgentumDecisions.damageAssignmentComplete(decision, argentumAmounts), onClick = {
                        submitArgentumDecision(ArgentumDecisions.damageAssignmentResponse(argentumAmounts.filterValues { it > 0 }))
                    }) { Text("ASSIGN") }
                    "SplitPilesDecision" -> ArgentumPileSplit(decision).let { split ->
                        TextButton(enabled = split.problem(argentumAmounts) == null, onClick = { submitArgentumDecision(split.response(argentumAmounts)) }) { Text("CONFIRM PILES") }
                    }
                    "ChooseReplacementDecision" -> TextButton(onClick = {
                        submitArgentumDecision(ArgentumReplacementChoice(decision).response(argentumReplacementPick.first, argentumReplacementPick.second))
                    }) { Text("CONFIRM") }
                    "BudgetModalDecision" -> ArgentumBudgetModes(decision).let { budget ->
                        TextButton(enabled = budget.spent(argentumBudgetCounts) <= budget.budget, onClick = { submitArgentumDecision(budget.response(argentumBudgetCounts)) }) { Text("CONFIRM") }
                    }
                    "CombatResolutionDecision" -> {
                        val board = ArgentumCombatResolution.from(decision)
                        TextButton(enabled = board != null && board.problem(argentumAmounts) == null, onClick = {
                            board?.let { submitArgentumDecision(it.response(decision.optString("playerId"), argentumAmounts)) }
                        }) { Text("CONFIRM DAMAGE") }
                    }
                    "DistributeDecision" -> TextButton(enabled = ArgentumDecisions.distributionComplete(decision, argentumAmounts), onClick = {
                        submitArgentumDecision(ArgentumDecisions.distributionResponse(argentumAmounts))
                    }) { Text("CONFIRM") }
                    else -> Row {
                        if (ArgentumDecisions.canCancel(decision)) TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.cancelResponse()) }) { Text("CANCEL CHOICE") }
                        TextButton(onClick = { recordDiagnostic("Player requested refresh for unhandled ${kind}", DiagnosticCategory.UNSUPPORTED); argentumSocket.requestResync() }) { Text("REFRESH") }
                    }
                }
            },
            dismissButton = {
                if (kind == "YesNoDecision") TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", false)) }) { Text(decision.decisionDisplayText("noText", "NO")) }
                else if (kind == "BatchYesNoDecision") Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", false).put("applyToAll", false)) }) { Text("${decision.decisionDisplayText("noText", "NO")} · ONCE") }
                    TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", false).put("applyToAll", true)) }) { Text("${decision.decisionDisplayText("noText", "NO")} · ALL") }
                }
                else if (kind == "SelectCardsDecision" && decision.optInt("minSelections", 0) == 0) TextButton(onClick = {
                    submitArgentumDecision(JSONObject().put("type", "CardsSelectedResponse").put("selectedCards", JSONArray()))
                }) { Text(decision.decisionDisplayText("declineLabel", "SELECT NONE")) }
                else if (kind == "SelectManaSourcesDecision" && decision.optBoolean("canDecline")) TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.declineManaResponse()) }) { Text("DECLINE") }
                // Every decision can be set aside to look at the board; the status strip brings it back.
                if (kind !in setOf("YesNoDecision", "BatchYesNoDecision")) TextButton(onClick = { argentumDeferredDecisionId = decisionId }) { Text("VIEW TABLE") }
            }
        )
    }

    argentumAttackPlan?.let { plan ->
        ArgentumAttackBoard(plan, ::argentumPiece, busy = argentumAwaitingUpdate,
            opponentBlockers = argentumSnapshot?.let { snap -> opponentBlockers(snap.players, snap.viewingPlayer) }.orEmpty(),
            onConfirm = { action ->
                val epoch = ArgentumActions.epochFor(plan.legalAction, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    recordDiagnostic("Declared ${action.optJSONObject("attackers")?.length() ?: 0} attacker(s)", DiagnosticCategory.ACTION)
                    argentumAttackPlan = null
                } else message = "Could not send the attack declaration. Refresh and try again."
            },
            onDismiss = { argentumAttackPlan = null },
            )
    }
    argentumBlockPlan?.let { plan ->
        ArgentumBlockBoard(plan, ::argentumPiece, busy = argentumAwaitingUpdate,
            warning = argentumLastError?.takeIf { it.contains("block", ignoreCase = true) },
            onConfirm = { action ->
                val epoch = ArgentumActions.epochFor(plan.legalAction, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    argentumLastError = null
                    argentumBlockGuard.markSent()
                    recordDiagnostic("Declared ${action.optJSONObject("blockers")?.length() ?: 0} blocker(s)", DiagnosticCategory.ACTION)
                    // The next authoritative snapshot either confirms this declaration
                    // (hide the board and offer priority) or an INVALID_ACTION releases
                    // the latch and preserves this plan's selections for correction.
                } else message = "Could not send the block declaration. Refresh and try again."
            },
            onDismiss = { argentumBlockPlan = null })
    }
    argentumComposer?.let { plan ->
        ArgentumActionComposer(plan, ::argentumName, busy = argentumAwaitingUpdate || argentumXChecking != null,
            onConfirm = { picks, x, distribution, color, removal, crew ->
                val refs = argentumSnapshot?.targets.orEmpty()
                val action = plan.build(picks, x, distribution, color, removal, crew) { id -> refs[id]?.let(ArgentumActionPlan::chosenTarget) }
                val epoch = ArgentumActions.epochFor(plan.info, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    recordDiagnostic("Sent ${action.optString("type")} (${plan.description}) with ${plan.chosenTargets(picks).size} target(s)${x?.let { ", X=$it" }.orEmpty()}", DiagnosticCategory.ACTION)
                    argentumComposer = null; argentumXChecking = null
                } else message = "Could not send that to Argentum. Refresh and try again."
            },
            onDismiss = { argentumComposer = null; argentumXChecking = null; argentumStackUse = null })
    }
    argentumDeckReview?.let { review ->
        ArgentumDeckReviewDialog(review,
            onPlayWithout = {
                argentumDeckReview = null
                recordDiagnostic("Player chose to play on Argentum without ${review.unsupported.size} unsupported card(s)", DiagnosticCategory.UNSUPPORTED)
                startArgentumPractice(vsAi = review.vsAi, joinLobbyCode = review.joinCode, acceptSubstitutions = true)
            },
            onSandbox = {
                argentumDeckReview = null
                recordDiagnostic("Player chose the Arcana sandbox for a deck with ${review.unsupported.size} unsupported card(s)", DiagnosticCategory.UNSUPPORTED)
                resetGame(startImmediately = true)
                homeOpen = false
                showOverview = true
                message = "Arcana sandbox: Argentum is not running this game. Use the table tools to resolve cards yourself."
            },
            onEditDeck = { argentumDeckReview = null; selectedPlayer = localSeat; deckEditorOpen = true },
            onCancel = { argentumDeckReview = null })
    }
    if (diagnosticViewerOpen) DiagnosticLogDialog(diagnosticLog, diagnosticRevision,
        onExport = { selection -> diagnosticExportSelection = selection; exportDiagnosticLog() },
        onClear = { diagnosticLog.clear(); diagnosticRevision++; optionPrefs.edit().putString("diagnostic_events", "").apply(); recordDiagnostic("Diagnostic log cleared", DiagnosticCategory.APP) },
        onDismiss = { diagnosticViewerOpen = false })

    if (changelogOpen) ChangelogDialog(onDismiss = { changelogOpen = false })

    // The guide is layered over the real screen at the end of this composition.
    // Argentum games: the hand drawer always shows YOUR hand and opens from any table screen.
    val handSeat = if (argentumEnabled) localSeat.coerceIn(players.indices) else selectedPlayer.coerceIn(players.indices)
    val handTrayVisible = showHand && gameInProgress && (if (argentumEnabled) players.getOrNull(handSeat) != null else !showOverview && canControlSelectedPlayer)
    // While a combat board or another Argentum choice is open, the hand is for looking only.
    val handViewOnly = argentumEnabled && (argentumPendingDecision != null || argentumAttackPlan != null || argentumBlockPlan != null || argentumComposer != null)

    AnimatedVisibility(
        visible = handTrayVisible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showHand = false }
        ) {
            AnimatedVisibility(
                visible = handTrayVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                // Never shorter than the tray chrome (~172dp) plus one readable card row (~110dp),
                // which matters on short landscape screens.
                val effectiveHandFraction = handSheetHeightFraction.coerceAtLeast((282f / configuration.screenHeightDp.coerceAtLeast(1)).coerceIn(0.38f, 0.95f))
                Column(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(effectiveHandFraction)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Panel)
                        .navigationBarsPadding()
                        .onGloballyPositioned { handSheetBounds = it.boundsInRoot() }
                        .clickable { }
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(36.dp).tutorialTarget("hand-drawer-grip")
                            .pointerInput(configuration.screenHeightDp) {
                                var dragDistance = 0f
                                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
                                val dismissDistancePx = with(density) { 120.dp.toPx() }
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragDistance += amount
                                        handSheetHeightFraction = (handSheetHeightFraction - amount / screenHeightPx).coerceIn(0.38f, 0.95f)
                                    },
                                    onDragEnd = {
                                        optionPrefs.edit().putFloat("hand_sheet_height_fraction", handSheetHeightFraction).putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", handSheetHeightFraction).apply()
                                        if (dragDistance > dismissDistancePx && handSheetHeightFraction <= 0.40f) showHand = false
                                        dragDistance = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BottomSheetDefaults.DragHandle(color = Muted)
                    }
                    Text("DRAG UP TO EXPAND · DRAG DOWN TO SHRINK", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
                    HandSheet(
                        cards = players.getOrNull(handSeat.coerceIn(players.indices))?.hand.orEmpty(), isLandscape = isLandscape,
                        availableHeight = LocalConfiguration.current.screenHeightDp.dp * effectiveHandFraction,
                        stackCount = visibleStackCount,
                        highlightedCardId = highlightedHandCardId,
                        actionOrder = handActionOrderRaw.split('|').filter(String::isNotBlank),
                        handRevealed = players[handSeat].handRevealedToAll,
                        canAdvance = canControlSelectedPlayer && selectedPlayer == activePlayer && !players[activePlayer].isComputer,
                        canControl = canControlSelectedPlayer || argentumEnabled,
                        commanderCanCast = players[handSeat].commanderCard?.let { commander ->
                            val owner = players[handSeat]
                            (canControlSelectedPlayer || argentumEnabled) && !owner.isEliminated && !owner.library.any { it.id == commander.id } && !owner.hand.any { it.id == commander.id } && !owner.battlefield.any { it.id == commander.id } && !owner.graveyard.any { it.id == commander.id } && !owner.exile.any { it.id == commander.id } && !owner.revealed.any { it.id == commander.id } && stackActions.none { it.card?.id == commander.id }
                        } == true,
                        onToggleReveal = { revealed -> if (!argentumEnabled) {
                            updatePlayer(selectedPlayer) { it.copy(handRevealedToAll = revealed) }
                            logAction(selectedPlayer, if (revealed) "revealed their hand to everyone" else "hid their hand")
                        } },
                        onClose = { showHand = false },
                        onDraw = { if (!argentumEnabled) { drawCard(selectedPlayer); showHand = true } },
                        onNextStep = { if (argentumEnabled) argentumPrimary() else advanceStep() },
                        onPassTurn = { if (argentumEnabled) argentumPrimary() else passTurn() },
                        argentumMode = argentumEnabled,
                        argentumPrimaryLabel = argentumPrimaryLabel?.takeIf { !argentumAwaitingUpdate && passToMyTurnFrom == null },
                        onUndo = if (argentumEnabled && argentumUndoAvailable && !argentumAwaitingUpdate) ({ requestArgentumUndo() }) else null,
                        legalActionCount = ArgentumActions.visibleChoices(argentumSnapshot?.legalActions.orEmpty()).size,
                        arrangement = handArrangement,
                        cardScale = handCardScale,
                        playableIds = argentumPlayableIds,
                        canRespond = argentumCanRespond && respondGlow,
                        commanderAffordable = argentumEnabled && players.getOrNull(handSeat)?.commanderCard?.id?.let { it in argentumPlayableIds } == true,
                        onStack = { run { if (argentumEnabled) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else stackOpen = true } },
                        onCastCommander = {
                            val commander = players.getOrNull(handSeat)?.commanderCard
                            if (argentumEnabled && commander != null) openArgentumActions(commander)
                            else if (commander == null) message = "Set a commander from your deck or battlefield first."
                            else { paymentSourceZone = "Command Zone"; paymentCard = selectedPlayer to commander }
                        },
                        onCombat = { run { if (argentumEnabled) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else if (canControlSelectedPlayer && selectedPlayer == activePlayer) combatDialogOpen = true } },
                        onMoreAction = { action ->
                            if (argentumEnabled) when (action) {
                                "LOG" -> logOpen = true
                                "GRAVEYARD" -> zoneDialog = handSeat to "Graveyard"
                                "EXILE" -> zoneDialog = handSeat to "Exile"
                                "REVEALED" -> zoneDialog = handSeat to "Revealed"
                                else -> run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } }
                            }
                            else if (action in setOf("LOG", "CHAT") || canControlSelectedPlayer) when (action) {
                                "TOKEN" -> { tokenTarget = selectedPlayer; tokenPickerOpen = true }
                                "REVEAL" -> {
                                    val player = players[selectedPlayer]
                                    if (player.library.isNotEmpty()) {
                                        updatePlayer(selectedPlayer) { it.copy(library = it.library.drop(1), revealed = it.revealed + player.library.first()) }
                                        logAction(selectedPlayer, "revealed the top card")
                                    }
                                }
                                "SHUFFLE" -> shuffleConfirmPlayer = selectedPlayer
                                "LOG" -> logOpen = true
                                "TRACKERS" -> trackerPlayer = selectedPlayer
                                "SEARCH" -> librarySearchPlayer = selectedPlayer
                                "CONCEDE" -> concedePlayer = selectedPlayer
                                "CHAT" -> chatOpen = true
                                "GRAVEYARD" -> zoneDialog = selectedPlayer to "Graveyard"
                                "EXILE" -> zoneDialog = selectedPlayer to "Exile"
                                "REVEALED" -> zoneDialog = selectedPlayer to "Revealed"
                            }
                        },
                        onSelect = { card -> run { if (handViewOnly) cardZoom.hold(card).also { cardZoom.release(false) } else if (argentumEnabled) openArgentumActions(card) else handActionCard = card } },
                        onDragDrop = { card, point ->
                            if (argentumEnabled) {
                                // A drag is only a visual gesture in Arcana. Ask Argentum
                                // for the card's legal actions instead of simulating a cast.
                                openArgentumActions(card)
                            } else {
                                val validTarget = battlefieldBounds?.contains(point) == true && handSheetBounds?.contains(point) != true
                                if (validTarget && canControlSelectedPlayer) {
                                    showHand = false
                                    performHandAction(card, "PLAY")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (deckEditorOpen) DeckEditorDialog(
        player = players[selectedPlayer],
        highlightedCardIds = importReviewCardIds,
        onRename = { card, name -> syncSavedDeckSwap(selectedPlayer, card.name, name); editDeckCard(selectedPlayer, card.id, name) },
        onRemove = { card -> editDeckCard(selectedPlayer, card.id, null, remove = true) },
        onAdd = { result -> updatePlayer(selectedPlayer) { p -> p.copy(library = p.library + makeCard(result.name, result.type, result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords)) } },
        onReplace = { card, result ->
            updatePlayer(selectedPlayer) { p ->
                fun replace(cards: List<GameCard>) = cards.map { current ->
                    if (current.id == card.id) current.copy(name = result.name, type = result.type, imageUrl = result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, importIssue = null, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords) else current
                }
                val commander = p.commanderCard?.let { current ->
                    if (current.id == card.id) current.copy(name = result.name, type = result.type, imageUrl = result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, importIssue = null, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords) else current
                }
                p.copy(library = replace(p.library), hand = replace(p.hand), commanderCard = commander, commanderName = commander?.name ?: p.commanderName)
            }
            importReviewCardIds = importReviewCardIds - card.id
            syncSavedDeckSwap(selectedPlayer, card.name, result.name)
        },
        onDismiss = { deckEditorOpen = false },
        loadSupportCatalog = { loadSupportCatalog() }
    )
    if (combatDialogOpen && !showOverview) CombatDialog(
        players = players, attackerIndex = activePlayer,
        onDismiss = { combatDialogOpen = false },
        onLog = { logAction(activePlayer, it) },
        onCombatDamage = { sourceCard, defender, amount ->
            if (amount > 0 && defender in players.indices) {
                val source = players[activePlayer]
                val isCommander = source.commanderCard?.id == sourceCard.id
                val sourceId = source.id
                val newCommanderDamage = (players[defender].commanderDamageReceived[sourceId] ?: 0) + if (isCommander) amount else 0
                val newLife = (players[defender].life - amount).coerceAtLeast(0)
                updatePlayer(defender) { p -> p.copy(
                    life = newLife,
                    commanderDamageReceived = if (isCommander) p.commanderDamageReceived + (sourceId to newCommanderDamage) else p.commanderDamageReceived
                ) }
                logAction(activePlayer, "dealt $amount combat damage to ${players.getOrNull(defender)?.name ?: "player"}")
                if (newLife == 0) eliminatePlayer(defender, "Life total reached 0")
                if (isCommander && newCommanderDamage >= 21) eliminatePlayer(defender, "21 commander damage from ${source.name}")
            }
        }
    )

            }
    WifiDialogs()
    FlashMessage()
}
