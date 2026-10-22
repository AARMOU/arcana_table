package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Lictor
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class LictorScenarioTest {
    @Test
    fun flashingInAfterAnOpponentsCreatureEnteredCreatesTramplingWarrior() {
        val d = customScenario(Lictor, "Grizzly Bears")
        val p = d.player1
        val q = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = q)
        val bear = d.putCardInHand(q, "Grizzly Bears")
        d.giveColorlessMana(q, 1)
        d.giveMana(q, Color.GREEN)
        assertNull(d.castSpell(q, bear).error)
        d.resolveAll()
        assertNull(d.passPriority(q).error)
        val lictor = d.putCardInHand(p, "Lictor")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, lictor).error)
        d.resolveAll()

        val source = d.findPermanent(p, "Lictor")!!
        val tokens = d.getCreatures(p).filter { it != source }
        assertTrue(d.projected.hasKeyword(source, Keyword.FLASH))
        assertEquals(1, tokens.size)
        assertEquals(3, d.projected.getPower(tokens.single()))
        assertEquals(3, d.projected.getToughness(tokens.single()))
        assertTrue(d.projected.hasKeyword(tokens.single(), Keyword.TRAMPLE))
    }

    @Test
    fun ownCreatureEnteringDoesNotSatisfyPheromoneTrail() {
        val d = customScenario(Lictor, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        val lictor = d.putCardInHand(p, "Lictor")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, lictor).error)
        d.resolveAll()
        assertEquals(2, d.getCreatures(p).size)
        assertTrue(d.state.stack.isEmpty())
    }

    @Test
    fun opponentEntryOnAnEarlierTurnDoesNotQualify() {
        val d = customScenario(Lictor, "Grizzly Bears")
        val p = d.player1
        val q = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = q)
        val bear = d.putCardInHand(q, "Grizzly Bears")
        d.giveColorlessMana(q, 1)
        d.giveMana(q, Color.GREEN)
        assertNull(d.castSpell(q, bear).error)
        d.resolveAll()
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val lictor = d.putCardInHand(p, "Lictor")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, lictor).error)
        d.resolveAll()
        assertEquals(1, d.getCreatures(p).size)
    }

    @Test
    fun creatureThatEnteredAndDiedStillQualifiesInTurnHistory() {
        val d = customScenario(Lictor, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val q = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = q)
        val bear = d.putCardInHand(q, "Grizzly Bears")
        d.giveColorlessMana(q, 1)
        d.giveMana(q, Color.GREEN)
        assertNull(d.castSpell(q, bear).error)
        d.resolveAll()
        val victim = d.findPermanent(q, "Grizzly Bears")!!
        assertNull(d.passPriority(q).error)
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, blade, listOf(victim)).error)
        d.resolveAll()
        assertTrue(d.getCreatures(q).isEmpty())
        assertNull(d.passPriority(q).error)
        val lictor = d.putCardInHand(p, "Lictor")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, lictor).error)
        d.resolveAll()
        assertEquals(2, d.getCreatures(p).size)
        assertTrue(d.getCreatures(p).any { d.projected.hasKeyword(it, Keyword.TRAMPLE) })
    }

}
