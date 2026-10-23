package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChitteringDispatcher
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class ChitteringDispatcherScenarioTest {
    @Test fun castAndAttackUsesDevoidAndMyriadWithoutCopiesInDuel() {
        val d = customScenario(ChitteringDispatcher)
        val p = d.player1
        val card = d.putCardInHand(p, "Chittering Dispatcher")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(card, Keyword.DEVOID))
        assertTrue(d.projected.hasKeyword(card, Keyword.MYRIAD))
        d.removeSummoningSickness(card)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(card), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(1, d.permanentsNamed(p, "Chittering Dispatcher").size)
    }

    @Test fun dyingProducesOneFunctionalEldraziSpawnManaToken() {
        val d = customScenario(ChitteringDispatcher, "Murder")
        val p = d.player1
        val card = d.putCreatureOnBattlefield(p, "Chittering Dispatcher")
        val removal = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, removal, listOf(card)).error)
        d.resolveAll()
        assertFalse(card in d.getCreatures(p))
        val spawns = d.permanentsNamed(p, "Eldrazi Spawn")
        assertEquals(1, spawns.size)
        assertNull(d.activate(p, spawns.single()).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").isEmpty())
    }
}
