package com.arcanatable

import com.wingedsheep.sdk.model.EntityId
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Secure per-recipient Argentum relay. The existing one-guest CloudLink stays intact.
 * This protocol REQUIRES Worker relay=1, and never writes a private stateUpdate
 * into the shared Cloudflare room snapshot.
 */
internal class ArgentumDirectedLink(
    private val endpoint: String,
    val code: String,
    private val isHost: Boolean,
    private val playerName: String,
    private val seatKinds: List<String> = emptyList(),
    private val graceMs: Long = 90_000L,
) {
    var onReady: () -> Unit = {}
    var onFailed: (String) -> Unit = {}
    var onClosed: () -> Unit = {}
    var onReconnecting: () -> Unit = {}
    var onReconnected: () -> Unit = {}
    var onRelay: (Int, JSONObject) -> Unit = { _, _ -> }
    var onPeerAway: (Int) -> Unit = {}
    var onPeerBack: (Int) -> Unit = {}
    var onPeerLeft: (Int) -> Unit = {}

    private val ended = AtomicBoolean(false)
    private val firstWelcome = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile var ownSeat: Int = -1
        private set
    @Volatile private var ready = false
    @Volatile private var droppedAt = 0L
    private var retryCount = 0
    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "arcana-directed-cloud").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(15, TimeUnit.SECONDS).build()
    private val seqByTarget = mutableMapOf<Int, Long>()
    private val outgoing = mutableMapOf<Int, ArrayDeque<Pair<Long, String>>>()
    private val lastFrom = mutableMapOf<Int, Long>()
    private val peers = mutableMapOf<Int, Boolean>()
    private val absent = mutableMapOf<Int, ScheduledFuture<*>>()
    private val everPresent = mutableSetOf<Int>()

    fun connect() {
        val url = ArgentumCloudLink.roomUrl(endpoint, code)
        if (url == null) { onFailed("Invalid online room service address."); return }
        open(url)
    }

    private fun open(url: String) {
        val fresh = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== socket || ended.get()) return
                val packet = JSONObject().put("type", if (isHost && !firstWelcome.get()) "create" else "join")
                    .put("code", code).put("password", "arcana-argentum-$code")
                    .put("name", playerName.take(24))
                if (ownSeat >= 0) packet.put("preferredSeat", ownSeat)
                webSocket.send(packet.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === socket && !ended.get())
                    runCatching { handle(JSONObject(text)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket === socket) disconnected()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket === socket) disconnected()
            }
        })
        socket = fresh
    }

    private fun handle(packet: JSONObject) {
        when (packet.optString("type")) {
            "welcome" -> {
                if (packet.optInt("relay") != 1) {
                    close()
                    onFailed("This online room service needs directed relay support for multiplayer.")
                    return
                }
                val assigned = packet.optInt("seat", -1)
                if (assigned !in 0..3 || (isHost && assigned != 0) ||
                    (!isHost && assigned == 0) ||
                    (ownSeat >= 0 && assigned != ownSeat)) {
                    close()
                    onFailed("The room assigned an unexpected player seat.")
                    return
                }
                ownSeat = assigned
                if (isHost) {
                    if (!sendPacket(JSONObject().put("type", "relaySeats")
                            .put("kinds", org.json.JSONArray(seatKinds)))) {
                        onFailed("Could not configure the online room.")
                    }
                } else welcomed()
            }
            "relay-seats-ack" -> {
                if (isHost && ownSeat == 0) welcomed()
            }
            "relay" -> {
                val from = packet.optInt("from", -1)
                val sequence = packet.optLong("seq", -1)
                val data = packet.optString("data")
                if (from !in 0..3 || sequence < 1 || from == ownSeat ||
                    (isHost && from == 0) || (!isHost && from != 0)) return
                val decoded = ArgentumCloudLink.unpack(data) ?: return
                synchronized(this) {
                    if (sequence <= (lastFrom[from] ?: 0L)) return
                    lastFrom[from] = sequence
                }
                if (decoded.optString("type") == "arcanaBye") {
                    absent.remove(from)?.cancel(false)
                    peers[from] = false
                    onPeerLeft(from)
                } else onRelay(from, decoded)
            }
            "state" -> {
                val state = packet.optJSONObject("payload")
                    ?: packet.optString("payload").takeIf(String::isNotBlank)?.let {
                        runCatching { JSONObject(it) }.getOrNull()
                    } ?: return
                val players = state.optJSONArray("players") ?: return
                val indices = if (isHost) 1..3 else 0..0
                for (other in indices) {
                    val now = players.optJSONObject(other)?.optBoolean("isOnline") == true
                    val was = peers[other] == true
                    if (now == was) continue
                    peers[other] = now
                    if (now) {
                        absent.remove(other)?.cancel(false)
                        if (other in everPresent) onPeerBack(other)
                        else everPresent += other
                    } else if (other in everPresent) {
                        onPeerAway(other)
                        absent.remove(other)?.cancel(false)
                        absent[other] = timer.schedule({
                            if (!ended.get() && peers[other] != true) onPeerLeft(other)
                        }, graceMs, TimeUnit.MILLISECONDS)
                    }
                }
            }
            "rejected" -> {
                val reason = packet.optString("reason", "The room rejected an action.")
                if (!ready) {
                    if (droppedAt != 0L && isHost) {
                        // A room whose last phone left can expire; retry as creator.
                        firstWelcome.set(false)
                        disconnected()
                    } else { close(); onFailed(reason) }
                }
            }
        }
    }

    private fun welcomed() {
        val first = !firstWelcome.getAndSet(true)
        ready = true
        droppedAt = 0L
        retryCount = 0
        if (first) onReady()
        else {
            // The Worker does not persist messages. Re-send our bounded ring and
            // request an authoritative snapshot to cover any missed history.
            val queued = synchronized(this) {
                outgoing.flatMap { (target, packets) ->
                    packets.map { (seq, data) -> Triple(target, seq, data) }
                }
            }
            queued.forEach { (target, seq, data) -> relayPacket(target, seq, data) }
            onReconnected()
        }
    }

    private fun disconnected() {
        if (ended.get()) return
        ready = false
        if (!firstWelcome.get()) {
            close()
            onFailed("Could not connect to the multiplayer room service.")
            return
        }
        if (droppedAt == 0L) { droppedAt = System.currentTimeMillis(); onReconnecting() }
        if (System.currentTimeMillis() - droppedAt >= graceMs) {
            close(); onClosed(); return
        }
        val wait = minOf(8_000L, 1_000L shl retryCount.coerceAtMost(3))
        retryCount++
        timer.schedule({
            if (!ended.get() && !ready) {
                val previous = socket
                socket = null
                previous?.cancel()
                ArgentumCloudLink.roomUrl(endpoint, code)?.let(::open)
            }
        }, wait, TimeUnit.MILLISECONDS)
    }

    private fun sendPacket(packet: JSONObject): Boolean {
        if (ended.get()) return false
        return socket?.send(packet.toString()) == true
    }

    private fun relayPacket(target: Int, seq: Long, data: String): Boolean =
        sendPacket(JSONObject().put("type", "relay").put("to", target)
            .put("seq", seq).put("data", data))

    fun sendTo(target: Int, message: JSONObject): Boolean {
        if (ended.get() || target !in 0..3 ||
            (isHost && (target == 0 || target > seatKinds.size)) ||
            (!isHost && target != 0)) return false
        val encoded = ArgentumCloudLink.pack(message)
        if (encoded.length > 400_000) return false
        val seq = synchronized(this) {
            val next = (seqByTarget[target] ?: 0L) + 1
            seqByTarget[target] = next
            val ring = outgoing.getOrPut(target) { ArrayDeque() }
            ring.addLast(next to encoded)
            while (ring.size > 12) ring.removeFirst()
            next
        }
        if (ready) relayPacket(target, seq, encoded)
        return true
    }

    fun configureAndClose() = close()

    fun close() {
        if (!ended.compareAndSet(false, true)) return
        if (ready) {
            val targets = if (isHost) (1..seatKinds.size).toList() else listOf(0)
            targets.forEach { sendToBye(it) }
            sendPacketBeforeClose(JSONObject().put("type", "leave"))
        }
        ready = false
        runCatching { socket?.close(1000, "left room") }
        socket = null
        absent.values.forEach { it.cancel(false) }
        timer.shutdownNow()
    }

    private fun sendToBye(target: Int) {
        val data = ArgentumCloudLink.pack(JSONObject().put("type", "arcanaBye"))
        val seq = synchronized(this) {
            val next = (seqByTarget[target] ?: 0L) + 1
            seqByTarget[target] = next
            next
        }
        socket?.send(JSONObject().put("type", "relay").put("to", target)
            .put("seq", seq).put("data", data).toString())
    }

    private fun sendPacketBeforeClose(packet: JSONObject) {
        socket?.send(packet.toString())
    }
}

/** Host-side bridge from the directed Worker to LocalArgentumHost's stable seat IDs. */
internal class ArgentumCloudMultiHost(
    endpoint: String,
    hostName: String,
    kinds: List<String>,
    private val onGuest: (EntityId, JSONObject) -> Unit,
    private val onGuestMessage: (EntityId, JSONObject) -> Unit,
    private val onGuestLeft: (EntityId) -> Unit,
    private val onFailed: (String) -> Unit,
    private val onNotice: (String) -> Unit = {},
) : ArgentumHostLink {
    val code = ArgentumCloudLink.newCode()
    private val friendKinds = kinds.toList()
    private val link = ArgentumDirectedLink(endpoint, code, true, hostName, kinds)
    var onRoomReady: () -> Unit = {}

    init {
        link.onReady = { onRoomReady() }
        link.onRelay = { from, message ->
            val id = seatId(from)
            if (message.optString("type") == "lanHello") onGuest(id, message)
            else onGuestMessage(id, message)
        }
        link.onPeerAway = { seat -> onNotice("Player ${seat + 1} disconnected; waiting for reconnection.") }
        link.onPeerBack = { seat -> onNotice("Player ${seat + 1} reconnected.") }
        link.onPeerLeft = { seat -> onGuestLeft(seatId(seat)) }
        link.onReconnecting = { onNotice("Host connection dropped; reconnecting.") }
        link.onReconnected = { onNotice("Host connection restored.") }
        link.onFailed = onFailed
        link.onClosed = { onFailed("The host could not reconnect to the online room.") }
    }

    fun start() = link.connect()
    fun sendTo(id: EntityId, message: JSONObject): Boolean = seatNumber(id)?.let {
        if (friendKinds.getOrNull(it - 1) == "friend") link.sendTo(it, message) else false
    } == true
    fun broadcastLobby(message: JSONObject): Boolean {
        if (message.optString("type") != "lanLobby") return false
        return friendKinds.indices.filter { friendKinds[it] == "friend" }
            .all { index -> sendTo(seatId(index + 1), message) }
    }
    override fun send(message: JSONObject): Boolean = false // never broadcast masked state
    override fun close() = link.close()

    companion object {
        fun seatId(number: Int) = EntityId(
            when (number) { 0 -> "offline-you"; 1 -> "offline-ai"; else -> "offline-ai-$number" })
        fun seatNumber(id: EntityId): Int? = when (id.value) {
            "offline-ai" -> 1
            "offline-ai-2" -> 2
            "offline-ai-3" -> 3
            else -> null
        }
    }
}

/** Guest-side bridge; the host can send only this seat's masked state. */
internal class ArgentumCloudMultiGuest(
    endpoint: String, code: String, name: String,
) : ArgentumGuestLink {
    private val link = ArgentumDirectedLink(
        endpoint, ArgentumCloudLink.cleanCode(code), false, name)
    override var onMessage: (JSONObject) -> Unit = {}
    override var onClosed: () -> Unit = {}

    fun connect(hello: JSONObject, done: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
        link.onReady = {
            link.sendTo(0, hello)
            if (finished.compareAndSet(false, true)) done(null)
        }
        link.onRelay = { from, packet -> if (from == 0) onMessage(packet) }
        link.onPeerBack = { if (it == 0) link.sendTo(0, JSONObject().put("type", "requestResync")) }
        link.onReconnected = {
            link.sendTo(0, JSONObject().put("type", "requestResync"))
            onMessage(JSONObject().put("type", "error").put("code", "LINK_NOTE")
                .put("message", "Connection restored. Refreshing the table."))
        }
        link.onPeerAway = {
            onMessage(JSONObject().put("type", "error").put("code", "LINK_NOTE")
                .put("message", "Host disconnected; waiting for them to return."))
        }
        link.onPeerLeft = { onClosed() }
        link.onClosed = { onClosed() }
        link.onFailed = { reason -> if (finished.compareAndSet(false, true)) done(reason) else onClosed() }
        link.connect()
    }

    override fun send(message: JSONObject): Boolean = link.sendTo(0, message)
    override fun close() = link.close()
}
