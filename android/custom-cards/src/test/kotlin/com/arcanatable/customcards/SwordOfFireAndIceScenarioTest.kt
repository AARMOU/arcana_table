package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SwordOfFireAndIce
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class SwordOfFireAndIceScenarioTest {
    @Test
    fun equippedCreatureDamageLetsYouDealTwoAndDraw() {
        val d = customScenario(SwordOfFireAndIce, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sword = d.putPermanentOnBattlefield(player, "Sword of Fire and Ice")
        d.giveColorlessMana(player, 2)
        val equip = d.cardRegistry.getCard("Sword of Fire and Ice")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, sword, equip.id, targets = listOf(ChosenTarget.Permanent(bearer)))).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        d.removeSummoningSickness(bearer)
        val handBefore = d.getHandSize(player)
        val lifeBefore = d.getLifeTotal(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bearer), d.player2).error)
        while (d.currentStep != Step.POSTCOMBAT_MAIN) {
            if (d.state.pendingDecision != null) d.decide(Choices(targets = listOf(d.player2)))
            else d.bothPass()
        }
        d.resolveAll(Choices(targets = listOf(d.player2)))

        // The equipped Grizzly Bears deals 4 combat damage, then the Sword deals 2 more.
        assertEquals(lifeBefore - 6, d.getLifeTotal(d.player2))
        assertEquals(handBefore + 1, d.getHandSize(player))
    }
}
