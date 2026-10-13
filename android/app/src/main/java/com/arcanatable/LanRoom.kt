package com.arcanatable

import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Small same-Wi-Fi room transport for early multiplayer testing. */
private const val ROOM_PORT = 38471
private const val HEARTBEAT_INTERVAL_SECONDS = 10L
private const val HEARTBEAT_TIMEOUT_SECONDS = 60L

private fun socketAddress(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedByDescending { network ->
            val name = network.name.lowercase()
            if (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap")) 1 else 0
        }
        .asSequence()
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress.orEmpty()
}.getOrDefault("")

private fun sendLine(writer: BufferedWriter, value: String) {
    synchronized(writer) {
        writer.write(value)
        writer.newLine()
        writer.flush()
    }
}

class LanRoomHost(
    private val roomCode: String,
    private val password: String,
    private val onStarted: (String) -> Unit,
    private val onSnapshot: (String) -> Unit,
    private val onPeerJoined: (seat: Int, name: String) -> Unit,
    private val onPeerLeft: (seat: Int) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onError: (String) -> Unit,
    initialSnapshot: String = "{}",
    private val onHostMigrationFinished: () -> Unit = {}
) {
    private val running = AtomicBoolean(true)
    private val peers = CopyOnWriteArrayList<Peer>()
    private val sendQueue = Executors.newSingleThreadExecutor { task -> Thread(task, "ArcanaRoomHostSend").apply { isDaemon = true } }
    private val reservedSeats = mutableSetOf<Int>()
    @Volatile private var transferTargetSeat = -1
    @Volatile private var server: ServerSocket? = null
    @Volatile private var latestSnapshot = initialSnapshot
    @Volatile var address: String = ""
        private set

    fun start() {
        Thread {
            try {
                val socketServer = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(ROOM_PORT)) }
                server = socketServer
                address = socketAddress()
                onStarted(address)
                while (running.get()) {
                    val socket = socketServer.accept()
                    socket.tcpNoDelay = true
                    Thread { serve(socket) }.start()
                }
            } catch (e: Exception) {
                if (running.get()) onError(e.message ?: "Could not open the room port.")
            }
        }.start()
    }

    fun publish(snapshot: String) {
        val authoritative = synchronized(this) {
            latestSnapshot = mergeHostRoomState(latestSnapshot, snapshot, peers.map { it.seat }.toSet())
            latestSnapshot
        }
        broadcastState(authoritative)
    }

    fun close() {
        running.set(false)
        sendQueue.shutdownNow()
        runCatching { server?.close() }
        peers.forEach { peer ->
            peer.removedByHost = true
            runCatching { peer.socket.close() }
        }
        peers.clear()
    }

    fun reserveSeat(seat: Int) {
        synchronized(this) { reservedSeats.add(seat) }
    }

    /** Starts a graceful transfer to the next occupied seat while keeping the current match intact. */
    fun beginHostTransfer(): Int? {
        val successor = peers.minByOrNull { it.seat } ?: return null
        transferTargetSeat = successor.seat
        return runCatching {
            sendLine(successor.writer, JSONObject().put("type", "host-transfer")
                .put("seat", successor.seat).put("payload", latestSnapshot).toString())
            successor.seat
        }.getOrNull()
    }

    /** Removes either a connected player or a host-reserved AI seat, then closes seat gaps. */
    fun removeSeat(seat: Int) {
        val removed = peers.firstOrNull { it.seat == seat }
        removed?.removedByHost = true
        synchronized(this) {
            // Keep the canonical room snapshot in the same seat order as the
            // peer list. Otherwise the next host publish can merge a shifted
            // peer with the removed player's stale deck and board.
            latestSnapshot = removeRoomSeat(latestSnapshot, seat)
            if (removed != null) peers.remove(removed)
            peers.filter { it.seat > seat }.forEach { it.seat -= 1 }
            val shiftedReserved = reservedSeats.mapNotNull { old ->
                when { old == seat -> null; old > seat -> old - 1; else -> old }
            }.toSet()
            reservedSeats.clear()
            reservedSeats.addAll(shiftedReserved)
        }
        runCatching { removed?.socket?.close() }
        peers.filter { it.seat >= seat }.forEach { peer ->
            queueWrite(peer, JSONObject().put("type", "seat").put("seat", peer.seat))
        }
    }

    private fun queueWrite(peer: Peer, packet: JSONObject) {
        if (!running.get()) return
        try {
            sendQueue.execute {
                if (running.get() && !peer.socket.isClosed) runCatching { sendLine(peer.writer, packet.toString()) }
                    .onFailure { onEvent("Host could not send a lobby update to seat ${peer.seat + 1}: ${it.message ?: it.javaClass.simpleName}") }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun serve(socket: Socket) {
        var peer: Peer? = null
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val request = reader.readLine()?.let(::JSONObject) ?: return
            if (request.optString("type") != "join" ||
                !request.optString("code").equals(roomCode, true) ||
                request.optString("password") != password) {
                sendLine(writer, JSONObject().put("type", "rejected").put("reason", "Room code or password is incorrect.").toString())
                return
            }
            val requestedSeat = request.optInt("preferredSeat", -1)
            val seat = synchronized(this) {
                val occupied = peers.map { it.seat }.toSet()
                if (requestedSeat in reservedSeats && requestedSeat !in occupied) {
                    reservedSeats.remove(requestedSeat)
                    requestedSeat
                } else {
                    val unavailable = occupied + reservedSeats
                    (1..3).firstOrNull { it !in unavailable } ?: -1
                }
            }
            if (seat < 0) {
                sendLine(writer, JSONObject().put("type", "rejected").put("reason", "This room already has four players.").toString())
                return
            }
            peer = Peer(socket, writer, seat)
            peers += peer
            // Clients send a heartbeat every few seconds. A read timeout removes
            // stale seats when a phone disappears without closing its TCP socket.
            socket.soTimeout = TimeUnit.SECONDS.toMillis(HEARTBEAT_TIMEOUT_SECONDS).toInt()
            socket.keepAlive = true
            socket.tcpNoDelay = true
            sendLine(writer, JSONObject().put("type", "welcome").put("seat", seat).toString())
            sendLine(writer, JSONObject().put("type", "state").put("payload", redactPrivateZones(latestSnapshot, seat)).toString())
            onPeerJoined(seat, request.optString("name").ifBlank { "PLAYER ${seat + 1}" }.take(24))
            while (running.get() && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val packet = JSONObject(line)
                when (packet.optString("type")) {
                    "ping" -> sendLine(peer.writer, JSONObject().put("type", "pong").toString())
                    "host-ready" -> {
                        if (peer.seat != transferTargetSeat) {
                            sendLine(peer.writer, JSONObject().put("type", "migration-rejected").put("reason", "Only the selected player can take over the room.").toString())
                            continue
                        }
                        val newAddress = packet.optString("address").trim()
                        if (newAddress.isBlank()) {
                            sendLine(peer.writer, JSONObject().put("type", "migration-rejected").put("reason", "The new host did not provide a Wi-Fi address.").toString())
                            continue
                        }
                        // The game state was sent to the successor in the private handoff
                        // packet. Tell every remaining device its new host and seat.
                        fun transferredSeat(oldSeat: Int): Int = when {
                            oldSeat == 0 -> -1
                            oldSeat == transferTargetSeat -> 0
                            oldSeat < transferTargetSeat -> oldSeat
                            else -> oldSeat - 1
                        }
                        peers.toList().forEach { other ->
                            if (other !== peer) runCatching {
                                sendLine(other.writer, JSONObject().put("type", "redirect")
                                    .put("address", newAddress).put("seat", transferredSeat(other.seat)).toString())
                            }
                        }
                        runCatching { sendLine(peer.writer, JSONObject().put("type", "migration-accepted").toString()) }
                        onEvent("Host role transferred to seat 2")
                        onHostMigrationFinished()
                        close()
                    }
                    "state" -> {
                        onEvent("Received game-state update from seat ${peer.seat + 1}")
                        val payload = packet.optString("payload", "{}")
                        val mergedSnapshot = synchronized(this) {
                            latestSnapshot = mergeRoomState(latestSnapshot, payload, peer.seat)
                            latestSnapshot
                        }
                        onSnapshot(mergedSnapshot)
                        // Echo the canonical, merged room state to the sender too. This
                        // confirms that its deck/readiness update reached the host.
                        broadcastState(mergedSnapshot)
                    }
                    "ready" -> {
                        onEvent("Received ready status from seat ${peer.seat + 1}")
                        val requestedReady = packet.optBoolean("ready")
                        val requestId = packet.optInt("requestId", -1)
                        val acceptedReady = synchronized(this) {
                            val players = JSONObject(latestSnapshot).optJSONArray("players") ?: JSONArray()
                            val player = players.optJSONObject(peer.seat)
                            val hasDeckAndOpeningHand = player?.optBoolean("deckLoaded") == true && player.optBoolean("openingHandKept")
                            !requestedReady || hasDeckAndOpeningHand
                        }
                        val effectiveReady = requestedReady && acceptedReady
                        val updatedSnapshot = synchronized(this) {
                            val current = JSONObject(latestSnapshot)
                            val ready = current.optJSONArray("readyPlayers") ?: JSONArray()
                            val readySeats = (0 until ready.length()).map { ready.optInt(it) }.toMutableSet()
                            if (effectiveReady) readySeats.add(peer.seat) else readySeats.remove(peer.seat)
                            current.put("readyPlayers", JSONArray().apply { readySeats.sorted().forEach { put(it) } })
                            latestSnapshot = current.toString()
                            latestSnapshot
                        }
                        sendLine(peer.writer, JSONObject().put("type", "ready-ack").put("seat", peer.seat)
                            .put("ready", effectiveReady).put("accepted", acceptedReady).put("requestId", requestId).toString())
                        onSnapshot(updatedSnapshot)
                        broadcastState(updatedSnapshot)
                    }
                    "deck-status" -> {
                        onEvent("Received deck status from seat ${peer.seat + 1}: loaded=${packet.optBoolean("loaded")}")
                        val loaded = packet.optBoolean("loaded")
                        val requestId = packet.optInt("requestId", -1)
                        val updatedSnapshot = synchronized(this) {
                            val current = JSONObject(latestSnapshot)
                            val players = current.optJSONArray("players") ?: JSONArray()
                            while (players.length() <= peer.seat) {
                                players.put(JSONObject().put("name", "PLAYER ${players.length() + 1}"))
                            }
                            val player = players.optJSONObject(peer.seat) ?: JSONObject()
                            player.put("name", packet.optString("name").ifBlank { player.optString("name", "PLAYER ${peer.seat + 1}") }.take(24))
                            player.put("isOnline", true)
                            player.put("deckLoaded", loaded)
                            player.put("mulligansTaken", 0)
                            player.put("openingHandKept", false)
                            if (packet.has("commanderName")) player.put("commanderName", packet.opt("commanderName"))
                            players.put(peer.seat, player)
                            current.put("players", players)
                            val ready = current.optJSONArray("readyPlayers") ?: JSONArray()
                            val readySeats = (0 until ready.length()).map { ready.optInt(it) }.toMutableSet()
                            readySeats.remove(peer.seat)
                            current.put("readyPlayers", JSONArray().apply { readySeats.sorted().forEach { put(it) } })
                            latestSnapshot = current.toString()
                            latestSnapshot
                        }
                        sendLine(peer.writer, JSONObject().put("type", "deck-status-ack").put("seat", peer.seat)
                            .put("loaded", loaded).put("requestId", requestId).toString())
                        onSnapshot(updatedSnapshot)
                        broadcastState(updatedSnapshot)
                    }
                }
            }
        } catch (_: SocketException) {
        } catch (_: SocketTimeoutException) {
            if (running.get()) onEvent("No heartbeat from seat ${(peer?.seat ?: -1) + 1}; closing the stale connection.")
        } catch (e: Exception) {
            if (running.get()) onError(e.message ?: "A player connection ended unexpectedly.")
        } finally {
            if (peer != null) {
                peers.remove(peer)
                if (!peer.removedByHost) onPeerLeft(peer.seat)
            }
            runCatching { socket.close() }
        }
    }

    private fun broadcastState(snapshot: String, except: Socket? = null) {
        peers.toList().forEach { peer ->
            if (peer.socket !== except) runCatching {
                val personal = redactPrivateZones(snapshot, peer.seat)
                queueWrite(peer, JSONObject().put("type", "state").put("payload", personal))
            }
        }
    }

    private class Peer(val socket: Socket, val writer: BufferedWriter, @Volatile var seat: Int, @Volatile var removedByHost: Boolean = false)
}

/** Removes a seat and remaps every snapshot field that is indexed by seat. */
private fun removeRoomSeat(source: String, removedSeat: Int): String = runCatching {
    val state = JSONObject(source)
    val oldPlayers = state.optJSONArray("players") ?: JSONArray()
    if (removedSeat !in 0 until oldPlayers.length()) return source
    val removedPlayerId = oldPlayers.optJSONObject(removedSeat)?.optString("id").orEmpty()

    fun remap(seat: Int): Int? = when {
        seat < 0 -> null
        seat == removedSeat -> 0
        seat > removedSeat -> seat - 1
        else -> seat
    }
    fun remapArray(key: String) {
        val old = state.optJSONArray(key) ?: return
        val shifted = JSONArray()
        for (index in 0 until old.length()) {
            val value = old.optInt(index, -1)
            if (value != removedSeat) remap(value)?.let { shifted.put(it) }
        }
        state.put(key, shifted)
    }

    val players = JSONArray()
    for (index in 0 until oldPlayers.length()) {
        if (index != removedSeat) oldPlayers.optJSONObject(index)?.let { players.put(it) }
    }
    state.put("players", players)
    remapArray("turnOrder")
    remapArray("readyPlayers")
    state.put("activePlayer", remap(state.optInt("activePlayer")) ?: 0)
    state.put("priorityPlayer", remap(state.optInt("priorityPlayer")) ?: 0)

    val proposer = state.optInt("endProposalBy", -1)
    state.put("endProposalBy", if (proposer == removedSeat) -1 else remap(proposer) ?: -1)
    val oldVotes = state.optJSONObject("endVotes") ?: JSONObject()
    val votes = JSONObject()
    if (proposer != removedSeat) {
        oldVotes.keys().forEach { key ->
            val oldSeat = key.toIntOrNull() ?: return@forEach
            if (oldSeat != removedSeat) remap(oldSeat)?.let { votes.put(it.toString(), oldVotes.optBoolean(key)) }
        }
    }
    state.put("endVotes", votes)
    if (removedPlayerId.isNotBlank()) {
        val oldPassed = state.optJSONArray("priorityPassed") ?: JSONArray()
        state.put("priorityPassed", JSONArray().apply {
            for (index in 0 until oldPassed.length()) {
                val id = oldPassed.optString(index)
                if (id != removedPlayerId) put(id)
            }
        })
    }
    state.toString()
}.getOrDefault(source)

/** Keep connected clients' seat-owned player data when the host publishes its UI snapshot. */
private fun mergeHostRoomState(currentSource: String, incomingSource: String, connectedSeats: Set<Int>): String = runCatching {
    val current = JSONObject(currentSource)
    val incoming = JSONObject(incomingSource)
    val currentPlayers = current.optJSONArray("players") ?: JSONArray()
    val incomingPlayers = incoming.optJSONArray("players") ?: JSONArray()
    val mergedPlayers = JSONArray()
    val playerCount = maxOf(currentPlayers.length(), incomingPlayers.length())
    for (seat in 0 until playerCount) {
        val currentPlayer = currentPlayers.optJSONObject(seat)
        val incomingPlayer = incomingPlayers.optJSONObject(seat)
        val selected = if (seat in connectedSeats) currentPlayer ?: incomingPlayer else incomingPlayer ?: currentPlayer
        if (selected != null) {
            val mergedPlayer = JSONObject(selected.toString())
            // The host's UI snapshot can lag behind a newly accepted socket.
            // The server's live peer list is authoritative for connection state;
            // otherwise a connected client's seat can be broadcast as offline
            // on every host snapshot until that client changes its deck state.
            mergedPlayer.put("isOnline", seat == 0 || seat in connectedSeats)
            // On a fresh join, the host UI has the name from the join request
            // while the server's previous snapshot may still contain PLAYER N.
            val joinedName = incomingPlayer?.optString("name").orEmpty()
            if (seat in connectedSeats && joinedName.isNotBlank()) mergedPlayer.put("name", joinedName)
            mergedPlayers.put(mergedPlayer)
        }
    }

    val currentReady = current.optJSONArray("readyPlayers") ?: JSONArray()
    val incomingReady = incoming.optJSONArray("readyPlayers") ?: JSONArray()
    val readySeats = (0 until currentReady.length()).map { currentReady.optInt(it) }
        .filter { it in connectedSeats }.toMutableSet()
    // The host owns its own and AI seats; remote human readiness arrives through
    // the dedicated ready packet and is preserved from the canonical snapshot.
    val hostOwnedReadySeats = (0 until incomingReady.length()).map { incomingReady.optInt(it) }
        .filterNot { it in connectedSeats }
    readySeats.addAll(hostOwnedReadySeats)
    readySeats.removeAll { seat -> seat !in connectedSeats && seat !in hostOwnedReadySeats }

    JSONObject(incoming.toString()).apply {
        put("players", mergedPlayers)
        put("readyPlayers", JSONArray().apply { readySeats.sorted().forEach { put(it) } })
    }.toString()
}.getOrDefault(currentSource)

private fun redactPrivateZones(source: String, viewerSeat: Int): String = runCatching {
    val state = JSONObject(source)
    val players = state.optJSONArray("players") ?: return source
    val safePlayers = JSONArray()
    for (index in 0 until players.length()) {
        val player = players.optJSONObject(index) ?: continue
        val safePlayer = JSONObject(player.toString())
        if (index != viewerSeat) {
            for (zone in listOf("hand", "library")) {
                if (zone == "hand" && player.optBoolean("handRevealedToAll")) continue
                val cards = player.optJSONArray(zone) ?: JSONArray()
                val hidden = JSONArray()
                for (cardIndex in 0 until cards.length()) {
                    val card = cards.optJSONObject(cardIndex)
                    hidden.put(JSONObject().put("id", card?.optInt("id") ?: 0).put("name", "Hidden card").put("type", "Hidden")
                        .put("color", card?.optLong("color") ?: 0xFF263B35L).put("tapped", false).put("isToken", false)
                        .put("counters", JSONObject()).put("isFaceDown", true))
                }
                safePlayer.put(zone, hidden)
            }
        }
        safePlayers.put(safePlayer)
    }
    state.put("players", safePlayers).toString()
}.getOrDefault(source)

/** Merge one seat's update while retaining the other seats' hidden hand and library identities. */
private fun mergeRoomState(currentSource: String, incomingSource: String, senderSeat: Int): String = runCatching {
    val current = JSONObject(currentSource)
    val incoming = JSONObject(incomingSource)
    val currentPlayers = current.optJSONArray("players") ?: JSONArray()
    val incomingPlayers = incoming.optJSONArray("players") ?: JSONArray()
    if (senderSeat !in 0 until currentPlayers.length()) return currentSource
    val merged = JSONArray()
    for (index in 0 until currentPlayers.length()) {
        val base = currentPlayers.optJSONObject(index) ?: JSONObject()
        val update = incomingPlayers.optJSONObject(index)
        // Each client owns only its assigned seat. A client snapshot may update
        // that seat's game data, but cannot rename seats, mark itself as AI, or
        // replace another player's board or hidden zones.
        val chosen = if (index == senderSeat && update != null) JSONObject(update.toString()) else JSONObject(base.toString())
        if (index == senderSeat) {
            listOf("id", "name", "isOnline", "isComputer", "deckLoaded").forEach { key ->
                if (base.has(key)) chosen.put(key, base.get(key)) else chosen.remove(key)
            }
        }
        if (chosen.length() > 0) merged.put(chosen)
    }
    val result = JSONObject(current.toString())
    // Shared turn/session state belongs to the active seat. Stack and priority
    // belong to the player currently holding priority, who may be a non-active
    // player responding to a spell or ability. The host (seat 0) can always
    // publish administrative changes. Lobby readiness uses its own ACK packet.
    val activeSeat = current.optInt("activePlayer", 0)
    if (senderSeat == 0 || senderSeat == activeSeat) {
        listOf("activePlayer", "turnNumber", "phaseIndex", "elapsedGameSeconds", "elapsedTurnSeconds", "turnOrder", "gameLog", "chat", "gameStarted", "endProposalBy", "endVotes", "gameEnded")
            .forEach { key -> if (incoming.has(key)) result.put(key, incoming.get(key)) }
    }
    val prioritySeat = current.optInt("priorityPlayer", activeSeat)
    if (senderSeat == 0 || senderSeat == prioritySeat) {
        listOf("stackActions", "priorityPlayer", "priorityPassed")
            .forEach { key -> if (incoming.has(key)) result.put(key, incoming.get(key)) }
    }
    if (senderSeat == 0 || senderSeat == activeSeat) {
        // Commander damage is entered during the active player's attack step but
        // belongs on the defending seat's card. Permit only that active seat's
        // own commander ID to change its corresponding damage entry.
        val sourcePlayer = incomingPlayers.optJSONObject(senderSeat)
        val sourceId = sourcePlayer?.optString("id").orEmpty()
        if (sourceId.isNotBlank()) {
            for (index in 0 until merged.length()) {
                if (index == senderSeat) continue
                val updatedTarget = incomingPlayers.optJSONObject(index) ?: continue
                val target = merged.optJSONObject(index) ?: continue
                val life = updatedTarget.optInt("life", target.optInt("life", 40)).coerceAtLeast(0)
                target.put("life", life)
                if (life == 0 && !target.optBoolean("isEliminated")) {
                    target.put("isEliminated", true)
                    target.put("eliminationReason", "Life total reached 0")
                }
                val updatedDamage = updatedTarget.optJSONObject("commanderDamageReceived")
                if (updatedDamage?.has(sourceId) == true) {
                    val damage = target.optJSONObject("commanderDamageReceived")?.let { JSONObject(it.toString()) } ?: JSONObject()
                    val amount = updatedDamage.optInt(sourceId).coerceAtLeast(0)
                    damage.put(sourceId, amount)
                    target.put("commanderDamageReceived", damage)
                    if (amount >= 21 && !target.optBoolean("isEliminated")) {
                        target.put("isEliminated", true)
                        target.put("eliminationReason", "21 commander damage from ${sourcePlayer.optString("name", "opponent")}")
                    }
                }
            }
        }
    }
    if ((current.optBoolean("gameStarted") || incoming.optBoolean("gameStarted")) &&
        (0 until merged.length()).count { !(merged.optJSONObject(it)?.optBoolean("isEliminated") ?: false) } <= 1) {
        result.put("gameEnded", true)
    }
    result.put("players", merged)
    // Readiness changes only through the dedicated "ready" packet.
    result.toString()
}.getOrDefault(currentSource)

class LanRoomClient(
    private val onSeatAssigned: (Int) -> Unit,
    private val onSnapshot: (String) -> Unit,
    private val onReadyConfirmed: (seat: Int, ready: Boolean, accepted: Boolean) -> Unit,
    private val onDeckStatusConfirmed: (seat: Int, loaded: Boolean) -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onHostTransfer: (String, Int) -> Unit = { _, _ -> },
    private val onRedirect: (String, Int) -> Unit = { _, _ -> },
    private val onMigrationAccepted: () -> Unit = {}
) {
    private val running = AtomicBoolean(true)
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    private val nextRequestId = java.util.concurrent.atomic.AtomicInteger(1)
    private val sendQueue = Executors.newSingleThreadExecutor { task -> Thread(task, "ArcanaRoomClientSend").apply { isDaemon = true } }
    private val heartbeatQueue: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "ArcanaRoomClientHeartbeat").apply { isDaemon = true } }
    private val lastPongAt = AtomicLong(0L)
    private val heartbeatStarted = AtomicBoolean(false)

    private fun disconnect(reason: String) {
        if (running.getAndSet(false)) {
            onDisconnected(reason)
            sendQueue.shutdownNow()
            heartbeatQueue.shutdownNow()
            runCatching { socket?.close() }
        }
    }

    private fun startHeartbeat() {
        if (!heartbeatStarted.compareAndSet(false, true)) return
        lastPongAt.set(System.currentTimeMillis())
        heartbeatQueue.scheduleAtFixedRate({
            if (!running.get()) return@scheduleAtFixedRate
            val now = System.currentTimeMillis()
            if (now - lastPongAt.get() > TimeUnit.SECONDS.toMillis(HEARTBEAT_TIMEOUT_SECONDS)) {
                disconnect("Connection to the host was lost (no heartbeat response).")
            } else {
                queuePacket(JSONObject().put("type", "ping"), "checking the host connection")
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    private fun writePacket(output: BufferedWriter, packet: JSONObject, action: String): Boolean {
        return try {
            sendLine(output, packet.toString())
            true
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            disconnect("Connection to the host failed while $action: $detail")
            false
        }
    }

    fun connect(host: String, roomCode: String, password: String, playerName: String, preferredSeat: Int? = null) {
        Thread {
            try {
                val connection = Socket()
                connection.keepAlive = true
                connection.tcpNoDelay = true
                // Publish the socket before connect() so Cancel Join can close a
                // still-pending connection attempt, not only an established socket.
                socket = connection
                if (!running.get()) {
                    runCatching { connection.close() }
                    return@Thread
                }
                connection.connect(InetSocketAddress(host.trim(), ROOM_PORT), 8_000)
                connection.soTimeout = 8_000
                connection.tcpNoDelay = true
                val output = BufferedWriter(OutputStreamWriter(connection.getOutputStream(), Charsets.UTF_8))
                writer = output
                val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.UTF_8))
                val joinPacket = JSONObject().put("type", "join").put("code", roomCode).put("password", password).put("name", playerName)
                if (preferredSeat != null) joinPacket.put("preferredSeat", preferredSeat)
                sendLine(output, joinPacket.toString())
                var assigned = false
                var redirected = false
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    val packet = JSONObject(line)
                    when (packet.optString("type")) {
                        "welcome" -> {
                            connection.soTimeout = 0
                            assigned = true
                            startHeartbeat()
                            onSeatAssigned(packet.optInt("seat", -1))
                        }
                        "seat" -> onSeatAssigned(packet.optInt("seat", -1))
                        "rejected" -> {
                            disconnect(packet.optString("reason", "The host rejected this connection."))
                            break
                        }
                        "state" -> onSnapshot(packet.optString("payload", "{}"))
                        "host-transfer" -> onHostTransfer(packet.optString("payload", "{}"), packet.optInt("seat", -1))
                        "redirect" -> {
                            redirected = true
                            onRedirect(packet.optString("address"), packet.optInt("seat", -1))
                            close()
                            break
                        }
                        "migration-accepted" -> {
                            onMigrationAccepted()
                            close()
                            break
                        }
                        "migration-rejected" -> {
                            disconnect(packet.optString("reason", "Host transfer was rejected."))
                            break
                        }
                        "pong" -> lastPongAt.set(System.currentTimeMillis())
                        "ready-ack" -> onReadyConfirmed(packet.optInt("seat", -1), packet.optBoolean("ready"), packet.optBoolean("accepted", true))
                        "deck-status-ack" -> onDeckStatusConfirmed(packet.optInt("seat", -1), packet.optBoolean("loaded"))
                    }
                }
                if (running.get() && assigned && !redirected) disconnect("Connection to the host was lost.")
            } catch (e: Exception) {
                if (running.get()) disconnect(e.toUserMessage(host))
            } finally {
                runCatching { socket?.close() }
            }
        }.start()
    }

    fun publish(snapshot: String) {
        queuePacket(JSONObject().put("type", "state").put("payload", snapshot), "syncing the game")
    }

    fun publishReady(ready: Boolean): Boolean {
        return queuePacket(JSONObject().put("type", "ready").put("ready", ready).put("requestId", nextRequestId.getAndIncrement()), "sending readiness")
    }

    fun publishDeckStatus(loaded: Boolean, playerName: String, commanderName: String?): Boolean {
        val packet = JSONObject().put("type", "deck-status").put("loaded", loaded).put("name", playerName)
            .put("commanderName", commanderName ?: JSONObject.NULL).put("requestId", nextRequestId.getAndIncrement())
        return queuePacket(packet, "sending deck status")
    }

    fun requestHostTransfer(address: String): Boolean = queuePacket(JSONObject().put("type", "host-ready").put("address", address), "transferring the host role")

    private fun queuePacket(packet: JSONObject, action: String): Boolean {
        if (!running.get() || writer == null) return false
        return try {
            sendQueue.execute {
                val output = writer ?: return@execute
                if (running.get()) writePacket(output, packet, action)
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    fun close() {
        running.set(false)
        sendQueue.shutdownNow()
        heartbeatQueue.shutdownNow()
        runCatching { socket?.close() }
    }

}

private fun Exception.toUserMessage(host: String): String = when (this) {
    is SocketTimeoutException -> "The host at ${host.trim()} did not answer. Check the IP and make sure both phones are on the same Wi-Fi."
    is ConnectException -> "Could not reach ${host.trim()}. Check the host IP, same Wi-Fi, and that the host room is still open."
    is UnknownHostException -> "That host address could not be found. Enter the host's local Wi-Fi IP, such as 192.168.1.25."
    else -> message ?: "Could not connect to the room host. Check the host IP and same Wi-Fi network."
}
