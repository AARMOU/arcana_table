package com.arcanatable

import androidx.compose.foundation.clickable
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
            val hostName = friendlyName(setup.you.commander)
            val onGuestMessage: (JSONObject) -> Unit = { msg -> argentumSocket.runningLocalHost?.receive(msg, com.arcanatable.engine.LocalArgentumHost.GUEST_ID) }
            val onGuestLeft: () -> Unit = { argentumSocket.runningLocalHost?.receive(JSONObject().put("type", "guestLeft"), com.arcanatable.engine.LocalArgentumHost.GUEST_ID) }
            if (online) {
                lateinit var host: ArgentumCloudHost
                host = ArgentumCloudHost(lobbyCloudEndpoint, hostName,
                    onGuest = { hello -> scope.launch { acceptWifiGuest(host, hello, setup, registry) } },
                    onGuestMessage = onGuestMessage, onGuestLeft = onGuestLeft,
                    onFailed = { reason -> scope.launch { if (wifiServer === host) { stopWifi(); message = "Couldn't open the online room: $reason"; recordDiagnostic("Online host failed: $reason", DiagnosticCategory.ERROR) } } },
                    onNotice = { text -> scope.launch { if (wifiServer === host) handleArgentumMessage(JSONObject().put("type", "error").put("code", ArgentumCloudGuest.LINK_NOTE).put("message", text)) } },
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
    val you = setup.you.copy(name = friendlyName(setup.you.commander))
    argentumSocket.localHostFactory = { emit -> com.arcanatable.engine.LocalArgentumHost(registry, you, seat, emit = emit).also { it.quietOpponentTurns = quietOpponentTurns } }
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


/** Debug-only four-player host path; regular two-player hosting above remains untouched. */
internal fun TableController.startMultiWifiHost() {
    if (offlineStarting || argentumEnabled || wifiServer != null) return
    val count = wifiMultiSeatCount.coerceIn(2, 4)
    val friends = (1 until count).filter(wifiMultiFriendSlots::contains)
    if (friends.isEmpty()) { message = "Choose at least one friend seat."; return }
    wifiMultiConfigOpen = false
    offlineStarting = true
    scope.launch {
        try {
            val (names, commander) = activeDeckCards()
            // AI fallbacks for every opponent seat, so the host can add seats from the lobby.
            val (setup, registry) = prepareOfflineSetup(names, commander, null, aiCount = 3)
            if (setup.you.commander == null) {
                message = "Choose a Commander deck before hosting a multiplayer game."
                return@launch
            }
            val you = setup.you.copy(name = friendlyName(setup.you.commander))
            val room = ArgentumWaitingLobby(you, listOf(setup.ai) + setup.extraAis, count)
            for (index in 1 until count) {
                room.setKind(index, if (index in friends) ArgentumWaitingLobby.Kind.FRIEND
                else ArgentumWaitingLobby.Kind.AI)
            }
            val address = ArgentumLan.localAddress()
            if (address.isBlank()) {
                message = "Connect this phone to Wi-Fi before hosting."
                return@launch
            }
            lateinit var server: ArgentumLanMultiServer
            server = ArgentumLanMultiServer(friendlyName(commander), room.friendSeatIds(),
                onGuest = { id, hello -> scope.launch {
                    acceptMultiWifiGuest(server, room, id, hello, registry)
                } },
                onGuestMessage = { id, packet ->
                    if (packet.optString("type") == "lanReady") scope.launch {
                        if (wifiServer === server) markMultiGuestReady(room, id, packet.optBoolean("ready"))
                    } else argentumSocket.runningLocalHost?.receive(packet, id)
                },
                onGuestLeft = { id -> scope.launch {
                    if (wifiServer === server) {
                        val starting = wifiMultiStartDepartures
                        if (starting != null) starting += id
                        else if (argentumSocket.runningLocalHost == null) {
                            room.guestLeft(id)
                            publishMultiLobby()
                        } else {
                            argentumSocket.runningLocalHost?.receive(
                                JSONObject().put("type", "guestLeft").put("seatId", id.value),
                                com.arcanatable.engine.LocalArgentumHost.HUMAN_ID)
                        }
                    }
                } },
            )
            server.start()
            val bound = withContext(Dispatchers.IO) { server.awaitReady(10_000) }
            if (!bound) {
                message = "Couldn't start the Wi-Fi multiplayer host: ${server.bindError?.javaClass?.simpleName ?: "socket not ready"}"
                server.close()
                return@launch
            }
            wifiServer = server
            wifiMultiLobby = room
            wifiMultiSetup = setup
            wifiMultiRegistry = registry
            wifiMultiLobbyState = room.lobbyMessage()
            wifiWaitingAddress = address
            recordDiagnostic("Four-player Wi-Fi lobby open; requested ${room.seatCount} seats at $address",
                DiagnosticCategory.CONNECTION)
        } catch (error: Throwable) {
            stopWifi()
            message = "Multiplayer hosting failed: ${error.javaClass.simpleName}: ${error.message?.take(200)}"
            recordDiagnostic("Multiplayer Wi-Fi host failed: ${error.javaClass.simpleName}: ${error.message}", DiagnosticCategory.ERROR)
        } finally {
            offlineStarting = false
        }
    }
}

/** Validate each seat before it may send game actions or receive its own private view. */
private suspend fun TableController.acceptMultiWifiGuest(
    server: ArgentumLanMultiServer,
    room: ArgentumWaitingLobby,
    id: com.wingedsheep.sdk.model.EntityId,
    hello: JSONObject,
    registry: com.wingedsheep.engine.registry.CardRegistry,
) {
    if (wifiServer !== server || wifiMultiLobby !== room) {
        server.rejectGuest(id, "This game is no longer accepting guests.")
        return
    }
    val name = hello.optString("name").ifBlank { "Friend" }.take(32)
    val deck = hello.optJSONArray("deck") ?: JSONArray()
    val names = (0 until deck.length()).map(deck::optString)
    val commander = hello.optString("commander").takeUnless { it.isBlank() || it == "null" }
    val (seat, notes) = withContext(Dispatchers.IO) {
        com.arcanatable.engine.OfflineGameSetup.lanSeat(offlineLibrary(), registry, name, names, commander)
    }
    if (wifiServer !== server || wifiMultiLobby !== room) {
        server.rejectGuest(id, "The host closed the room.")
        return
    }
    if (seat == null || !room.seatGuestAt(id, seat)) {
        server.rejectGuest(id, notes.firstOrNull() ?: "That deck or seat isn't available.")
        return
    }
    if (!server.admitGuest(id)) {
        room.guestLeft(id)
        return
    }
    server.sendTo(id, JSONObject().put("type", "lanSeat").put("seat", id.value).put("name", seat.name))
    notes.forEach { note -> server.sendTo(id, JSONObject().put("type", "error")
        .put("code", "WIFI_NOTE").put("message", note)) }
    publishMultiLobby()
    recordDiagnostic("Multiplayer guest joined seat ${id.value}", DiagnosticCategory.CONNECTION)
}

/** Host alone decides when every requested friend has arrived and gameplay can start. */
internal fun TableController.startMultiWifiGame() {
    val room = wifiMultiLobby ?: return
    val server = wifiServer as? ArgentumLanMultiServer ?: return
    val registry = wifiMultiRegistry ?: return
    val roster = room.start() ?: return
    wifiMultiStartDepartures = mutableSetOf()
    scope.launch {
        try {
            wifiRole = "host"
            wifiWaitingAddress = null
            argentumSocket.localHostFactory = { emit ->
                com.arcanatable.engine.LocalArgentumHost(
                    registry, roster.players[0], roster.opponent,
                    extraOpponents = roster.extraOpponents, emit = emit
                ).also { it.quietOpponentTurns = quietOpponentTurns }
            }
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
            var attempt = 0
            while (argentumSocket.runningLocalHost == null && attempt++ < 20) delay(100)
            val host = argentumSocket.runningLocalHost
                ?: error("The local Argentum engine did not start.")
            for ((id, seat) in roster.humanGuests) {
                host.setGuest(id, seat) { packet -> server.sendTo(id, packet) }
            }
            releaseMultiStartDepartures(host)
            argentumSocket.createCommanderPracticeLobby(vsAi = false)
            wifiMultiLobbyState = null
            recordDiagnostic("Wi-Fi Commander started with ${roster.players.size} seats and ${roster.humanGuests.size + 1} humans",
                DiagnosticCategory.ENGINE)
        } catch (error: Throwable) {
            wifiMultiStartDepartures = null
            message = "Could not start the multiplayer engine: ${error.message ?: error.javaClass.simpleName}"
            argentumSocket.close()
            stopWifi()
            argentumEnabled = false
            homeOpen = true
        }
    }
}


/** Production-capable internet multiplayer with private, directed Worker relay. */
internal fun TableController.startMultiOnlineHost() {
    if (offlineStarting || argentumEnabled || wifiServer != null) return
    val count = wifiMultiSeatCount.coerceIn(2, 4)
    val friends = (1 until count).filter(wifiMultiFriendSlots::contains)
    if (friends.isEmpty()) { message = "Choose at least one friend seat."; return }
    wifiMultiConfigOpen = false
    offlineStarting = true
    scope.launch {
        try {
            val (names, commander) = activeDeckCards()
            // AI fallbacks for every opponent seat, so the host can add seats from the lobby.
            val (setup, registry) = prepareOfflineSetup(names, commander, null, aiCount = 3)
            if (setup.you.commander == null) {
                message = "Choose a Commander deck before hosting."
                return@launch
            }
            val you = setup.you.copy(name = friendlyName(setup.you.commander))
            val room = ArgentumWaitingLobby(you, listOf(setup.ai) + setup.extraAis, count)
            for (index in 1 until count) room.setKind(index,
                if (index in friends) ArgentumWaitingLobby.Kind.FRIEND else ArgentumWaitingLobby.Kind.AI)
            val kinds = room.transportKinds()
            lateinit var host: ArgentumCloudMultiHost
            host = ArgentumCloudMultiHost(lobbyCloudEndpoint, friendlyName(commander), kinds,
                onGuest = { id, hello -> scope.launch {
                    acceptMultiOnlineGuest(host, room, id, hello, registry)
                } },
                onGuestMessage = { id, packet ->
                    if (packet.optString("type") == "lanReady") scope.launch {
                        if (wifiServer === host) markMultiGuestReady(room, id, packet.optBoolean("ready"))
                    } else argentumSocket.runningLocalHost?.receive(packet, id)
                },
                onGuestLeft = { id -> scope.launch {
                    if (wifiServer === host) {
                        val starting = wifiMultiStartDepartures
                        if (starting != null) starting += id
                        else if (argentumSocket.runningLocalHost == null) {
                            room.guestLeft(id)
                            // Release only this seat's server-side claim; rotate its
                            // credential before another phone may join this slot.
                            host.rejectGuest(id)
                            publishMultiLobby()
                        } else argentumSocket.runningLocalHost?.receive(
                            JSONObject().put("type", "guestLeft").put("seatId", id.value),
                            com.arcanatable.engine.LocalArgentumHost.HUMAN_ID)
                    }
                } },
                onFailed = { reason -> scope.launch {
                    if (wifiServer === host) {
                        stopWifi()
                        message = "Internet multiplayer room failed: $reason"
                        recordDiagnostic("Online multiplayer failed: $reason", DiagnosticCategory.ERROR)
                    }
                } },
                onNotice = { note -> scope.launch {
                    if (wifiServer === host) message = note
                } },
            )
            host.onRoomReady = {
                scope.launch {
                    if (wifiServer === host) {
                        onlineRoomCode = host.code
                        wifiMultiLobbyState = room.lobbyMessage()
                        recordDiagnostic("Internet Commander lobby ready", DiagnosticCategory.CONNECTION)
                    }
                }
            }
            wifiServer = host
            wifiMultiLobby = room
            wifiMultiSetup = setup
            wifiMultiRegistry = registry
            wifiMultiLobbyState = room.lobbyMessage()
            host.start()
        } catch (error: Throwable) {
            stopWifi()
            message = "Online Commander failed: ${error.javaClass.simpleName}: ${error.message?.take(200)}"
        } finally {
            offlineStarting = false
        }
    }
}

private suspend fun TableController.acceptMultiOnlineGuest(
    host: ArgentumCloudMultiHost,
    room: ArgentumWaitingLobby,
    id: com.wingedsheep.sdk.model.EntityId,
    hello: JSONObject,
    registry: com.wingedsheep.engine.registry.CardRegistry,
) {
    if (wifiServer !== host || wifiMultiLobby !== room) return
    val guestName = hello.optString("name").ifBlank { "Friend" }.take(32)
    val deck = hello.optJSONArray("deck") ?: JSONArray()
    val cards = (0 until deck.length()).map(deck::optString)
    val commander = hello.optString("commander").takeUnless { it.isBlank() || it == "null" }
    val (seat, notes) = withContext(Dispatchers.IO) {
        com.arcanatable.engine.OfflineGameSetup.lanSeat(
            offlineLibrary(), registry, guestName, cards, commander)
    }
    if (wifiServer !== host || wifiMultiLobby !== room) return
    if (seat == null || !room.seatGuestAt(id, seat)) {
        host.sendTo(id, JSONObject().put("type", "error").put("code", "WIFI")
            .put("message", notes.firstOrNull() ?: "That seat or deck is not available."))
        host.rejectGuest(id)
        return
    }
    host.sendTo(id, JSONObject().put("type", "lanSeat")
        .put("seat", id.value).put("name", seat.name))
    notes.forEach { host.sendTo(id, JSONObject().put("type", "error")
        .put("code", "WIFI_NOTE").put("message", it)) }
    publishMultiLobby()
    recordDiagnostic("Remote human admitted to ${id.value}", DiagnosticCategory.CONNECTION)
}

internal fun TableController.startMultiOnlineGame() {
    val room = wifiMultiLobby ?: return
    val server = wifiServer as? ArgentumCloudMultiHost ?: return
    val registry = wifiMultiRegistry ?: return
    val roster = room.start() ?: return
    wifiMultiStartDepartures = mutableSetOf()
    scope.launch {
        try {
            wifiRole = "online-host"
            onlineRoomCode = null
            argentumSocket.localHostFactory = { emit ->
                com.arcanatable.engine.LocalArgentumHost(
                    registry, roster.players[0], roster.opponent, emit = emit,
                    extraOpponents = roster.extraOpponents,
                ).also { it.quietOpponentTurns = quietOpponentTurns }
            }
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
            var tries = 0
            while (argentumSocket.runningLocalHost == null && tries++ < 20) delay(100)
            val engine = argentumSocket.runningLocalHost
                ?: error("Authoritative Argentum engine could not start.")
            for ((id, seat) in roster.humanGuests) {
                engine.setGuest(id, seat) { packet -> server.sendTo(id, packet) }
            }
            releaseMultiStartDepartures(engine)
            argentumSocket.createCommanderPracticeLobby(vsAi = false)
            wifiMultiLobbyState = null
            recordDiagnostic("Internet Commander started with ${roster.players.size} seats",
                DiagnosticCategory.ENGINE)
        } catch (e: Throwable) {
            wifiMultiStartDepartures = null
            argentumSocket.close()
            stopWifi()
            argentumEnabled = false
            homeOpen = true
            message = "Could not start online Commander: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}

/** Guests on any network join by code; the relay assigns and protects their seat. */
internal fun TableController.joinMultiOnlineGame(code: String) {
    val clean = ArgentumCloudLink.cleanCode(code)
    if (wifiConnecting || argentumEnabled || clean.length !in 6..8) return
    wifiConnecting = true
    val (names, commander) = activeDeckCards()
    if (commander == null) {
        wifiConnecting = false
        message = "Pick a Commander deck before joining online multiplayer."
        return
    }
    val hello = JSONObject().put("type", "lanHello")
        .put("name", friendlyName(commander)).put("deck", JSONArray(names))
        .put("commander", commander)
    val guest = ArgentumCloudMultiGuest(lobbyCloudEndpoint, clean, friendlyName(commander))
    argentumSocket.attachLanGuest(guest)
    guest.connect { failure -> scope.launch {
        wifiConnecting = false
        if (failure != null) {
            guest.close()
            argentumSocket.close()
            message = "Couldn't join the multiplayer room: $failure"
            return@launch
        }
        onlineJoinOpen = false
        wifiRole = "online-guest"
        wifiGuestWaiting = true
        wifiGuestLobbyState = null
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
        // The session receiver is active before the host can validate or reply.
        if (!guest.send(hello)) {
            argentumSocket.close()
            stopWifi()
            argentumEnabled = false
            homeOpen = true
            message = "Could not submit your Commander deck to the online host."
            return@launch
        }
        message = "Joined online Commander; waiting for host and other players."
        recordDiagnostic("Joined directed-relay multiplayer room", DiagnosticCategory.CONNECTION)
    } }
}

/**
 * Friends who dropped while START was binding seats: the engine registered them as people, so
 * tell it they left (it hands each seat to the AI) instead of leaving seats nobody will play.
 * Runs on the main thread after every setGuest call is queued, so the engine sees them in order.
 */
internal fun TableController.releaseMultiStartDepartures(host: com.arcanatable.engine.LocalArgentumHost) {
    val departed = wifiMultiStartDepartures.orEmpty().toList()
    wifiMultiStartDepartures = null
    for (id in departed) {
        host.receive(JSONObject().put("type", "guestLeft").put("seatId", id.value),
            com.arcanatable.engine.LocalArgentumHost.HUMAN_ID)
    }
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
            val hello = JSONObject().put("type", "lanHello").put("name", friendlyName(commander))
                .put("deck", JSONArray(names)).put("commander", commander)
            argentumSocket.attachLanGuest(guest)
            withContext(Dispatchers.IO) { guest.connect(address, hello) }
            wifiJoinOpen = false
            wifiRole = "guest"
            wifiGuestWaiting = true
            wifiGuestLobbyState = null
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
    val hello = JSONObject().put("type", "lanHello").put("name", friendlyName(commander))
        .put("deck", JSONArray(names)).put("commander", commander)
    val guest = ArgentumCloudGuest(lobbyCloudEndpoint, clean, friendlyName(commander))
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
    wifiMultiLobby = null
    wifiMultiSetup = null
    wifiMultiRegistry = null
    wifiMultiLobbyState = null
    wifiGuestLobbyState = null
    wifiGuestWaiting = false
    wifiGuestSeatId = null
    multiLobbySeatChangePending = false
    wifiMultiStartDepartures = null
}

/* ---------- Multiplayer lobby: roster, ready-up and host controls (before START) ---------- */

/** Host: refresh the lobby on this phone and send the roster (no decks) to every seated guest. */
internal fun TableController.publishMultiLobby() {
    val room = wifiMultiLobby ?: return
    val state = room.lobbyMessage()
    wifiMultiLobbyState = state
    when (val server = wifiServer) {
        is ArgentumLanMultiServer -> server.broadcastLobby(state)
        is ArgentumCloudMultiHost -> server.broadcastLobby(state)
        else -> Unit
    }
}

/** Host: a seated guest marked themselves ready or not. Ignored once the game has started. */
private fun TableController.markMultiGuestReady(
    room: ArgentumWaitingLobby, id: com.wingedsheep.sdk.model.EntityId, ready: Boolean,
) {
    if (wifiMultiLobby !== room || argentumSocket.runningLocalHost != null) return
    if (room.setReady(id, ready)) {
        publishMultiLobby()
        recordDiagnostic("Lobby seat ${id.value} ${if (ready) "ready" else "not ready"}", DiagnosticCategory.CONNECTION)
    }
}

/** Host: take a seated guest out of the lobby. Their seat reopens for another friend. */
internal fun TableController.removeMultiGuest(seatId: String) {
    val room = wifiMultiLobby ?: return
    val id = com.wingedsheep.sdk.model.EntityId(seatId)
    if (argentumSocket.runningLocalHost != null || !room.isSeated(id)) return
    val reason = "The host removed you from the lobby."
    when (val server = wifiServer) {
        is ArgentumLanMultiServer -> server.rejectGuest(id, reason)
        is ArgentumCloudMultiHost -> {
            server.sendTo(id, JSONObject().put("type", "error").put("code", "WIFI").put("message", reason))
            server.rejectGuest(id)
        }
        else -> return
    }
    room.removeGuest(id)
    publishMultiLobby()
    recordDiagnostic("Host removed lobby seat ${id.value}", DiagnosticCategory.CONNECTION)
}

/** Host: make an open opponent seat wait for a friend, or play it as AI. */
internal fun TableController.setMultiSeatFriend(index: Int, friend: Boolean) {
    val room = wifiMultiLobby ?: return
    val previous = room.kindOf(index) ?: return
    val wanted = if (friend) ArgentumWaitingLobby.Kind.FRIEND else ArgentumWaitingLobby.Kind.AI
    if (previous == wanted) return
    changeMultiSeats(room, apply = { it.setKind(index, wanted) }, revert = { it.setKind(index, previous) })
}

/** Host: change the number of seats (2–4). Never drops a seated guest. */
internal fun TableController.resizeMultiLobby(count: Int) {
    val room = wifiMultiLobby ?: return
    val previous = room.seatCount
    if (count == previous) return
    changeMultiSeats(room, apply = { it.resize(count) }, revert = { it.resize(previous) })
}

/**
 * Applies a seat change to the lobby, then to the transport. Wi-Fi updates the seats its server
 * accepts; online asks the room service, which refuses if a phone is in an affected seat. A
 * refused change is undone so the lobby always matches the seats phones can actually take.
 */
private fun TableController.changeMultiSeats(
    room: ArgentumWaitingLobby,
    apply: (ArgentumWaitingLobby) -> Boolean,
    revert: (ArgentumWaitingLobby) -> Unit,
) {
    if (argentumSocket.runningLocalHost != null || multiLobbySeatChangePending) return
    if (!apply(room)) { message = "That seat can't change while a player is in it."; return }
    when (val server = wifiServer) {
        is ArgentumLanMultiServer -> {
            if (!server.updateGuestSeats(room.friendSeatIds())) {
                revert(room)
                message = "A friend is joining that seat right now. Try again in a moment."
            }
            publishMultiLobby()
        }
        is ArgentumCloudMultiHost -> {
            multiLobbySeatChangePending = true
            publishMultiLobby()
            val sent = server.reconfigure(room.transportKinds()) { ok, reason ->
                scope.launch {
                    if (wifiServer !== server || wifiMultiLobby !== room) return@launch
                    multiLobbySeatChangePending = false
                    if (!ok) {
                        revert(room)
                        message = "Couldn't change seats: ${reason ?: "the room service refused."}"
                    }
                    publishMultiLobby()
                }
            }
            if (!sent) {
                multiLobbySeatChangePending = false
                revert(room)
                publishMultiLobby()
                message = "Couldn't reach the room service to change seats. Try again."
            }
        }
        else -> { revert(room); publishMultiLobby() }
    }
}

/** Guest: tell the host this phone is ready (or no longer ready) to start. */
internal fun TableController.setGuestLobbyReady(ready: Boolean) {
    if (wifiGuestSeatId == null) return
    if (!argentumSocket.send(JSONObject().put("type", "lanReady").put("ready", ready))) {
        message = "Couldn't reach the host. Check your connection and try again."
    }
}

/** Host's "waiting for your friend" card and the guest's "find a host" dialog. */
@Composable
internal fun TableController.WifiDialogs() {
    // 2–4 player rooms (host and guests) use the full lobby screen; the dialogs below are the
    // one-guest paths and the host's room setup.
    MultiplayerLobbyScreen()
    if (wifiMultiConfigOpen) {
        AlertDialog(
            onDismissRequest = { wifiMultiConfigOpen = false },
            title = { Text(if (wifiMultiOnline) "Host online Commander" else "Host Wi-Fi Commander") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (wifiMultiOnline) "Choose 2–4 seats. Friends can join across different networks using the room code." else "Choose 2–4 seats. Friends must use the same Wi-Fi.")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (2..4).forEach { size ->
                            TextButton(onClick = {
                                wifiMultiSeatCount = size
                                wifiMultiFriendSlots = wifiMultiFriendSlots.filter { it < size }
                                    .toSet().ifEmpty { setOf(1) }
                            }) { Text(if (wifiMultiSeatCount == size) "[$size]" else "$size") }
                        }
                    }
                    (1 until wifiMultiSeatCount).forEach { seat ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Seat ${seat + 1}")
                            TextButton(onClick = {
                                wifiMultiFriendSlots = if (seat in wifiMultiFriendSlots)
                                    wifiMultiFriendSlots - seat else wifiMultiFriendSlots + seat
                            }) {
                                Text(if (seat in wifiMultiFriendSlots) "Friend · change to AI"
                                    else "AI · change to friend")
                            }
                        }
                    }
                    Text("You can change seats in the lobby. Start the game once every friend has joined and is ready.")
                }
            },
            confirmButton = { TextButton(
                enabled = (1 until wifiMultiSeatCount).any(wifiMultiFriendSlots::contains),
                onClick = { if (wifiMultiOnline) startMultiOnlineHost() else startMultiWifiHost() }
            ) { Text("HOST LOBBY") } },
            dismissButton = { TextButton(onClick = { wifiMultiConfigOpen = false }) { Text("CANCEL") } },
        )
    }
    wifiWaitingAddress?.let { address ->
        if (wifiMultiLobbyState == null) {
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
    }
    // One-guest online host only; 2–4 player online rooms show the lobby screen.
    onlineRoomCode?.takeIf { wifiMultiLobbyState == null }?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Waiting for your friend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share this code with your friend. They choose Join online.")
                    Text(code, fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Text("Keep this screen open until the other player joins.")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { stopWifi() }) { Text("CANCEL") } },
        )
    }
    if (onlineJoinOpen) {
        AlertDialog(
            onDismissRequest = { if (!wifiConnecting) onlineJoinOpen = false },
            title = { Text(if (wifiMultiOnlineJoin) "Join online Commander" else "Join online") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (wifiConnecting) "Connecting…" else "Type the room code shown on your friend's phone.")
                    OutlinedTextField(value = onlineCodeInput, onValueChange = { onlineCodeInput = ArgentumCloudLink.cleanCode(it).take(8) },
                        label = { Text("Room code") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = !wifiConnecting && onlineCodeInput.length >= 6, onClick = { if (wifiMultiOnlineJoin) joinMultiOnlineGame(onlineCodeInput) else joinOnlineGame(onlineCodeInput) }) { Text("JOIN") } },
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            modifier = Modifier.clickable { if (flashMessage?.id == flash.id) flashMessage = null })
    }
}
