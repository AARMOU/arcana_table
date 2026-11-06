package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AlmsCollector
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

private val TargetDrawsTwoTest = card("Target Draws Two Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Target player draws two cards."
    spell {
        val who = target(Targets.Player)
        effect = Effects.DrawCards(2, who)
    }
    metadata { rarity = Rarity.COMMON; collectorNumber = "1" }
}

private val TargetDrawsOneTwiceTest = card("Target Draws One Twice Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Target player draws a card, then draws a card."
    spell {
        val who = target(Targets.Player)
        effect = Effects.DrawCards(1, who) then Effects.DrawCards(1, who)
    }
    metadata { rarity = Rarity.COMMON; collectorNumber = "2" }
}

private val EachPlayerDrawsTwoTest = card("Each Player Draws Two Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Each player draws two cards."
    spell { effect = Effects.DrawCards(2, EffectTarget.PlayerRef(Player.Each)) }
    metadata { rarity = Rarity.COMMON; collectorNumber = "3" }
}

/**
 * Alms Collector — "If an opponent would draw two or more cards, instead you and that player each
 * draw a card." On engine patch 0042 (announcement-level draw replacement replaces the whole
 * instruction). Rulings 2017-08-25 drive the cases.
 */
class AlmsCollectorScenarioTest {

    private val testSpells = listOf(TargetDrawsTwoTest, TargetDrawsOneTwiceTest, EachPlayerDrawsTwoTest)

    private fun twoPlayer(vararg library: String): GameTestDriver =
        customScenario(AlmsCollector, *library).apply { testSpells.forEach(::registerCard) }

    private fun GameTestDriver.hand(p: EntityId) = getHand(p).size

    private fun GameTestDriver.cast(caster: EntityId, name: String, target: EntityId? = null) {
        val id = putCardInHand(caster, name)
        assertNull(castSpell(caster, id, listOfNotNull(target)).error)
        resolveAll()
    }

    @Test
    fun anOpponentDrawingTwoInsteadDrawsOneAndYouDrawOne() {
        val d = twoPlayer()
        val me = d.player1; val opp = d.player2
        d.putCreatureOnBattlefield(me, "Alms Collector")
        val (mine, theirs) = d.hand(me) to d.hand(opp)
        d.cast(me, "Target Draws Two Test", opp)
        assertEquals("that player draws one, not two", theirs + 1, d.hand(opp))
        assertEquals("you draw one", mine + 1, d.hand(me))
    }

    @Test
    fun youDrawingTwoIsNotReplaced() {
        val d = twoPlayer()
        val me = d.player1
        d.putCreatureOnBattlefield(me, "Alms Collector")
        val before = d.hand(me)
        d.cast(me, "Target Draws Two Test", me)
        assertEquals("only opponents' draws are watched", before + 2, d.hand(me))
    }

    @Test
    fun twoSeparateOneCardDrawsAreNotReplaced() {
        // Ruling: count how many times the word "draw" is used — two single draws are not "two or more".
        val d = twoPlayer()
        val me = d.player1; val opp = d.player2
        d.putCreatureOnBattlefield(me, "Alms Collector")
        val (mine, theirs) = d.hand(me) to d.hand(opp)
        d.cast(me, "Target Draws One Twice Test", opp)
        assertEquals(theirs + 2, d.hand(opp))
        assertEquals(mine, d.hand(me))
    }

    @Test
    fun anOpponentsDrawStepIsNotReplaced() {
        val d = twoPlayer()
        val me = d.player1; val opp = d.player2
        d.putCreatureOnBattlefield(me, "Alms Collector")
        val (mine, theirs) = d.hand(me) to d.hand(opp)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, opp)
        assertEquals("one card in the draw step", theirs + 1, d.hand(opp))
        assertEquals(mine, d.hand(me))
    }

    @Test
    fun withoutAlmsCollectorTheOpponentDrawsTwo() {
        val d = twoPlayer()
        val theirs = d.hand(d.player2)
        d.cast(d.player1, "Target Draws Two Test", d.player2)
        assertEquals(theirs + 2, d.hand(d.player2))
    }

    @Test
    fun twoAlmsCollectorsAndEachPlayerDrawsTwoBothEndUpWithTwo() {
        // Ruling: each Alms Collector replaces the other player's draw; both players draw two.
        val d = twoPlayer()
        val a = d.player1; val b = d.player2
        d.putCreatureOnBattlefield(a, "Alms Collector")
        d.putCreatureOnBattlefield(b, "Alms Collector")
        val (ha, hb) = d.hand(a) to d.hand(b)
        d.cast(a, "Each Player Draws Two Test")
        assertEquals(ha + 2, d.hand(a))
        assertEquals(hb + 2, d.hand(b))
    }

    @Test
    fun fourPlayerOnlyTheCollectorsControllerAndTheDrawerDraw() {
        val d = CardPool.driver().apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(AlmsCollector))))
            testSpells.forEach(::registerCard)
        }
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (caster, collector, drawer, bystander) = seats
        d.putCreatureOnBattlefield(collector, "Alms Collector")
        val before = seats.associateWith { d.hand(it) }
        d.cast(caster, "Target Draws Two Test", drawer)
        assertEquals("drawer draws one", before[drawer]!! + 1, d.hand(drawer))
        assertEquals("Alms Collector's controller draws one", before[collector]!! + 1, d.hand(collector))
        assertEquals("caster unaffected (hand minus the cast spell's net 0)", before[caster]!!, d.hand(caster))
        assertEquals("bystander unaffected", before[bystander]!!, d.hand(bystander))
    }

    @Test
    fun fourPlayerTwoCollectorsOnlyOneAppliesToAThirdPlayersDraw() {
        // Ruling: the drawing player chooses which Alms Collector applies; exactly one of the two
        // controllers draws, and the drawer draws exactly one card.
        val d = CardPool.driver().apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(AlmsCollector))))
            testSpells.forEach(::registerCard)
        }
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (caster, c1, c2, drawer) = seats
        d.putCreatureOnBattlefield(c1, "Alms Collector")
        d.putCreatureOnBattlefield(c2, "Alms Collector")
        val before = seats.associateWith { d.hand(it) }
        d.cast(caster, "Target Draws Two Test", drawer)
        assertEquals(before[drawer]!! + 1, d.hand(drawer))
        val extra = (d.hand(c1) - before[c1]!!) + (d.hand(c2) - before[c2]!!)
        assertEquals("exactly one collector's controller draws", 1, extra)
    }

    @Test
    fun aPausedReplacementDrawDoesNotLeakTheOriginalExtraCard() {
        // Your replacement draw can itself pause (here: an optional dredge prompt from Stinkweed
        // Imp in your graveyard). After answering, the opponent must still draw exactly one card —
        // the original "draw two" was replaced as a whole (engine patch 0042).
        val d = twoPlayer("Stinkweed Imp")
        val me = d.player1; val opp = d.player2
        d.putCreatureOnBattlefield(me, "Alms Collector")
        d.putCardInGraveyard(me, "Stinkweed Imp")
        val (mine, theirs) = d.hand(me) to d.hand(opp)
        val spell = d.putCardInHand(me, "Target Draws Two Test")
        assertNull(d.castSpell(me, spell, listOf(opp)).error)
        var asked = false
        var guard = 0
        while (guard++ < 40) {
            val decision = d.state.pendingDecision
            when {
                decision != null -> { asked = true; d.decide(Choices(yes = false)) }
                d.state.stack.isNotEmpty() -> d.bothPass()
                else -> break
            }
        }
        assertTrue("the dredge question paused the replacement", asked)
        assertEquals("you drew one (declined dredge)", mine + 1, d.hand(me))
        assertEquals("that player drew exactly one", theirs + 1, d.hand(opp))
    }
}
