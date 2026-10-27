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

/** A turn change can follow the active player only while the user is already in Player view.
 * Selecting Table view opts out of automatic view changes until the user opens a board.
 */
internal fun shouldFollowActivePlayerToBoard(followEnabled: Boolean, showingTable: Boolean, active: Int, playerCount: Int): Boolean =
    followEnabled && !showingTable && active in 0 until playerCount

/**
 * A 2–4 seat lobby is already a live connection, even before Argentum starts a game.
 * The legacy room fields are null for both ArgentumLanMultiServer and ArgentumCloudMultiHost.
 */
internal fun shouldKeepMultiplayerConnectionGuard(
    legacyHost: Boolean,
    legacyClient: Boolean,
    cloudClient: Boolean,
    multiTransport: Boolean,
    argentumEnabled: Boolean,
): Boolean = legacyHost || legacyClient || cloudClient || multiTransport || argentumEnabled

/** All of the main screen's effects (LaunchedEffect / SideEffect / DisposableEffect). */
@Composable
internal fun TableController.TableEffects(darkMode: Boolean) {
    SideEffect { cardZoom.enabled = argentumEnabled; if (!argentumEnabled) cardZoom.dismiss() }
    LaunchedEffect(showOverview, selectedPlayer, players.size, reducedSwipeMotion) {
        val targetPage = if (showOverview) 0 else (selectedPlayer + 1).coerceIn(0, players.size)
        if (viewPagerState.currentPage != targetPage) {
            if (reducedSwipeMotion) viewPagerState.scrollToPage(targetPage)
            else viewPagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(viewPagerState, players.size) {
        snapshotFlow { viewPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                showOverview = page == 0
                if (page in 1..players.size) selectedPlayer = page - 1
                if (page == 0 && !argentumEnabled) showHand = false
            }
    }
    LaunchedEffect(lobbyOpen, lobbyInternetMode) {
        if (lobbyOpen && lobbyInternetMode && (lobbyCloudEndpoint.isBlank() || isLegacyExampleCloudEndpoint(lobbyCloudEndpoint))) {
            lobbyCloudEndpoint = DEFAULT_CLOUD_ROOM_ENDPOINT
            optionPrefs.edit().putString("cloud_room_endpoint", DEFAULT_CLOUD_ROOM_ENDPOINT).apply()
        }
    }
    // "Always yes for this card this turn": answer the same prompt from the same card automatically.
    LaunchedEffect(argentumPendingDecision?.optString("id"), argentumAwaitingUpdate) {
        val decision = argentumPendingDecision ?: return@LaunchedEffect
        if (argentumAwaitingUpdate || decision.optString("type") != "YesNoDecision") return@LaunchedEffect
        val turn = argentumSnapshot?.turnNumber ?: return@LaunchedEffect
        if (argentumAutoYes[autoYesKey(decision)] != turn) return@LaunchedEffect
        delay(250)
        recordDiagnostic("Auto-answered yes: ${decision.optString("prompt")}", DiagnosticCategory.ACTION)
        submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true))
    }
    // "Use 5 / Use all": activate the same ability on one copy after another, from each new state.
    LaunchedEffect(argentumStackUse, argentumSnapshot, argentumAwaitingUpdate, argentumPendingDecision, argentumComposer) {
        val use = argentumStackUse ?: return@LaunchedEffect
        if (!use.started || argentumAwaitingUpdate || argentumComposer != null) return@LaunchedEffect
        if (argentumPendingDecision != null && argentumPendingDecision?.optString("type") != "PlayCardDecision") { argentumStackUse = null; return@LaunchedEffect }
        val snapshot = argentumSnapshot ?: return@LaunchedEffect
        delay(120)
        val action = StackUses.next(use, snapshot.legalActions) { id -> argentumPiece(id).name }
        val epoch = snapshot.interactionEpoch
        if (action == null || epoch == null || use.remaining <= 0) { argentumStackUse = null; return@LaunchedEffect }
        argentumStackUse = use.copy(remaining = use.remaining - 1)
        if (!submitArgentumAction(action, epoch)) argentumStackUse = null
    }
    // "Pass to my turn": press Argentum's own PassPriority until your next turn or until you're needed.
    LaunchedEffect(passToMyTurnFrom, argentumSnapshot, argentumPendingDecision, argentumAwaitingUpdate, argentumStatus) {
        val start = passToMyTurnFrom ?: return@LaunchedEffect
        val snapshot = argentumSnapshot ?: return@LaunchedEffect
        if (!argentumEnabled || argentumStatus != ArgentumConnectionStatus.CONNECTED) { passToMyTurnFrom = null; return@LaunchedEffect }
        delay(300) // let a burst of updates settle before answering
        val outcome = ArgentumPassing.next(
            startTurn = start, turnNumber = snapshot.turnNumber, activePlayer = snapshot.activePlayer, localSeat = localSeat,
            priorityPlayer = snapshot.priorityPlayer,
            legalActionTypes = snapshot.legalActions.mapNotNull { it.optJSONObject("action")?.optString("type") }.toSet(),
            hasPendingDecision = argentumPendingDecision != null,
            opponentStackItem = snapshot.stack.firstOrNull { !it.isYours }?.name,
            gameOver = gameEndedOnline || snapshot.gameOver, awaitingUpdate = argentumAwaitingUpdate,
        )
        when (outcome.step) {
            ArgentumPassing.Step.PASS -> sendArgentumPriority()
            ArgentumPassing.Step.WAIT -> Unit
            ArgentumPassing.Step.REACHED_YOUR_TURN -> {
                passToMyTurnFrom = null
                recordDiagnostic("Pass to my turn: reached your turn ${snapshot.turnNumber}", DiagnosticCategory.ACTION)
                android.widget.Toast.makeText(context, "Your turn.", android.widget.Toast.LENGTH_SHORT).show()
            }
            ArgentumPassing.Step.INTERRUPTED -> {
                passToMyTurnFrom = null
                recordDiagnostic("Pass to my turn: stopped (${outcome.reason})", DiagnosticCategory.ACTION)
                outcome.reason?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
    // Following turns navigates only between player boards. Table view is sticky.
    LaunchedEffect(selectedPlayer, localSeat) { if (selectedPlayer != localSeat) lastOpponentViewed = selectedPlayer }
    LaunchedEffect(argentumTurnKey) {
        val active = argentumTurnKey?.second ?: return@LaunchedEffect
        if (!shouldFollowActivePlayerToBoard(followActivePlayer, showOverview, active, players.size)) return@LaunchedEffect
        selectedPlayer = active
    }

    // New multiplayer lobbies use wifiServer (LAN or directed cloud), not the legacy roomHost.
    // Observe it as a key so the connection guard starts before a game is launched.
    LaunchedEffect(roomHost, roomClient, cloudRoomClient, wifiServer, argentumEnabled) {
        if (shouldKeepMultiplayerConnectionGuard(
                roomHost != null, roomClient != null, cloudRoomClient != null,
                wifiServer != null, argentumEnabled
            )
        ) LanConnectionService.start(context)
        else LanConnectionService.stop(context)
    }
    LaunchedEffect(argentumEnabled) {
        if (argentumEnabled && argentumStatus == ArgentumConnectionStatus.DISCONNECTED) argentumSocket.connect(lobbyPlayerName)
        if (!argentumEnabled) argentumSocket.close()
    }
    LaunchedEffect(argentumStatus, argentumEnabled) {
        if (argentumEnabled) recordDiagnostic("Argentum connection status: ${argentumStatus.name.lowercase()}")
    }
    LaunchedEffect(argentumQueue) {
        for (incoming in argentumQueue) {
            // A late update from a game you already left must not overwrite your table and deck.
            if (!argentumEnabled && incoming.optString("type") in setOf("stateUpdate", "stateDeltaUpdate", "gameOver", "mulliganDecision", "chooseBottomCards")) continue
            runCatching { handleArgentumMessage(incoming) }.onFailure { error ->
                recordDiagnostic("Could not apply Argentum ${incoming.optString("type")} message: ${error.javaClass.simpleName}", DiagnosticCategory.ERROR)
                argentumAwaitingUpdate = false
                argentumSocket.requestResync()
            }
        }
    }
    LaunchedEffect(argentumYourMove) {
        if (!argentumYourMove) return@LaunchedEffect
        delay(350) // ignore momentary priority during Argentum's own auto-passing
        // Playing a friend: when they're waiting on you during their turn, make it obvious.
        if (wifiRole != null && argentumCanRespond && appInForeground) { buzz(250); flashMessage = FlashText("Your move", 0xFF8BE3B4) }
        if (appInForeground) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        else if (backgroundAlertsEnabled) postGameAlert(context, "Your move", argentumPendingDecision?.optString("prompt")?.takeIf { it.isNotBlank() } ?: "Argentum is waiting for you.")
    }
    LaunchedEffect(argentumTurnKey) {
        val (turn, active) = argentumTurnKey ?: return@LaunchedEffect
        val seat = players.getOrNull(active) ?: return@LaunchedEffect
        val isYou = active == localSeat
        turnBanner = TurnBannerState(TurnColors.label(isYou, seat.name, seat.isComputer), isYou, turn)
        if (isYou && aiRecaps.isNotEmpty()) {
            if (showAiRecap) recapText = formatTurnRecaps(aiRecaps.filter { it.turn < turn }, players.map { it.name }).takeIf { it.isNotBlank() }
            aiRecaps = emptyList()
        }
        if (isYou && showAiRecap && !recapText.isNullOrBlank()) {
            recordDiagnostic("Recap: ${recapText}", DiagnosticCategory.ENGINE)
        }
        if (isYou && buzzOnTurn && appInForeground) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        delay(if (isYou) 1400 else 900)
        turnBanner = null
    }
    LaunchedEffect(argentumTurnKey) {
        val (_, active) = argentumTurnKey ?: return@LaunchedEffect
        // A lesson decides when the hand opens; opening it here would cover what the lesson points at.
        if (active != localSeat || !autoOpenHand || !argentumEnabled || tutorialChapterId != null) return@LaunchedEffect
        delay(350)
        if (argentumPendingDecision == null && argentumAttackPlan == null && argentumBlockPlan == null &&
            argentumActionChoices.isEmpty() && !gameEndedOnline) showHand = true
    }
    // One "Notice time" setting (Adjust UI) for banners, the played-card pop-up and the recap (twice as long: more to read).
    LaunchedEffect(playBanner?.key, playedPopupSeconds) {
        val banner = playBanner ?: return@LaunchedEffect
        delay(readableNotificationDurationMs("${banner.name}: ${banner.detail}", playedPopupSeconds))
        if (playBanner?.key == banner.key) playBanner = null
    }
    LaunchedEffect(recapText, playedPopupSeconds) {
        val recap = recapText ?: return@LaunchedEffect
        delay(readableNotificationDurationMs(recap, playedPopupSeconds * 2f))
        if (recapText == recap) recapText = null
    }
    LaunchedEffect(argentumLastError) {
        // Socket-level problems (unreadable messages, failed connects) reach the searchable log too.
        argentumLastError?.let { recordDiagnostic("Argentum problem: $it", DiagnosticCategory.ERROR) }
    }
    DisposableEffect(argentumSocket) {
        onDispose { argentumSocket.close() }
    }
    LaunchedEffect(Unit) {
        // After a relaunch or recreation, surface the Resume / End choice instead of a stale table.
        if (argentumResumeAvailable && !argentumEnabled) {
            homeOpen = true
            recordDiagnostic("Previous Argentum game may still be active; offering Resume (no automatic reconnect)", DiagnosticCategory.CONNECTION)
        }
    }
    LaunchedEffect(argentumAwaitingUpdate) {
        if (argentumAwaitingUpdate) {
            // Offline there's no network to lose: the engine on this phone is just thinking. A resync
            // would queue behind it and make it slower, and unlocking lets stale taps through, so wait
            // much longer before giving up.
            argentumSlow = false
            if (argentumOffline) { delay(10_000); if (argentumAwaitingUpdate) argentumSlow = true; delay(80_000) } else delay(8_000)
            argentumSlow = false
            if (argentumAwaitingUpdate) {
                recordDiagnostic("Argentum did not confirm an action within ${if (argentumOffline) 90 else 8} s; requested a full state refresh", DiagnosticCategory.CONNECTION)
                argentumAwaitingUpdate = false
                argentumSocket.requestResync()
                message = "Argentum has not confirmed that action yet. Arcana refreshed the table state."
            }
        }
    }

    LaunchedEffect(Unit) {
        recordDiagnostic("Application opened")
        while (true) {
            delay(1_000)
            if (roomClient == null && cloudRoomClient == null) {
                if (gameTimerRunning) elapsedGameSeconds++
                if (turnTimerRunning) elapsedTurnSeconds++
            }
        }
    }

    LaunchedEffect(players, onlineMode) { if (!onlineMode) savePlayers(context, players) }
    LaunchedEffect(players, activePlayer, turnNumber, phaseIndex, gameTurnOrder, lobbyReadyPlayers, lobbyTurnOrder, gameLog, chatMessages, gameStartedOnline, endProposalBy, endVotes, gameEndedOnline, stackActions, priorityPlayer, priorityPassed, triggerPrompts, roomHost, roomClient, cloudRoomClient, roomConnected, onlineStateReady) {
        delay(180)
        publishOnlineSnapshot()
    }
    LaunchedEffect(elapsedGameSeconds, elapsedTurnSeconds, roomHost, roomClient, cloudRoomClient, roomConnected, onlineStateReady) {
        if (elapsedGameSeconds % 5L == 0L || elapsedTurnSeconds % 5L == 0L) publishOnlineSnapshot()
    }
    // Capture the instances that this effect owns. Reading the delegated Compose
    // state values inside onDispose would see the newly assigned room/client and
    // could close it immediately when the keys change during recomposition.
    val hostOwnedByEffect = roomHost
    val clientOwnedByEffect = roomClient
    val cloudClientOwnedByEffect = cloudRoomClient
    DisposableEffect(hostOwnedByEffect, clientOwnedByEffect, cloudClientOwnedByEffect) {
        onDispose {
            hostOwnedByEffect?.close()
            clientOwnedByEffect?.close()
            cloudClientOwnedByEffect?.close()
            LanConnectionService.stop(context)
        }
    }
    LaunchedEffect(players) { nextId = maxOf(nextId, (players.flatMap { it.library + it.hand + it.battlefield + it.graveyard + it.exile + it.revealed }.maxOfOrNull { it.id } ?: 0) + 1) }
    LaunchedEffect(stackActions, localSeat, onlineMode) {
        targetSelectionStackItemId = stackActions.firstOrNull {
            !it.targetsConfirmed && it.targetCount != null &&
                (!onlineMode || it.controllerId == players.getOrNull(localSeat)?.id)
        }?.id
    }
    LaunchedEffect(chatMessages.size, chatOpen) {
        if (chatMessages.size > observedChatCount && !chatOpen) unreadChatCount += chatMessages.size - observedChatCount
        observedChatCount = chatMessages.size
        if (chatOpen) unreadChatCount = 0
    }
    // A lesson step about your own board keeps your player view while that step is showing.
    LaunchedEffect(showOverview, tutorialHoldsPlayerView, tutorialStepIndex) {
        if (showOverview && tutorialChapterId != null && tutorialHoldsPlayerView == tutorialStepIndex) {
            delay(50)
            if (showOverview && tutorialHoldsPlayerView == tutorialStepIndex) { showOverview = false; selectedPlayer = localSeat }
        }
    }
    LaunchedEffect(gameStartedOnline) {
        if (gameStartedOnline) {
            homeOpen = false; showOverview = true; showHand = false; lobbyOpen = false
            // A lesson that just started its practice game puts back the view its step needs.
            if (tutorialChapterId != null) tutorialRouteScreen()
        }
    }
    LaunchedEffect(gameEndedOnline) {
        if (gameEndedOnline) { gameTimerRunning = false; turnTimerRunning = false; showOverview = true; showHand = false }
    }
    LaunchedEffect(pendingStackOpen, triggerPrompts.size, paymentCard, manaPoolOpen, targetSelectionStackItemId, creatureTypeChoicePrompt) {
        if (pendingStackOpen && triggerPrompts.isEmpty() && paymentCard == null && !manaPoolOpen && targetSelectionStackItemId == null && creatureTypeChoicePrompt == null) {
            delay(180)
            stackOpen = true
            pendingStackOpen = false
        }
    }
    LaunchedEffect(stackActions.isNotEmpty(), triggerPrompts.size, paymentCard, manaPoolOpen, manaChoicePrompt, creatureTypeChoicePrompt, pendingStackOpen) {
        if (stackActions.isNotEmpty()) {
            stackCycleActive = true
            return@LaunchedEffect
        }
        if (!stackCycleActive || triggerPrompts.isNotEmpty() || paymentCard != null || manaPoolOpen || manaChoicePrompt != null || creatureTypeChoicePrompt != null || pendingStackOpen) return@LaunchedEffect
        delay(250)
        if (stackActions.isEmpty() && triggerPrompts.isEmpty() && paymentCard == null && !manaPoolOpen && manaChoicePrompt == null && creatureTypeChoicePrompt == null && !pendingStackOpen && !tutorialPracticeActive) {
            stackOpen = false
            stackCycleActive = false
        }
    }
    SideEffect {
        val systemBar = if (darkMode) android.graphics.Color.rgb(16, 22, 21) else android.graphics.Color.rgb(242, 245, 241)
        activity?.window?.statusBarColor = systemBar
        activity?.window?.navigationBarColor = systemBar
    }

    LaunchedEffect(stackActions, priorityPlayer, priorityPassed, players, gameGeneration, onlineMode, argentumEnabled) {
        val current = players.getOrNull(priorityPlayer)
        if (!argentumEnabled && (!onlineMode || localSeat == 0) && stackActions.isNotEmpty() && current?.isComputer == true && !current.isEliminated) {
            delay(350)
            val passed = priorityPassed + current.id
            priorityPassed = passed
            val eligibleIds = players.filterNot { it.isEliminated }.map { it.id }.toSet()
            if (eligibleIds.isNotEmpty() && eligibleIds.all { it in passed }) {
                resolveTopOfStack()
            } else {
                priorityPlayer = nextTurnIndex(priorityPlayer, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
            }
        }
    }

    LaunchedEffect(activePlayer, turnNumber, players.getOrNull(activePlayer)?.deckLoaded, gameGeneration, argentumEnabled) {
        if (!argentumEnabled && (!onlineMode || localSeat == 0) && !aiRoutineRunning && players.getOrNull(activePlayer)?.let { it.isComputer && it.deckLoaded } == true) {
            runComputerTurn(activePlayer)
        }
    }
}
