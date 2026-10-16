package com.arcanatable.ui

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * TEST DOUBLE ONLY. A local stand-in for Argentum's game server that speaks the same `/game`
 * WebSocket protocol and `/api/decks/validate` endpoint, replaying payloads recorded from the real
 * server (src/test/resources/argentum). It lets UI tests drive scenarios the live engine will not
 * produce on demand: unsupported decisions, malformed data and connection drops.
 */
class FakeArgentumServer {
    private val server = MockWebServer()
    val connections = AtomicInteger(0)
    val received = CopyOnWriteArrayList<JSONObject>()
    @Volatile var socket: WebSocket? = null
    @Volatile var stateMessage: JSONObject = fixture("state-main-phase-with-actions.json")
    @Volatile var gameRunning = false

    val viewingPlayerId: String get() = stateMessage.getJSONObject("state").optString("viewingPlayerId")
    val origin: String get() = server.url("/").toString().trimEnd('/')

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/decks/validate") == true -> MockResponse().setHeader("Content-Type", "application/json")
                    .setBody(JSONObject().put("valid", true).put("totalCards", 100).put("errors", JSONArray()).put("warnings", JSONArray()).toString())
                request.path == "/game" -> MockResponse().withWebSocketUpgrade(listener)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    fun shutdown() { runCatching { socket?.close(1000, "done") }; runCatching { server.shutdown() } }

    fun send(message: JSONObject) { socket?.send(message.toString()) }
    fun sendRaw(text: String) { socket?.send(text) }
    fun sendState() = send(stateMessage)
    fun dropConnection() { socket?.close(1001, "server going away"); socket = null }

    fun withDecision(decision: JSONObject): JSONObject = JSONObject(stateMessage.toString())
        .put("legalActions", JSONArray()).put("pendingDecision", decision)

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            connections.incrementAndGet()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            received += message
            when (message.optString("type")) {
                "connect" -> if (message.has("token") && gameRunning) {
                    webSocket.send(JSONObject().put("type", "reconnected").put("playerId", viewingPlayerId).put("token", "fake-token").put("context", "game").toString())
                    webSocket.send(stateMessage.toString()) // Argentum pushes state on reconnect.
                } else webSocket.send(JSONObject().put("type", "connected").put("playerId", viewingPlayerId).put("token", "fake-token").toString())
                "createQuickGameLobby" -> webSocket.send(JSONObject().put("type", "quickGameLobbyState").put("lobbyId", "lobby-1")
                    .put("youPlayerId", viewingPlayerId).put("vsAi", true).put("players", JSONArray().put(JSONObject().put("playerId", viewingPlayerId))).toString())
                "setQuickGameLobbyReady" -> {
                    gameRunning = true
                    webSocket.send(JSONObject().put("type", "gameStarted").put("players", JSONArray().put(JSONObject().put("playerId", viewingPlayerId).put("isYou", true))).toString())
                    webSocket.send(stateMessage.toString())
                }
                "requestResync" -> webSocket.send(stateMessage.toString())
                "concede" -> { gameRunning = false; webSocket.send(JSONObject().put("type", "gameOver").put("reason", "CONCESSION").toString()) }
            }
        }
    }

    companion object {
        fun fixture(name: String): JSONObject =
            JSONObject(requireNotNull(FakeArgentumServer::class.java.classLoader!!.getResource("argentum/$name")).readText())
    }
}
