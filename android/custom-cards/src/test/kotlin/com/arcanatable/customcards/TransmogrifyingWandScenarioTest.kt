package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TransmogrifyingWand
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class TransmogrifyingWandScenarioTest {
    @Test
    fun destroysTargetAndGivesItsControllerTheOxWhileSpendingOneCharge() {
        val d = customScenario(TransmogrifyingWand, "Grizzly Bears")
        val p = d.player1
        val wandCard = d.putCardInHand(p, "Transmogrifying Wand")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, wandCard).error)
        d.resolveAll()

        val wand = d.findPermanent(p, "Transmogrifying Wand")!!
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Transmogrifying Wand")!!.script.activatedAbilities.single()
        d.giveColorlessMana(p, 1)
        assertNull(
            d.submit(
                ActivateAbility(p, wand, ability.id, targets = listOf(ChosenTarget.Permanent(victim))),
            ).error,
        )
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        val ox = d.permanentsNamed(d.player2, "Ox").single()
        assertEquals(2, d.projected.getPower(ox))
        assertEquals(4, d.projected.getToughness(ox))
        assertTrue(d.permanentsNamed(p, "Ox").isEmpty())
        assertEquals(
            2,
            d.state.getEntity(wand)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE),
        )
    }
}
