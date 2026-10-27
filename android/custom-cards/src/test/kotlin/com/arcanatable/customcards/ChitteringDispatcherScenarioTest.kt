package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChitteringDispatcher
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ChitteringDispatcherScenarioTest {
    private fun setup() = customScenario(ChitteringDispatcher, "Lightning Bolt", "Unsummon")

    private fun GameTestDriver.spawns(p: EntityId) = getCreatures(p).filter { projected.hasSubtype(it, "Spawn") }

    @Test fun castColorlessCreatureWithMyriad() {
        val d = setup()
        val card = d.putCardInHand(d.player1, "Chittering Dispatcher")
        assertTrue("devoid", d.state.projectedState.getColors(card).isEmpty())
        d.giveMana(d.player1, Color.GREEN); d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        val dispatcher = d.permanentsNamed(d.player1, "Chittering Dispatcher").single()
        assertTrue(d.projected.hasKeyword(dispatcher, Keyword.MYRIAD))
        assertEquals(2, d.projected.getPower(dispatcher)); assertEquals(3, d.projected.getToughness(dispatcher))
    }

    @Test fun dyingCreatesOneSpawnThatSacrificesForColorless() {
        val d = setup()
        val dispatcher = d.putCreatureOnBattlefield(d.player1, "Chittering Dispatcher")
        assertTrue(d.spawns(d.player1).isEmpty())
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, bolt, listOf(dispatcher)).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player1, "Chittering Dispatcher").isEmpty())
        val spawn = d.spawns(d.player1).single()
        assertEquals(0, d.projected.getPower(spawn)); assertEquals(1, d.projected.getToughness(spawn))
        assertTrue(d.state.projectedState.getColors(spawn).isEmpty())
        val sac = d.abilityActions(d.player1, spawn).first().action as ActivateAbility
        assertNull(d.submit(sac).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertTrue(d.spawns(d.player1).isEmpty())
    }

    @Test fun bouncingAlsoCreatesASpawnForItsController() {
        val d = setup()
        val dispatcher = d.putCreatureOnBattlefield(d.player2, "Chittering Dispatcher")
        val unsummon = d.putCardInHand(d.player1, "Unsummon")
        d.giveMana(d.player1, Color.BLUE)
        assertNull(d.castSpell(d.player1, unsummon, listOf(dispatcher)).error)
        d.resolveAll()
        assertEquals(1, d.spawns(d.player2).size)
        assertTrue(d.spawns(d.player1).isEmpty())
    }

    @Test fun inTwoPlayerGamesMyriadMakesNoCopies() {
        val d = setup()
        val dispatcher = d.putCreatureOnBattlefield(d.player1, "Chittering Dispatcher")
        d.removeSummoningSickness(dispatcher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(dispatcher), d.player2).error)
        d.resolveAll()
        assertEquals(1, d.getCreatures(d.player1).count { d.getCardName(it) == "Chittering Dispatcher" })
    }
}
