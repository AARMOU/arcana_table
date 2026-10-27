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

/** Overlays and dialogs drawn over the table (part 2: rooms, lobby, deck import, local-rules dialogs). */
@Composable
internal fun TableController.TableOverlaysB() {
            Box(Modifier.fillMaxSize()) {





    if (lobbyOpen) LobbyDialog(
        players = players,
        roomCreated = lobbyCreated || roomConnected,
        isHost = lobbyCreated,
        gameStarted = gameStartedOnline,
        gameEnded = gameEndedOnline,
        joining = lobbyJoinMode,
        roomCode = lobbyRoomCode,
        password = lobbyPassword,
        joinCode = lobbyJoinCode,
        joinPassword = lobbyJoinPassword,
        hostInput = lobbyHostInput,
        internetMode = lobbyInternetMode,
        cloudEndpoint = lobbyCloudEndpoint,
        playerName = lobbyPlayerName,
        hostAddress = lobbyHostAddress,
        localSeat = localSeat,
        onlineStatus = onlineStatus,
        readyPlayers = lobbyReadyPlayers,
        pendingReady = pendingReadyState,
        deckSyncConfirmed = localDeckSyncConfirmed,
        onTakeMulligan = { takeOpeningMulligan(localSeat) },
        onKeepOpeningHand = { bottomIds -> keepOpeningHand(localSeat, bottomIds) },
        turnOrder = lobbyTurnOrder,
        notice = lobbyNotice,
        onRemovePlayer = ::removeLobbyPlayer,
        onExportDiagnostics = { run { exportDiagnosticLog() } },
        onDismiss = { run { cancelRoomJoin(); lobbyOpen = false } },
        onCancelJoin = { run { cancelRoomJoin() } },
        onSelectJoin = { run { lobbyJoinMode = true; lobbyNotice = null } },
        onSelectCreate = { run { lobbyJoinMode = false; lobbyNotice = null } },
        onSelectNetwork = { internet -> run {
            lobbyInternetMode = internet
            if (internet && (lobbyCloudEndpoint.isBlank() || isLegacyExampleCloudEndpoint(lobbyCloudEndpoint))) {
                lobbyCloudEndpoint = DEFAULT_CLOUD_ROOM_ENDPOINT
                optionPrefs.edit().putString("cloud_room_endpoint", DEFAULT_CLOUD_ROOM_ENDPOINT).apply()
            }
            lobbyNotice = null
        } },
        onCloudEndpoint = { lobbyCloudEndpoint = it.trim(); optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply() },
        onPassword = { lobbyPassword = it },
        onJoinCode = { lobbyJoinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
        onJoinPassword = { lobbyJoinPassword = it },
        onHostInput = { lobbyHostInput = it.trim() },
        onPlayerName = { lobbyPlayerName = it.take(24) },
        onCreate = { run { if (lobbyInternetMode) startInternetRoom() else {
            recordDiagnostic("Lobby creation requested")
            lobbyRoomCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
            roomClient?.close(); roomClient = null
            roomHost?.close()
            localPlayersBackup = players
            players = listOf(players.first().copy(name = lobbyPlayerName.trim().ifBlank { "PLAYER" }, isOnline = true))
            selectedPlayer = 0
            activePlayer = 0
            localSeat = 0
            lobbyCreated = true
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
            onlineStatus = "Starting room host"
            val host = LanRoomHost(
                roomCode = lobbyRoomCode,
                password = lobbyPassword,
                onStarted = { address -> scope.launch(Dispatchers.Main) {
                    lobbyHostAddress = address
                    onlineStatus = if (address.isBlank()) "Room is open · local IP not found" else "Room is open"
                    roomConnected = true
                    onlineStateReady = true
                    recordDiagnostic(if (address.isBlank()) "Lobby host started without a detected local IP" else "Lobby host started with a detected local IP")
                    if (address.isBlank()) lobbyNotice = "The room server is open, but this phone's Wi-Fi IP could not be detected. Check Wi-Fi or hotspot settings before inviting players."
                } },
                onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
                onPeerJoined = { seat, name -> scope.launch(Dispatchers.Main) {
                    if (players.size <= seat) players = players + TablePlayer(name = name, isOnline = true)
                    else players = players.mapIndexed { index, player -> if (index == seat) player.copy(name = name, isOnline = true) else player }
                    recordDiagnostic("Remote player joined: seat ${seat + 1}")
                    lobbyNotice = "$name joined the room."
                    if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Player connected", "$name joined the game room.")
                } },
                onPeerLeft = { seat -> scope.launch(Dispatchers.Main) {
                    if (seat in players.indices && seat > 0) {
                        val departed = players[seat].name
                        roomHost?.reserveSeat(seat)
                        players = players.mapIndexed { index, player -> if (index == seat) player.copy(isOnline = false) else player }
                        lobbyReadyPlayers = lobbyReadyPlayers - seat
                        recordDiagnostic("Remote player disconnected; reserved seat ${seat + 1} for reconnect")
                        lobbyNotice = "$departed lost connection. Their seat is reserved; they can try to reconnect."
                        if (message == null) message = "$departed lost connection. Their seat is reserved while they reconnect."
                        if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Player disconnected", "$departed lost connection. Their seat is reserved for reconnect.")
                    }
                } },
                onEvent = { event -> scope.launch(Dispatchers.Main) { recordDiagnostic(event) } },
                onError = { error -> scope.launch(Dispatchers.Main) {
                    recordDiagnostic("Room host error: $error")
                    lobbyHostAddress = ""
                    roomConnected = false
                    onlineStateReady = false
                    onlineStatus = "Host failed to start"
                    lobbyNotice = "Couldn't start the room host: $error. Close this lobby and try creating it again."
                    roomHost?.close()
                    roomHost = null
                    lobbyCreated = false
                } },
                onHostMigrationFinished = { scope.launch(Dispatchers.Main) {
                    recordDiagnostic("Previous host left after successful handoff")
                    leaveOnlineRoom()
                } }
            )
            roomHost = host
            host.start()
            scope.launch {
                delay(10_000)
                if (roomHost === host && onlineStatus == "Starting room host") {
                    recordDiagnostic("Lobby host startup timed out")
                    host.close()
                    roomHost = null
                    lobbyCreated = false
                    roomConnected = false
                    onlineStateReady = false
                    lobbyHostAddress = ""
                    onlineStatus = "Host startup timed out"
                    lobbyNotice = "The room didn't start. Check that no other Arcana Table room is open, then try again."
                }
            }
        } } },
        onJoin = { run { if (lobbyInternetMode) {
            if (lobbyCloudEndpoint.isBlank()) {
                lobbyNotice = "Enter your deployed Cloudflare Worker URL first."
            } else {
                lobbyCloudEndpoint = lobbyCloudEndpoint.trim().removeSuffix("/")
                optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply()
                roomHost?.close(); roomHost = null
                roomClient?.close(); roomClient = null
                cloudRoomClient?.close()
                localPlayersBackup = players
                roomConnected = false
                onlineStateReady = false
                localDeckSyncConfirmed = false
                pendingReadyState = null
                lobbyCreated = false
                lobbyRoomCode = lobbyJoinCode
                lobbyPassword = lobbyJoinPassword
                recordDiagnostic("Internet lobby join requested")
                lobbyNotice = "Connecting securely to the internet room…"
                onlineStatus = "Connecting"
                val client = makeCloudRoomClient(lobbyJoinCode, lobbyJoinPassword, creating = false)
                cloudRoomClient = client
                client.connect(lobbyCloudEndpoint, lobbyJoinCode, lobbyJoinPassword, lobbyPlayerName, create = false)
                scope.launch {
                    delay(15_000)
                    if (cloudRoomClient === client && !roomConnected) {
                        client.close()
                        cloudRoomClient = null
                        onlineStatus = "Connection timed out"
                        lobbyNotice = "The room service did not answer in time. Check the Worker URL and internet connection, then try again."
                    }
                }
            }
        } else {
            roomHost?.close(); roomHost = null
            cloudRoomClient?.close(); cloudRoomClient = null
            roomClient?.close()
            localPlayersBackup = players
            roomConnected = false
            onlineStateReady = false
            localDeckSyncConfirmed = false
            pendingReadyState = null
            recordDiagnostic("Lobby join requested")
            lobbyNotice = "Connecting to $lobbyHostInput…"
            onlineStatus = "Connecting"
            val client = makeRoomClient(lobbyHostInput, lobbyJoinCode, lobbyJoinPassword)
            roomClient = client
            client.connect(lobbyHostInput, lobbyJoinCode, lobbyJoinPassword, lobbyPlayerName)
        } } },
        onToggleReady = { index -> run {
            val isHostAi = lobbyCreated && index in players.indices && players[index].isComputer
            if (index != localSeat && !isHostAi) lobbyNotice = "Each player marks their own seat ready on their device."
            else if (!players[index].deckLoaded) lobbyNotice = "${players[index].name} needs an imported deck before becoming ready."
            else if (!players[index].openingHandKept && !players[index].isComputer) lobbyNotice = "Choose Keep Hand or Mulligan before marking ready."
            else {
                val isBecomingReady = index !in lobbyReadyPlayers
                if ((roomClient != null || cloudRoomClient != null) && index == localSeat) {
                    pendingReadyState = isBecomingReady
                    recordDiagnostic("Ready status sent: seat ${localSeat + 1}, ready=$isBecomingReady")
                    val sent = roomClient?.publishReady(isBecomingReady) ?: cloudRoomClient?.publishReady(isBecomingReady) ?: false
                    if (!sent) {
                        pendingReadyState = null
                        lobbyNotice = "Your ready status could not be sent. Check the connection and retry."
                        recordDiagnostic("Ready status send failed: seat ${localSeat + 1}")
                    } else lobbyNotice = if (isBecomingReady) "Sending ready status to the host…" else "Sending your not-ready status to the host…"
                    scope.launch {
                        delay(5_000)
                        if (pendingReadyState == isBecomingReady) {
                            pendingReadyState = null
                            lobbyNotice = "The host has not confirmed that readiness change. Reopen the lobby or try again."
                            recordDiagnostic("Ready confirmation timed out: seat ${localSeat + 1}, requested=$isBecomingReady")
                        }
                    }
                } else {
                    lobbyReadyPlayers = if (isBecomingReady) lobbyReadyPlayers + index else lobbyReadyPlayers - index
                    recordDiagnostic("Ready status changed locally: seat ${index + 1}, ready=$isBecomingReady")
                    lobbyNotice = if (isBecomingReady) "${players[index].name} is ready to start." else "${players[index].name} is not ready yet."
                }
            }
        } },
        onImportDeck = { index -> run {
            selectedPlayer = index
            lobbyOpen = false
            importError = null
            importOpen = true
        } },
        onAddAi = { run {
            lobbyOpen = false
            addAiOpponent()
        } },
        onRandomizeTurnOrder = { run {
            val randomized = players.indices.shuffled()
            lobbyTurnOrder = randomized
            lobbyNotice = "${players[randomized.first()].name} will take the first turn."
        } },
        onStartGame = { run {
            val order = lobbyTurnOrder ?: players.indices.toList()
            recordDiagnostic("Host started game with ${players.size} occupied seat(s)")
            resetGame(startImmediately = true, order = order, preserveOpeningHands = true)
            endProposalBy = -1
            endVotes = emptyMap()
            logAction(activePlayer, "started the game and goes first")
        } },
        onLeave = { run {
            if (cloudRoomClient != null) {
                lobbyNotice = "Leaving the room. The internet room stays available for the other players."
                recordDiagnostic("Player left the internet room")
                leaveOnlineRoom()
            } else if (lobbyCreated && gameStartedOnline && !gameEndedOnline) {
                val successorSeat = roomHost?.beginHostTransfer()
                if (successorSeat == null) {
                    lobbyNotice = "The room needs another connected player before you can hand off hosting. The game is still running here."
                    recordDiagnostic("Host leave blocked because no successor could accept the room")
                } else {
                    lobbyNotice = "Handing the room to ${players.getOrNull(successorSeat)?.name ?: "the next player"}… Keep this screen open until the transfer finishes."
                    onlineStatus = "Transferring host"
                    recordDiagnostic("Host initiated transfer to seat ${successorSeat + 1}")
                }
            } else {
                leaveOnlineRoom()
            }
        } },
    )

    handActionCard?.let { card -> HandCardActionsDialog(
        card, hasCommander = players.getOrNull(selectedPlayer)?.let { it.commanderCard != null || !it.commanderName.isNullOrBlank() } == true,
        isCommanderCard = players.getOrNull(selectedPlayer)?.commanderCard?.id == card.id,
        onDismiss = { handActionCard = null }, onAction = { action -> run { performHandAction(card, action) } }
    ) }

    if (connectionLostReason != null || reconnecting) AlertDialog(
        onDismissRequest = { if (!reconnecting) connectionLostReason = null },
        title = { Text(if (reconnecting) "Reconnecting…" else "Connection interrupted") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reconnecting) CircularProgressIndicator(color = Mint, strokeWidth = 2.dp)
                Text(connectionLostReason ?: "Arcana Table is trying to restore your room connection.", color = Muted)
                if (reconnecting) Text("The host keeps your seat reserved while you reconnect.", color = Mint, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { reconnectToRoom() }, enabled = !reconnecting) {
                Text(if (reconnecting) "TRYING…" else "RECONNECT", color = if (reconnecting) Muted else Mint)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                reconnectJob?.cancel()
                reconnectJob = null
                reconnecting = false
                roomClient?.close()
                roomClient = null
                if (connectionLost) {
                    roomConnected = false
                    onlineStatus = "Disconnected"
                }
                connectionLostReason = null
            }) { Text(if (reconnecting) "CANCEL" else "STAY OFFLINE", color = Muted) }
        },
        containerColor = Panel,
        titleContentColor = SoftWhite,
        textContentColor = Muted
    )

    if (importOpen) {
        LaunchedEffect(Unit) { loadSupportCatalog() }
        Dialog(onDismissRequest = { if (!importing) cancelDeckImport() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val maxImportDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
            Surface(Modifier.fillMaxWidth(0.96f).heightIn(max = maxImportDialogHeight), shape = RoundedCornerShape(18.dp), color = Panel) {
                Box(Modifier.fillMaxWidth().heightIn(max = maxImportDialogHeight)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${if (players[selectedPlayer].deckLoaded) "Replace deck for" else "Import deck for"} ${players[selectedPlayer].name}", color = SoftWhite, fontSize = 19.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { run { if (!importing) cancelDeckImport() } }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Cancel deck import", tint = Muted) }
                        }
                        Text("Paste a deck list or choose a .txt/.csv file. Import replaces this player's deck.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = { run { savedDecksOpen = true } }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("SAVED DECKS · ${savedDecks.size}", color = Mint) }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { run { filePicker.launch(arrayOf("text/*", "application/*")) } }, enabled = !importing && !argentumDeckLoading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CHOOSE FILE", fontSize = 9.sp) }
                            OutlinedButton(
                                onClick = { run {
                                    argentumDeckLoading = true
                                    importError = null
                                    scope.launch {
                                        try {
                                            val decks = withContext(Dispatchers.IO) { ArgentumDeckApi(argentumOrigin).commanderExamples() }
                                            argentumDeckOptions = decks
                                            if (decks.isEmpty()) importError = "Argentum did not return any Commander examples that pass its current deck validator."
                                            else argentumDeckPickerOpen = true
                                        } catch (error: Exception) {
                                            importError = error.message ?: "Could not load Argentum's premade decks. Check your connection and try again."
                                        } finally { argentumDeckLoading = false }
                                    }
                                } },
                                enabled = !importing && !argentumDeckLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text(if (argentumDeckLoading) "LOADING…" else "ARGENTUM DECKS", fontSize = 9.sp, color = Mint) }
                        }
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp).padding(top = 6.dp),
                            placeholder = { Text("1 Sol Ring\n36 Island\n...") },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !importing && !argentumDeckLoading
                        )
                        if (importText.isNotBlank()) {
                            val importedCardCount = remember(importText) { parseDeckList(importText).sumOf { it.count } }
                            Text("${importedCardCount} cards in list · scroll inside the box to review", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            // Offline pre-check against the shipped Argentum library (names as written; typo fixes happen on Import).
                            val supportReport = remember(importText, supportCatalog) { supportCatalog?.check(parseDeckList(importText)) }
                            if (supportReport != null) {
                                Text(supportReport.summary(), color = if (supportReport.unsupportedCopies > 0) CounterGold else Muted, fontSize = 10.sp)
                                if (supportReport.unsupportedNames.isNotEmpty()) Text(
                                    "Not in Argentum: ${supportReport.unsupportedNames.take(8).joinToString()}${if (supportReport.unsupportedNames.size > 8) ", +${supportReport.unsupportedNames.size - 8} more" else ""}",
                                    color = CounterGold, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                                )
                            } else Text(if (supportCatalogFailed) "Argentum library check unavailable in this build." else "Checking Argentum's card library…", color = Muted, fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importSaveToLibrary, onCheckedChange = { importSaveToLibrary = it }, enabled = !importing)
                            Text("Save this imported deck for later", color = SoftWhite, fontSize = 11.sp)
                        }
                        if (importSaveToLibrary) OutlinedTextField(importSaveName, { importSaveName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Saved deck name (optional)") }, enabled = !importing)
                        importError?.let { Text(it, color = Color(0xFFFF9C91), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                        if (importing) Text("Matching card names and trying automatic fixes…", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { run { if (!importing) cancelDeckImport() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                            Button(onClick = { run { importDeck(importText) } }, enabled = !importing && !argentumDeckLoading, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (importing || argentumDeckLoading) "LOADING" else "IMPORT") }
                        }
                    }
                }
            }
        }
    }

    if (argentumDeckPickerOpen) Dialog(onDismissRequest = { argentumDeckPickerOpen = false }) {
        Surface(
            Modifier.fillMaxWidth(0.96f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f),
            shape = RoundedCornerShape(18.dp), color = Panel
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ARGENTUM COMMANDER DECKS", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("These lists come from Argentum and passed its live Commander deck validator.", color = Muted, fontSize = 12.sp)
                argentumDeckOptions.forEach { deck ->
                    Surface(
                        Modifier.fillMaxWidth().clickable {
                            importText = deck.asImportText()
                            importError = null
                            // The list is now in the import box above; no pop-up needed.
                            argentumDeckPickerOpen = false
                        },
                        shape = RoundedCornerShape(12.dp), color = PanelLight, border = BorderStroke(1.dp, Muted.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(deck.name, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Commander · ${deck.commander} · 100 cards", color = SoftWhite, fontSize = 11.sp)
                            if (deck.description.isNotBlank()) Text(deck.description, color = Muted, fontSize = 11.sp)
                            deck.note?.let { Text(it, color = Color(0xFFFFD271), fontSize = 10.sp) }
                        }
                    }
                }
                TextButton(onClick = { argentumDeckPickerOpen = false }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { Text("CLOSE", color = Muted) }
            }
        }
    }

    if (savedDecksOpen) SavedDecksDialog(
        decks = savedDecks,
        onLoad = { deck -> run {
            if (onlineMode) selectedPlayer = localSeat
            importText = deck.text
            importError = null
            importOpen = true
            savedDecksOpen = false
        } },
        onDelete = { deck -> run {
            savedDecks = savedDecks.filterNot { it.name == deck.name && it.text == deck.text }
            saveSavedDecks(context, savedDecks)
        } },
        onDismiss = { run { savedDecksOpen = false } }
    )

    if (SHOW_DECK_IMPORT_REVIEW_UI && importReviewOpen) {
        val reviewPlayerIndex = selectedPlayer
        val reviewPlayer = players.getOrNull(reviewPlayerIndex)
        val cardsNeedingReview = reviewPlayer?.let { player ->
            (player.library + player.hand + listOfNotNull(player.commanderCard))
                .filter { it.id in importReviewCardIds && it.importIssue != null }
                .distinctBy { it.id }
        }.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!importAutoFixInProgress) importReviewOpen = false },
            title = { Text("Review imported cards") },
            text = { Text(importReviewMessage, color = Muted, fontSize = 12.sp) },
            // All actions stack in one right-aligned column, so the dismiss button
            // is not stranded beside a multi-row group.
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cardsNeedingReview.isNotEmpty()) TextButton(
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !importAutoFixInProgress,
                            onClick = {
                                val groupedCards = cardsNeedingReview.groupBy { it.name }
                                importAutoFixInProgress = true
                                importReviewMessage = "Checking ${groupedCards.size} card ${if (groupedCards.size == 1) "name" else "names"} for close matches…"
                                scope.launch {
                                    val fixedIds = mutableSetOf<Int>()
                                    val matchedNames = mutableListOf<Pair<String, String>>()
                                    val unresolvedNames = mutableListOf<String>()
                                    var lookupFailures = 0
                                    for ((name, copies) in groupedCards) {
                                        val lookup = runCatching { Scryfall.fuzzyNamedCard(name) }
                                        val candidate = lookup.getOrNull()?.takeIf {
                                            cardNameSimilarity(name, it.name) >= 0.78
                                        }
                                        if (candidate == null) {
                                            unresolvedNames += name
                                            if (lookup.isFailure) lookupFailures++
                                            continue
                                        }
                                        val ids = copies.mapTo(mutableSetOf()) { it.id }
                                        updatePlayer(reviewPlayerIndex) { player ->
                                            fun fix(card: GameCard) = if (card.id in ids) card.copy(
                                                name = candidate.name,
                                                type = candidate.type,
                                                imageUrl = candidate.imageUrl,
                                                oracleText = candidate.oracleText,
                                                manaValue = candidate.manaValue,
                                                importIssue = null,
                                                artCropUrl = candidate.artCropUrl,
                                                manaCost = candidate.manaCost,
                                                power = candidate.power,
                                                toughness = candidate.toughness,
                                                keywords = candidate.keywords
                                            ) else card
                                            val commander = player.commanderCard?.let(::fix)
                                            val commanderWasFixed = player.commanderCard?.id?.let(ids::contains) == true
                                            player.copy(
                                                library = player.library.map(::fix),
                                                hand = player.hand.map(::fix),
                                                commanderCard = commander,
                                                commanderName = if (commanderWasFixed) commander?.name else player.commanderName
                                            )
                                        }
                                        fixedIds += ids
                                        matchedNames += name to candidate.name
                                    }
                                    importReviewCardIds = importReviewCardIds - fixedIds
                                    val allReviewCardsFixed = unresolvedNames.isEmpty() && lookupFailures == 0
                                    if (allReviewCardsFixed && importOverflowCardIds.isEmpty()) {
                                        importReviewCardIds = emptySet()
                                        importReviewOpen = false
                                        message = "Auto Fix succeeded. All cards needing review were matched."
                                    } else {
                                        importReviewMessage = buildString {
                                            if (matchedNames.isNotEmpty()) {
                                                append("Auto Fix matched ${matchedNames.size} card ${if (matchedNames.size == 1) "name" else "names"} and updated ${fixedIds.size} flagged ${if (fixedIds.size == 1) "card" else "cards"}.")
                                            } else if (lookupFailures == 0) {
                                                append("Auto Fix couldn’t identify the cards.")
                                            }
                                            if (unresolvedNames.isNotEmpty()) {
                                                append("\n\nYou’ll need to resolve these manually: ")
                                                append(unresolvedNames.take(5).joinToString(", "))
                                                if (unresolvedNames.size > 5) append(" and ${unresolvedNames.size - 5} more")
                                                append(".")
                                            }
                                            if (lookupFailures > 0) append("\n\nCouldn’t check $lookupFailures ${if (lookupFailures == 1) "name" else "names"}. Check your connection and try Auto Fix again.")
                                            if (importOverflowCardIds.isNotEmpty()) append("\n\n${importOverflowCardIds.size} over-limit ${if (importOverflowCardIds.size == 1) "card remains" else "cards remain"}.")
                                            if (unresolvedNames.isNotEmpty() || lookupFailures > 0) append("\n\nOpen Editor to resolve the remaining cards manually.")
                                        }
                                    }
                                    importAutoFixInProgress = false
                                }
                            }
                        ) { Text(if (importAutoFixInProgress) "FIXING…" else "AUTO FIX", color = Mint) }
                        if (importOverflowCardIds.isNotEmpty()) TextButton(
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !importAutoFixInProgress,
                            onClick = {
                                val overflow = importOverflowCardIds
                                updatePlayer(reviewPlayerIndex) { p -> p.copy(hand = p.hand.filterNot { it.id in overflow }, library = p.library.filterNot { it.id in overflow }) }
                                importReviewCardIds = importReviewCardIds - overflow
                                importOverflowCardIds = emptySet()
                                importReviewMessage = "Removed ${overflow.size} card(s) over the deck limit."
                            }
                        ) { Text("TRIM EXCESS", color = Mint) }
                    if (cardsNeedingReview.isNotEmpty()) TextButton(onClick = { if (!importAutoFixInProgress) { importReviewOpen = false; deckEditorOpen = true } }, enabled = !importAutoFixInProgress, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("OPEN EDITOR", color = Mint)
                    }
                    val reviewFinished = cardsNeedingReview.isEmpty() && importOverflowCardIds.isEmpty()
                    TextButton(onClick = { if (!importAutoFixInProgress) importReviewOpen = false }, enabled = !importAutoFixInProgress, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (reviewFinished) "CLOSE" else "LATER", color = SoftWhite)
                    }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    if (tokenPickerOpen) TokenPickerDialog(
        onDismiss = { tokenPickerOpen = false },
        onAdd = { token ->
            val created = makeCard(token.name, token.type, token.imageUrl, token = true)
            updatePlayer(tokenTarget) { it.copy(battlefield = it.battlefield + created) }
            logAction(tokenTarget, "created a ${token.name} token")
            applyCreatureEntryLifeEffects(tokenTarget, created)
            tokenPickerOpen = false
        }
    )

    counterTarget?.let { (playerIndex, cardId) ->
        val card = players[playerIndex].battlefield.firstOrNull { it.id == cardId }
        if (card != null) CounterPickerDialog(card, onDismiss = { counterTarget = null }, onAdd = { counter ->
            updateCard(playerIndex, cardId) { current -> current.copy(counters = current.counters + (counter to ((current.counters[counter] ?: 0) + 1))) }
            logAction(playerIndex, "put a $counter counter on ${card.name}")
            counterTarget = null
        })
    }

    // The Lunar Whale (and other LookAtTopOfLibrary grants) do not use priority.
    // Read only the viewer's individually authorized top card from the masked engine view;
    // casting/land play, unlike looking, MUST be offered by Argentum as a legal action.
    if (argentumTopLibraryOpen) {
        val snap = argentumSnapshot?.takeIf { it.viewingPlayer == localSeat }
        val top = snap?.topLibraryCard
        val playChoices = if (snap != null && top != null)
            ArgentumActions.forCardServerId(snap.legalActions, snap.serverCardIds[top.id])
                .filter { it.optJSONObject("action")?.optString("type") in setOf("CastSpell", "PlayLand") }
        else emptyList()
        AlertDialog(
            onDismissRequest = { argentumTopLibraryOpen = false },
            title = { Text("Top of your library · private") },
            text = {
                if (top == null) Text("Argentum is not currently allowing you to look at the top card.", color = Muted)
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(top.name, color = SoftWhite)
                    Text(top.type, color = Muted)
                    if (!top.manaCost.isNullOrBlank()) ManaCostPips(top.manaCost.orEmpty())
                    Text(top.oracleText.orEmpty(), color = SoftWhite)
                    Text(
                        if (playChoices.isEmpty()) "You may look without priority. Playing or casting is not currently legal."
                        else "Argentum controls timing, available land plays, targets, and mana payment.",
                        color = Muted
                    )
                }
            },
            confirmButton = {
                if (top != null && playChoices.isNotEmpty())
                    TextButton(onClick = {
                        argentumTopLibraryOpen = false
                        openArgentumActions(top)
                    }) { Text("PLAY / CAST OPTIONS", color = Mint) }
            },
            dismissButton = {
                Row {
                    if (top != null) TextButton(onClick = {
                        argentumTopLibraryOpen = false
                        preview = CardPreview(localSeat, top, fromHand = false, canManipulate = false)
                    }) { Text("LARGE VIEW", color = Muted) }
                    TextButton(onClick = { argentumTopLibraryOpen = false }) { Text("CLOSE", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    preview?.let { item ->
        CardPreviewPopup(item = item, onDismiss = { preview = null })
    }

    zoneDialog?.let { (index, zone) ->
        val cards = when (zone) {
            "Graveyard" -> players[index].graveyard
            "Exile" -> players[index].exile
            else -> players[index].revealed
        }
        val legalZoneCardIds = if (argentumEnabled && zone in setOf("Graveyard", "Exile")) {
            val snapshot = argentumSnapshot
            cards.filter { card -> snapshot != null &&
                ArgentumActions.forCardServerId(snapshot.legalActions, snapshot.serverCardIds[card.id]).isNotEmpty()
            }.map { it.id }.toSet()
        } else emptySet()
        CardZoneDialog(
            zone, players[index].name, cards, onDismiss = { zoneDialog = null },
            onPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = false) },
            manualPlayAllowed = !argentumEnabled && zone in setOf("Graveyard", "Exile") &&
                (!onlineMode || index == localSeat && !gameEndedOnline),
            argentumPlayableIds = legalZoneCardIds,
            onPlayCard = { card ->
                if (argentumEnabled && card.id in legalZoneCardIds) {
                    // Close the zone dialog before opening Argentum's legal-action sheet.
                    zoneDialog = null
                    scope.launch { delay(120); openArgentumActions(card) }
                } else if (!argentumEnabled) beginManualPlayFromZone(index, zone, card)
            },
            // Argentum owns every zone change in its games; Arcana only shows them.
            canMoveCards = !argentumEnabled && zone in listOf("Graveyard", "Exile") && (!onlineMode || index == localSeat && !gameEndedOnline),
            onMoveCard = { card, destination ->
                val canControlZone = !onlineMode || index == localSeat && !gameEndedOnline
                if (canControlZone && destination == "Command Zone" && card.id == players[index].commanderCard?.id) {
                    updatePlayer(index) { p -> p.copy(graveyard = p.graveyard.filterNot { it.id == card.id }, exile = p.exile.filterNot { it.id == card.id }, revealed = p.revealed.filterNot { it.id == card.id }, commanderCard = card, commanderName = card.name) }
                    logAction(index, "moved ${card.name} to the command zone")
                } else if (canControlZone) {
                    updatePlayer(index) { p ->
                        val sourceCards = when (zone) {
                            "Graveyard" -> p.graveyard
                            "Exile" -> p.exile
                            else -> p.revealed
                        }
                        if (sourceCards.none { it.id == card.id }) p else {
                            val withoutCard = when (zone) {
                                "Graveyard" -> p.copy(graveyard = p.graveyard.filterNot { it.id == card.id })
                                "Exile" -> p.copy(exile = p.exile.filterNot { it.id == card.id })
                                else -> p.copy(revealed = p.revealed.filterNot { it.id == card.id })
                            }
                            when (destination) {
                                "Hand" -> withoutCard.copy(hand = withoutCard.hand + card)
                                "Battlefield" -> withoutCard.copy(battlefield = withoutCard.battlefield + card.copy(tapped = false))
                                "Library (top)" -> withoutCard.copy(library = listOf(card) + withoutCard.library)
                                "Library (bottom)" -> withoutCard.copy(library = withoutCard.library + card)
                                "Graveyard" -> withoutCard.copy(graveyard = withoutCard.graveyard + card)
                                "Exile" -> withoutCard.copy(exile = withoutCard.exile + card)
                                "Revealed" -> withoutCard.copy(revealed = withoutCard.revealed + card)
                                else -> p
                            }
                        }
                    }
                    val destinationLabel = if (destination == "Library (top)") "the top of the library" else if (destination == "Library (bottom)") "the bottom of the library" else destination.lowercase()
                    logAction(index, "moved ${card.name} from ${zone.lowercase()} to $destinationLabel")
                    if (destination == "Battlefield" && needsCreatureTypeChoice(card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(index, card.id, card.name)
                    if (destination == "Battlefield") applyCreatureEntryLifeEffects(index, card)
                }
            },
            commanderId = players[index].commanderCard?.id,
            canMoveCommander = !onlineMode || index == localSeat && !gameEndedOnline
        )
    }

    if (message != null) AlertDialog(onDismissRequest = { message = null }, title = { Text("Arcana Table") }, text = { Text(message!!, modifier = Modifier.clickable { message = null }) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted)

    if (logOpen) GameLogDialog(
        entries = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { it.description } else gameLog,
        ownership = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { if (it.isTurnMarker) null else it.isYours } else null,
        turnMarkers = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { it.isTurnMarker } else null,
        groupingKeys = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { it.groupingKey } else null,
        previewableCards = players.flatMapIndexed { index, player ->
            (player.battlefield + player.graveyard + player.exile + player.revealed.filterNot { it.isFaceDown } + listOfNotNull(player.commanderCard))
                .filterNot { it.isFaceDown }.map { index to it }
        },
        onPreviewCard = { owner, card -> preview = CardPreview(owner, card, fromHand = false, canManipulate = false) },
        onDismiss = { run { logOpen = false } }
    )
    if (rulesOpen) RulesLookupDialog(onDismiss = { rulesOpen = false })
    AnimatedVisibility(
        visible = chatOpen,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable { chatOpen = false }, contentAlignment = Alignment.CenterEnd) {
            LocalChatPanel(
                players = players, activePlayer = if (onlineMode) localSeat else activePlayer, messages = chatMessages, draft = chatDraft,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f).clickable(enabled = false) {},
                onDraft = { chatDraft = it }, onDismiss = { run { chatOpen = false } }, onSend = { run {
            val text = chatDraft.trim()
            if (text.isNotEmpty()) {
                if (sendTableChat(text)) {
                    logAction(if (onlineMode) localSeat else activePlayer, "sent a chat message")
                    chatDraft = ""
                } else message = "Chat was not sent. Check the room connection or shorten the message."
            }
                } }
            )
        }
    }

    if (tutorialHubOpen) TutorialHubDialog(
        onDismiss = { tutorialHubOpen = false },
        onStart = { id, index -> startTutorial(id, index) },
        gameRunning = argentumEnabled
    )
    if (optionsOpen) OptionsPage(
        darkMode = darkMode, timerVisible = turnTimerVisible,
        backgroundAlertsEnabled = backgroundAlertsEnabled, showEngineStatus = showEngineStatus,
        followActivePlayer = followActivePlayer,
        connectionLabel = when {
            argentumEnabled && wifiRole == "online-host" -> "Online game with a friend · you are hosting"
            argentumEnabled && wifiRole == "online-guest" -> "Online game with a friend · the other phone is hosting"
            argentumEnabled && wifiRole == "host" -> "Wi-Fi game · you are hosting"
            argentumEnabled && wifiRole == "guest" -> "Wi-Fi game · the other phone is hosting"
            argentumEnabled && argentumOffline -> "Offline game · Argentum running on this phone"
            argentumEnabled -> "Argentum · ${argentumStatus.name.lowercase().replaceFirstChar(Char::uppercase)}"
            else -> "Argentum · not in a game"
        },
        pingResult = argentumPingResult,
        versionLabel = "${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
        diagnosticCount = diagnosticLog.entries.size,
        onTestConnection = {
            argentumPingResult = "Testing…"
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { ArgentumDeckApi(argentumOrigin).ping() } }
                argentumPingResult = result.fold({ "Server reachable · $it ms" }, { "Can't reach Argentum: ${it.message ?: "network error"}" })
                recordDiagnostic("Connection test: ${argentumPingResult}", DiagnosticCategory.CONNECTION)
            }
        },
        onReconnect = if (argentumEnabled && argentumStatus != ArgentumConnectionStatus.CONNECTED && argentumStatus != ArgentumConnectionStatus.REPLACED) ({
            recordDiagnostic("Player chose Reconnect (Options)", DiagnosticCategory.CONNECTION); argentumSocket.retryNow()
        }) else null,
        // While a lesson is pointing at these links, tapping one moves the lesson on instead of
        // leaving Settings, so the lesson stays on screen.
        onViewDiagnostics = { if (tutorialChapterId == null) { optionsOpen = false; diagnosticViewerOpen = true } },
        onExportDiagnostics = { if (tutorialChapterId == null) exportDiagnosticLog() },
        onWhatsNew = { optionsOpen = false; changelogOpen = true },
        onTutorials = { if (tutorialChapterId == null) { optionsOpen = false; tutorialHubOpen = true } },
        engineTrialResult = engineTrialResult,
        onEngineSelfTest = {
            engineTrialResult = "Running an AI-vs-AI game on this phone…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    // The engine and AI recurse deeply; give them a large stack like a server thread.
                    var outcome: com.arcanatable.engine.EmbeddedEngineTrial.Result? = null
                    val worker = Thread(null, {
                        outcome = com.arcanatable.engine.EmbeddedEngineTrial.run({ context.assets.open("argentum/por-cards.jsonl") })
                    }, "argentum-engine-trial", 256L shl 20)
                    worker.start(); worker.join()
                    outcome
                }
                engineTrialResult = result?.summary() ?: "Engine trial did not return a result."
                recordDiagnostic(engineTrialResult!!, DiagnosticCategory.ENGINE)
            }
        },
        onFollowActivePlayer = { value -> run { followActivePlayer = value; optionPrefs.edit().putBoolean("follow_active_player", value).apply() } },
        respondToOwnStack = respondToOwnStack,
        onRespondToOwnStack = { value -> respondToOwnStack = value; optionPrefs.edit().putBoolean("respond_to_own_stack", value).apply(); if (argentumOffline) argentumSocket.send("setRespondToOwnStack", JSONObject().put("enabled", value)) },
        quietOpponentTurns = quietOpponentTurns,
        onQuietOpponentTurns = { value -> quietOpponentTurns = value; optionPrefs.edit().putBoolean("quiet_opponent_turns", value).apply(); if (argentumOffline) argentumSocket.send("setQuietOpponentTurns", JSONObject().put("enabled", value)) },
        reducedSwipeMotion = reducedSwipeMotion, cardSizeScale = cardSizeScale,
        onOpenLayoutLab = { run { optionsOpen = false; layoutLabOpen = true; TutorialActions.tap("setting-adjust-ui") } },
        onToggleTheme = { run { onToggleTheme() } },
        onToggleTimerVisibility = { run { turnTimerVisible = !turnTimerVisible } },
        onToggleBackgroundAlerts = { enabled -> run { setBackgroundAlerts(enabled) } },
        onShowEngineStatus = { value -> showEngineStatus = value; optionPrefs.edit().putBoolean("show_engine_status", value).apply() },
        onReducedMotion = { value -> run { reducedSwipeMotion = value; optionPrefs.edit().putBoolean("reduced_swipe_motion", value).apply() } },
        onCardSizeScale = { value -> run { cardSizeScale = value; optionPrefs.edit().putFloat("card_size_scale", value).putFloat("layout_card_size_$layoutOrientationSuffix", value).apply() } },
        onClearAllData = { run { optionsOpen = false; clearAllConfirmOpen = true } },
        onDismiss = { run { optionsOpen = false } },
    )
    if (layoutLabOpen) AdjustUiPage(
        orientationName = if (isLandscape) "LANDSCAPE" else "PORTRAIT",
        cardSize = cardSizeScale, onCardSize = { value -> cardSizeScale = value.coerceIn(.75f, 1.4f); optionPrefs.edit().putFloat("card_size_scale", cardSizeScale).putFloat("layout_card_size_$layoutOrientationSuffix", cardSizeScale).apply() },
        spacing = boardSpacingScale, onSpacing = { value -> boardSpacingScale = value.coerceIn(.5f, 2f); optionPrefs.edit().putFloat("layout_board_spacing_$layoutOrientationSuffix", boardSpacingScale).apply() },
        handHeight = handSheetHeightFraction, onHandHeight = { value -> handSheetHeightFraction = value.coerceIn(.38f, .95f); optionPrefs.edit().putFloat("hand_sheet_height_fraction", handSheetHeightFraction).putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", handSheetHeightFraction).apply() },
        footerScale = footerButtonScale, onFooterScale = { value -> footerButtonScale = value.coerceIn(.78f, 1.3f); optionPrefs.edit().putFloat("layout_footer_button_scale_$layoutOrientationSuffix", footerButtonScale).apply() },
        handScale = handCardScale, onHandScale = { value -> handCardScale = value.coerceIn(.7f, 1.4f); optionPrefs.edit().putFloat("layout_hand_card_scale_$layoutOrientationSuffix", handCardScale).apply() },
        arrangement = handArrangement, onArrangement = { value -> handArrangement = value; optionPrefs.edit().putString("layout_hand_arrangement_$layoutOrientationSuffix", value.name).apply() },
        actions = quickActionOrderRaw.split('|').filter(String::isNotBlank), onActions = { order -> quickActionOrderRaw = order.joinToString("|"); optionPrefs.edit().putString("layout_quick_actions_$layoutOrientationSuffix", quickActionOrderRaw).apply() },
        popup = showPlayedPopup, onPopup = { v -> showPlayedPopup = v; optionPrefs.edit().putBoolean("adjust_play_popup", v).apply() },
        feed = showRecentPlays, onFeed = { v -> showRecentPlays = v; optionPrefs.edit().putBoolean("adjust_recent_plays", v).apply() },
        banners = showAbilityBanners, onBanners = { v -> showAbilityBanners = v; optionPrefs.edit().putBoolean("adjust_ability_banners", v).apply() },
        stackPile = showStackPile, onStackPile = { v -> showStackPile = v; optionPrefs.edit().putBoolean("adjust_stack_pile", v).apply() },
        recap = showAiRecap, onRecap = { v -> showAiRecap = v; optionPrefs.edit().putBoolean("adjust_ai_recap", v).apply() },
        turnBanner = showTurnBanner, onTurnBanner = { v -> showTurnBanner = v; optionPrefs.edit().putBoolean("adjust_turn_banner", v).apply() },
        turnBuzz = buzzOnTurn, onTurnBuzz = { v -> buzzOnTurn = v; optionPrefs.edit().putBoolean("adjust_turn_buzz", v).apply() },
        stackCards = stackSameCards, onStackCards = { v -> stackSameCards = v; optionPrefs.edit().putBoolean("adjust_stack_cards", v).apply() },
        respondGlow = respondGlow, onRespondGlow = { v -> respondGlow = v; optionPrefs.edit().putBoolean("adjust_respond_glow", v).apply() },
        autoOpenHand = autoOpenHand, onAutoOpenHand = { v -> autoOpenHand = v; optionPrefs.edit().putBoolean("adjust_auto_open_hand", v).apply() },
        popupSeconds = playedPopupSeconds, onPopupSeconds = { v -> playedPopupSeconds = v.coerceIn(1f, 8f); optionPrefs.edit().putFloat("adjust_notice_seconds", playedPopupSeconds).apply() },
        combatSize = combatCardScale, onCombatSize = { v -> combatCardScale = v.coerceIn(.65f, 1.35f); optionPrefs.edit().putFloat("adjust_combat_card_scale", combatCardScale).apply() },
        onReset = {
            cardSizeScale = 1f; boardSpacingScale = 1f; handSheetHeightFraction = .52f; footerButtonScale = 1f; handCardScale = 1f; handArrangement = HandArrangement.AUTO_FIT
            quickActionOrderRaw = defaultQuickActionOrder
            showPlayedPopup = true; showRecentPlays = true; showAbilityBanners = true; showStackPile = true; showAiRecap = true; showTurnBanner = true; buzzOnTurn = true; stackSameCards = true; respondGlow = true; autoOpenHand = true
            playedPopupSeconds = 4f; combatCardScale = 1f
            optionPrefs.edit().putFloat("card_size_scale", 1f).putFloat("layout_card_size_$layoutOrientationSuffix", 1f).putFloat("layout_board_spacing_$layoutOrientationSuffix", 1f)
                .putFloat("hand_sheet_height_fraction", .52f).putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", .52f).putFloat("layout_hand_card_scale_$layoutOrientationSuffix", 1f)
                .putFloat("layout_footer_button_scale_$layoutOrientationSuffix", 1f)
                .putString("layout_hand_arrangement_$layoutOrientationSuffix", HandArrangement.AUTO_FIT.name).putString("layout_quick_actions_$layoutOrientationSuffix", defaultQuickActionOrder)
                .putBoolean("adjust_play_popup", true).putBoolean("adjust_recent_plays", true).putBoolean("adjust_ability_banners", true).putBoolean("adjust_stack_pile", true).putBoolean("adjust_ai_recap", true)
                .putBoolean("adjust_turn_banner", true).putBoolean("adjust_turn_buzz", true).putBoolean("adjust_stack_cards", true).putBoolean("adjust_respond_glow", true).putBoolean("adjust_auto_open_hand", true).putFloat("adjust_notice_seconds", 4f).putFloat("adjust_combat_card_scale", 1f).apply()
        },
        onDismiss = { layoutLabOpen = false; optionsOpen = true }
    )

    if (resetConfirmOpen) AlertDialog(
        onDismissRequest = { resetConfirmOpen = false },
        title = { Text("Reset this game?") },
        text = { Text("This will shuffle each player's non-token cards into a new library, deal a new opening hand, reset life and timers, and clear the current board and game log. Player names, decks, and commanders will stay.") },
        confirmButton = { TextButton(onClick = { resetGame() }) { Text("RESET GAME", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { resetConfirmOpen = false }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (onlineMode && endProposalBy >= 0 && localSeat !in endVotes && !gameEndedOnline) {
        val proposer = players.getOrNull(endProposalBy)?.name ?: "A player"
        AlertDialog(
            onDismissRequest = { submitEndVote(false) },
            title = { Text("End this game?") },
            text = { Text("$proposer proposes ending the game. Everyone must agree. If you decline, the game continues.") },
            confirmButton = { TextButton(onClick = { submitEndVote(true) }) { Text("AGREE TO END", color = Mint) } },
            dismissButton = { TextButton(onClick = { submitEndVote(false) }) { Text("KEEP PLAYING", color = SoftWhite) } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }
    LaunchedEffect(gameEndedOnline) { if (!gameEndedOnline) gameEndedNoticeDismissed = false }
    // Its buttons must dismiss it, or it would sit on top of the lobby it opens.
    if (onlineMode && gameEndedOnline && !argentumEnabled && !gameEndedNoticeDismissed) AlertDialog(
        onDismissRequest = { gameEndedNoticeDismissed = true },
        title = { Text("Game ended by unanimous vote") },
        text = { Text("Every player agreed to end the game. The table is now read-only.") },
        confirmButton = { TextButton(onClick = { gameEndedNoticeDismissed = true; homeOpen = true; showOverview = true; lobbyOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("OPEN LOBBY", color = Mint) } },
        dismissButton = { TextButton(onClick = { gameEndedNoticeDismissed = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("VIEW TABLE", color = SoftWhite) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (clearAllConfirmOpen) AlertDialog(
        onDismissRequest = { clearAllConfirmOpen = false },
        title = { Text("Clear all app data?") },
        text = { Text("This clears the current game, room, chat, logs, and game settings. The Internet room URL resets to your default. Saved decks are kept.") },
        confirmButton = { TextButton(onClick = { clearAllData() }) { Text("CLEAR EVERYTHING", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { clearAllConfirmOpen = false }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (manaPoolOpen) ManaPoolDialog(
        player = players.getOrNull(manaTarget) ?: players.first(),
        onChange = { color, delta -> changeMana(manaTarget, color, delta) },
        onClear = { clearMana(manaTarget) },
        onDismiss = { manaPoolOpen = false }
    )

    landEntryPrompt?.let { prompt ->
        val life = players.getOrNull(prompt.playerIndex)?.life ?: 0
        fun placeLand(enterTapped: Boolean, payTwoLife: Boolean = false) {
            val playerIndex = prompt.playerIndex
            if (playerIndex in players.indices &&
                players[playerIndex].cardsInPlaySource(prompt.sourceZone).any { it.id == prompt.card.id }) {
                updatePlayer(playerIndex) { player ->
                    val withoutCard = player.removeFromPlaySource(prompt.sourceZone, prompt.card.id) ?: return@updatePlayer player
                    withoutCard.copy(
                        life = if (payTwoLife) player.life - 2 else player.life,
                        battlefield = withoutCard.battlefield + prompt.card.copy(tapped = enterTapped)
                    )
                }
                logAction(playerIndex, "played ${prompt.card.name} from ${prompt.sourceZone.lowercase()}${if (payTwoLife) " and paid 2 life" else if (enterTapped) " tapped" else " untapped"}")
                if (needsCreatureTypeChoice(prompt.card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(playerIndex, prompt.card.id, prompt.card.name)
            }
            landEntryPrompt = null
        }
        AlertDialog(
            onDismissRequest = { landEntryPrompt = null },
            title = { Text(if (prompt.isShockLand) "Pay 2 life for ${prompt.card.name}?" else "How should ${prompt.card.name} enter?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prompt.conditionText.isNotBlank()) Text(prompt.conditionText, color = Muted)
                    if (prompt.isShockLand) Text("Pay 2 life to have it enter untapped, or let it enter tapped.", color = SoftWhite)
                    else Text("Choose the entry state. Check the condition above; Arcana will track the tapped state you choose.", color = SoftWhite)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prompt.isShockLand) TextButton(onClick = { placeLand(enterTapped = false, payTwoLife = true) }, enabled = life >= 2, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("PAY 2 LIFE · ENTER UNTAPPED", color = if (life >= 2) Mint else Muted)
                    } else TextButton(onClick = { placeLand(enterTapped = false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("ENTER UNTAPPED", color = Mint)
                    }
                    TextButton(onClick = { placeLand(enterTapped = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("ENTER TAPPED", color = Mint) }
                    TextButton(onClick = { landEntryPrompt = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    paymentCard?.let { (owner, card) ->
        val commanderCast = paymentSourceZone == "Command Zone" && players.getOrNull(owner)?.commanderCard?.id == card.id
        val tax = if (commanderCast) (players.getOrNull(owner)?.commanderCastCount ?: 0) * 2 else 0
        val payable = paySimpleManaCost(players.getOrNull(owner)?.manaPool.orEmpty(), card.manaCost, tax)
        val costKnown = card.manaCost != null
        AlertDialog(
            onDismissRequest = { paymentCard = null; paymentSourceZone = null; handActionCard = null },
            title = { Text(if (commanderCast) "Cast commander?" else "Cast ${if (card.isFaceDown) "face down" else card.name} from ${paymentSourceZone ?: "Hand"}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mana cost:")
                    if (card.manaCost.isNullOrBlank()) Text("unknown") else ManaCostPips(card.manaCost.orEmpty())
                    if (tax > 0) Text("+ $tax commander tax")
                }
                    Text("Tracked pool: ${players.getOrNull(owner)?.manaPool?.entries?.joinToString { (color, amount) -> "${color} $amount" }?.ifBlank { "empty" } ?: "empty"}", color = Muted)
                    if (!costKnown) Text("No parsed mana cost is available. Use manual payment after checking the card.", color = CounterGold)
                    else if (!supportsSimpleManaCost(card.manaCost)) Text("This cost uses hybrid, phyrexian, X, or other special symbols. Review and pay it manually.", color = CounterGold)
                    else if (payable == null) Text("The tracked pool does not have the required colored and generic mana.", color = CounterGold)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { castFromPayment(owner, card, commanderCast) }, enabled = payable != null && costKnown, modifier = Modifier.heightIn(min = 48.dp)) { Text("PAY FROM MANA POOL", color = if (payable != null && costKnown) Mint else Muted) }
                    TextButton(onClick = { castFromPayment(owner, card, commanderCast, manualPayment = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("I PAID MANUALLY", color = CounterGold) }
                    TextButton(onClick = { paymentCard = null; paymentSourceZone = null; handActionCard = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = SoftWhite
        )
    }

    if (stackOpen && argentumEnabled) Dialog(onDismissRequest = { stackOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp), shape = RoundedCornerShape(18.dp), color = Panel) {
            Box {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Argentum stack and priority", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val snap = argentumSnapshot
                    val stackItems = snap?.stack.orEmpty()
                    Text(if (stackItems.isEmpty()) "The Argentum stack is empty." else "ON THE STACK · ${stackItems.size}  ·  top resolves first", color = Mint, fontWeight = FontWeight.Bold)
                    val priorityName = snap?.let { s -> s.players.getOrNull(s.priorityPlayer)?.let { if (s.priorityPlayer == s.viewingPlayer) "You" else it.name } }
                    Text("PRIORITY · ${priorityName ?: "—"}", color = CounterGold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.tutorialTarget("priority-indicator"))
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).tutorialTarget("stack-items")) { StackCardList(stackItems) }
                    Text("Argentum controls priority and resolves the stack. Arcana displays the server state and offers only server-provided actions.", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { stackOpen = false; run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } }, enabled = argentumSnapshot?.legalActions?.isNotEmpty() == true) {
                        Text("VIEW LEGAL ACTIONS", color = Mint)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { stackOpen = false; TutorialActions.tap("stack-close") }, modifier = Modifier.heightIn(min = 48.dp).tutorialTarget("stack-close")) { Text("CLOSE", color = SoftWhite) }
                    TextButton(onClick = {
                        // The stack drill's next gesture is CLOSE. Keep the real sheet open
                        // through the confirmed pass so that control remains available.
                        if (!tutorialGated) stackOpen = false
                        sendArgentumPriority()
                    }, enabled = !argentumAwaitingUpdate && argentumSnapshot?.legalActions?.any { it.optJSONObject("action")?.optString("type") == "PassPriority" } == true,
                        modifier = Modifier.heightIn(min = 48.dp).tutorialTarget("stack-pass")) {
                        Text("PASS PRIORITY", color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TutorialLayer(Modifier.matchParentSize())
            }
        }
    } else if (stackOpen) StackPriorityDialog(
        players = players, stack = stackActions, priorityPlayer = priorityPlayer, passedCount = priorityPassed.size,
        eligibleCount = players.count { !it.isEliminated }, canResolve = stackActions.isNotEmpty() && stackActions.none { !it.targetsConfirmed } && players.filterNot { it.isEliminated }.all { it.id in priorityPassed },
        canAct = !gameEndedOnline && stackActions.none { !it.targetsConfirmed } && players.getOrNull(priorityPlayer)?.isEliminated == false && (if (onlineMode) priorityPlayer == localSeat else priorityPlayer == selectedPlayer),
        onPreviewCard = { action, card ->
            val ownerIndex = players.indexOfFirst { it.id == action.controllerId }.coerceAtLeast(0)
            preview = CardPreview(ownerIndex, card, fromHand = false, canManipulate = false)
        },
        onStackItemTap = { run {} },
        onCastFromHand = { run {
            selectedPlayer = if (onlineMode) localSeat else priorityPlayer
            showOverview = false
            showHand = true
            stackOpen = false
        } },
        onAddTrigger = { title -> run { stackActions = stackActions + StackAction(title = title, controllerId = players.getOrNull(priorityPlayer)?.id.orEmpty(), kind = "TRIGGER"); priorityPassed = emptySet() } },
        onPassPriority = { run {
            val currentId = players.getOrNull(priorityPlayer)?.id
            if (currentId != null) priorityPassed = priorityPassed + currentId
            priorityPlayer = nextTurnIndex(priorityPlayer, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
        } },
        onResolve = { run { resolveTopOfStack() } },
        onDismiss = { run { stackOpen = false } }
    )

    val targetSelectionItem = stackActions.firstOrNull { it.id == targetSelectionStackItemId }
    targetSelectionItem?.let { item ->
        val controllerIndex = players.indexOfFirst { it.id == item.controllerId }.coerceAtLeast(0)
        TargetSelectionDialog(
            stackItem = item,
            candidates = buildTargetCandidates(players, stackActions, item.id, players.getOrNull(controllerIndex)?.id.orEmpty()),
            onConfirm = { selectedTargets ->
                stackActions = stackActions.map { current -> if (current.id == item.id) current.copy(targets = selectedTargets, targetsConfirmed = true) else current }
                logAction(controllerIndex, "chose ${selectedTargets.size} target${if (selectedTargets.size == 1) "" else "s"} for ${item.title}")
                targetSelectionStackItemId = null
                if (stackActions.none { !it.targetsConfirmed && it.targetCount != null }) {
                    stackOpen = true
                    pendingStackOpen = false
                }
            }
        )
    }

    if (trackerPlayer in players.indices) PlayerTrackerDialog(
        player = players[trackerPlayer], players = players, playerIndex = trackerPlayer,
        engineAuthoritative = argentumEnabled,
        onUpdate = { updated ->
            val target = trackerPlayer
            updatePlayer(target) { updated }
            if (!players[target].isEliminated && (updated.playerCounters["Poison"] ?: 0) >= 10) eliminatePlayer(target, "10 poison counters")
        },
        onDismiss = { trackerPlayer = -1 }
    )

    if (concedePlayer in players.indices) AlertDialog(
        onDismissRequest = { concedePlayer = -1 },
        title = { Text("Concede this game?") },
        text = { Text("${players[concedePlayer].name} will be marked eliminated and cannot take further game actions. This does not close the room for other players.") },
        confirmButton = { TextButton(onClick = { concede(concedePlayer); concedePlayer = -1 }) { Text("CONCEDE", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { concedePlayer = -1 }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (shuffleConfirmPlayer in players.indices) AlertDialog(
        onDismissRequest = { shuffleConfirmPlayer = -1 },
        title = { Text("Shuffle library?") },
        text = { Text("This will randomize ${players[shuffleConfirmPlayer].name}’s library order.") },
        confirmButton = {
            TextButton(onClick = {
                val target = shuffleConfirmPlayer
                shuffleConfirmPlayer = -1
                updatePlayer(target) { it.copy(library = it.library.shuffled()) }
                logAction(target, "shuffled their library")
            }) { Text("SHUFFLE") }
        },
        dismissButton = { TextButton(onClick = { shuffleConfirmPlayer = -1 }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (librarySearchPlayer in players.indices) {
        val owner = librarySearchPlayer
        LibrarySearchDialog(
            player = players[owner],
            onPreview = { preview = CardPreview(owner, it, fromHand = false, canManipulate = false) },
            onTakeToHand = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, hand = p.hand + card) }; logAction(owner, "found ${card.name} and put it into hand") },
            onPutBattlefield = { card ->
                updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, battlefield = p.battlefield + card) }
                logAction(owner, "found ${card.name} and put it onto the battlefield")
                if (needsCreatureTypeChoice(card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(owner, card.id, card.name)
                applyCreatureEntryLifeEffects(owner, card)
            },
            onPutGraveyard = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, graveyard = p.graveyard + card) }; logAction(owner, "found ${card.name} and put it into the graveyard") },
            onPutExile = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, exile = p.exile + card) }; logAction(owner, "found ${card.name} and exiled it") },
            onMoveToTop = { card -> updatePlayer(owner) { p -> p.copy(library = listOf(card) + p.library.filterNot { it.id == card.id }) }; logAction(owner, "put ${card.name} on top of the library") },
            onMoveToBottom = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id } + card) }; logAction(owner, "put ${card.name} on the bottom of the library") },
            onShuffle = { updatePlayer(owner) { it.copy(library = it.library.shuffled()) }; logAction(owner, "shuffled their library") },
            onDismiss = { librarySearchPlayer = -1 }
        )
    }

    manaChoicePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { manaChoicePrompt = null },
            title = { Text("Choose mana · ${prompt.cardName}") },
            text = { Text("Which mana did this permanent produce?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    prompt.options.forEach { option ->
                        TextButton(onClick = {
                            updatePlayer(prompt.playerIndex) { player ->
                                player.copy(manaPool = player.manaPool.toMutableMap().apply {
                                    option.pool.forEach { (color, count) -> put(color, ((get(color) ?: 0) + count).coerceAtMost(99)) }
                                })
                            }
                            logAction(prompt.playerIndex, "added ${option.label} mana")
                            manaChoicePrompt = null
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                            ManaSymbolText(option.label, color = Mint, fontSize = 14.sp)
                        }
                    }
                    TextButton(onClick = { manaChoicePrompt = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    val visibleTriggerPrompt = if (onlineMode) triggerPrompts.firstOrNull {
        it.controllerIndex == localSeat || (roomHost != null && players.getOrNull(it.controllerIndex)?.isComputer == true)
    } else triggerPrompts.firstOrNull()
    creatureTypeChoicePrompt?.let { prompt ->
        CreatureTypeChoiceDialog(
            cardName = prompt.cardName,
            onConfirm = { chosenType ->
                updatePlayer(prompt.playerIndex) { player ->
                    player.copy(battlefield = player.battlefield.map { card ->
                        if (card.id == prompt.cardId) card.copy(chosenCreatureType = chosenType) else card
                    })
                }
                logAction(prompt.playerIndex, "chose $chosenType for ${prompt.cardName}")
                creatureTypeChoicePrompt = null
            }
        )
    }

    if (!manaPoolOpen && manaChoicePrompt == null && creatureTypeChoicePrompt == null) visibleTriggerPrompt?.let { prompt ->
        val payment = when (prompt.kind) { "RHYSTIC" -> 1; "TITHE" -> 2; else -> 0 }
        val canPay = payment == 0 || trackedMana(prompt.eventPlayerIndex) >= payment
        val targetCandidates = players.indices.filter { target ->
            !players[target].isEliminated && (prompt.targetRule != "OPPONENT" || target != prompt.controllerIndex)
        }
        var selectedTriggerTarget by remember(prompt.cardName, prompt.controllerIndex, prompt.eventPlayerIndex, prompt.kind, prompt.effectText) { mutableIntStateOf(-1) }
        val promptText = when (prompt.kind) {
            "RHYSTIC" -> "${players[prompt.eventPlayerIndex].name} cast a spell. Pay {1} for ${prompt.cardName}, or let ${players[prompt.controllerIndex].name} draw a card?"
            "TITHE" -> "${players[prompt.eventPlayerIndex].name} drew a card. Pay {2} for ${prompt.cardName}, or create a Treasure for ${players[prompt.controllerIndex].name}?"
            "AUTO_EFFECT" -> "${prompt.cardName} triggered: ${prompt.triggerText}. The app can apply this effect: ${prompt.effectText}"
            "TARGET_EFFECT" -> "${prompt.cardName} triggered: ${prompt.triggerText}. Choose a target for: ${prompt.effectText}"
            else -> "${prompt.cardName} triggered: ${prompt.triggerText}. Choose any modes, targets, and other decisions on the card, resolve it manually, then confirm. Effect: ${prompt.effectText}"
        }
        AlertDialog(
            // A tap outside must not make the pay-or-not decision for anyone.
            onDismissRequest = {},
            title = { Text(if (prompt.kind == "RHYSTIC" || prompt.kind == "TITHE") "Triggered choice · ${prompt.cardName}" else "Triggered ability · ${prompt.cardName}") },
            text = { Column {
                Text(promptText, color = SoftWhite)
                if (prompt.targetRule != null) {
                    Text("TARGET", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    targetCandidates.forEach { target ->
                        TextButton(onClick = { selectedTriggerTarget = target }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) {
                            Text("${if (selectedTriggerTarget == target) "✓ " else ""}${players[target].name}", color = if (selectedTriggerTarget == target) Mint else SoftWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (payment > 0) {
                    Text("Tracked mana for ${players.getOrNull(prompt.eventPlayerIndex)?.name ?: "player"}: ${trackedMana(prompt.eventPlayerIndex)}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    if (!canPay) Text("Paying is unavailable until enough mana is tracked.", color = CounterGold, fontSize = 10.sp)
                    TextButton(onClick = { manaTarget = prompt.eventPlayerIndex; manaPoolOpen = true }, modifier = Modifier.heightIn(min = 42.dp)) { Text("UPDATE MANA POOL", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            } },
            confirmButton = {
                val enabled = when (prompt.kind) {
                    "RHYSTIC", "TITHE" -> canPay
                    "TARGET_EFFECT" -> selectedTriggerTarget in targetCandidates
                    "MANUAL" -> prompt.targetRule == null || selectedTriggerTarget in targetCandidates
                    else -> true
                }
                TextButton(onClick = { resolveTrigger(prompt, paid = prompt.kind == "RHYSTIC" || prompt.kind == "TITHE", targetIndex = selectedTriggerTarget.takeIf { it >= 0 }) }, enabled = enabled) {
                    Text(when (prompt.kind) {
                        "RHYSTIC" -> "${players[prompt.eventPlayerIndex].name} PAYS {1}"
                        "TITHE" -> "${players[prompt.eventPlayerIndex].name} PAYS {2}"
                        "AUTO_EFFECT" -> "RESOLVE EFFECT"
                        "TARGET_EFFECT" -> "APPLY TO TARGET"
                        else -> "DONE · MANUAL RESOLUTION"
                    }, color = if (enabled) Mint else Muted)
                }
            },
            dismissButton = {
                when (prompt.kind) {
                    "RHYSTIC" -> TextButton(onClick = { resolveTrigger(prompt, paid = false) }) { Text("DON'T PAY · ${players[prompt.controllerIndex].name} DRAWS") }
                    "TITHE" -> TextButton(onClick = { resolveTrigger(prompt, paid = false) }) { Text("DON'T PAY · ${players[prompt.controllerIndex].name} GETS TREASURE") }
                    "AUTO_EFFECT", "TARGET_EFFECT" -> TextButton(onClick = { resolveTrigger(prompt, paid = false, targetIndex = selectedTriggerTarget.takeIf { it >= 0 }, manual = true) }) { Text("RESOLVE MANUALLY", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }
            }
}
