package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RuinsOfOranRief
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class RuinsOfOranRiefScenarioTest {
    @Test
    fun entersTappedAndAddsCounterToColorlessCreatureWhichEnteredThisTurn() {
        val d = customScenario(RuinsOfOranRief, "Ulamog's Crusher")
        val p = d.player1
        val q = d.player2
        val card = d.putCardInHand(p, "Ruins of Oran-Rief")
        assertNull(d.playLand(p, card).error)
        d.resolveAll()
        val ruins = d.findPermanent(p, "Ruins of Oran-Rief")!!
        assertTrue(d.isTapped(ruins))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = q)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val eldrazi = d.putCardInHand(p, "Ulamog's Crusher")
        d.giveColorlessMana(p, 8)
        assertNull(d.castSpell(p, eldrazi).error)
        d.resolveAll()
        val creature = d.findPermanent(p, "Ulamog's Crusher")!!
        val id = d.cardRegistry.getCard("Ruins of Oran-Rief")!!.script.activatedAbilities[1].id
        assertNull(d.submit(ActivateAbility(p, ruins, id, targets = listOf(ChosenTarget.Permanent(creature)))).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
    }
}
