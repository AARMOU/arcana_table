package com.arcanatable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** Host: prepares your deck, then waits on the network for a friend to join. */
internal fun TableController.startWifiHost() {
    if (offlineStarting || argentumEnabled || wifiServer != null) return
    offlineStarting = true
    argentumLastError = null
    recordDiagnostic("Wi-Fi: hosting requested", DiagnosticCategory.ACTION)
    scope.launch {
        try {
            val (names, commander) = activeDeckCards()
            val (setup, registry) = prepareOfflineSetup(names, commander, null)
            if (setup.you.commander == null) {
                message = "Wi-Fi games use Commander decks. Pick a Commander deck on the home screen first."
                return@launch
            }
            val address = ArgentumLan.localAddress()
            if (address.isBlank()) { message = "This phone isn't on a Wi-Fi network. Connect to Wi-Fi and try again."; return@launch }
            val hostName = lobbyPlayerName.ifBlank { "Host" }
            lateinit var server: ArgentumLanServer
            server = ArgentumLanServer(hostName,
                onGuest = { hello -> scope.launch { acceptWifiGuest(server, hello, setup, registry) } },
                onGuestMessage = { msg -> argentumSocket.runningLocalHost?.receive(msg, com.arcanatable.engine.LocalArgentumHost.GUEST_ID) },
                onGuestLeft = { argentumSocket.runningLocalHost?.receive(JSONObject().put("type", "guestLeft"), com.arcanatable.engine.LocalArgentumHost.GUEST_ID) },
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
    server: ArgentumLanServer,
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
    wifiRole = "host"
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

/** Stops hosting or waiting (the game itself ends through closeArgentumSession). */
internal fun TableController.stopWifi() {
    wifiServer?.close()
    wifiServer = null
    wifiWaitingAddress = null
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

