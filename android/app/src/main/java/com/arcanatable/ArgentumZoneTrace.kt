package com.arcanatable

import org.json.JSONObject

/**
 * Observed changes in Argentum's already viewer-masked snapshots.
 * Never asserts that an action or trigger resolved; snapshots can skip intermediate states.
 * Raw server IDs and hidden card names are never included in exported messages.
 */
internal class ArgentumZoneTrace {
    private data class CardAt(val zone: String, val owner: String, val token: Boolean, val name: String?)
    private var previous: Map<String, CardAt>? = null
    private val labels = mutableMapOf<String, Int>()
    private var nextLabel = 1

    fun reset() { previous = null; labels.clear(); nextLabel = 1 }

    fun observe(state: JSONObject): List<String> {
        val cards = state.optJSONObject("cards") ?: return emptyList()
        val zones = state.optJSONArray("zones") ?: return emptyList()
        val viewer = state.optString("viewingPlayerId")
        val now = linkedMapOf<String, CardAt>()
        for (i in 0 until zones.length()) {
            val zone = zones.optJSONObject(i) ?: continue
            val descriptor = zone.optJSONObject("zoneId") ?: continue
            val type = descriptor.optString("zoneType").uppercase()
            val owner = descriptor.optString("ownerId")
            val ids = zone.optJSONArray("cardIds") ?: continue
            for (j in 0 until ids.length()) {
                val id = ids.optString(j)
                val card = cards.optJSONObject(id) ?: continue
                val visible = owner == viewer || type in setOf("BATTLEFIELD", "GRAVEYARD", "COMMAND") ||
                    (type == "EXILE" && !card.optBoolean("isFaceDown"))
                val name = if (visible && !card.optBoolean("isFaceDown")) card.optString("name").takeIf { it.isNotBlank() } else null
                now[id] = CardAt(type, owner, card.optBoolean("isToken"), name)
            }
        }
        val before = previous
        previous = now
        if (before == null) return listOf("Zone trace baseline captured; subsequent entries describe observed snapshot changes")
        val changes = mutableListOf<String>()
        for (id in (before.keys + now.keys)) {
            val old = before[id]
            val current = now[id]
            if (old?.zone == current?.zone && old?.owner == current?.owner) continue
            if (old == null && current == null) continue
            val label = labels.getOrPut(id) { nextLabel++ }
            // Identity is deliberately omitted: the same card may become hidden across snapshots.
            val title: String? = null
            val prefix = "Observed card #$label" + (if (title != null) " ($title)" else "")
            val from = old?.zone ?: "not visible"
            val to = current?.zone ?: if (old?.token == true) "not visible (token may have ceased to exist)" else "not visible"
            changes += "$prefix: $from → $to"
        }
        return if (changes.size <= 30) changes else changes.take(30) + "Zone trace: ${changes.size - 30} additional changes omitted"
    }
}
