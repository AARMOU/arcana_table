package com.arcanatable

import org.json.JSONObject

/**
 * Room-scoped plaintext chat, carried over authenticated Arcana LAN/online seat links.
 * Chat is never forwarded into Argentum's authoritative rules/action engine.
 * Only the host creates delivery packets; callers cannot choose their seat or name.
 */
internal object ArgentumRoomChat {
    const val REQUEST = "arcanaChatSend"
    const val DELIVERY = "arcanaChat"
    const val MAX_TEXT = 500
    const val MAX_AUTHOR = 32

    internal data class Message(
        val id: Long, val seat: Int, val author: String, val text: String, val timestamp: String
    )

    fun normalizedText(raw: String): String? {
        val cleaned = raw.trim()
        return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT && !it.any { ch -> ch == '\u0000' } }
    }

    fun request(raw: String): JSONObject? =
        normalizedText(raw)?.let { JSONObject().put("type", REQUEST).put("text", it) }

    fun requestedText(packet: JSONObject): String? =
        if (packet.optString("type") == REQUEST) normalizedText(packet.optString("text")) else null

    fun delivery(id: Long, seat: Int, author: String, text: String, timestamp: String): JSONObject {
        require(id > 0 && seat in 0..3)
        val clean = requireNotNull(normalizedText(text))
        val name = author.trim().take(MAX_AUTHOR).ifEmpty { "Player ${seat + 1}" }
        return JSONObject().put("type", DELIVERY).put("id", id).put("seat", seat)
            .put("author", name).put("text", clean).put("timestamp", timestamp.take(32))
    }

    fun parseDelivery(packet: JSONObject): Message? {
        if (packet.optString("type") != DELIVERY) return null
        val id = packet.optLong("id", -1L)
        val seat = packet.optInt("seat", -1)
        val rawAuthor = packet.optString("author")
        val author = rawAuthor.trim()
        val text = normalizedText(packet.optString("text")) ?: return null
        if (id <= 0 || seat !in 0..3 || author.isEmpty() || author.length > MAX_AUTHOR) return null
        return Message(id, seat, author, text, packet.optString("timestamp").take(32))
    }
}
