package com.arcanatable

import com.wingedsheep.sdk.model.EntityId
import org.json.JSONObject

/**
 * Sideband chat for an Argentum multiplayer session. Transport peers are already
 * authenticated by LAN admission or the directed Cloudflare seat relay.
 * Chat never goes through LocalArgentumHost.receive or affects game priority.
 */
internal fun TableController.resetRoomChat() {
    chatMessages = emptyList()
    unreadChatCount = 0
    observedChatCount = 0
    roomChatNextId = 0L
    roomChatSeen.clear()
    roomChatDisplayRevision = 0
    observedRoomChatRevision = 0
}

internal fun TableController.receiveRoomChat(packet: JSONObject) {
    val received = ArgentumRoomChat.parseDelivery(packet) ?: return
    // Directed relays may replay queued messages on reconnect.
    if (!roomChatSeen.add(received.id)) return
    if (roomChatSeen.size > 400) {
        // Old delivery ids no longer belong to the last 200 displayed messages.
        roomChatSeen.remove(roomChatSeen.first())
    }
    chatMessages = (chatMessages + ChatMessage(received.author, received.text, received.timestamp)).takeLast(200)
    roomChatDisplayRevision++
}

private fun TableController.broadcastRoomChat(packet: JSONObject) {
    val room = wifiMultiLobby ?: return
    for (seatId in room.friendSeatIds()) {
        if (!room.isSeated(seatId)) continue
        when (val server = wifiServer) {
            is ArgentumLanMultiServer -> server.sendTo(seatId, packet)
            is ArgentumCloudMultiHost -> server.sendTo(seatId, packet)
        }
    }
}

/** Called only with transport-authenticated seat ID; never trust client-supplied sender info. */
internal fun TableController.receiveRoomChatFromGuest(seatId: EntityId, packet: JSONObject) {
    val room = wifiMultiLobby ?: return
    if (!room.isSeated(seatId)) return
    val seat = ArgentumCloudMultiHost.seatNumber(seatId) ?: return
    if (seat !in 1..3) return
    val rosterEntry = room.lobbyMessage().optJSONArray("seats")?.optJSONObject(seat) ?: return
    if (rosterEntry.optString("kind") != "human" || rosterEntry.optString("seatId") != seatId.value) return
    val text = ArgentumRoomChat.requestedText(packet) ?: return
    val message = ArgentumRoomChat.delivery(
        ++roomChatNextId, seat, rosterEntry.optString("name"), text, formatClock(elapsedGameSeconds)
    )
    receiveRoomChat(message)
    broadcastRoomChat(message) // includes the sender for an authoritative echo
}

/** Uses the current Arcana room link, never sends chat to the Argentum rules engine. */
internal fun TableController.sendTableChat(raw: String): Boolean {
    val text = ArgentumRoomChat.normalizedText(raw) ?: return false
    if (text.equals("/mute goose", ignoreCase = true) && wifiServer is ArgentumCloudMultiHost) {
        val sent = (wifiServer as ArgentumCloudMultiHost).muteGoose()
        if (sent) message = "Goosegeta chat mute requested."
        return sent
    }
    when (wifiServer) {
        is ArgentumLanMultiServer, is ArgentumCloudMultiHost -> {
            val room = wifiMultiLobby ?: return false
            val host = room.lobbyMessage().optJSONArray("seats")?.optJSONObject(0) ?: return false
            val message = ArgentumRoomChat.delivery(
                ++roomChatNextId, 0, host.optString("name"), text, formatClock(elapsedGameSeconds)
            )
            receiveRoomChat(message)
            broadcastRoomChat(message)
            return true
        }
    }
    if (argentumEnabled && wifiRole in setOf("guest", "online-guest")) {
        return argentumSocket.send(ArgentumRoomChat.request(text) ?: return false)
    }
    // Preserve Arcana's legacy local-table and non-Argentum room behavior.
    val speaker = if (onlineMode) localSeat else activePlayer
    chatMessages = (chatMessages + ChatMessage(
        players.getOrNull(speaker)?.name ?: "Player", text, formatClock(elapsedGameSeconds)
    )).takeLast(200)
    return true
}
