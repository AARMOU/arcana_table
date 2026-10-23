package com.arcanatable

import org.json.JSONObject

/**
 * "Use 5" / "Use all" for a stack of identical permanents (Treasures, Clues, Foods): the same
 * ability, activated on one copy after another. Every activation is Argentum's own legal action for
 * that copy, taken from the latest state; nothing is invented on the phone.
 */
data class StackUse(val sourceName: String, val description: String, val remaining: Int, val manaColor: String? = null, val started: Boolean = false)

object StackUses {
    /** Legal actions that are this same ability on a permanent of this name, needing no choices. */
    fun matching(legal: List<JSONObject>, nameOf: (String) -> String?, sourceName: String, description: String): List<JSONObject> =
        legal.filter { info ->
            val action = info.optJSONObject("action") ?: return@filter false
            info.optString("description") == description && !info.optBoolean("requiresTargets") && !info.optBoolean("hasXCost") &&
                info.optBoolean("isAffordable", true) && action.optString("sourceId").takeIf(String::isNotBlank)?.let(nameOf) == sourceName
        }

    /** How many copies offer [info]'s ability right now (1 when it can't be repeated). */
    fun count(info: JSONObject, legal: List<JSONObject>, nameOf: (String) -> String?): Int {
        val source = info.optJSONObject("action")?.optString("sourceId")?.takeIf(String::isNotBlank) ?: return 1
        if (info.optBoolean("requiresTargets") || info.optBoolean("hasXCost")) return 1
        val name = nameOf(source) ?: return 1
        return matching(legal, nameOf, name, info.optString("description")).size.coerceAtLeast(1)
    }

    /** The next activation for [use], with its mana colour filled in, or null when none is left. */
    fun next(use: StackUse, legal: List<JSONObject>, nameOf: (String) -> String?): JSONObject? {
        if (use.remaining <= 0) return null
        val info = matching(legal, nameOf, use.sourceName, use.description).firstOrNull() ?: return null
        val action = JSONObject(info.optJSONObject("action").toString())
        if (info.optBoolean("requiresManaColorChoice")) {
            val colors = info.optJSONArray("availableManaColors")
            val pick = use.manaColor ?: colors?.optString(0)?.takeIf(String::isNotBlank) ?: return null
            action.put("manaColorChoice", pick)
        }
        return action
    }
}
