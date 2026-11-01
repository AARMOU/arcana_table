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

    // ------------------------------------------------ outcome previews (#759 review fixtures)

    private fun result(attacker: String, selection: Map<String, List<String>>, vararg cards: Pair<String, GameCard>) =
        blockResult(attacker, selection, Board(*cards).piece)

    @Test fun vanillaBlocksTradeKillChumpAndHoldOff() {
        val bear = "bear" to card(1, "Grizzly Bears", "2", "2")
        assertEquals(BlockOutcome.UNBLOCKED, result("bear", emptyMap(), bear).outcome)
        assertEquals(BlockOutcome.TRADE, result("bear", mapOf("cub" to listOf("bear")), bear, "cub" to card(2, "Cub", "2", "2")).outcome)
        assertEquals(BlockOutcome.ATTACKER_DIES, result("bear", mapOf("ogre" to listOf("bear")), bear, "ogre" to card(3, "Ogre", "3", "3")).outcome)
        val chump = result("bear", mapOf("elf" to listOf("bear")), bear, "elf" to card(4, "Elf", "1", "1"))
        assertEquals(BlockOutcome.CHUMP, chump.outcome)
        assertEquals(listOf("elf"), chump.blockersDying)
        assertEquals(BlockOutcome.HELD_OFF, result("bear", mapOf("wall" to listOf("bear")), bear, "wall" to card(5, "Wall", "0", "4")).outcome)
    }

    @Test fun markedDamageDeathtouchAndIndestructibleCount() {
        val hurt = card(1, "Hurt Ogre", "3", "3").copy(damage = 2)
        assertEquals(BlockOutcome.TRADE, result("o", mapOf("e" to listOf("o")), "o" to hurt, "e" to card(2, "Elf", "1", "1")).outcome)
        val angel = "angel" to card(3, "Serra Angel", "4", "4", keywords = listOf("FLYING"))
        assertEquals(BlockOutcome.TRADE, result("angel", mapOf("s" to listOf("angel")), angel, "s" to card(4, "Snake", "1", "1", keywords = listOf("DEATHTOUCH"))).outcome)
        assertEquals(BlockOutcome.HELD_OFF, result("angel", mapOf("g" to listOf("angel")), angel, "g" to card(5, "Golem", "1", "1", keywords = listOf("INDESTRUCTIBLE"))).outcome)
    }

    @Test fun firstStrikeAttackerSurvivesAnEqualBlocker() {
        val r = result("fs", mapOf("b" to listOf("fs")), "fs" to card(1, "Knight", "2", "2", keywords = listOf("FIRST_STRIKE")), "b" to card(2, "Bear", "2", "2"))
        assertEquals(BlockOutcome.CHUMP, r.outcome)
        assertEquals(false, r.attackerDies)
    }

    @Test fun doubleStrikeBlockerKillsAThreeThreeInTwoSteps() {
        val attacker = card(1, "Ogre", "3", "3")
        val ds = card(2, "Duelist", "2", "2", keywords = listOf("DOUBLE_STRIKE"))
        assertEquals(BlockOutcome.TRADE, result("o", mapOf("d" to listOf("o")), "o" to attacker, "d" to ds).outcome)
        // The attack screen must not call the 3/3 safe against that blocker.
        assertEquals(AttackRisk("Could trade with Duelist", true), attackRisk(attacker, listOf(ds)))
    }

    @Test fun doubleStrikeAttackerKillsFirstAndSurvives() {
        val r = result("d", mapOf("b" to listOf("d")), "d" to card(1, "Duelist", "2", "2", keywords = listOf("DOUBLE_STRIKE")), "b" to card(2, "Bear", "2", "2"))
        assertEquals(BlockOutcome.CHUMP, r.outcome)
        assertEquals(false, r.attackerDies)
    }

    @Test fun aBlockerOnTwoAttackersIsUnclearNotDoubleCounted() {
        val board = Board("a1" to card(1, "Ogre", "3", "3"), "a2" to card(2, "Ogre", "3", "3"), "w" to card(3, "Big Wall", "3", "8"))
        val selection = mapOf("w" to listOf("a1", "a2"))
        listOf("a1", "a2").forEach { a ->
            val r = blockResult(a, selection, board.piece)
            assertEquals(BlockOutcome.UNCLEAR, r.outcome)
            assertNull(r.attackerDies)
        }
    }

    @Test fun severalBlockersKnowTheAttackerDiesButNotWhichBlockerDoes() {
        val board = Board("angel" to card(1, "Serra Angel", "4", "4"), "cub" to card(2, "Cub", "2", "2"), "wall" to card(3, "Wall", "2", "4"))
        val r = blockResult("angel", mapOf("cub" to listOf("angel"), "wall" to listOf("angel")), board.piece)
        assertEquals(BlockOutcome.UNCLEAR, r.outcome)
        assertEquals(true, r.attackerDies)
        assertNull(r.blockersDying)
        // An attacker that can't kill any of them: nobody on the blocking side dies, for certain.
        val small = Board("e" to card(4, "Elf", "1", "1"), "w1" to card(5, "Wall", "0", "4"), "w2" to card(6, "Wall", "0", "4"))
        assertEquals(BlockOutcome.HELD_OFF, blockResult("e", mapOf("w1" to listOf("e"), "w2" to listOf("e")), small.piece).outcome)
    }

    @Test fun unmodelledMechanicsAreUnclearNotGuessed() {
        val infect = card(1, "Blight Mamba", "1", "1", keywords = listOf("INFECT"))
        val r = result("i", mapOf("b" to listOf("i")), "i" to infect, "b" to card(2, "Bear", "2", "2"))
        assertEquals(BlockOutcome.UNCLEAR, r.outcome)
        assertEquals("infect", r.reason)
        val pro = card(3, "Knight", "2", "2", oracle = "Protection from green")
        assertTrue(attackRisk(pro, listOf(card(4, "Bear", "2", "2")))!!.unclear)
        val trigger = card(5, "Beast", "2", "2", oracle = "Whenever Beast blocks or becomes blocked, it gets +2/+2 until end of turn.")
        assertEquals(BlockOutcome.UNCLEAR, result("t", mapOf("b" to listOf("t")), "t" to trigger, "b" to card(6, "Bear", "2", "2")).outcome)
    }

    @Test fun attackRiskNamesTradesDeathsEvasionAndMenace() {
        val elves = card(10, "Llanowar Elves", "1", "1")
        val birds = card(11, "Birds of Paradise", "0", "1", keywords = listOf("FLYING"))
        assertEquals(AttackRisk("Could trade with Llanowar Elves", true), attackRisk(card(1, "Saproling", "1", "1"), listOf(elves, birds)))
        assertEquals(AttackRisk("Survives any one blocker", false), attackRisk(card(2, "Badgermole Cub", "2", "2"), listOf(elves, birds)))
        assertEquals(AttackRisk("0 power: deals no damage", true), attackRisk(card(3, "Gilded Goose", "0", "2"), listOf(elves)))
        assertEquals(AttackRisk("Could die to Wall of Spikes", true), attackRisk(card(4, "Elf", "1", "1"), listOf(card(12, "Wall of Spikes", "1", "5"))))
        assertEquals(AttackRisk("Nothing they have can block it", false), attackRisk(card(6, "Drake", "2", "2", keywords = listOf("FLYING")), listOf(elves, elves.copy(id = 14, tapped = true))))
        val menace = card(7, "Goblin", "2", "2", keywords = listOf("MENACE"))
        assertEquals(AttackRisk("Menace: they can't block it", false), attackRisk(menace, listOf(elves)))
        assertTrue(attackRisk(menace, listOf(elves, card(15, "Bear", "2", "2")))!!.unclear)
        assertTrue(attackRisk(card(8, "Raider", "2", "2", oracle = "Whenever Raider attacks, it gets +2/+0 until end of turn."), listOf(elves))!!.unclear)
        assertNull(attackRisk(null, listOf(elves)))
    }

    @Test fun unblockedDamageCountsDoubleStrikeAndLifeIsOnlyShownForPlainDamage() {
        val ds = card(1, "Duelist", "3", "3", keywords = listOf("DOUBLE_STRIKE"))
        val board = Board("d" to ds)
        assertEquals(6, damageThrough("d", emptyMap(), board.piece))
        assertEquals(mapOf("p1" to 6), attackPreview(mapOf("d" to "p1"), board.piece))
        assertEquals(14, lifeAfterUnblocked(20, listOf(ds), 6))
        // 3 infect damage is poison, not 20 → 17.
        assertNull(lifeAfterUnblocked(20, listOf(card(2, "Mamba", "3", "3", keywords = listOf("INFECT"))), 3))
        assertNull(lifeAfterUnblocked(20, listOf(null), 3))
    }

    @Test fun mixedStackOutcomesAreCountedPerCreature() {
        val board = Board("g1" to card(1, "Goblin", "1", "1"), "g2" to card(2, "Goblin", "1", "1"), "g3" to card(3, "Goblin", "1", "1"),
            "elf" to card(4, "Elf", "1", "1"), "wall" to card(5, "Wall", "0", "4"))
        val selection = mapOf("elf" to listOf("g1"), "wall" to listOf("g2"))
        assertEquals("1 Unblocked · 1 Trade · 1 Held off", stackOutcomeSummary(listOf("g1", "g2", "g3"), selection, board.piece))
    }

    @Test fun summaryNamesAreCounted() {
        assertEquals("None", namesWithCounts(emptyList()))
        assertEquals("2 × Goblin Token, Elf", namesWithCounts(listOf("Goblin Token", "Elf", "Goblin Token")))
    }
}
