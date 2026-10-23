package com.arcanatable

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Online play for offline Argentum: the same host/guest game as Wi-Fi, carried over Arcana's
 * internet room service (the Cloudflare Worker in `cloud-room`) instead of the local network.
 *
 * The room service has no plain "forward this" message, so each phone posts its outgoing messages
 * into its own seat of the shared room state (a seat may always update itself) as a numbered,
 * gzip-compressed outbox, and reads the other seat's outbox from every state broadcast. Only the
 * last few messages are kept, so a burst between broadcasts is never lost. Nothing on the server
 * needs to change.
 */
class ArgentumCloudLink(
    private val endpoint: String,
    val code: String,
    private val creating: Boolean,
    private val playerName: String,
    private val graceMs: Long = GRACE_MS,
) {
    @Volatile var onMessage: (JSONObject) -> Unit = {}
    @Volatile var onPeerJoined: () -> Unit = {}
    /** The other phone has been gone longer than the grace period: give up on it. */
    @Volatile var onPeerLeft: () -> Unit = {}
    /** The other phone just dropped; it has [graceMs] to come back. */
    @Volatile var onPeerAway: () -> Unit = {}
    /** The other phone came back within the grace period. */
    @Volatile var onPeerBack: () -> Unit = {}
    @Volatile var onReady: () -> Unit = {}
    @Volatile var onFailed: (String) -> Unit = {}
    /** This phone's own connection is gone for good (it couldn't get back within the grace period). */
    @Volatile var onClosed: () -> Unit = {}
    /** This phone's connection dropped; it is trying to get back in. */
    @Volatile var onReconnecting: () -> Unit = {}
    /** Back in the room after a drop (the outbox has been posted again). */
    @Volatile var onReconnected: () -> Unit = {}

    private val open = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val everOpened = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var seat = -1
    private val outbox = ArrayDeque<Pair<Long, String>>()
    private var nextSeq = 1L
    private var lastSeen = 0L
    private var peerOnline = false
    @Volatile private var peerAway = false
    @Volatile private var peerGoneTask: java.util.concurrent.ScheduledFuture<*>? = null
    // Reconnecting: when the drop happened, how many tries so far, and (host) whether to recreate the room.
    @Volatile private var droppedAt = 0L
    @Volatile private var attempts = 0
    @Volatile private var recreate = false
    private val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "arcana-cloud-link").apply { isDaemon = true } }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val isReconnecting: Boolean get() = droppedAt != 0L

    fun connect() {
        val url = roomUrl(endpoint, code) ?: run { onFailed("The online room service address isn't valid."); return }
        open(url)
    }

    private fun open(url: String) {
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== socket) return
                // First time: create or join as asked. After a drop: take our old seat back; if the
                // room was emptied meanwhile (both phones dropped), the host makes it again.
                val type = when {
                    !everOpened.get() -> if (creating) "create" else "join"
                    creating && recreate -> "create"
                    else -> "join"
                }
                val hello = JSONObject().put("type", type).put("code", code).put("password", password(code)).put("name", playerName.take(24))
                if (everOpened.get() && seat >= 0) hello.put("preferredSeat", seat)
                webSocket.send(hello.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) { if (webSocket === socket) runCatching { handle(JSONObject(text)) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (webSocket === socket) dropped() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (webSocket === socket) dropped() }
        })
    }

    /** The socket went away. Before the first welcome that's a failure; after it, try to get back in. */
    private fun dropped() {
        if (closed.get()) return
        open.set(false)
        if (!everOpened.get()) {
            if (closed.getAndSet(true)) return
            onFailed("Couldn't reach the online room service. Check your internet connection.")
            return
        }
        if (droppedAt == 0L) { droppedAt = System.currentTimeMillis(); attempts = 0; recreate = false; onReconnecting() }
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (closed.get()) return
        val since = System.currentTimeMillis() - droppedAt
        if (since >= graceMs) {
            if (!closed.getAndSet(true)) { dropSocket(); timer.shutdownNow(); onClosed() }
            return
        }
        val delay = minOf(RETRY_MAX_MS, RETRY_BASE_MS shl minOf(attempts, 4))
        attempts++
        runCatching {
            timer.schedule(Runnable {
                if (closed.get() || open.get()) return@Runnable
                dropSocket()
                roomUrl(endpoint, code)?.let(::open)
            }, delay, TimeUnit.MILLISECONDS)
        }
    }

    /** Forgets the current socket before cancelling it, so its own failure callback is ignored. */
    private fun dropSocket() { val old = socket; socket = null; runCatching { old?.cancel() } }

    private fun handle(packet: JSONObject) {
        when (packet.optString("type")) {
            "welcome" -> {
                seat = packet.optInt("seat", seat)
                open.set(true)
                if (!everOpened.getAndSet(true)) {
                    onReady()
                } else if (droppedAt != 0L) {
                    droppedAt = 0L
                    postOutbox()
                    onReconnected()
                }
            }
            "rejected" -> when {
                !everOpened.get() -> if (!closed.getAndSet(true)) { socket?.cancel(); onFailed(packet.optString("reason", "The room service turned that down.")) }
                !open.get() -> {
                    // Getting back in was turned down: the host tries making the room again next time.
                    if (creating) recreate = !recreate
                    dropSocket()
                    scheduleRetry()
                }
                // Once in, a rejection is about one message (too big, say): nothing to tear down.
            }
            "state" -> {
                val payload = packet.opt("payload")
                val state = when (payload) { is JSONObject -> payload; is String -> runCatching { JSONObject(payload) }.getOrNull(); else -> null } ?: return
                readPeer(state)
            }
        }
    }

    private fun readPeer(state: JSONObject) {
        if (seat < 0) return
        val peer = if (seat == 0) 1 else 0
        val player = state.optJSONArray("players")?.optJSONObject(peer)
        val online = player?.optBoolean("isOnline") == true
        if (online && !peerOnline) {
            peerOnline = true
            peerGoneTask?.cancel(false); peerGoneTask = null
            if (peerAway) { peerAway = false; onPeerBack() } else onPeerJoined()
        }
        if (!online && peerOnline) {
            peerOnline = false
            peerAway = true
            onPeerAway()
            peerGoneTask = runCatching { timer.schedule(Runnable { if (peerAway && !closed.get()) { peerAway = false; onPeerLeft() } }, graceMs, TimeUnit.MILLISECONDS) }.getOrNull()
        }
        val messages = player?.optJSONObject("arcanaRelay")?.optJSONArray("msgs") ?: return
        val incoming = (0 until messages.length()).mapNotNull { messages.optJSONArray(it) }.map { it.optLong(0) to it.optString(1) }.sortedBy { it.first }
        synchronized(this) {
            for ((seq, data) in incoming) {
                if (seq <= lastSeen) continue
                lastSeen = seq
                val message = unpack(data) ?: continue
                if (message.optString("type") == BYE) {
                    // The other phone left on purpose: no grace period.
                    peerOnline = false; peerAway = false
                    peerGoneTask?.cancel(false); peerGoneTask = null
                    onPeerLeft()
                } else onMessage(message)
            }
        }
    }

    /**
     * Posts [message] for the other phone. While reconnecting it waits in the outbox and goes out
     * as soon as this phone is back in. False only once the link is closed.
     */
    fun send(message: JSONObject): Boolean {
        if (closed.get()) return false
        synchronized(this) {
            outbox.addLast(nextSeq++ to pack(message))
            // Keep the newest few, within the room service's size limit.
            while (outbox.size > MAX_KEPT || (outbox.size > 1 && outbox.sumOf { it.second.length } > MAX_BYTES)) outbox.removeFirst()
        }
        if (open.get()) postOutbox()
        return true
    }

    private fun postOutbox() {
        val ws = socket ?: return
        if (!open.get() || seat < 0) return
        val payload = synchronized(this) {
            val players = JSONArray()
            for (i in 0 until seat) players.put(JSONObject())
            players.put(JSONObject().put("arcanaRelay", JSONObject().put("msgs", JSONArray(outbox.map { JSONArray().put(it.first).put(it.second) }))))
            JSONObject().put("players", players).toString()
        }
        ws.send(JSONObject().put("type", "state").put("payload", payload).toString())
    }

    /** Test hook: the connection drops as if the network went away. */
    internal fun simulateDrop() { runCatching { socket?.cancel() } }

    fun close() {
        if (closed.get()) return
        // Say goodbye first, so the other phone ends the game now instead of waiting out the grace period.
        if (open.get()) send(JSONObject().put("type", BYE))
        closed.set(true)
        runCatching { socket?.send(JSONObject().put("type", "leave").toString()) }
        socket?.close(1000, "left")
        socket = null
        timer.shutdownNow()
    }

    companion object {
        private const val MAX_KEPT = 12
        private const val BYE = "arcanaBye"
        /** How long a dropped phone (this one or the other) gets to come back before the game ends. */
        const val GRACE_MS = 90_000L
        private const val RETRY_BASE_MS = 1_000L
        private const val RETRY_MAX_MS = 8_000L
        private const val MAX_BYTES = 300_000
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** A fresh six-character room code (no 0/O or 1/I to mix up). */
        fun newCode(): String = (1..6).map { ALPHABET.random() }.joinToString("")

        fun cleanCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

        private fun password(code: String) = "arcana-argentum-$code"

        fun pack(message: JSONObject): String {
            val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(message.toString().toByteArray(Charsets.UTF_8)) } }.toByteArray()
            return java.util.Base64.getEncoder().encodeToString(bytes)
        }

        fun unpack(data: String): JSONObject? = runCatching {
            val bytes = java.util.Base64.getDecoder().decode(data)
            JSONObject(GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes(), Charsets.UTF_8) })
        }.getOrNull()

        fun roomUrl(endpoint: String, code: String): String? {
            val trimmed = endpoint.trim().removeSuffix("/")
            val base = when {
                trimmed.startsWith("https://", true) -> "wss://" + trimmed.substringAfter("://")
                trimmed.startsWith("wss://", true) -> trimmed
                // Local testing only (a relay running on this machine).
                trimmed.startsWith("http://127.0.0.1", true) || trimmed.startsWith("http://localhost", true) -> "ws://" + trimmed.substringAfter("://")
                else -> return null
            }.substringBefore("/room/").trimEnd('/')
            val clean = cleanCode(code)
            return if (clean.matches(Regex("[A-Z0-9]{6,8}"))) "$base/room/$clean" else null
        }
    }
}

/** The host phone's side online: waits for a guest's "lanHello", then passes messages both ways. */
class ArgentumCloudHost(
    endpoint: String,
    hostName: String,
    private val onGuest: (hello: JSONObject) -> Unit,
    private val onGuestMessage: (JSONObject) -> Unit,
    private val onGuestLeft: () -> Unit,
    private val onFailed: (String) -> Unit,
    /** Connection news for the host's screen ("Reconnecting…", "Your friend is back."). */
    private val onNotice: (String) -> Unit = {},
    graceMs: Long = ArgentumCloudLink.GRACE_MS,
) : ArgentumHostLink {
    val code = ArgentumCloudLink.newCode()
    private val link = ArgentumCloudLink(endpoint, code, creating = true, playerName = hostName, graceMs = graceMs)
    internal fun simulateDrop() = link.simulateDrop()
    @Volatile private var helloSeen = false

    fun start() {
        link.onMessage = { message ->
            if (!helloSeen) { if (message.optString("type") == "lanHello") { helloSeen = true; onGuest(message) } }
            else onGuestMessage(message)
        }
        link.onPeerAway = { if (helloSeen) onNotice("Your friend's connection dropped. Waiting up to ${ArgentumCloudLink.GRACE_MS / 1000} s for them to come back…") }
        link.onPeerBack = { if (helloSeen) onNotice("Your friend is back.") }
        link.onPeerLeft = { if (helloSeen) onGuestLeft() }
        link.onReconnecting = { onNotice("Your connection dropped. Reconnecting…") }
        link.onReconnected = { onNotice("Back online.") }
        link.onClosed = { onNotice("Couldn't get back online, so the game with your friend has ended."); if (helloSeen) onGuestLeft() }
        link.onFailed = onFailed
        link.connect()
    }

    override fun send(message: JSONObject): Boolean = link.send(message)
    override fun close() = link.close()
}

/** The guest phone's side online. */
class ArgentumCloudGuest(endpoint: String, code: String, playerName: String, graceMs: Long = ArgentumCloudLink.GRACE_MS) : ArgentumGuestLink {
    private val link = ArgentumCloudLink(endpoint, ArgentumCloudLink.cleanCode(code), creating = false, playerName = playerName, graceMs = graceMs)
    internal fun simulateDrop() = link.simulateDrop()
    override var onMessage: (JSONObject) -> Unit = {}
    override var onClosed: () -> Unit = {}

    /** Joins the room and sends [hello] once in. Calls [done] with null on success, else the reason. */
    fun connect(hello: JSONObject, done: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
        fun notice(text: String) = onMessage(JSONObject().put("type", "error").put("code", LINK_NOTE).put("message", text))
        // After any gap, ask the host for a fresh view: messages from the gap may have been dropped.
        fun resync() { link.send(JSONObject().put("type", "requestResync")) }
        link.onMessage = { onMessage(it) }
        link.onClosed = { onClosed() }
        link.onPeerLeft = { onClosed() }
        link.onPeerAway = { notice("The host's connection dropped. Waiting up to ${ArgentumCloudLink.GRACE_MS / 1000} s for it to come back…") }
        link.onPeerBack = { notice("The host is back."); resync() }
        link.onReconnecting = { notice("Your connection dropped. Reconnecting…") }
        link.onReconnected = { notice("Back online."); resync() }
        link.onFailed = { reason -> if (!finished.getAndSet(true)) done(reason) }
        link.onReady = { link.send(hello); if (!finished.getAndSet(true)) done(null) }
        link.connect()
    }

    override fun send(message: JSONObject): Boolean = link.send(message)
    override fun close() = link.close()

    companion object {
        /** A connection note shown on the table, not an error. */
        const val LINK_NOTE = "LINK_NOTE"
    }
}
