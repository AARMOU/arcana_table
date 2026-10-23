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

/** Online rooms (LAN and cloud): joining, hosting, syncing snapshots and end-of-game votes. */

internal fun TableController.leaveOnlineRoom() {
    val ownPlayer = players.getOrNull(localSeat)?.copy(isOnline = false)
    roomClient?.close(); roomClient = null
    cloudRoomClient?.leave(); cloudRoomClient = null
    roomHost?.close(); roomHost = null
    val backup = localPlayersBackup
    val restoredOwn = when {
        ownPlayer == null -> backup?.firstOrNull()
        ownPlayer.deckLoaded || backup?.firstOrNull()?.deckLoaded != true -> ownPlayer
        else -> backup.first()
    }
    players = if (restoredOwn != null && !backup.isNullOrEmpty()) backup.mapIndexed { index, player -> if (index == 0) restoredOwn else player.copy(isOnline = false) }
        else if (restoredOwn != null) listOf(restoredOwn) else backup.orEmpty()
    localPlayersBackup = null
    roomConnected = false; lobbyCreated = false; onlineStatus = "Offline"; localSeat = 0
    onlineStateReady = false; gameStartedOnline = false; pendingReadyState = null
    selectedPlayer = 0
    lobbyOpen = false; homeOpen = true; showOverview = true
}

internal fun TableController.buildOnlineSnapshot(): String {
    val sharedOrder = if (gameStartedOnline) gameTurnOrder else lobbyTurnOrder.orEmpty()
    return OnlineGameState(players, activePlayer, turnNumber, phaseIndex, elapsedGameSeconds, elapsedTurnSeconds, sharedOrder, lobbyReadyPlayers, gameLog.takeLast(250), chatMessages.takeLast(200).map { OnlineChatMessage(it.author, it.text, it.timestamp) }, gameStartedOnline, endProposalBy, endVotes, gameEndedOnline, stackActions, priorityPlayer, priorityPassed, triggerPrompts).toJson()
}

internal fun TableController.publishOnlineSnapshot() {
    if ((roomHost != null || roomClient != null || cloudRoomClient != null) && roomConnected && onlineStateReady) {
        val snapshot = buildOnlineSnapshot()
        if (snapshot != lastRemoteSnapshot) {
            roomHost?.publish(snapshot)
            roomClient?.publish(snapshot)
            cloudRoomClient?.publish(snapshot)
        }
    }
}

internal fun TableController.removeLobbyPlayer(index: Int) {
    if (!lobbyCreated || lobbyJoinMode || index <= 0 || index !in players.indices) return
    roomHost?.removeSeat(index)
    val removedName = players[index].name
    players = players.filterIndexed { playerIndex, _ -> playerIndex != index }
    fun remap(playerIndex: Int): Int = when {
        playerIndex == index -> 0
        playerIndex > index -> playerIndex - 1
        else -> playerIndex
    }
    selectedPlayer = remap(selectedPlayer).coerceIn(players.indices)
    activePlayer = remap(activePlayer).coerceIn(players.indices)
    priorityPlayer = remap(priorityPlayer).coerceIn(players.indices)
    gameTurnOrder = gameTurnOrder.filter { it != index }.map { if (it > index) it - 1 else it }
    lobbyReadyPlayers = lobbyReadyPlayers.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
    lobbyTurnOrder = lobbyTurnOrder?.filter { it != index }?.map { if (it > index) it - 1 else it }
    if (pendingAiPlayerIndex == index) pendingAiPlayerIndex = -1 else if (pendingAiPlayerIndex > index) pendingAiPlayerIndex--
    if (endProposalBy == index) { endProposalBy = -1; endVotes = emptyMap() }
    else if (endProposalBy > index) endProposalBy--
    endVotes = endVotes.filterKeys { it != index }.mapKeys { (seat, _) -> if (seat > index) seat - 1 else seat }
    triggerPrompts = triggerPrompts.filter { it.controllerIndex != index && it.eventPlayerIndex != index }.map {
        it.copy(controllerIndex = remap(it.controllerIndex), eventPlayerIndex = remap(it.eventPlayerIndex))
    }
    gameGeneration++
    lobbyNotice = "$removedName was removed from the lobby."
}

internal fun TableController.applyOnlineSnapshot(source: String) {
    val state = parseOnlineGameState(source) ?: return
    inboundSnapshotSequence += 1
    val firstSnapshotForConnection = !onlineStateReady
    val wasGameStarted = gameStartedOnline
    val oldActivePlayer = activePlayer
    val oldPriorityPlayer = priorityPlayer
    val oldStackIds = stackActions.map { it.id }.toSet()
    val oldGameLog = gameLog
    val previousReady = lobbyReadyPlayers
    val previousPlayers = players
    lastRemoteSnapshot = state.toJson()
    val receivedPlayers = state.players.mapIndexed { index, player ->
        if (roomConnected && index == localSeat) player.copy(isOnline = true) else player
    }.toMutableList()
    while (receivedPlayers.size <= localSeat && receivedPlayers.size < 4) receivedPlayers += TablePlayer(name = if (receivedPlayers.size == localSeat) lobbyPlayerName else "PLAYER ${receivedPlayers.size + 1}")
    // This device owns its assigned seat. Keep its current player data while
    // connected so an older server snapshot cannot undo a local card move
    // that is still waiting for its outbound snapshot to be published.
    // The first snapshot after connecting remains authoritative so a
    // reconnect can restore the room's saved state.
    if (!firstSnapshotForConnection) {
        previousPlayers.getOrNull(localSeat)?.let { localPlayer ->
            if (localSeat in receivedPlayers.indices) {
                receivedPlayers[localSeat] = localPlayer.copy(isOnline = true)
            }
        }
    }
    players = receivedPlayers
    logZoneSnapshotChange(players, "online snapshot #$inboundSnapshotSequence")
    receivedPlayers.forEachIndexed { index, player ->
        val oldLoaded = previousPlayers.getOrNull(index)?.deckLoaded
        if (oldLoaded != null && oldLoaded != player.deckLoaded) recordDiagnostic("Lobby deck status updated: seat ${index + 1}, loaded=${player.deckLoaded}")
        val oldOnline = previousPlayers.getOrNull(index)?.isOnline
        if (!firstSnapshotForConnection && index != localSeat && oldOnline != null && oldOnline != player.isOnline) {
            val event = if (player.isOnline) "${player.name} reconnected" else "${player.name} lost connection"
            recordDiagnostic("Player connection changed: seat ${index + 1} ${if (player.isOnline) "online" else "offline"}")
            if (lobbyOpen) lobbyNotice = "$event."
            if (message == null) message = "$event."
            if (backgroundAlertsEnabled && !appInForeground) {
                postGameAlert(context, if (player.isOnline) "Player reconnected" else "Player disconnected", event)
            }
        }
    }
    if (localSeat in players.indices) players = players.mapIndexed { index, player -> if (index == localSeat) player.copy(isOnline = true) else player }
    activePlayer = state.activePlayer.coerceIn(players.indices)
    turnNumber = state.turnNumber
    phaseIndex = state.phaseIndex.coerceAtMost(phaseSteps.lastIndex)
    elapsedGameSeconds = state.elapsedGameSeconds
    elapsedTurnSeconds = state.elapsedTurnSeconds
    gameTurnOrder = state.turnOrder
    lobbyTurnOrder = state.turnOrder.takeIf { it.isNotEmpty() && !state.gameStarted }
    lobbyReadyPlayers = state.readyPlayers
    ((previousReady union state.readyPlayers) - (previousReady intersect state.readyPlayers)).sorted().forEach { seat ->
        recordDiagnostic("Lobby readiness updated: seat ${seat + 1}, ready=${seat in state.readyPlayers}")
    }
    if (lobbyOpen && previousReady != state.readyPlayers) {
        val becameReady = (state.readyPlayers - previousReady).firstOrNull()
        val becameNotReady = (previousReady - state.readyPlayers).firstOrNull()
        val changedSeat = becameReady ?: becameNotReady
        if (changedSeat != null) {
            val name = state.players.getOrNull(changedSeat)?.name ?: "A player"
            lobbyNotice = if (becameReady != null) "$name is ready to start." else "$name is no longer ready."
        }
    }
    gameLog = state.gameLog
    if (backgroundAlertsEnabled && !appInForeground && wasGameStarted && state.gameStarted) {
        if (state.activePlayer == localSeat && oldActivePlayer != localSeat) postGameAlert(context, "Your turn", "It is your turn in Arcana Table.")
        val oldTail = oldGameLog.lastOrNull()
        val additions = if (oldTail == null) emptyList() else state.gameLog.dropWhile { it != oldTail }.drop(1)
        additions.takeLast(4).filterNot { it.contains("${players.getOrNull(localSeat)?.name.orEmpty()} ") }.forEach { entry ->
            postGameAlert(context, "Game play", entry.substringAfter("· ", entry))
        }
    }
    chatMessages = state.chat.map { ChatMessage(it.author, it.text, it.timestamp) }
    gameStartedOnline = state.gameStarted
    endProposalBy = state.endProposalBy
    endVotes = state.endVotes
    gameEndedOnline = state.gameEnded
    stackActions = state.stackActions
    targetSelectionStackItemId = state.stackActions.firstOrNull { !it.targetsConfirmed && it.targetCount != null && it.controllerId == players.getOrNull(localSeat)?.id }?.id
    triggerPrompts = state.triggerPrompts
    priorityPlayer = state.priorityPlayer.coerceIn(players.indices)
    priorityPassed = state.priorityPassed
    val stackItemAdded = state.stackActions.any { it.id !in oldStackIds }
    val priorityArrivedForLocalPlayer = wasGameStarted && oldPriorityPlayer != priorityPlayer && priorityPlayer == localSeat
    val joiningWithOpenStack = firstSnapshotForConnection && state.gameStarted && state.stackActions.isNotEmpty()
    if (state.gameStarted && state.stackActions.isNotEmpty() &&
        (stackItemAdded || priorityArrivedForLocalPlayer || joiningWithOpenStack)
    ) {
        pendingStackOpen = true
        if (backgroundAlertsEnabled && !appInForeground) {
            val title = if (priorityArrivedForLocalPlayer) "Your priority" else "Stack update"
            val detail = if (priorityArrivedForLocalPlayer) "It is your turn to respond on the stack." else "A spell or ability was added to the stack."
            postGameAlert(context, title, detail)
        }
        recordDiagnostic(if (priorityArrivedForLocalPlayer) "Stack priority arrived at local seat ${localSeat + 1}" else "Shared stack update received")
    }
    onlineStateReady = true
    // Server snapshots arrive for every remote action (and periodically for
    // timers). Preserve the local player's current view and open hand; only
    // select the local seat when first syncing or when the game starts.
    if (firstSnapshotForConnection || (!wasGameStarted && state.gameStarted)) {
        selectedPlayer = localSeat.coerceIn(players.indices)
        showOverview = true
        showHand = false
    } else {
        selectedPlayer = selectedPlayer.coerceIn(players.indices)
    }
}

internal fun TableController.makeHostTransferState(source: String, targetSeat: Int): String? {
    val state = parseOnlineGameState(source) ?: return null
    if (state.players.size < 2 || targetSeat !in 1 until state.players.size) return null
    fun shift(seat: Int): Int = when {
        seat == 0 || seat < 0 -> -1
        seat == targetSeat -> 0
        seat < targetSeat -> seat
        else -> seat - 1
    }
    val shiftedPlayers = listOf(state.players[targetSeat]) + state.players.indices
        .filter { it != 0 && it != targetSeat }.map { state.players[it] }
    val shiftedProposal = shift(state.endProposalBy)
    val shifted = state.copy(
        players = shiftedPlayers,
        activePlayer = shift(state.activePlayer).coerceAtLeast(0).coerceIn(shiftedPlayers.indices),
        priorityPlayer = shift(state.priorityPlayer).coerceAtLeast(0).coerceIn(shiftedPlayers.indices),
        turnOrder = state.turnOrder.map(::shift).filter { it >= 0 },
        readyPlayers = state.readyPlayers.map(::shift).filter { it >= 0 }.toSet(),
        endProposalBy = shiftedProposal,
        endVotes = state.endVotes.mapNotNull { (seat, vote) -> shift(seat).takeIf { it >= 0 }?.let { it to vote } }.toMap(),
        priorityPassed = state.priorityPassed.intersect(shiftedPlayers.map { it.id }.toSet()),
        triggerPrompts = state.triggerPrompts.mapNotNull { prompt ->
            val controller = shift(prompt.controllerIndex)
            val eventPlayer = shift(prompt.eventPlayerIndex)
            if (controller < 0 || eventPlayer < 0) null else prompt.copy(controllerIndex = controller, eventPlayerIndex = eventPlayer)
        }
    )
    return shifted.toJson()
}

internal fun TableController.startTransferredHost(source: String, targetSeat: Int, predecessor: LanRoomClient) {
    val shiftedSource = makeHostTransferState(source, targetSeat)
    if (shiftedSource == null) {
        lobbyNotice = "Host transfer needs another connected player. The room is still hosted here."
        return
    }
    val shifted = parseOnlineGameState(shiftedSource) ?: return
    roomHost?.close()
    lobbyCreated = false
    localSeat = 0
    roomConnected = true
    onlineStateReady = false
    applyOnlineSnapshot(shiftedSource)
    lobbyNotice = "Taking over the room…"
    onlineStatus = "Starting new room host"
    fun revertFailedTransfer(message: String) {
        roomHost?.close()
        roomHost = null
        lobbyCreated = false
        localSeat = targetSeat
        roomConnected = true
        onlineStateReady = true
        applyOnlineSnapshot(source)
        onlineStatus = "Connected to host"
        lobbyNotice = message
        recordDiagnostic("Host transfer could not start; returned to the original room")
    }
    val host = LanRoomHost(
        roomCode = lobbyRoomCode,
        password = lobbyPassword,
        onStarted = { address -> scope.launch(Dispatchers.Main) {
            lobbyHostAddress = address
            onlineStatus = if (address.isBlank()) "New host address unavailable" else "Room host transferred"
            onlineStateReady = true
            if (address.isBlank() || !predecessor.requestHostTransfer(address)) {
                revertFailedTransfer("Could not start the new room host. You are still connected to the original host.")
            } else {
                lobbyNotice = "Room transferred. Waiting for players to reconnect…"
                recordDiagnostic("Successor started the new room host")
            }
        } },
        onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
        onPeerJoined = { seat, name -> scope.launch(Dispatchers.Main) {
            if (players.size <= seat) players = players + TablePlayer(name = name, isOnline = true)
            else players = players.mapIndexed { index, player -> if (index == seat) player.copy(name = name, isOnline = true) else player }
            recordDiagnostic("Player reconnected after host transfer: seat ${seat + 1}")
        } },
        onPeerLeft = { seat -> scope.launch(Dispatchers.Main) {
            if (seat in players.indices && seat > 0) removeLobbyPlayer(seat)
        } },
        onEvent = { event -> scope.launch(Dispatchers.Main) { recordDiagnostic(event) } },
        onError = { error -> scope.launch(Dispatchers.Main) {
            revertFailedTransfer("New host could not start the room: $error. You are still connected to the original host.")
            recordDiagnostic("Transferred room host error: $error")
        } },
        initialSnapshot = shiftedSource,
        onHostMigrationFinished = { scope.launch(Dispatchers.Main) { leaveOnlineRoom() } }
    )
    shifted.players.forEachIndexed { seat, player -> if (seat > 0 && player.isOnline) host.reserveSeat(seat) }
    roomHost = host
    host.start()
}

internal fun TableController.submitEndVote(approve: Boolean) {
    if (endProposalBy < 0 || localSeat !in players.indices) return
    val nextVotes = endVotes + (localSeat to approve)
    if (!approve) {
        endProposalBy = -1
        endVotes = emptyMap()
        logAction(localSeat, "voted to continue playing; end proposal declined")
        return
    }
    endVotes = nextVotes
    logAction(localSeat, "approved ending the game")
    if (players.indices.all { nextVotes[it] == true }) {
        gameEndedOnline = true
        logAction(localSeat, "all players agreed to end the game")
    }
}

internal fun TableController.openLobby(joining: Boolean) {
    topMenuExpanded = false
    lobbyOpen = true
    lobbyCreated = roomHost != null || (cloudRoomClient != null && localSeat == 0)
    lobbyJoinMode = joining && roomHost == null && roomClient == null && cloudRoomClient == null
    lobbyNotice = null
    lobbyTurnOrder = null
}

internal fun TableController.makeRoomClient(hostAddress: String, code: String, password: String, preferredSeat: Int? = null): LanRoomClient {
    return LanRoomClient(
        onSeatAssigned = { seat -> scope.launch(Dispatchers.Main) {
            val previousSeat = localSeat
            localSeat = seat.coerceAtLeast(0)
            if (roomConnected) {
                if (selectedPlayer == previousSeat) selectedPlayer = localSeat
            } else {
                selectedPlayer = localSeat
                lobbyCreated = false
                lobbyJoinMode = false
                lobbyRoomCode = code
                lobbyPassword = password
                roomConnected = true
                    connectionLost = false
                    connectionLostReason = null
                onlineStatus = "Connected to host"
                recordDiagnostic("Connected to host and assigned seat ${localSeat + 1}")
                lobbyNotice = "Connected. Import your deck, choose Keep Hand or Mulligan, then mark yourself ready."
            }
        } },
        onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
        onReadyConfirmed = { seat, ready, accepted -> scope.launch(Dispatchers.Main) {
            recordDiagnostic("Host confirmed readiness: seat ${seat + 1}, ready=$ready, accepted=$accepted")
            if (seat == localSeat) {
                if (accepted) lobbyReadyPlayers = if (ready) lobbyReadyPlayers + seat else lobbyReadyPlayers - seat
                pendingReadyState = null
                lobbyNotice = when {
                    !accepted -> "The host has not received your opening-hand choice yet. Wait a moment, then try readying up again."
                    ready -> "The host confirmed you are ready."
                    else -> "The host confirmed you are not ready."
                }
            }
        } },
        onDeckStatusConfirmed = { seat, loaded -> scope.launch(Dispatchers.Main) {
            recordDiagnostic("Host confirmed deck status: seat ${seat + 1}, loaded=$loaded")
            if (seat == localSeat) {
                localDeckSyncConfirmed = loaded
                lobbyNotice = if (loaded) "The host confirmed your deck is loaded. Choose Keep Hand or Mulligan before readying up." else "The host confirmed your deck was removed."
            }
        } },
        onDisconnected = { reason -> scope.launch(Dispatchers.Main) {
            val interruptedSession = roomConnected || onlineStateReady || gameStartedOnline
            roomConnected = false
            onlineStatus = "Disconnected"
            pendingReadyState = null
            localDeckSyncConfirmed = false
            recordDiagnostic("Disconnected from host: $reason")
            lobbyNotice = reason
            if (interruptedSession) {
                connectionLost = true
                if (!reconnecting) connectionLostReason = reason
                if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Connection interrupted", "Connection to the game host was lost. Open Arcana Table to reconnect.")
            }
            roomClient?.close()
            roomClient = null
        } },
        onHostTransfer = { source, targetSeat -> scope.launch(Dispatchers.Main) {
            val predecessor = roomClient ?: return@launch
            recordDiagnostic("Received host transfer offer")
            startTransferredHost(source, targetSeat, predecessor)
        } },
        onRedirect = { address, seat -> scope.launch(Dispatchers.Main) {
            val oldClient = roomClient
            oldClient?.close()
            lobbyHostInput = address
            roomConnected = false
            onlineStateReady = false
            onlineStatus = "Reconnecting to new host"
            lobbyNotice = "Host changed. Reconnecting to the room…"
            recordDiagnostic("Room redirected to the new host")
            val redirected = makeRoomClient(address, lobbyRoomCode, lobbyPassword, seat)
            roomClient = redirected
            redirected.connect(address, lobbyRoomCode, lobbyPassword, lobbyPlayerName, seat)
        } },
        onMigrationAccepted = { scope.launch(Dispatchers.Main) {
            if (lobbyCreated && localSeat == 0) {
                roomClient?.close()
                roomClient = null
                roomConnected = true
                onlineStateReady = true
                onlineStatus = "Room host transferred"
                lobbyNotice = "The room is yours. Other players are reconnecting."
                recordDiagnostic("Host transfer confirmed by previous host")
            }
        } }
    )
}

internal fun TableController.makeCloudRoomClient(code: String, password: String, creating: Boolean, preferredSeat: Int? = null): CloudRoomClient {
    return CloudRoomClient(
        onSeatAssigned = { seat -> scope.launch(Dispatchers.Main) {
            localSeat = seat
            selectedPlayer = seat
            lobbyCreated = creating
            lobbyJoinMode = false
            lobbyRoomCode = code
            lobbyPassword = password
            roomConnected = true
            onlineStateReady = true
            connectionLost = false
            connectionLostReason = null
            onlineStatus = "Connected to internet room"
            lobbyNotice = if (creating) "Internet room is open. Share the room code and password." else "Connected. Import your deck, choose Keep Hand or Mulligan, then mark yourself ready."
            recordDiagnostic("Internet room connected; assigned seat ${seat + 1}")
        } },
        onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
        onReadyConfirmed = { seat, ready, accepted -> scope.launch(Dispatchers.Main) {
            recordDiagnostic("Internet room confirmed readiness: seat ${seat + 1}, ready=$ready, accepted=$accepted")
            if (seat == localSeat) {
                if (accepted) lobbyReadyPlayers = if (ready) lobbyReadyPlayers + seat else lobbyReadyPlayers - seat
                pendingReadyState = null
                lobbyNotice = when {
                    !accepted -> "The room has not received your opening-hand choice yet. Wait a moment, then try readying up again."
                    ready -> "The room confirmed you are ready."
                    else -> "The room confirmed you are not ready."
                }
            }
        } },
        onDeckStatusConfirmed = { seat, loaded -> scope.launch(Dispatchers.Main) {
            recordDiagnostic("Internet room confirmed deck status: seat ${seat + 1}, loaded=$loaded")
            if (seat == localSeat) {
                localDeckSyncConfirmed = loaded
                lobbyNotice = if (loaded) "The room confirmed your deck is loaded. Choose Keep Hand or Mulligan before readying up." else "The room confirmed your deck was removed."
            }
        } },
        onDisconnected = { reason -> scope.launch(Dispatchers.Main) {
            val hadConnection = roomConnected || onlineStateReady || gameStartedOnline
            roomConnected = false
            onlineStatus = "Disconnected from internet room"
            pendingReadyState = null
            localDeckSyncConfirmed = false
            lobbyNotice = reason
            recordDiagnostic("Internet room disconnected: $reason")
            if (hadConnection) {
                connectionLost = true
                connectionLostReason = reason
                if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Connection interrupted", "Connection to the internet room was lost. Open Arcana Table to reconnect.")
            } else if (creating && !reconnecting) {
                lobbyCreated = false
            }
            if (cloudRoomClient != null) cloudRoomClient = null
        } }
    )
}

internal fun TableController.startInternetRoom() {
    if (lobbyCloudEndpoint.isBlank()) {
        lobbyNotice = "Enter your deployed Cloudflare Worker URL first."
        return
    }
    lobbyCloudEndpoint = lobbyCloudEndpoint.trim().removeSuffix("/")
    optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply()
    lobbyRoomCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
    roomClient?.close(); roomClient = null
    roomHost?.close(); roomHost = null
    cloudRoomClient?.close()
    localPlayersBackup = players
    players = listOf(players.first().copy(name = lobbyPlayerName.trim().ifBlank { "PLAYER" }, isOnline = true))
    selectedPlayer = 0
    activePlayer = 0
    localSeat = 0
    lobbyCreated = false
    roomConnected = false
    gameStartedOnline = false
    endProposalBy = -1
    endVotes = emptyMap()
    gameEndedOnline = false
    onlineStateReady = false
    lobbyReadyPlayers = emptySet()
    pendingReadyState = null
    lobbyTurnOrder = null
    lobbyHostAddress = ""
    onlineStatus = "Connecting"
    lobbyNotice = "Opening a secure internet room…"
    val client = makeCloudRoomClient(lobbyRoomCode, lobbyPassword, creating = true)
    cloudRoomClient = client
    client.connect(lobbyCloudEndpoint, lobbyRoomCode, lobbyPassword, lobbyPlayerName, create = true, initialSnapshot = buildOnlineSnapshot())
    scope.launch {
        delay(15_000)
        if (cloudRoomClient === client && !roomConnected) {
            client.close()
            cloudRoomClient = null
            lobbyCreated = false
            onlineStatus = "Connection timed out"
            lobbyNotice = "The room service did not answer in time. Check the Worker URL and internet connection, then try again."
        }
    }
}

internal fun TableController.reconnectToRoom() {
    if (reconnecting) return
    if ((!lobbyInternetMode && lobbyHostInput.isBlank()) || (lobbyInternetMode && lobbyCloudEndpoint.isBlank()) || lobbyRoomCode.isBlank() || lobbyPassword.isBlank()) {
        connectionLostReason = "The saved room details are incomplete. Rejoin from the lobby."
        return
    }
    reconnecting = true
    connectionLostReason = null
    lobbyOpen = true
    lobbyJoinMode = true
    lobbyNotice = "Trying to reconnect to the game…"
    onlineStatus = "Reconnecting"
    roomConnected = false
    onlineStateReady = false
    reconnectJob = scope.launch {
        var recovered = false
        if (!lobbyInternetMode) {
            // Give the host's heartbeat timeout a moment to reserve this seat.
            delay(3_200)
        }
        repeat(4) { attempt ->
            if (!reconnecting) return@launch
            if (lobbyInternetMode) {
                val reconnectClient = makeCloudRoomClient(lobbyRoomCode, lobbyPassword, creating = lobbyCreated, preferredSeat = localSeat)
                cloudRoomClient = reconnectClient
                val name = players.getOrNull(localSeat)?.name?.takeIf { it.isNotBlank() && it != "YOU" } ?: lobbyPlayerName
                reconnectClient.connect(lobbyCloudEndpoint, lobbyRoomCode, lobbyPassword, name, create = false, preferredSeat = localSeat)
                recovered = withTimeoutOrNull(12_000L) {
                    while (!roomConnected) delay(120)
                    true
                } ?: false
                if (recovered) {
                    reconnecting = false
                    connectionLost = false
                    connectionLostReason = null
                    lobbyNotice = "Reconnected to the internet room."
                    recordDiagnostic("Reconnected to the internet room")
                    return@launch
                }
                reconnectClient.close()
                if (cloudRoomClient === reconnectClient) cloudRoomClient = null
                if (attempt < 3) delay(900L * (attempt + 1))
                return@repeat
            }
            val client = makeRoomClient(lobbyHostInput, lobbyRoomCode, lobbyPassword, localSeat)
            roomClient = client
            val name = players.getOrNull(localSeat)?.name?.takeIf { it.isNotBlank() && it != "YOU" } ?: lobbyPlayerName
            client.connect(lobbyHostInput, lobbyRoomCode, lobbyPassword, name, localSeat)
            recovered = withTimeoutOrNull(9_000L) {
                while (!roomConnected) delay(120)
                true
            } ?: false
            if (recovered) {
                reconnecting = false
                connectionLost = false
                connectionLostReason = null
                lobbyNotice = "Reconnected to the room."
                recordDiagnostic("Reconnected to the game room")
                return@launch
            }
            client.close()
            if (roomClient === client) roomClient = null
            if (attempt < 3) delay(900L * (attempt + 1))
        }
        reconnecting = false
        if (!recovered) {
            connectionLost = true
            onlineStatus = "Disconnected"
            connectionLostReason = "Couldn't reconnect after four attempts. Check that the host is still in the room and both phones are on the same Wi-Fi, then try again."
            lobbyNotice = connectionLostReason
            recordDiagnostic("Automatic reconnect failed after four attempts")
        }
    }
}

internal fun TableController.cancelRoomJoin() {
    if (onlineStatus != "Connecting") return
    roomClient?.close()
    roomClient = null
    cloudRoomClient?.close()
    cloudRoomClient = null
    roomConnected = false
    onlineStateReady = false
    onlineStatus = "Offline"
    lobbyNotice = "Join canceled. You can change the details and try again."
}
