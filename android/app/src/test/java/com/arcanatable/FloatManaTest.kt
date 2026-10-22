package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.engine.registry.CardRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Build 112: float mana by hand from a land's actions; auto-pay then spends the floating mana first. */
class FloatManaTest {
    private val porCards by lazy { File("src/main/assets/argentum/por-cards.jsonl").bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) } }

    private fun cmc(cost: String): Int = Regex("\\{([^}]+)\\}").findAll(cost).sumOf { m -> m.groupValues[1].toIntOrNull() ?: if (m.groupValues[1] == "X") 0 else 1 }

    @Test fun manaLabelsAreShort() {
        assertEquals("Add {G}", floatManaLabel("{T}: Add {G}."))
        assertEquals("Add {R} or {G}", floatManaLabel("{T}: Add {R} or {G}."))
        assertEquals("{1}, {T}: Add {C}{C}", floatManaLabel("{1}, {T}: Add {C}{C}."))
    }

    @Test fun floatManaChoicesKeepOnlyUsableManaAbilities() {
        val forest = JSONObject().put("isManaAbility", true).put("description", "{T}: Add {G}.")
        val treasure = JSONObject().put("isManaAbility", true).put("description", "{T}, Sacrifice this artifact: Add one mana of any color.")
        val tapped = JSONObject().put("isManaAbility", true).put("isAffordable", false).put("description", "{T}: Add {G}.")
        val spell = JSONObject().put("description", "Cast Grizzly Bears")
        assertEquals(listOf(forest), ArgentumActions.floatManaChoices(listOf(forest, treasure, tapped, spell)))
    }

    @Test fun floatingManaIsSpentBeforeTappingMoreLands() {
        val registry = CardRegistry().apply { register(porCards) }
        // This is a payment scenario, so use a reproducible two-mana spell and a passive opponent.
        // The old random duel could stop at combat or a decision before ever floating any mana.
        val you = LocalSeat("You", List(20) { "Forest" } + List(20) { "Moon Sprite" }, null)
        val ai = LocalSeat("AI", List(40) { "Mountain" }, null)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, you, ai, paceMs = 0, gameSeed = 11L, emit = { inbox.put(it) })
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))

        var stage = 0 // 0 = looking, 1 = mana floated, 2 = spell cast
        var floatedPool = 0
        var tappedAfterFloat = 0
        var spellCost = 0
        var spellId = ""
        var tappedAfterCast = -1
        var poolAfterCast = -1
        var steps = 0
        var lastState = "No state received"
        fun send(action: JSONObject, epoch: String) =
            host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", epoch))

        try {
            while (steps++ < 1500 && stage < 3) {
                val msg = inbox.poll(10, TimeUnit.SECONDS) ?: error("Host stopped before payment stage $stage: $lastState")
                when (msg.optString("type")) {
                    "error" -> error("Host rejected payment scenario: ${msg.optString("message")}")
                    "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                    "gameOver" -> break
                    "stateUpdate" -> {
                        val epoch = msg.optString("interactionEpoch")
                        val legalJson = msg.optJSONArray("legalActions") ?: JSONArray()
                        val snapshot = ArgentumTableSnapshot.parse(msg.getJSONObject("state"), legalJson, epoch)
                        if (snapshot.gameOver) break
                        val me = snapshot.players[snapshot.viewingPlayer]
                        val lands = me.battlefield.filter { it.type.contains("Land") }
                        val pool = me.manaPool.values.sum()
                        val legal = snapshot.legalActions
                        fun type(info: JSONObject) = info.optJSONObject("action")?.optString("type")
                        lastState = "turn=${snapshot.turnNumber} step=${snapshot.stepKey} legal=${legal.map(::type)} pending=${msg.optJSONObject("pendingDecision")?.optString("type")}"
                        when (stage) {
                            1 -> { floatedPool = pool; tappedAfterFloat = lands.count { it.tapped } }
                            2 -> {
                                tappedAfterCast = lands.count { it.tapped }; poolAfterCast = pool; stage = 3; continue
                            }
                        }
                        if (legal.isEmpty()) continue
                        if (stage == 1) {
                            val cast = legal.firstOrNull { type(it) == "CastSpell" && it.optJSONObject("action")?.optString("cardId") == spellId }
                            if (cast != null) { send(cast.getJSONObject("action"), epoch); stage = 2; continue }
                        }
                        if (stage == 0) {
                            legal.firstOrNull { type(it) == "PlayLand" }?.let { send(it.getJSONObject("action"), epoch); continue }
                            val untapped = lands.count { !it.tapped }
                            val cast = legal.firstOrNull { info ->
                                type(info) == "CastSpell" && info.optBoolean("isAffordable", true) && !info.optBoolean("requiresTargets") && !info.optBoolean("hasXCost") &&
                                    cmc(info.optString("manaCostString")) in 2..untapped
                            }
                            val manaAbility = ArgentumActions.floatManaChoices(legal).firstOrNull()
                            if (cast != null && manaAbility != null && pool == 0) {
                                spellCost = cmc(cast.optString("manaCostString"))
                                spellId = cast.getJSONObject("action").optString("cardId")
                                send(manaAbility.getJSONObject("action"), epoch); stage = 1; continue
                            }
                        }
                        legal.firstOrNull { type(it) == "PassPriority" }?.let { send(it.getJSONObject("action"), epoch) }
                    }
                }
            }
        } finally {
            host.shutdown()
        }
        println("[float-mana] stage=$stage floated=$floatedPool tappedAfterFloat=$tappedAfterFloat cost=$spellCost tappedAfterCast=$tappedAfterCast poolAfterCast=$poolAfterCast")
        assertEquals("the spell was cast", 3, stage)
        assertEquals("one mana floated", 1, floatedPool)
        assertEquals("floating mana was spent", 0, poolAfterCast)
        assertTrue("only cost-1 more lands were tapped", tappedAfterCast == tappedAfterFloat + spellCost - 1)
    }
}
