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
    seatKinds: List<String> = emptyList(),
    private val graceMs: Long = 90_000L,
) {
    /** Opponent seat kinds ("friend"/"ai"); the host may change unoccupied seats before START. */
    @Volatile private var seatKinds: List<String> = seatKinds.toList()
    /** Callback for a host's in-lobby seat change, answered by the Worker's ack or rejection. */
    @Volatile private var pendingSeats: Pair<List<String>, (Boolean, String?) -> Unit>? = null
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
    /** Worker-generated random credential, never shared with other seats or room snapshots. */
    @Volatile private var seatToken: String? = null
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
    private val inbox = RelayInbox()
    /** Identifies this phone's link, so a receiver can tell a new phone in a seat from a repeat. */
    private val linkId = java.util.UUID.randomUUID().toString()
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
                if (ownSeat >= 0) {
                    packet.put("preferredSeat", ownSeat)
                    seatToken?.let { packet.put("seatToken", it) }
                }
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
                val credential = packet.optString("seatToken").takeIf { it.isNotBlank() && it != "null" }
                if (credential == null || (seatToken != null && seatToken != credential)) {
                    close()
                    onFailed("The room did not verify your private player seat.")
                    return
                }
                seatToken = credential
                ownSeat = assigned
                if (isHost && !firstWelcome.get()) {
                    if (!sendPacket(JSONObject().put("type", "relaySeats")
                            .put("kinds", org.json.JSONArray(seatKinds)))) {
                        onFailed("Could not configure the online room.")
                    }
                } else welcomed()
            }
            "relay-kicked" -> {
                close()
                onFailed("Your deck was rejected or the host removed your seat.")
            }
            "relay-seats-ack" -> {
                val pending = pendingSeats
                if (pending != null && firstWelcome.get()) {
                    pendingSeats = null
                    seatKinds = pending.first
                    pending.second(true, null)
                } else if (isHost && ownSeat == 0) welcomed()
            }
            "relay" -> {
                val from = packet.optInt("from", -1)
                val sequence = packet.optLong("seq", -1)
                val data = packet.optString("data")
                if (from !in 0..3 || sequence < 1 || from == ownSeat ||
                    (isHost && from == 0) || (!isHost && from != 0)) return
                val decoded = ArgentumCloudLink.unpack(data) ?: return
                val sender = decoded.optString(RelayInbox.LINK_FIELD)
                decoded.remove(RelayInbox.LINK_FIELD)
                when (inbox.accept(from, sender, sequence)) {
                    RelayInbox.Verdict.DUPLICATE -> return
                    // A different phone now holds that seat: whatever we queued for the
                    // previous one must never be replayed to it.
                    RelayInbox.Verdict.NEW_SENDER -> forget(from)
                    RelayInbox.Verdict.SAME_SENDER -> Unit
                }
                if (decoded.optString("type") == "arcanaBye") {
                    forget(from)
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
                            if (!ended.get() && peers[other] != true) { forget(other); onPeerLeft(other) }
                        }, graceMs, TimeUnit.MILLISECONDS)
                    }
                }
            }
            "rejected" -> {
                val reason = packet.optString("reason", "The room rejected an action.")
                val pending = pendingSeats
                if (pending != null && packet.optString("request") == "relaySeats") {
                    pendingSeats = null
                    pending.second(false, reason)
                    return
                }
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
        // A seat change in flight is never answered once the socket is gone; report it.
        pendingSeats?.let { pending ->
            pendingSeats = null
            pending.second(false, "The connection to the room service dropped.")
        }
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
        val encoded = ArgentumCloudLink.pack(JSONObject(message.toString()).put(RelayInbox.LINK_FIELD, linkId))
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

    /** Drop the replay ring for a seat whose phone is gone, so a newcomer never gets its messages. */
    private fun forget(seat: Int) {
        synchronized(this) { outgoing.remove(seat) }
    }

    /**
     * Host only, before START: change which opponent seats wait for friends and how many there
     * are. The Worker refuses if an occupied seat would be removed or turned into an AI seat;
     * [done] reports the outcome. Seat kinds change here only after the Worker agrees.
     */
    fun reconfigureSeats(kinds: List<String>, done: (Boolean, String?) -> Unit): Boolean {
        if (!isHost || !ready || kinds.size !in 1..3 || kinds.any { it != "friend" && it != "ai" } ||
            pendingSeats != null) return false
        pendingSeats = kinds.toList() to done
        if (!sendPacket(JSONObject().put("type", "relaySeats").put("kinds", org.json.JSONArray(kinds)))) {
            pendingSeats = null
            return false
        }
        return true
    }

    fun kickGuest(seat: Int): Boolean {
        if (!isHost || seat !in 1..3) return false
        forget(seat)
        return sendPacket(JSONObject().put("type", "relayKick").put("to", seat))
    }



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
        val data = ArgentumCloudLink.pack(JSONObject().put("type", "arcanaBye").put(RelayInbox.LINK_FIELD, linkId))
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
    @Volatile private var friendKinds = kinds.toList()
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
    fun rejectGuest(id: EntityId): Boolean = seatNumber(id)?.let(link::kickGuest) == true

    /** Change opponent seat kinds before START; [done] runs on the link thread with the result. */
    fun reconfigure(kinds: List<String>, done: (Boolean, String?) -> Unit): Boolean =
        link.reconfigureSeats(kinds) { ok, reason ->
            if (ok) friendKinds = kinds.toList()
            done(ok, reason)
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

    /** Do not send the deck hello until the caller has activated its message listener. */
    fun connect(done: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
        link.onReady = {
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

/**
 * Relay de-duplication per sending seat. Messages carry the sender's link ID; when a different
 * phone takes a seat (a rejected deck rejoining, or a newcomer in a freed seat) its numbering
 * starts again at 1, so the count for that seat restarts instead of dropping everything it sends.
 */
internal class RelayInbox {
    enum class Verdict { DUPLICATE, SAME_SENDER, NEW_SENDER }

    private val lastSeq = mutableMapOf<Int, Long>()
    private val lastLink = mutableMapOf<Int, String>()

    @Synchronized fun accept(from: Int, link: String, seq: Long): Verdict {
        val previous = lastLink[from]
        val newSender = previous != link
        if (newSender) { lastLink[from] = link; lastSeq[from] = 0L }
        if (seq <= (lastSeq[from] ?: 0L)) return Verdict.DUPLICATE
        lastSeq[from] = seq
        return if (newSender && previous != null) Verdict.NEW_SENDER else Verdict.SAME_SENDER
    }

    companion object { const val LINK_FIELD = "_relayLink" }
}
