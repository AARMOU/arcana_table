package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Example data for Studio's "Show me…" sample screens (owner, Nov 7). Plain, recognisable placeholders. Nothing here is
 * sent to the engine: the sample screens' buttons do nothing.
 */
internal object StudioPreviews {
    /**
     * Off for now: CI's test of these samples (`showMeSamplesOpenWithoutTheRealMoment`) never finishes, even with the
     * samples remembered, so something in them may keep the screen busy forever. Kept off the owner's phone until found.
     */
    const val ENABLED = false
    private val names = listOf("Grizzly Bears", "Llanowar Elves", "Serra Angel", "Shivan Dragon", "Forest", "Island", "Lightning Bolt", "Counterspell")
    private val colors = listOf(0xFF4E7A44, 0xFF4E7A44, 0xFFC9B98A, 0xFFA2483A, 0xFF4E7A44, 0xFF3F6A93, 0xFFA2483A, 0xFF3F6A93)

    fun cards(n: Int): List<GameCard> = (0 until n).map { i ->
        GameCard(990_000 + i, names[i % names.size], if (i % names.size in 4..5) "Land" else "Creature", Color(colors[i % colors.size]))
    }

    fun piece(id: String): CombatPiece = when {
        id.startsWith("preview-player") -> CombatPiece(id, if (id.endsWith("1")) "AI" else "OPPONENT 2", life = 40)
        else -> cards(4)[id.removePrefix("preview-a").toIntOrNull()?.coerceIn(0, 3) ?: 0].let { CombatPiece(id, it.name, it) }
    }

    fun attackPlan(): ArgentumAttackPlan {
        val info = JSONObject()
            .put("action", JSONObject().put("type", "DeclareAttackers"))
            .put("validAttackers", JSONArray(listOf("preview-a0", "preview-a1", "preview-a2")))
            .put("mandatoryAttackers", JSONArray())
            .put("validAttackTargets", JSONArray(listOf("preview-player1", "preview-player2")))
        return checkNotNull(ArgentumAttackPlan.from(info))
    }

    fun actions(): List<JSONObject> = listOf(
        JSONObject().put("description", "Cast Grizzly Bears").put("action", JSONObject().put("type", "CastSpell").put("sourceId", "preview-a0")),
        JSONObject().put("description", "Activate: add {G}").put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", "preview-a1")),
        JSONObject().put("description", "Play Forest").put("action", JSONObject().put("type", "PlayLand").put("sourceId", "preview-a4")),
    )
}
