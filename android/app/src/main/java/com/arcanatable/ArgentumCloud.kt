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
) {
    @Volatile var onMessage: (JSONObject) -> Unit = {}
    @Volatile var onPeerJoined: () -> Unit = {}
    @Volatile var onPeerLeft: () -> Unit = {}
    @Volatile var onReady: () -> Unit = {}
    @Volatile var onFailed: (String) -> Unit = {}
    @Volatile var onClosed: () -> Unit = {}

    private val open = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var seat = -1
    private val outbox = ArrayDeque<Pair<Long, String>>()
    private var nextSeq = 1L
    private var lastSeen = 0L
    private var peerOnline = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun connect() {
        val url = roomUrl(endpoint, code) ?: run { onFailed("The online room service address isn't valid."); return }
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", if (creating) "create" else "join").put("code", code)
                    .put("password", password(code)).put("name", playerName.take(24)).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { handle(JSONObject(text)) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed.getAndSet(true)) return
                if (!open.get()) onFailed("Couldn't reach the online room service. Check your internet connection.") else onClosed()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (!closed.getAndSet(true)) onClosed() }
        })
    }

    private fun handle(packet: JSONObject) {
        when (packet.optString("type")) {
            "welcome" -> { seat = packet.optInt("seat", -1); open.set(true); onReady() }
            "rejected" -> if (!open.get()) { closed.set(true); socket?.cancel(); onFailed(packet.optString("reason", "The room service turned that down.")) }
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
        if (online && !peerOnline) { peerOnline = true; onPeerJoined() }
        if (!online && peerOnline) { peerOnline = false; onPeerLeft() }
        val messages = player?.optJSONObject("arcanaRelay")?.optJSONArray("msgs") ?: return
        val incoming = (0 until messages.length()).mapNotNull { messages.optJSONArray(it) }.map { it.optLong(0) to it.optString(1) }.sortedBy { it.first }
        synchronized(this) {
            for ((seq, data) in incoming) {
                if (seq <= lastSeen) continue
                lastSeen = seq
                unpack(data)?.let(onMessage)
            }
        }
    }

    /** Posts [message] for the other phone. */
    fun send(message: JSONObject): Boolean {
        val ws = socket ?: return false
        if (!open.get() || seat < 0) return false
        val payload = synchronized(this) {
            outbox.addLast(nextSeq++ to pack(message))
            // Keep the newest few, within the room service's size limit.
            while (outbox.size > MAX_KEPT || (outbox.size > 1 && outbox.sumOf { it.second.length } > MAX_BYTES)) outbox.removeFirst()
            val players = JSONArray()
            for (i in 0 until seat) players.put(JSONObject())
            players.put(JSONObject().put("arcanaRelay", JSONObject().put("msgs", JSONArray(outbox.map { JSONArray().put(it.first).put(it.second) }))))
            JSONObject().put("players", players).toString()
        }
        return ws.send(JSONObject().put("type", "state").put("payload", payload).toString())
    }

    fun close() {
        if (closed.getAndSet(true)) return
        runCatching { socket?.send(JSONObject().put("type", "leave").toString()) }
        socket?.close(1000, "left")
        socket = null
    }

    companion object {
        private const val MAX_KEPT = 12
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
) : ArgentumHostLink {
    val code = ArgentumCloudLink.newCode()
    private val link = ArgentumCloudLink(endpoint, code, creating = true, playerName = hostName)
    @Volatile private var helloSeen = false

    fun start() {
        link.onMessage = { message ->
            if (!helloSeen) { if (message.optString("type") == "lanHello") { helloSeen = true; onGuest(message) } }
            else onGuestMessage(message)
        }
        link.onPeerLeft = { if (helloSeen) onGuestLeft() }
        link.onFailed = onFailed
        link.connect()
    }

    override fun send(message: JSONObject): Boolean = link.send(message)
    override fun close() = link.close()
}

/** The guest phone's side online. */
class ArgentumCloudGuest(endpoint: String, code: String, playerName: String) : ArgentumGuestLink {
    private val link = ArgentumCloudLink(endpoint, ArgentumCloudLink.cleanCode(code), creating = false, playerName = playerName)
    override var onMessage: (JSONObject) -> Unit = {}
    override var onClosed: () -> Unit = {}

    /** Joins the room and sends [hello] once in. Calls [done] with null on success, else the reason. */
    fun connect(hello: JSONObject, done: (String?) -> Unit) {
        val finished = AtomicBoolean(false)
        link.onMessage = { onMessage(it) }
        link.onClosed = { onClosed() }
        link.onPeerLeft = { onClosed() }
        link.onFailed = { reason -> if (!finished.getAndSet(true)) done(reason) }
        link.onReady = { link.send(hello); if (!finished.getAndSet(true)) done(null) }
        link.connect()
    }

    override fun send(message: JSONObject): Boolean = link.send(message)
    override fun close() = link.close()
}
