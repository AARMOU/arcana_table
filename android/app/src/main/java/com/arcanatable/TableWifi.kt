package com.arcanatable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * Wi-Fi play: one phone hosts (runs Argentum's engine, exactly as an offline game), the other
 * joins over the local network and plays through it. Both need the app and a Commander deck.
 */

/** Host: prepares your deck, then waits for a friend to join, on this Wi-Fi or ([online]) over the internet. */
internal fun TableController.startWifiHost(online: Boolean = false) {
    if (offlineStarting || argentumEnabled || wifiServer != null) return
    offlineStarting = true
    argentumLastError = null
    recordDiagnostic(if (online) "Online: hosting requested" else "Wi-Fi: hosting requested", DiagnosticCategory.ACTION)
    scope.launch {
        try {
            val (names, commander) = activeDeckCards()
            val (setup, registry) = prepareOfflineSetup(names, commander, null)
            if (setup.you.commander == null) {
                message = "Games with a friend use Commander decks. Pick a Commander deck on the home screen first."
                return@launch
            }
            val hostName = lobbyPlayerName.ifBlank { "Host" }
            val onGuestMessage: (JSONObject) -> Unit = { msg -> argentumSocket.runningLocalHost?.receive(msg, com.arcanatable.engine.LocalArgentumHost.GUEST_ID) }
            val onGuestLeft: () -> Unit = { argentumSocket.runningLocalHost?.receive(JSONObject().put("type", "guestLeft"), com.arcanatable.engine.LocalArgentumHost.GUEST_ID) }
            if (online) {
                lateinit var host: ArgentumCloudHost
                host = ArgentumCloudHost(lobbyCloudEndpoint, hostName,
                    onGuest = { hello -> scope.launch { acceptWifiGuest(host, hello, setup, registry) } },
                    onGuestMessage = onGuestMessage, onGuestLeft = onGuestLeft,
                    onFailed = { reason -> scope.launch { if (wifiServer === host) { stopWifi(); message = "Couldn't open the online room: $reason"; recordDiagnostic("Online host failed: $reason", DiagnosticCategory.ERROR) } } },
                )
                wifiServer = host
                onlineRoomCode = host.code
                host.start()
                recordDiagnostic("Online host waiting (room code made)", DiagnosticCategory.CONNECTION)
                return@launch
            }
            val address = ArgentumLan.localAddress()
            if (address.isBlank()) { message = "This phone isn't on a Wi-Fi network. Connect to Wi-Fi and try again."; return@launch }
            lateinit var server: ArgentumLanServer
            server = ArgentumLanServer(hostName,
                onGuest = { hello -> scope.launch { acceptWifiGuest(server, hello, setup, registry) } },
                onGuestMessage = onGuestMessage, onGuestLeft = onGuestLeft,
            )
            server.start()
            wifiServer = server
            wifiWaitingAddress = address
            setup.notes.forEach { recordDiagnostic("Wi-Fi host: $it", DiagnosticCategory.ENGINE) }
            recordDiagnostic("Wi-Fi host waiting at $address", DiagnosticCategory.CONNECTION)
        } catch (error: Throwable) {
            stopWifi()
            argentumLastError = "Couldn't start hosting: ${error.javaClass.simpleName}: ${error.message?.take(200)}"
            message = argentumLastError
            recordDiagnostic("Wi-Fi host failed: ${error.javaClass.simpleName}: ${error.message}", DiagnosticCategory.ERROR)
        } finally {
            offlineStarting = false
        }
    }
}

/** Host: a friend joined with their deck. Builds their seat and starts the game. */
private suspend fun TableController.acceptWifiGuest(
    server: ArgentumHostLink,
    hello: JSONObject,
    setup: com.arcanatable.engine.OfflineSetup,
    registry: com.wingedsheep.engine.registry.CardRegistry,
) {
    if (wifiServer !== server) return
    val guestName = hello.optString("name").ifBlank { "Friend" }.take(32)
    val deck = hello.optJSONArray("deck") ?: JSONArray()
    val names = (0 until deck.length()).map { deck.optString(it) }
    val commander = hello.optString("commander").takeIf { it.isNotBlank() && it != "null" }
    val (seat, notes) = withContext(Dispatchers.IO) {
        com.arcanatable.engine.OfflineGameSetup.lanSeat(offlineLibrary(), registry, guestName, names, commander)
    }
    if (seat == null) {
        val reason = notes.firstOrNull() ?: "That deck can't be used."
        server.send(JSONObject().put("type", "error").put("code", "WIFI").put("message", reason))
        message = "$guestName tried to join, but: $reason"
        delay(500)
        stopWifi()
        return
    }
    notes.forEach { note -> recordDiagnostic("Wi-Fi: $note", DiagnosticCategory.ENGINE); server.send(JSONObject().put("type", "error").put("code", "WIFI_NOTE").put("message", note)) }
    wifiWaitingAddress = null
    onlineRoomCode = null
    wifiRole = if (server is ArgentumCloudHost) "online-host" else "host"
    argentumSocket.localHostFactory = { emit -> com.arcanatable.engine.LocalArgentumHost(registry, setup.you, seat, emit = emit).also { it.quietOpponentTurns = quietOpponentTurns } }
    argentumOffline = true
    argentumDeckForLobby = null
    argentumCommanderForLobby = null
    argentumVsAiLobby = false
    argentumLobbySubmitted = true
    argentumLocalPlayersBackup = players.toList()
    argentumEnabled = true
    homeOpen = false
    lobbyOpen = false
    argentumSocket.connect(lobbyPlayerName)
    var waitCount = 0
    while (argentumSocket.runningLocalHost == null && waitCount < 20) { delay(100); waitCount++ }
    val host = argentumSocket.runningLocalHost
    if (host == null) { message = "Couldn't start the game engine."; stopWifi(); return }
    host.setGuest(seat) { msg -> server.send(msg) }
    argentumSocket.createCommanderPracticeLobby(vsAi = false)
    recordDiagnostic("Wi-Fi game started: you vs $guestName (${seat.commander})", DiagnosticCategory.ENGINE)
}

/** Guest: look for a host on this network. */
internal fun TableController.searchWifiHosts() {
    if (wifiSearching) return
    wifiSearching = true
    recordDiagnostic("Wi-Fi: searching for a host", DiagnosticCategory.CONNECTION)
    scope.launch {
        val lock = runCatching {
            (context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createMulticastLock("arcana-wifi-search").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        try {
            wifiFound = withContext(Dispatchers.IO) { ArgentumLan.discover(3_000) }
            recordDiagnostic("Wi-Fi: found ${wifiFound.size} host(s)", DiagnosticCategory.CONNECTION)
        } finally {
            runCatching { lock?.release() }
            wifiSearching = false
        }
    }
}

/** Guest: join the host at [address] with your deck. */
internal fun TableController.joinWifiGame(address: String) {
    if (wifiConnecting || argentumEnabled || address.isBlank()) return
    wifiConnecting = true
    argentumLastError = null
    recordDiagnostic("Wi-Fi: joining requested", DiagnosticCategory.ACTION)
    scope.launch {
        val guest = ArgentumLanGuest()
        try {
            val (names, commander) = activeDeckCards()
            if (commander == null) { message = "Wi-Fi games use Commander decks. Pick a Commander deck on the home screen first."; return@launch }
            val hello = JSONObject().put("type", "lanHello").put("name", lobbyPlayerName.ifBlank { "Friend" })
                .put("deck", JSONArray(names)).put("commander", commander)
            argentumSocket.attachLanGuest(guest)
            withContext(Dispatchers.IO) { guest.connect(address, hello) }
            wifiJoinOpen = false
            wifiRole = "guest"
            argentumOffline = true
            argentumDeckForLobby = null
            argentumCommanderForLobby = null
            argentumVsAiLobby = false
            argentumLobbySubmitted = true
            argentumLocalPlayersBackup = players.toList()
            argentumEnabled = true
            homeOpen = false
            lobbyOpen = false
            argentumSocket.connect(lobbyPlayerName)
            message = "Joined. Waiting for the host's phone to start the game…"
            recordDiagnostic("Wi-Fi: joined host at $address", DiagnosticCategory.CONNECTION)
        } catch (error: Throwable) {
            guest.close()
            argentumSocket.close()
            argentumLastError = "Couldn't join $address. Check both phones are on the same Wi-Fi and the other phone is hosting. (${error.javaClass.simpleName})"
            message = argentumLastError
            recordDiagnostic("Wi-Fi join failed: ${error.javaClass.simpleName}: ${error.message}", DiagnosticCategory.ERROR)
        } finally {
            wifiConnecting = false
        }
    }
}

/** Guest: join a friend's online game by its room code. */
internal fun TableController.joinOnlineGame(code: String) {
    val clean = ArgentumCloudLink.cleanCode(code)
    if (wifiConnecting || argentumEnabled || clean.length !in 6..8) { if (clean.length !in 6..8) message = "Room codes are 6 letters or numbers."; return }
    wifiConnecting = true
    argentumLastError = null
    recordDiagnostic("Online: joining requested", DiagnosticCategory.ACTION)
    val (names, commander) = activeDeckCards()
    if (commander == null) { wifiConnecting = false; message = "Games with a friend use Commander decks. Pick a Commander deck on the home screen first."; return }
    val hello = JSONObject().put("type", "lanHello").put("name", lobbyPlayerName.ifBlank { "Friend" })
        .put("deck", JSONArray(names)).put("commander", commander)
    val guest = ArgentumCloudGuest(lobbyCloudEndpoint, clean, lobbyPlayerName.ifBlank { "Friend" })
    argentumSocket.attachLanGuest(guest)
    guest.connect(hello) { failure ->
        scope.launch {
            wifiConnecting = false
            if (failure != null) {
                guest.close()
                argentumSocket.close()
                argentumLastError = "Couldn't join room $clean: $failure"
                message = argentumLastError
                recordDiagnostic("Online join failed: $failure", DiagnosticCategory.ERROR)
                return@launch
            }
            onlineJoinOpen = false
            wifiRole = "online-guest"
            argentumOffline = true
            argentumDeckForLobby = null
            argentumCommanderForLobby = null
            argentumVsAiLobby = false
            argentumLobbySubmitted = true
            argentumLocalPlayersBackup = players.toList()
            argentumEnabled = true
            homeOpen = false
            lobbyOpen = false
            argentumSocket.connect(lobbyPlayerName)
            message = "Joined room $clean. Waiting for the host's phone to start the game…"
            recordDiagnostic("Online: joined a room", DiagnosticCategory.CONNECTION)
        }
    }
}

/** Stops hosting or waiting (the game itself ends through closeArgentumSession). */
internal fun TableController.stopWifi() {
    wifiServer?.close()
    wifiServer = null
    wifiWaitingAddress = null
    onlineRoomCode = null
    wifiRole = null
}

/** Host's "waiting for your friend" card and the guest's "find a host" dialog. */
@Composable
internal fun TableController.WifiDialogs() {
    wifiWaitingAddress?.let { address ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Waiting for your friend") },
            text = {
                Text("Keep this screen open. On the other phone: PLAY → Wi-Fi with a friend → Join a game.\n\n" +
                    "If it doesn't find you, type this address there:\n$address")
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { stopWifi() }) { Text("CANCEL") } },
        )
    }
    onlineRoomCode?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Waiting for your friend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tell your friend this room code. On their phone: PLAY → Play with a friend → Join online.")
                    Text(code, fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Text("Keep this screen open until they join.")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { stopWifi() }) { Text("CANCEL") } },
        )
    }
    if (onlineJoinOpen) {
        AlertDialog(
            onDismissRequest = { if (!wifiConnecting) onlineJoinOpen = false },
            title = { Text("Join online") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (wifiConnecting) "Connecting…" else "Type the room code shown on your friend's phone.")
                    OutlinedTextField(value = onlineCodeInput, onValueChange = { onlineCodeInput = ArgentumCloudLink.cleanCode(it).take(8) },
                        label = { Text("Room code") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = !wifiConnecting && onlineCodeInput.length >= 6, onClick = { joinOnlineGame(onlineCodeInput) }) { Text("JOIN") } },
            dismissButton = { TextButton(enabled = !wifiConnecting, onClick = { onlineJoinOpen = false }) { Text("CLOSE") } },
        )
    }
    if (wifiJoinOpen) {
        AlertDialog(
            onDismissRequest = { if (!wifiConnecting) wifiJoinOpen = false },
            title = { Text("Join a Wi-Fi game") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        wifiConnecting -> Text("Connecting…")
                        wifiSearching -> Text("Looking for a phone hosting on this Wi-Fi…")
                        wifiFound.isEmpty() -> Text("No host found yet. Check the other phone is hosting and on the same Wi-Fi, then search again, or type its address.")
                    }
                    wifiFound.forEach { found ->
                        Button(enabled = !wifiConnecting, onClick = { joinWifiGame(found.address) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Join ${found.name}")
                        }
                    }
                    OutlinedTextField(
                        value = wifiAddressInput, onValueChange = { wifiAddressInput = it.trim().take(40) },
                        label = { Text("Host address (e.g. 192.168.1.20)") }, singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !wifiConnecting && wifiAddressInput.isNotBlank(), onClick = { joinWifiGame(wifiAddressInput) }) { Text("JOIN") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !wifiSearching && !wifiConnecting, onClick = { searchWifiHosts() }) { Text("SEARCH AGAIN") }
                    TextButton(enabled = !wifiConnecting, onClick = { wifiJoinOpen = false }) { Text("CLOSE") }
                }
            },
        )
    }
}


/** A short message flashed over the table: [text] in [color] (ARGB), fading out. */
data class FlashText(val text: String, val color: Long, val id: Long = System.nanoTime())

/** Vibrates the phone for [ms] milliseconds. */
internal fun TableController.buzz(ms: Long) {
    runCatching {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator)
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** Shows [TableController.flashMessage] big in the middle of the screen, fading out over about 1.5 seconds. */
@Composable
internal fun TableController.FlashMessage() {
    val flash = flashMessage ?: return
    val alpha = androidx.compose.runtime.remember(flash.id) { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(flash.id) {
        alpha.animateTo(0f, androidx.compose.animation.core.tween(1_500, delayMillis = 300))
        if (flashMessage?.id == flash.id) flashMessage = null
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(flash.text, color = androidx.compose.ui.graphics.Color(flash.color).copy(alpha = alpha.value),
            fontSize = androidx.compose.ui.unit.TextUnit(54f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
}
