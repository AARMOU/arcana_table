package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stacked combat screens: stacks, counts, last-turn replay, block hints, previews and suggestions. */
class CombatStacksTest {
    private fun card(id: Int, name: String, p: String = "1", t: String = "1", keywords: List<String> = emptyList(),
                     counters: Map<String, Int> = emptyMap(), commander: Boolean = false, oracle: String? = null) =
        GameCard(id, name, "Creature — Test", Color.Red, power = p, toughness = t, keywords = keywords, counters = counters, isCommander = commander, oracleText = oracle)

    private class Board(vararg cards: Pair<String, GameCard>) {
        val byId = cards.toMap()
        val piece: (String) -> CombatPiece = { id -> byId[id]?.let { CombatPiece(id, it.name, it) } ?: CombatPiece(id, id) }
    }

    private fun attackPlan(attackers: List<String>, targets: List<String>, mandatory: List<String> = emptyList()) =
        ArgentumAttackPlan.from(JSONObject().put("action", JSONObject().put("type", "DeclareAttackers"))
            .put("validAttackers", JSONArray(attackers)).put("validAttackTargets", JSONArray(targets)).put("mandatoryAttackers", JSONArray(mandatory)))!!

    @Test fun identicalTokensStackButCountersAndCommandersStandAlone() {
        val board = Board(
            "g1" to card(1, "Goblin Token"), "g2" to card(2, "Goblin Token"), "g3" to card(3, "Goblin Token"),
            "g4" to card(4, "Goblin Token", counters = mapOf("+1/+1" to 1)),
            "k" to card(5, "Krenko, Mob Boss", "3", "3", commander = true),
        )
        val stacks = stacksOf(listOf("g1", "g2", "g3", "g4", "k"), board.piece)
        assertEquals(listOf(listOf("g1", "g2", "g3"), listOf("g4"), listOf("k")), stacks.map { it.ids })
    }

    @Test fun aCountBecomesThatManyAttackersAtLegalTargets() {
        val plan = attackPlan(listOf("g1", "g2", "g3", "g4"), listOf("mara", "jonah"), mandatory = listOf("g4"))
        val optionFor = plan.options.associateBy { it.attackerId }
        val ids = listOf("g1", "g2", "g3", "g4")
        val picked = allocateStack(emptyMap(), ids, mapOf("mara" to 2, "jonah" to 1), optionFor)
        assertEquals(3, picked.size)
        assertEquals("the creature that must attack is used first", "mara", picked["g4"])
        assertEquals(mapOf("mara" to 2, "jonah" to 1), stackAllocation(picked, ids))
        // Asking for none still sends the creature that must attack.
        assertEquals(mapOf("g4" to "mara"), allocateStack(picked, ids, emptyMap(), optionFor))
    }

    @Test fun plusAndMinusStayInsideTheStack() {
        val plan = attackPlan(listOf("a", "b"), listOf("ai"))
        val optionFor = plan.options.associateBy { it.attackerId }
        val stack = CombatStack("a", listOf("a", "b"))
        var selection = emptyMap<String, String>()
        repeat(5) { selection = stepStack(selection, stack, +1, optionFor, "ai") }
        assertEquals(2, selection.size)
        selection = stepStack(selection, stack, -1, optionFor, "ai")
        assertEquals(1, selection.size)
    }

    @Test fun sameAsLastTurnRebuildsTheAttackOnNewTokens() {
        CombatMemory.forget()
        val first = Board("g1" to card(1, "Goblin Token"), "g2" to card(2, "Goblin Token"), "g3" to card(3, "Goblin Token"))
        val plan1 = attackPlan(listOf("g1", "g2", "g3"), listOf("ai"))
        val stacks1 = stacksOf(listOf("g1", "g2", "g3"), first.piece)
        CombatMemory.remember("me", stacks1, mapOf("g1" to "ai", "g2" to "ai"), first.piece)
        assertNull("an attack Argentum has not accepted is not last turn's", CombatMemory.replay("me", stacks1, emptyMap(), plan1.options.associateBy { it.attackerId }, first.piece))
        CombatMemory.commit("me")

        val next = Board("n1" to card(9, "Goblin Token"), "n2" to card(10, "Goblin Token"), "n3" to card(11, "Goblin Token"), "n4" to card(12, "Goblin Token"))
        val plan2 = attackPlan(listOf("n1", "n2", "n3", "n4"), listOf("ai"))
        val replay = CombatMemory.replay("me", stacksOf(listOf("n1", "n2", "n3", "n4"), next.piece), emptyMap(), plan2.options.associateBy { it.attackerId }, next.piece)
        assertEquals(2, replay!!.size)
        assertNull(CombatMemory.replay("someone else", stacks1, emptyMap(), plan1.options.associateBy { it.attackerId }, first.piece))
    }

    @Test fun blockersSpreadAcrossAStackOfAttackers() {
        val board = Board("g1" to card(1, "Goblin Token"), "g2" to card(2, "Goblin Token"), "g3" to card(3, "Goblin Token"),
            "s1" to card(4, "Soldier Token"), "s2" to card(5, "Soldier Token"))
        val goblins = CombatStack("g1", listOf("g1", "g2", "g3"))
        val soldiers = CombatStack("s1", listOf("s1", "s2"))
        var selection = emptyMap<String, List<String>>()
        repeat(3) { selection = addBlock(selection, soldiers, goblins, emptyMap(), board.piece) }
        assertEquals(mapOf("s1" to listOf("g1"), "s2" to listOf("g2")), selection)
        assertEquals(2, stackBlocking(selection, soldiers, goblins))
        selection = removeBlock(selection, soldiers, goblins, mandatory = mapOf("s1" to listOf("g1")))
        assertEquals("a required block stays", mapOf("s1" to listOf("g1")), selection)
        assertEquals(mapOf("s1" to listOf("g1")), removeBlock(selection, soldiers, goblins, mandatory = mapOf("s1" to listOf("g1"))))
    }

    @Test fun evasionGreysOutBlockersThatCannotBlock() {
        val flyer = card(1, "Serra Angel", "4", "4", keywords = listOf("FLYING"))
        assertEquals("can't block flying", blockProblem(flyer, card(2, "Bear")))
        assertNull(blockProblem(flyer, card(3, "Spider", keywords = listOf("REACH"))))
        assertNull(blockProblem(flyer, card(4, "Archer", oracle = "Archer can block creatures with flying.")))
        assertNull(blockProblem(card(5, "Bear"), card(6, "Bird", keywords = listOf("Flying"))))
    }

    @Test fun menaceNeedsTwoBlockers() {
        val board = Board("m" to card(1, "Brute", "3", "3", keywords = listOf("MENACE")), "a" to card(2, "A"), "b" to card(3, "B"))
        assertEquals(listOf("m"), menaceShort(mapOf("a" to listOf("m")), board.piece))
        assertTrue(menaceShort(mapOf("a" to listOf("m"), "b" to listOf("m")), board.piece).isEmpty())
    }

    @Test fun damagePreviewCountsTrampleAndDoubleStrike() {
        val board = Board(
            "t" to card(1, "Trampler", "5", "5", keywords = listOf("TRAMPLE")),
            "d" to card(2, "Duelist", "2", "2", keywords = listOf("DOUBLE_STRIKE")),
            "w" to card(3, "Wall", "0", "3"),
        )
        assertEquals(5 + 4, incomingDamage(listOf("t", "d"), emptyMap(), board.piece))
        assertEquals(2 + 4, incomingDamage(listOf("t", "d"), mapOf("w" to listOf("t")), board.piece))
    }

    @Test fun commanderDamageAddsUpByCommander() {
        val board = Board("k" to card(1, "Krenko, Mob Boss", "3", "3", commander = true), "g" to card(2, "Goblin"))
        assertEquals(mapOf("Krenko, Mob Boss" to 3), incomingCommanderDamage(listOf("k", "g"), emptyMap(), board.piece))
        assertEquals(mapOf("Krenko, Mob Boss" to 3), commanderPreview(mapOf("k" to "ai", "g" to "ai"), "ai", board.piece))
    }

    @Test fun suggestedBlocksChumpOnlyWhenTheDamageWouldBeLethal() {
        val board = Board("big" to card(1, "Giant", "6", "6"), "chump" to card(2, "Squire", "1", "1"))
        assertTrue(suggestBlocks(listOf("big"), listOf("chump"), life = 30, emptyMap(), emptyMap(), board.piece).isEmpty())
        assertEquals(mapOf("chump" to listOf("big")), suggestBlocks(listOf("big"), listOf("chump"), life = 5, emptyMap(), emptyMap(), board.piece))
    }

    @Test fun suggestedBlocksCountCommanderDamageTowardTwentyOne() {
        val board = Board("k" to card(1, "Krenko, Mob Boss", "4", "4", commander = true), "chump" to card(2, "Squire", "1", "1"))
        val blocks = suggestBlocks(listOf("k"), listOf("chump"), life = 40, mapOf("Krenko, Mob Boss" to 18), emptyMap(), board.piece)
        assertEquals(mapOf("chump" to listOf("k")), blocks)
    }

    @Test fun blockPlanReadsWhoEachAttackerIsAttacking() {
        val state = JSONObject().put("cards", JSONObject()
            .put("a1", JSONObject().put("isAttacking", true).put("attackingTarget", "me"))
            .put("a2", JSONObject().put("isAttacking", true).put("attackingTarget", JSONObject().put("value", "jonah"))))
        val info = JSONObject().put("action", JSONObject().put("type", "DeclareBlockers")).put("validBlockers", JSONArray(listOf("b1")))
        val plan = ArgentumBlockPlan.from(info, state)
        assertNotNull(plan)
        assertEquals(mapOf("a1" to "me", "a2" to "jonah"), plan!!.attackTargets)
    }
}
