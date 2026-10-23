package com.arcanatable

import com.arcanatable.engine.OfflineGameSetup
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Build 97 helpers: stack use, the played-card feed and recap, attack stacks, number limits, premades. */
class Build97Test {
    private fun treasure(source: String) = JSONObject().put("description", "{T}, Sacrifice this artifact: Add one mana of any color.")
        .put("isManaAbility", true).put("requiresManaColorChoice", true).put("availableManaColors", JSONArray(listOf("RED", "GREEN")))
        .put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", source).put("abilityId", "a1"))

    private val names = mapOf("t1" to "Treasure", "t2" to "Treasure", "t3" to "Treasure", "c1" to "Clue")

    @Test fun treasuresShowAsSacrificeForMana() {
        val t = treasure("t1")
        assertTrue(ArgentumActions.isSacrificeMana(t))
        assertEquals("Sacrifice for mana", ArgentumActions.menuLabel(t))
        val landMana = JSONObject().put("description", "{T}: Add {G}.").put("isManaAbility", true).put("action", JSONObject().put("type", "ActivateAbility"))
        assertEquals(listOf(t), ArgentumActions.visibleChoices(listOf(t, landMana)))
    }

    @Test fun stackUseRepeatsTheSameAbilityWithTheSameColour() {
        val legal = listOf(treasure("t1"), treasure("t2"), treasure("t3"))
        assertEquals(3, StackUses.count(legal[0], legal, names::get))
        val use = StackUse("Treasure", legal[0].getString("description"), remaining = 2, manaColor = "GREEN", started = true)
        val next = StackUses.next(use, legal.drop(1), names::get)!!
        assertEquals("t2", next.getString("sourceId"))
        assertEquals("GREEN", next.getString("manaColorChoice"))
        assertNull(StackUses.next(use.copy(remaining = 0), legal, names::get))
        assertNull(StackUses.next(use, emptyList(), names::get))
    }

    @Test fun feedKeepsSpellsAndLandsButNotTokens() {
        val cards = JSONObject()
            .put("s1", JSONObject().put("typeLine", "Creature — Elf").put("imageUri", "https://img/elf"))
            .put("l1", JSONObject().put("typeLine", "Basic Land — Forest"))
            .put("k1", JSONObject().put("typeLine", "Token Creature — Rabbit").put("isToken", true))
        val events = JSONArray()
            .put(JSONObject().put("type", "spellCast").put("spellId", "s1").put("spellName", "Llanowar Elves").put("isYours", false).put("description", "Opponent cast Llanowar Elves"))
            .put(JSONObject().put("type", "permanentEntered").put("cardId", "l1").put("cardName", "Forest").put("isYours", false))
            .put(JSONObject().put("type", "permanentEntered").put("cardId", "k1").put("cardName", "Rabbit").put("isYours", false))
            .put(JSONObject().put("type", "abilityTriggered").put("sourceId", "s1").put("sourceName", "Llanowar Elves").put("abilityDescription", "Draw a card").put("isYours", false))
        val played = PlayFeedParser.parse(events, cards)
        assertEquals(listOf("Llanowar Elves", "Forest", "Llanowar Elves"), played.map { it.name })
        assertEquals(listOf(PlayedCard.Kind.SPELL, PlayedCard.Kind.LAND, PlayedCard.Kind.ABILITY), played.map { it.kind })
        assertEquals("https://img/elf", played[0].imageUrl)

        val recap = PlayFeedParser.recap(JSONArray()
            .put(events.get(0)).put(events.get(1))
            .put(JSONObject().put("type", "creatureAttacked").put("creatureName", "Bear"))
            .put(JSONObject().put("type", "lifeChanged").put("playerId", "me").put("oldLife", 40).put("newLife", 36).put("isYours", true))
            .put(JSONObject().put("type", "creatureDied").put("creatureName", "Hare Apparent")), cards, "me", TurnRecap())
        assertEquals("AI's turn: played Llanowar Elves, Forest; attacked with 1; you lost 4 life; died: Hare Apparent.", recap.line("AI"))
    }

    @Test fun identicalAttackersShareOneTile() {
        val info = JSONObject().put("action", JSONObject().put("type", "DeclareAttackers").put("playerId", "me"))
            .put("validAttackers", JSONArray(listOf("r1", "r2", "r3", "c1"))).put("validAttackTargets", JSONArray(listOf("ai")))
        val plan = ArgentumAttackPlan.from(info)!!
        val piece = { id: String -> CombatPiece(id, if (id.startsWith("r")) "Rabbit Token" else "Captain") }
        val groups = attackGroups(plan, piece)
        assertEquals(2, groups.size)
        assertEquals(listOf("r1", "r2", "r3"), groups["r1"])
    }

    @Test fun numbersStayInsideArgentumsLimits() {
        assertEquals(55, clampToRange(63, 0..55))
        assertEquals(2, clampToRange(-4, 2..9))
        assertEquals(7, clampToRange(7, 0..55))
    }

    @Test fun alwaysYesIsKeyedByCardAndQuestion() {
        val d = JSONObject().put("prompt", "Discard a card to draw?").put("context", JSONObject().put("sourceName", "Ratchet"))
        assertEquals("Ratchet|Discard a card to draw?", autoYesKey(d))
    }

    @Test fun roccoAndTailsArePremadesTheAiCanUse() {
        val decks = OfflineGameSetup.parsePremades(File("src/main/assets/argentum/commander-decks.json").readText())
        val rocco = decks.first { it.id == "tokens_win" }
        val tails = decks.first { it.id == "artificer_v4" }
        assertEquals("Rocco, Cabaretti Caterer", rocco.commander)
        assertEquals(100, rocco.cards.size)
        assertEquals(100, tails.cards.size)
    }

    @Test fun bounceCostsAreChosenAndSent() {
        val info = JSONObject().put("description", "Return an Elf permanent you control to its owner's hand: Untap target")
            .put("requiresTargets", true).put("validTargets", JSONArray(listOf("land1")))
            .put("additionalCostInfo", JSONObject().put("description", "Return an Elf you control").put("costType", "ReturnToHand")
                .put("validBounceTargets", JSONArray(listOf("elf1", "elf2"))).put("bounceCount", 1))
            .put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", "elf1"))
        val plan = ArgentumActionPlan.from(info)
        assertTrue(plan.needsInput)
        val cost = plan.slots.first { it.costField != null }
        assertEquals("bouncedPermanents", cost.costField)
        val target = plan.slots.first { it.costField == null }
        val picks = mapOf(cost.index to listOf("elf2"), target.index to listOf("land1"))
        assertNull(plan.problem(picks, emptyMap(), null))
        val built = plan.build(picks, null, emptyMap(), null) { id -> JSONObject().put("type", "Permanent").put("entityId", id) }
        assertEquals("elf2", built.getJSONObject("costPayment").getJSONArray("bouncedPermanents").getString(0))
        assertEquals(1, built.getJSONArray("targets").length())
        assertEquals(listOf("land1"), plan.chosenTargets(picks))
    }
}
