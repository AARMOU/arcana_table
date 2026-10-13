package com.arcanatable

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Secure WebSocket transport for internet rooms hosted by cloud-room/. */
class CloudRoomClient(
    private val onSeatAssigned: (Int) -> Unit,
    private val onSnapshot: (String) -> Unit,
    private val onReadyConfirmed: (seat: Int, ready: Boolean, accepted: Boolean) -> Unit,
    private val onDeckStatusConfirmed: (seat: Int, loaded: Boolean) -> Unit,
    private val onDisconnected: (String) -> Unit
) {
    private val running = AtomicBoolean(true)
    private val connected = AtomicBoolean(false)
    private val reportedDisconnect = AtomicBoolean(false)
    private val nextRequestId = AtomicInteger(1)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var assignedSeat = -1

    fun connect(
        endpoint: String,
        roomCode: String,
        password: String,
        playerName: String,
        create: Boolean,
        preferredSeat: Int? = null,
        initialSnapshot: String? = null
    ) {
        val wsUrl = makeRoomUrl(endpoint, roomCode)
        if (wsUrl == null) {
            fail("Enter a valid Cloudflare Worker URL, for example https://arcana-table-rooms.yourname.workers.dev")
            return
        }
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                if (!running.get()) { socket.close(1000, "canceled"); return }
                connected.set(true)
                val packet = JSONObject()
                    .put("type", if (create) "create" else "join")
                    .put("code", roomCode.trim().uppercase())
                    .put("password", password)
                    .put("name", playerName.trim().ifBlank { "PLAYER" }.take(24))
                if (create && !initialSnapshot.isNullOrBlank()) packet.put("payload", initialSnapshot)
                preferredSeat?.let { packet.put("preferredSeat", it) }
                if (!socket.send(packet.toString())) fail("Could not send the room request.")
            }

            override fun onMessage(socket: WebSocket, text: String) {
                val packet = runCatching { JSONObject(text) }.getOrElse {
                    fail("The room server sent an unreadable response.")
                    return
                }
                when (packet.optString("type")) {
                    "welcome" -> {
                        assignedSeat = packet.optInt("seat", -1)
                        if (assignedSeat !in 0..3) { fail("The room server assigned an invalid player seat."); return }
                        onSeatAssigned(assignedSeat)
                    }
                    "state" -> onSnapshot(packet.optString("payload", "{}"))
                    "ready-ack" -> onReadyConfirmed(
                        packet.optInt("seat", -1), packet.optBoolean("ready"), packet.optBoolean("accepted", true)
                    )
                    "deck-status-ack" -> onDeckStatusConfirmed(packet.optInt("seat", -1), packet.optBoolean("loaded"))
                    "rejected" -> fail(packet.optString("reason", "The room server rejected the request."))
                    "left" -> Unit
                    "pong" -> Unit
                }
            }

            override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                socket.close(code, reason)
                connected.set(false)
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                if (running.get()) fail("Connection to the internet room ended: ${reason.ifBlank { "closed" }}")
            }

            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                if (running.get()) fail(connectionMessage(t, response))
            }
        })
    }

    fun publish(snapshot: String): Boolean = send(JSONObject().put("type", "state").put("payload", snapshot))

    fun publishReady(ready: Boolean): Boolean = send(
        JSONObject().put("type", "ready").put("ready", ready).put("requestId", nextRequestId.getAndIncrement())
    )

    fun publishDeckStatus(loaded: Boolean, playerName: String, commanderName: String?): Boolean = send(
        JSONObject().put("type", "deck-status").put("loaded", loaded)
            .put("name", playerName.take(24)).put("commanderName", commanderName ?: JSONObject.NULL)
            .put("requestId", nextRequestId.getAndIncrement())
    )

    fun leave() {
        send(JSONObject().put("type", "leave"))
        close()
    }

    fun close() {
        if (!running.getAndSet(false)) return
        connected.set(false)
        webSocket?.close(1000, "client closed")
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private fun send(packet: JSONObject): Boolean {
        val socket = webSocket ?: return false
        return running.get() && connected.get() && socket.send(packet.toString())
    }

    private fun fail(message: String) {
        if (reportedDisconnect.compareAndSet(false, true)) onDisconnected(message)
        running.set(false)
        connected.set(false)
        webSocket?.cancel()
    }

    private fun makeRoomUrl(endpoint: String, code: String): String? = runCatching {
        val trimmed = endpoint.trim().removeSuffix("/")
        val normalized = when {
            trimmed.startsWith("https://", true) -> "wss://${trimmed.substringAfter("://")}" 
            trimmed.startsWith("wss://", true) || trimmed.startsWith("ws://", true) -> trimmed
            else -> return null
        }
        require(normalized.startsWith("wss://", true)) { "Internet rooms require HTTPS/WSS." }
        val base = normalized.substringBefore("/room/").trimEnd('/')
        val cleanCode = code.trim().uppercase()
        require(cleanCode.matches(Regex("[A-Z0-9]{6,8}")))
        "$base/room/$cleanCode"
    }.getOrNull()

    private fun connectionMessage(error: Throwable, response: Response?): String {
        val status = response?.code
        if (status != null) return when (status) {
            404 -> "The room service URL was not found. Check the deployed Worker address."
            426 -> "The room service needs a secure WebSocket connection. Check its URL and deployment."
            else -> "The room service returned HTTP $status. Check the Worker URL and try again."
        }
        val detail = error.message.orEmpty()
        return when {
            detail.contains("timeout", true) -> "The internet room server did not answer. Check your internet connection and Worker URL."
            detail.contains("resolve", true) || detail.contains("host", true) -> "Could not find that room server. Check the Worker URL."
            else -> "Could not connect to the internet room: ${detail.ifBlank { error.javaClass.simpleName }}"
        }
    }
}
